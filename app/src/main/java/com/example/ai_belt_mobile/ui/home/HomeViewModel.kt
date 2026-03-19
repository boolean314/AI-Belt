package com.example.ai_belt_mobile.ui.home

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_belt_mobile.ble.BleManager
import com.example.ai_belt_mobile.network.RetrofitClient
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
    private val _recognitionResult = MutableStateFlow("")
    val recognitionResult: StateFlow<String> = _recognitionResult

    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text

    private var asr: ASR? = null
    private var audioRecorderManager: AudioRecorderManager? = null
    
    @Volatile
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

    // region 语音模块 - API
    private fun initVoiceModule() {
        if (asr != null) return

        try {
            Log.d("HomeViewModel", "初始化ASR")
            asr = ASR()
            asr?.registerCallbacks(object : AsrCallbacks {
                override fun onResult(asrResult: ASR.ASRResult, o: Any?) {
                    val status = asrResult.status
                    val result = asrResult.bestMatchText

                    Log.d("HomeViewModel", "识别结果: $result, status: $status")

                    viewModelScope.launch(Dispatchers.Main) {
                        _recognitionResult.value = "识别结果: $result\n"
                        _text.value = result
                    }
                    
                    if (status == 2) {
                        isRunning = false
                        //等ai那边对接
                        /*viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val response = RetrofitClient.aiService.sendRecognition(result)
                                Log.d("HomeViewModel", "AI 响应: ${response.want}, ${response.toDo}, ${response.what}")

                            } catch (e: Exception) {
                                Log.e("HomeViewModel", "向ai请求失败", e)
                            }
                        }
                        */


                    }
                }

                override fun onError(asrError: ASR.ASRError, o: Any?) {
                    val errorCode = asrError.code
                    val errorMsg = asrError.errMsg
                    Log.e("HomeViewModel", "Recognition error: $errorMsg, code: $errorCode")
                    
                    isRunning = false
                    viewModelScope.launch(Dispatchers.Main) {
                        _recognitionResult.value = "识别出错: $errorMsg, 错误码: $errorCode\n"
                    }
                }

                override fun onBeginOfSpeech() {
                    Log.d("HomeViewModel", "Begin of speech")
                }

                override fun onEndOfSpeech() {
                    Log.d("HomeViewModel", "End of speech")
                }
            })
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error initializing ASR module", e)
            asr = null
        }
    }

    fun startVoiceRecognition() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isRunning) {
                Log.d("HomeViewModel", "此时isRunning已经是true，跳过启动")
                return@launch
            }
            isRunning = true

            // 重置资源
            asr = null
            audioRecorderManager = null
            
            try {
                initVoiceModule()
                asr?.apply {
                    language("zh_cn")
                    domain("slm")
                    accent("mandarin")
                    vinfo(true)
                    dwa("wpgs")
                }
                
                val ret = asr?.start("home_asr") ?: -1
                if (ret != 0) throw Exception("ASR 启动失败: $ret")

                Log.d("HomeViewModel", "ASR 成功启动")
                        
                audioRecorderManager = AudioRecorderManager.getInstance()
                audioRecorderManager?.registerCallBack(object : AudioRecorderManager.AudioDataCallback {
                    override fun onAudioData(data: ByteArray, size: Int) {
                        asr?.write(data)
                    }
                    override fun onAudioVolume(db: Double, volume: Int) {}
                })
                
                audioRecorderManager?.startRecord()
                Log.d("HomeViewModel", "开始录音识别")

                withContext(Dispatchers.Main) {
                    _recognitionResult.value = "正在识别...\n"
                    _text.value = null
                }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error in startVoiceRecognition", e)
                isRunning = false
                withContext(Dispatchers.Main) {
                    _recognitionResult.value = "识别开启失败：${e.message}\n"
                }
            }
        }
    }

    fun stopVoiceRecognition() {
        isRunning = false
        Log.d("HomeViewModel", "停止识别流程")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 停止录音
                audioRecorderManager?.stopRecord()
                audioRecorderManager = null
                Log.d("HomeViewModel", "录音已停止")

                // 2. 通知云端停止
                asr?.stop(false)
                Log.d("HomeViewModel", "ASR 已停止信号已发送")

            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error in stopVoiceRecognition", e)
            } finally {
                asr = null
                audioRecorderManager = null
            }
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

    @SuppressLint("MissingPermission")
    fun startScan() = bleManager.startScan()
    @SuppressLint("MissingPermission")
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

    fun sendOnCommand(text: String): Boolean {
        val state = _bleState.value
        if (state !is HomeBleState.Connected) return false
        bleManager.writeText(text)
        return true
    }
    // endregion

    override fun onCleared() {
        audioRecorderManager?.stopRecord()
        asr?.stop(true)
        asr = null
        bleManager.disconnect()

        super.onCleared()
    }
    


}