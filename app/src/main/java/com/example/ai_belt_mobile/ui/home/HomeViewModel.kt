package com.example.ai_belt_mobile.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_belt_mobile.utils.AudioRecorderManager
import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recognitionResult = MutableStateFlow("\n")
    val recognitionResult: StateFlow<String> = _recognitionResult

    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text

    private var asr: ASR? = null
    private var audioRecorderManager: AudioRecorderManager? = null
    private var isRunning = false

    init {
        initASR()
    }

    private fun initASR() {
        if (asr == null) {
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
                        // 识别结束
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
    }

    fun startVoiceRecognition() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isRunning) {
                Log.d("HomeViewModel", "Recognition is already running")

            }

            // 每次开始识别前重新初始化ASR，确保能多次识别
            asr = null
            initASR()

            asr?.apply {
                language("zh_cn")
                domain("iat")
                accent("mandarin")
                vinfo(true)
                dwa("wpgs")

                val ret = start(System.currentTimeMillis().toString())
                if (ret == 0) {
                    isRunning = true

                    // 初始化并启动录音
                    audioRecorderManager = AudioRecorderManager.getInstance()
                    audioRecorderManager?.registerCallBack(object : AudioRecorderManager.AudioDataCallback {
                        override fun onAudioData(data: ByteArray, size: Int) {
                            // 将音频数据传递给ASR
                            writeAudioData(data)
                        }

                        override fun onAudioVolume(db: Double, volume: Int) {
                            // 可以在这里处理音量变化
                        }
                    })
                    audioRecorderManager?.startRecord()

                    withContext(Dispatchers.Main) {
                        _isRecording.value = true
                        _recognitionResult.value = "正在识别...\n"
                        _text.value = null // 清空之前的识别结果
                    }
                } else {
                    Log.e("HomeViewModel", "Failed to start recognition, error code: $ret")
                    withContext(Dispatchers.Main) {
                        _recognitionResult.value = "识别开启失败，错误码: $ret\n"
                    }
                }
            }
        }
    }

    fun stopVoiceRecognition() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isRunning) {
                // 停止录音
                audioRecorderManager?.stopRecord()
                audioRecorderManager = null

                // 停止语音识别
                asr?.stop(false)
                isRunning = false

                withContext(Dispatchers.Main) {
                    _isRecording.value = false
                }
            }
        }
    }

    private fun writeAudioData(data: ByteArray) {
        if (isRunning) {
            asr?.write(data)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderManager?.stopRecord()
        asr?.stop(true)
        asr = null
    }
}