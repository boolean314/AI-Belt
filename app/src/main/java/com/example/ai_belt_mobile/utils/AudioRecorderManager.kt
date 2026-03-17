package com.example.ai_belt_mobile.utils

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorderManager private constructor() {
    
    private val TAG = "AudioRecorder"
    private val sampleRateInHz = 16000
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channels = AudioFormat.CHANNEL_IN_MONO
    private val bufferSize: Int
    private var mRecorder: AudioRecord? = null
    private val isStart = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var callback: AudioDataCallback? = null
    
    init {
        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channels, audioFormat)
        mRecorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channels, audioFormat, bufferSize)
    }
    
    fun registerCallBack(callback: AudioDataCallback) {
        this.callback = callback
    }
    
    companion object {
        private var instance: AudioRecorderManager? = null
        
        fun getInstance(): AudioRecorderManager {
            if (instance == null) {
                synchronized(AudioRecorderManager::class.java) {
                    if (instance == null) {
                        instance = AudioRecorderManager()
                    }
                }
            }
            return instance!!
        }
    }
    
    /**
     * 销毁线程方法
     */
    private fun destroyThread() {
        synchronized(this) {
            try {
                isStart.set(false)
                if (recordThread != null && recordThread!!.isAlive) {
                    try {
                        recordThread!!.interrupt()
                        recordThread!!.join() // 确保线程已终止
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        recordThread = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                recordThread = null
            }
        }
    }
    
    /**
     * 启动录音线程
     */
    private fun startThread() {
        destroyThread()
        isStart.set(true)
        if (recordThread == null) {
            recordThread = Thread(recordRunnable)
            recordThread!!.start()
        }
    }
    
    /**
     * 录音线程
     */
    private val recordRunnable = Runnable { 
        try {
            mRecorder?.let { recorder ->
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                val tempBuffer = ByteArray(bufferSize)
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    stopRecord()
                    return@Runnable
                }
                recorder.startRecording()
                
                while (isStart.get()) {
                    synchronized(this) {
                        mRecorder?.let {
                            val bytesRecord = it.read(tempBuffer, 0, bufferSize)
                            if (bytesRecord == AudioRecord.ERROR_INVALID_OPERATION || bytesRecord == AudioRecord.ERROR_BAD_VALUE) {
                                return@synchronized
                            }
                            if (bytesRecord != 0 && bytesRecord != -1 && isStart.get()) {
                                // 计算音量
                                var sumSquares = 0.0
                                val sampleCount = bytesRecord / 2  // 每个样本16位(2字节)

                                for (i in 0 until bytesRecord step 2) {
                                    // 将两个字节转换为一个16位短整型
                                    val sample = (tempBuffer[i].toInt() and 0xFF or (tempBuffer[i + 1].toInt() and 0xFF shl 8)).toShort()
                                    // 计算平方和
                                    sumSquares += sample.toDouble() * sample.toDouble()
                                }

                                // 计算RMS (均方根)
                                val rms = Math.sqrt(sumSquares / sampleCount)

                                // 转换为分贝值 (防止除以0)
                                var db = -120.0 // 默认极低值
                                if (rms > 1e-10) {  // 避免log(0)
                                    db = 20 * Math.log10(rms / 32767.0)
                                }

                                // 映射到0-9音量等级
                                val volume = if (db > -60) {
                                    Math.min(9, Math.max(0, ((db + 60) * 9 / 40.0).toInt()))
                                } else {
                                    0
                                }
                                
                                callback?.onAudioVolume(db, volume)
                                // 将音频数据传递给回调
                                callback?.onAudioData(tempBuffer, bytesRecord)
                            } else {
                                return@synchronized
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "录音异常:${e.toString()}")
            e.printStackTrace()
        } finally {
            mRecorder?.let {
                it.stop()
                it.release()
                mRecorder = null
            }
        }
    }
    
    /**
     * 启动录音
     */
    fun startRecord() {
        try {
            startThread()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 停止录音
     */
    fun stopRecord() {
        destroyThread()
        synchronized(this) {
            try {
                callback = null
                mRecorder?.let {
                    if (it.state == AudioRecord.STATE_INITIALIZED) {
                        it.stop()
                    }
                    it.release()
                    mRecorder = null
                }
                instance = null
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                instance = null // 确保单例实例被释放
            }
        }
    }
    
    interface AudioDataCallback {
        fun onAudioData(data: ByteArray, size: Int)
        fun onAudioVolume(db: Double, volume: Int)
    }
}