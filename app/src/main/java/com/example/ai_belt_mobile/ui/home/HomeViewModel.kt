package com.example.ai_belt_mobile.ui.home

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_belt_mobile.ble.BleManager
import com.example.ai_belt_mobile.utils.AudioRecorderManager
import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface HomeBleState {
    data object Disconnected : HomeBleState
    data class Connecting(val name: String) : HomeBleState
    data class Connected(val name: String, val battery: Int?) : HomeBleState
    data class Error(val msg: String) : HomeBleState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    // region 语音模块 - 状态与字段
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recognitionResult = MutableStateFlow("\n")
    val recognitionResult: StateFlow<String> = _recognitionResult

    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text

    private var asr: ASR? = null
    private var audioRecorderManager: AudioRecorderManager? = null
    private var isRunning = false
    // endregion

    // region BLE模块 - 状态与字段
    private val _bleState = MutableStateFlow<HomeBleState>(HomeBleState.Disconnected)
    val bleState = _bleState.asStateFlow()

    private val _scanDeviceEvents = MutableSharedFlow<BluetoothDevice>(extraBufferCapacity = 32)
    val scanDeviceEvents = _scanDeviceEvents.asSharedFlow()

    private var currentDeviceName: String = "设备"
    private val bleManager: BleManager by lazy {
        BleManager(getApplication<Application>().applicationContext, bleListener)
    }

    private val targetMac = "68:25:DD:C3:07:22"
    // endregion

    init {
        initVoiceModule()
    }

    // region 语音模块 - API
    private fun initVoiceModule() {
        if (asr != null) return

        asr = ASR()
        asr?.registerCallbacks(object : AsrCallbacks {
            override fun onResult(asrResult: ASR.ASRResult, o: Any?) {
                val status = asrResult.status
                val result = asrResult.bestMatchText

                Log.d("HomeViewModel", "Recognition result: $result, status: $status")

                viewModelScope.launch(Dispatchers.Main) {
                    _recognitionResult.value = "识别结果: $result\n"
                    _text.value = result
                }

                if (status == 2) {
                    stopVoiceRecognition()
                }
            }

            override fun onError(asrError: ASR.ASRError, o: Any?) {
                val errorCode = asrError.code
                val errorMsg = asrError.errMsg

                Log.e("HomeViewModel", "Recognition error: $errorMsg, code: $errorCode")

                viewModelScope.launch(Dispatchers.Main) {
                    _recognitionResult.value = "识别出错: $errorMsg, 错误码: $errorCode\n"
                }

                stopVoiceRecognition()
            }

            override fun onBeginOfSpeech() {
                Log.d("HomeViewModel", "Begin of speech")
            }

            override fun onEndOfSpeech() {
                Log.d("HomeViewModel", "End of speech")
            }
        })
    }

    fun startVoiceRecognition() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isRunning) return@launch

            asr = null
            initVoiceModule()

            asr?.apply {
                language("zh_cn")
                domain("iat")
                accent("mandarin")
                vinfo(true)
                dwa("wpgs")

                val ret = start(System.currentTimeMillis().toString())
                if (ret == 0) {
                    isRunning = true

                    audioRecorderManager = AudioRecorderManager.getInstance()
                    audioRecorderManager?.registerCallBack(object : AudioRecorderManager.AudioDataCallback {
                        override fun onAudioData(data: ByteArray, size: Int) {
                            writeAudioData(data)
                        }

                        override fun onAudioVolume(db: Double, volume: Int) {
                            // no-op
                        }
                    })
                    audioRecorderManager?.startRecord()

                    withContext(Dispatchers.Main) {
                        _isRecording.value = true
                        _recognitionResult.value = "正在识别...\n"
                        _text.value = null
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _recognitionResult.value = "识别开启失败，错误码: $ret\n"
                    }
                }
            }
        }
    }

    fun stopVoiceRecognition() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isRunning) return@launch

            audioRecorderManager?.stopRecord()
            audioRecorderManager = null

            asr?.stop(false)
            isRunning = false

            withContext(Dispatchers.Main) {
                _isRecording.value = false
            }
        }
    }

    private fun writeAudioData(data: ByteArray) {
        if (isRunning) {
            asr?.write(data)
        }
    }
    // endregion

    // region BLE模块 - API
    private val bleListener = object : BleManager.Listener {
        override fun onScanFound(device: BluetoothDevice) {
            val addr = try { device.address ?: "" } catch (_: SecurityException) { "" }
            if (addr.equals(targetMac, ignoreCase = true)) {
                _scanDeviceEvents.tryEmit(device)
            }
        }

        override fun onConnected() {
            _bleState.value = HomeBleState.Connected(currentDeviceName, null)
            requestBattery()
        }

        override fun onDisconnected() {
            _bleState.value = HomeBleState.Disconnected
        }

        override fun onMessage(bytes: ByteArray) {
            val battery = parseBattery(bytes) ?: return
            val oldState = _bleState.value
            if (oldState is HomeBleState.Connected) {
                _bleState.value = oldState.copy(battery = battery)
            }
        }

        override fun onError(msg: String) {
            _bleState.value = HomeBleState.Error(msg)
        }
    }

    fun startScan() = bleManager.startScan()

    fun stopScan() = bleManager.stopScan()

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        currentDeviceName = safeDeviceName(device)
        _bleState.value = HomeBleState.Connecting(currentDeviceName)
        bleManager.stopScan()
        bleManager.connect(device, getApplication<Application>().applicationContext)
    }

    fun disconnect() = bleManager.disconnect()

    private fun requestBattery() {
        bleManager.write("BAT?\n".toByteArray())
    }

    private fun parseBattery(bytes: ByteArray): Int? {
        val text = bytes.toString(Charsets.UTF_8).trim()

        Regex("(?i)(?:BAT|BATT|BATTERY)\\s*[:=]\\s*(\\d{1,3})")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it.coerceIn(0, 100) }

        text.toIntOrNull()?.let { return it.coerceIn(0, 100) }

        if (bytes.size == 1) {
            val value = bytes[0].toInt() and 0xFF
            if (value in 0..100) return value
        }

        return null
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: device.address ?: "设备"
        } catch (_: SecurityException) {
            "设备"
        }
    }
    // endregion

    override fun onCleared() {
        // region 语音模块 - 资源释放
        audioRecorderManager?.stopRecord()
        asr?.stop(true)
        asr = null
        // endregion

        // region BLE模块 - 资源释放
        bleManager.disconnect()
        // endregion

        super.onCleared()
    }
}