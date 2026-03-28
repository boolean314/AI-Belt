package com.example.ai_belt_mobile.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.iflytek.sparkchain.core.tts.OnlineTTS
import com.iflytek.sparkchain.core.tts.TTS
import com.iflytek.sparkchain.core.tts.TTSCallbacks
import java.util.*

class SparkChainTTSManager private constructor() {
    private var isInitialized = false
    private var onlineTTS: OnlineTTS? = null
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var isSpeaking = false
    private var audioPlayHandler: Handler? = null
    private val speakQueue = LinkedList<String>()
    private val SAMPLE_RATE = 16000 // 采样率
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO // 单声道输出
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // PCM 16位编码

    companion object {
        @Volatile
        private var instance: SparkChainTTSManager? = null

        fun getInstance(): SparkChainTTSManager {
            return instance ?: synchronized(this) {
                instance ?: SparkChainTTSManager().also {
                    instance = it
                    it.initAudioPlayThread()
                }
            }
        }
    }

    private fun initAudioPlayThread() {
        Thread {
            Looper.prepare()
            audioPlayHandler = object : Handler(Looper.myLooper()!!) {
                override fun handleMessage(msg: Message) {
                    super.handleMessage(msg)
                    when (msg.what) {
                        0x0000 -> initAudioTrack() // AUDIOPLAYER_INIT
                        0x0001 -> startAudioTrack() // AUDIOPLAYER_START
                        0x0002 -> writeAudioData(msg.obj as ByteArray) // AUDIOPLAYER_WRITE
                        0x0003 -> stopAudioTrack() // AUDIOPLAYER_END
                    }
                }
            }
            Looper.loop()
        }.start()
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize, AudioTrack.MODE_STREAM)
        audioPlayHandler?.sendEmptyMessage(0x0001)
    }

    private fun startAudioTrack() {
        audioTrack?.let {
            isPlaying = true
            it.play()
        }
    }

    private fun writeAudioData(audioData: ByteArray) {
        audioTrack?.let {
            if (isPlaying && audioData.isNotEmpty()) {
                it.write(audioData, 0, audioData.size)
            }
        }
    }

    private fun stopAudioTrack() {
        audioTrack?.let {
            it.stop()
            isPlaying = false
        }
    }

    fun init(context: Context) {
        if (isInitialized) return

        // SparkChain SDK已经在Application中初始化，这里只需要标记为已初始化
        isInitialized = true
        Log.d("SparkChainTTSManager", "TTS初始化成功")
    }

    fun speak(text: String) {
        if (!isInitialized) {
            Log.e("SparkChainTTSManager", "TTS未初始化")
            return
        }

        // 将文本添加到队列
        synchronized(speakQueue) {
            speakQueue.add(text)
            Log.d("SparkChainTTSManager", "添加到播报队列: $text, 队列长度: ${speakQueue.size}")
        }

        // 如果当前没有正在播报，开始处理队列
        if (!isSpeaking) {
            processQueue()
        }
    }

    /**
     * 处理播报队列
     */
    private fun processQueue() {
        synchronized(speakQueue) {
            if (speakQueue.isEmpty()) {
                isSpeaking = false
                return
            }

            // 取出队列中的第一个文本
            val text = speakQueue.poll()
            if (text == null) {
                isSpeaking = false
                return
            }

            isSpeaking = true
            Log.d("SparkChainTTSManager", "开始播报: $text")

            // 停止之前的合成
            stop()

            // 初始化音频轨道
            audioPlayHandler?.sendEmptyMessage(0x0000)

            // 创建OnlineTTS实例
            onlineTTS = OnlineTTS("xiaoyan") // 使用晓燕发音人
            onlineTTS?.apply {
                speed(50) // 语速：50为默认值
                pitch(50) // 语调：50为默认值
                volume(80) // 音量：80为较大音量
                bgs(0) // 无背景音
                registerCallbacks(object : TTSCallbacks {
                    override fun onResult(result: TTS.TTSResult, o: Any?) {
                        val audio = result.getData() // 音频数据
                        val status = result.getStatus() // 数据状态

                        if (audio != null && audio.isNotEmpty()) {
                            audioPlayHandler?.obtainMessage(0x0002, audio)?.sendToTarget()
                        }

                        if (status == 2) {
                            // 音频合成回调结束状态
                            audioPlayHandler?.sendEmptyMessage(0x0003)
                            // 处理下一个队列项
                            processQueue()
                        }
                    }

                    override fun onError(error: TTS.TTSError, o: Any?) {
                        val errCode = error.getCode()
                        val errMsg = error.getErrMsg()
                        Log.e("SparkChainTTSManager", "合成出错！code:$errCode, msg:$errMsg")
                        stop()
                        // 处理下一个队列项
                        processQueue()
                    }
                })

                // 开始合成
                val ret = aRun(text)
                if (ret != 0) {
                    Log.e("SparkChainTTSManager", "合成出错! ret=$ret")
                    stop()
                    // 处理下一个队列项
                    processQueue()
                }
            }
        }
    }

    fun stop() {
        if (isInitialized) {
            onlineTTS?.let {
                it.stop()
                onlineTTS = null
            }
            audioPlayHandler?.sendEmptyMessage(0x0003)
            Log.d("SparkChainTTSManager", "停止语音合成")
        }
    }

    fun release() {
        // 无论 isInitialized 状态如何，只要调用 release 就应该彻底重置
        stop()
        audioTrack?.release()
        audioTrack = null
        // 停止并清理 HandlerThread (如果有的话)
        audioPlayHandler?.removeCallbacksAndMessages(null)
        audioPlayHandler = null
        isInitialized = false
        //把静态实例置空，这样下次 getInstance 才会跑全新的构造函数
        instance = null
        Log.d("SparkChainTTSManager", "TTS单例已彻底销毁并重置实例")
    }
}