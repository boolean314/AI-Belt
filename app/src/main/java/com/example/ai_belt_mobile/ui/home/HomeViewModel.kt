package com.example.ai_belt_mobile.ui.home

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_belt_mobile.ble.BleManager
import com.example.ai_belt_mobile.data.local.UserSessionStore
import com.example.ai_belt_mobile.data.remote.RecognitionRequest
import com.example.ai_belt_mobile.navigation.LocationManager
import com.example.ai_belt_mobile.voice.SparkChainTTSManager
import com.example.ai_belt_mobile.navigation.NavigationManager
import com.example.ai_belt_mobile.repository.SpeakToAiRep
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.ai_belt_mobile.data.remote.AiResponse
import kotlin.io.writeText
import kotlin.text.get
import kotlin.text.toHexString

sealed interface HomeBleState {
    data object Disconnected : HomeBleState
    data class Connecting(val name: String) : HomeBleState
    data class Connected(val name: String, val battery: Int?) : HomeBleState
    data class Error(val msg: String) : HomeBleState
}

sealed interface HomeNavigationState {
    data object Idle : HomeNavigationState
    data object RequestingPermission : HomeNavigationState
    data object GettingLocation : HomeNavigationState
    data object Navigating : HomeNavigationState
    data class Error(val msg: String) : HomeNavigationState
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
    private var isEmergencyVoiceRecognition = false

    // 添加方法来设置标志输入的语音是否为紧急情况时触发的
    fun setEmergencyVoiceRecognition(emergency: Boolean) {
        isEmergencyVoiceRecognition = emergency
    }

    // endregion

    init{
        // 监听来自 NavigationManager 的偏角数据，由持有真实蓝牙连接的 ViewModel 负责发送

    }

    // region BLE模块 - 状态与字段
    private val _bleState = MutableStateFlow<HomeBleState>(HomeBleState.Disconnected)
    val bleState = _bleState.asStateFlow()

    private val _scanDeviceEvents = MutableSharedFlow<BluetoothDevice>(extraBufferCapacity = 32)
    val scanDeviceEvents = _scanDeviceEvents.asSharedFlow()

    private var currentDeviceName: String = "设备"
    private val bleManager: BleManager by lazy {
        BleManager(getApplication<Application>().applicationContext, bleListener)
    }
    private val bleWriteMutex = Mutex() // Mutex for serializing BLE writes

    private val targetMac = "E0:72:A1:F3:E2:91"

    private val _bleEmergencyEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val bleEmergencyEvents = _bleEmergencyEvents.asSharedFlow()

    // Hotspot Credentials storage and UI event
    private val PREFS_NAME = "HotspotPrefs"
    private val KEY_HOTSPOT_SSID = "hotspot_ssid"
    private val KEY_HOTSPOT_PASSWORD = "hotspot_password"

    // Emits when the UI should show the hotspot input dialog
    private val _showHotspotInputDialog = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showHotspotInputDialog = _showHotspotInputDialog.asSharedFlow()

    // endregion
    
    // region 导航模块 - 状态与字段
    private val _navigationState = MutableStateFlow<HomeNavigationState>(HomeNavigationState.Idle)
    val navigationState: StateFlow<HomeNavigationState> = _navigationState
    
    private val locationManager: LocationManager by lazy {
        LocationManager(getApplication<Application>().applicationContext)
    }
    
    private val navigationManager: NavigationManager by lazy {
        NavigationManager.getInstance(getApplication<Application>().applicationContext)
    }
    
    private val ttsManager: SparkChainTTSManager by lazy {
        SparkChainTTSManager.getInstance()
    }

    private val start = MutableStateFlow(false)
    val startNavigation: StateFlow<Boolean> = start.asStateFlow()
    private val dest= MutableStateFlow("")
    val destination: StateFlow<String> = dest.asStateFlow()
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
                        isRunning = false
                        // 只在非紧急情况下向AI发送请求
                        if (!isEmergencyVoiceRecognition) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    Log.d("HomeViewModel", "向ai请求: $result")
                                    val response = SpeakToAiRep().sendRecognition(
                                        RecognitionRequest(result)
                                    )
                                    Log.d("HomeViewModel", "AI 响应: ${response.code}, ${response.message},${response.mean?.want},${response.mean?.where},${response.mean?.what}")
                                    if(response.code==200){
                                        when(response.mean?.want){
                                            "navigation"->{
                                                //后续将点击开始导航的逻辑移动到这里
                                                dest.value=response.mean.where?:""
                                                start.value = true
                                            }
                                            "recognition"->{
                                                response.mean.what?.let {
                                                    Log.d("HomeViewModel", "识别结果: $it")
                                                    ttsManager.speak(it)
                                                }
                                            }
                                            else->{
                                                ttsManager.speak("暂不支持该功能")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("HomeViewModel", "向ai请求失败", e)
                                }
                            }
                        }

                        isEmergencyVoiceRecognition = false

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
    fun resetStartNavigation() {
        start.value = false
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

    fun clearRecognitionCache() {
        _recognitionResult.value = ""
        _text.value = null
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
            // 这里先不请求，等待CCCD订阅成功
        }

        override fun onNotifyReady() {
            // requestBattery() call removed from here to prevent collision with hotspot credentials
        }

        override fun onDisconnected() {
            _bleState.value = HomeBleState.Disconnected
        }

        override fun onMessage(bytes: ByteArray) {
            val text = bytes.toString(Charsets.UTF_8).trim()

            Log.d(
                "BLE_RX",
                "len=${bytes.size}, utf8='${text.replace("\n", "\\n").replace("\r", "\\r")}', hex=${bytes.toHexString()}"
            )

            // EMERGENCY：兼容带换行/前后缀
            if (text.contains("EMERGENCY", ignoreCase = true)) {
                Log.d("BLE_RX", "matched EMERGENCY -> emit emergency event")
                _bleEmergencyEvents.tryEmit(Unit)
                return
            }

            // 电量：优先二进制，再兜底文本
            val battery = parseBatteryPayload(bytes, text)
            if (battery == null) {
                Log.d("BLE_RX", "battery parse failed")
                return
            }

            Log.d("BLE_RX", "battery parsed=$battery")
            val oldState = _bleState.value
            if (oldState is HomeBleState.Connected) {
                _bleState.value = oldState.copy(battery = battery.coerceIn(0, 100))
                Log.d("BLE_RX", "ui battery updated=$battery")
            } else {
                Log.d("BLE_RX", "ignore battery update: not connected state=$oldState")
            }
        }

        override fun onError(msg: String) {
            _bleState.value = HomeBleState.Error(msg)
        }

        override fun onRequestHotspotCredentials() {
            Log.d("HomeViewModel", "BLEManager requested hotspot credentials (UI input).")
            val appContext = getApplication<Application>().applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
            val storedSsid = prefs.getString(KEY_HOTSPOT_SSID, null)
            val storedPassword = prefs.getString(KEY_HOTSPOT_PASSWORD, null)

            if (storedSsid == null || storedPassword == null) {
                Log.d("HomeViewModel", "No stored hotspot credentials found. Requesting user input.")
                viewModelScope.launch {
                    _showHotspotInputDialog.emit(Unit)
                }
            }
            // 有存储凭据时，等待 onMtuReady 发送
        }

        override fun onMtuReady() {
            Log.d("HomeViewModel", "MTU ready, sending hotspot credentials and user ID")
            val appContext = getApplication<Application>().applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
            val storedSsid = prefs.getString(KEY_HOTSPOT_SSID, null)
            val storedPassword = prefs.getString(KEY_HOTSPOT_PASSWORD, null)

            if (storedSsid != null && storedPassword != null) {
                Log.d("HomeViewModel", "Found stored hotspot credentials. Sending automatically.")
                Log.d("HomeViewModel", "发送热点凭据: SSID=$storedSsid, Password=${storedPassword.replace(Regex("."), "*")}")
                // 使用 Dispatchers.IO 确保在后台线程执行
                viewModelScope.launch(Dispatchers.IO) {
                    Log.d("HomeViewModel", "【onMtuReady】进入协程，准备获取锁")
                    bleWriteMutex.withLock {
                        Log.d("HomeViewModel", "【onMtuReady】获取锁成功")
                        val hotspotSent = bleManager.sendHotspotCredentials(storedSsid, storedPassword)
                        Log.d("HomeViewModel", "热点凭据发送结果: $hotspotSent")
                        
                        // 等待2秒后发送用户ID（无论热点凭据发送是否成功）
                        Log.d("HomeViewModel", "【onMtuReady】等待2秒后发送用户ID...")
                        kotlinx.coroutines.delay(2000)
                        
                        // 已持有锁，调用 alreadyLocked=true 版本避免死锁
                        val idSent = sendCurrentDisabilityIdToBoard(true)
                        Log.d("HomeViewModel", "用户ID发送结果: $idSent")
                    }
                }
            } else {
                Log.d("HomeViewModel", "No stored hotspot credentials, waiting for user input")
            }
        }
    }

    fun setHotspotCredentials(ssid: String, password: String) {
        val appContext = getApplication<Application>().applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_HOTSPOT_SSID, ssid)
            .putString(KEY_HOTSPOT_PASSWORD, password)
            .apply()
        Log.d("HomeViewModel", "Hotspot credentials saved to SharedPreferences. Sending to BLE device.")
        Log.d("HomeViewModel", "发送热点凭据: SSID=$ssid, Password=${password.replace(Regex("."), "*")}")
        viewModelScope.launch {
            bleWriteMutex.withLock {
                bleManager.sendHotspotCredentials(ssid, password)
                sendCurrentDisabilityIdToBoard()
            }
        }
    }

    // New method to explicitly request the hotspot input dialog
    fun requestHotspotInputDialog() {
        viewModelScope.launch {
            _showHotspotInputDialog.emit(Unit)
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

    fun disconnect() {
        bleManager.disconnect()
        _bleState.value = HomeBleState.Disconnected
    }

    private fun requestBattery() {
        Log.d("HomeViewModel", "请求电池信息: BAT?\n")
        viewModelScope.launch { // Launch a coroutine for suspend call
            bleWriteMutex.withLock {
                bleManager.write("BAT?\n".toByteArray())
            }
        }
    }

    private suspend fun sendCurrentDisabilityIdToBoard(): Boolean {
        return sendCurrentDisabilityIdToBoard(false)
    }

    private suspend fun sendCurrentDisabilityIdToBoard(alreadyLocked: Boolean): Boolean {
        val appContext = getApplication<Application>().applicationContext
        val session = UserSessionStore.get(appContext)

        if (session == null) {
            Log.w("HomeViewModel", "【用户ID发送】失败：未登录会话，session=null")
            return false
        }
        
        Log.d("HomeViewModel", "【用户ID发送】会话信息 - id=${session.id}, identity=${session.identity}, name=${session.name}, phone=${session.phone}")
        
        if (session.identity != 0) {
            Log.d("HomeViewModel", "【用户ID发送】跳过：当前不是残疾人端(identity=${session.identity})")
            return false
        }

        val payload = "id:${session.id}\n"
        Log.d("HomeViewModel", "【用户ID发送】准备发送 - payload='$payload', 长度=${payload.length}")
        
        // 根据是否已持有锁决定是否再次获取锁
        val result = if (alreadyLocked) {
            Log.d("HomeViewModel", "【用户ID发送】已持有锁，直接调用 bleManager.writeText")
            bleManager.writeText(payload)
        } else {
            bleWriteMutex.withLock {
                Log.d("HomeViewModel", "【用户ID发送】获取锁成功，调用 bleManager.writeText")
                bleManager.writeText(payload)
            }
        }
        
        Log.d("HomeViewModel", "【用户ID发送】发送结果 - success=$result, payload='$payload'")
        
        if (!result) {
            Log.w("HomeViewModel", "【用户ID发送】警告：发送返回false，请检查BLE连接状态")
        }
        
        return result
    }

    private fun parseBatteryText(text: String): Int? {
        // 支持 BAT:78 / BATTERY=78 / 78
        Regex("(?i)(?:BAT|BATT|BATTERY)\\s*[:=]\\s*(\\d{1,3})")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it.coerceIn(0, 100) }

        text.toIntOrNull()?.let { return it.coerceIn(0, 100) }

        // 兜底：提取字符串里的首个数字（例如 "78%"）
        Regex("(\\d{1,3})").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it.coerceIn(0, 100)
        }
        return null
    }

    private fun parseBatteryPayload(bytes: ByteArray, text: String): Int? {
        // A. 1字节整型（最常见）
        if (bytes.size == 1) {
            val v = bytes[0].toInt() and 0xFF
            if (v in 0..100) {
                Log.d("BLE_RX", "battery parsed by uint8: $v")
                return v
            }
        }

        // B. 4字节整型（先小端，再大端）
        if (bytes.size >= 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF

            val littleEndian = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            if (littleEndian in 0..100) {
                Log.d("BLE_RX", "battery parsed by int32 LE: $littleEndian")
                return littleEndian
            }

            val bigEndian = b3 or (b2 shl 8) or (b1 shl 16) or (b0 shl 24)
            if (bigEndian in 0..100) {
                Log.d("BLE_RX", "battery parsed by int32 BE: $bigEndian")
                return bigEndian
            }
        }

        // C. 文本兜底
        return parseBatteryText(text)
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: device.address ?: "设备"
        } catch (_: SecurityException) {
            "设备"
        }
    }

    suspend fun sendOnCommand(text: String): Boolean {
        Log.d("HomeViewModel", "【sendOnCommand】开始发送指令 - text='$text', 长度=${text.length}")
        
        val result = bleWriteMutex.withLock {
            Log.d("HomeViewModel", "【sendOnCommand】获取锁成功，调用 bleManager.writeText")
            val writeResult = bleManager.writeText(text)
            Log.d("HomeViewModel", "【sendOnCommand】bleManager.writeText 返回: $writeResult")
            writeResult
        }
        
        Log.d("HomeViewModel", "【sendOnCommand】发送完成 - result=$result, text='$text'")
        return result
    }

    // region 导航模块 - API
    fun initNavigation() {
        ttsManager.init(getApplication<Application>().applicationContext)
        viewModelScope.launch {
            navigationManager.beltAngleFlow.collect { angle ->
                if (angle > 30.0 || angle < -30.0) {
                    val angleString = "change:$angle"
                    Log.d("HomeViewModel", "发送角度偏离数据到板子: $angleString")
                    val sent = sendOnCommand(angleString) // sendOnCommand is now suspend
                    Log.i("HomeViewModel", "后台发送偏角数据: $angleString, 发送结果: $sent")
                }
            }
        }
    }
    
    fun startNavigation(destination: String, hasLocationPermission: Boolean, onNavigationStarted: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                _navigationState.value = HomeNavigationState.RequestingPermission
                
                if (hasLocationPermission) {
                    _navigationState.value = HomeNavigationState.GettingLocation
                    navigationManager.init()
                    // 获取当前位置
                    locationManager.getAccurateLocation { location ->
                        if (location != null) {
                            viewModelScope.launch(Dispatchers.Main) {
                                _navigationState.value = HomeNavigationState.Navigating
                                Log.d("HomeViewModel", "当前位置: ${location.latitude}, ${location.longitude}")
                                // 开始导航
                                navigationManager.startWalkingNavigation(
                                    startLocation = location,
                                    destination = destination,
                                    onNavigationStarted = onNavigationStarted,
                                    onError = onError
                                )
                            }
                        } else {
                            viewModelScope.launch(Dispatchers.Main) {
                                _navigationState.value = HomeNavigationState.Error("无法获取当前位置")
                                onError("无法获取当前位置")
                            }
                        }
                    }
                } else {
                    viewModelScope.launch(Dispatchers.Main) {
                        _navigationState.value = HomeNavigationState.Error("需要定位权限才能导航")
                        onError("需要定位权限才能导航")
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "导航失败: ${e.message}")
                viewModelScope.launch(Dispatchers.Main) {
                    _navigationState.value = HomeNavigationState.Error("导航失败: ${e.message}")
                    onError("导航失败: ${e.message}")
                }
            }
        }
    }

    
    fun releaseNavigation() {
        locationManager.stop()
    }
    // endregion

    override fun onCleared() {
        audioRecorderManager?.stopRecord()
        asr?.stop(true)
        asr = null
        bleManager.disconnect()
        releaseNavigation()

        super.onCleared()
    }
    

    private fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { each -> "%02X".format(each) }

}