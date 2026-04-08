package com.example.ai_belt_mobile.utils

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class   AudioRecorderManager private constructor() {

    private val TAG = "AudioRecorder"
    private val sampleRateInHz = 16000
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channels = AudioFormat.CHANNEL_IN_MONO
    private val bufferSize: Int
    private var mRecorder: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var callback: AudioDataCallback? = null

    // 使用专门的锁对象，避免 synchronized(this) 带来的潜在死锁
    private val recorderLock = Any()

    init {
        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channels, audioFormat)
        initRecorder()
    }

    @SuppressLint("MissingPermission")
    private fun initRecorder() {
        try {
            mRecorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channels, audioFormat, bufferSize)
            if (mRecorder?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                mRecorder = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioRecord", e)
            mRecorder = null
        }
    }

    fun registerCallBack(callback: AudioDataCallback) {
        this.callback = callback
    }

    companion object {
        @Volatile
        private var instance: AudioRecorderManager? = null

        fun getInstance(): AudioRecorderManager {
            return instance ?: synchronized(this) {
                instance ?: AudioRecorderManager().also { instance = it }
            }
        }
    }

    /**
     * 录音线程任务
     */
    private val recordRunnable = Runnable {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            
            val recorder = synchronized(recorderLock) { mRecorder }
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Recorder not ready")
                return@Runnable
            }

            try {
                recorder.startRecording()
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for recording", e)
                return@Runnable
            }
            
            val tempBuffer = ByteArray(bufferSize)

            while (isRecording.get()) {
                val bytesRead = recorder.read(tempBuffer, 0, bufferSize)
                
                if (bytesRead <= 0) {
                    if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION || bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Read error: $bytesRead")
                        break
                    }
                    continue
                }

                // 处理音频数据
                processAudioData(tempBuffer, bytesRead)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording exception", e)
        } finally {
            releaseRecorderInternal()
        }
    }

    private fun processAudioData(tempBuffer: ByteArray, bytesRead: Int) {
        val currentCallback = callback ?: return
        
        // 计算音量 (RMS)
        var sumSquares = 0.0
        val sampleCount = bytesRead / 2
        for (i in 0 until bytesRead step 2) {
            val sample = (tempBuffer[i].toInt() and 0xFF or (tempBuffer[i + 1].toInt() and 0xFF shl 8)).toShort()
            sumSquares += sample.toDouble() * sample.toDouble()
        }

        val rms = Math.sqrt(sumSquares / sampleCount)
        var db = -120.0
        if (rms > 1e-10) {
            db = 20 * Math.log10(rms / 32767.0)
        }

        val volume = if (db > -60) {
            Math.min(9, Math.max(0, ((db + 60) * 9 / 40.0).toInt()))
        } else {
            0
        }

        currentCallback.onAudioVolume(db, volume)
        currentCallback.onAudioData(tempBuffer.copyOf(bytesRead), bytesRead)
    }

    /**
     * 启动录音
     */
    fun startRecord() {
        if (isRecording.get()) return
        
        synchronized(recorderLock) {
            if (mRecorder == null) initRecorder()
            isRecording.set(true)
            recordThread = Thread(recordRunnable, "AudioRecordThread").apply { start() }
        }
    }

    /**
     * 停止录音
     */
    fun stopRecord() {
        if (!isRecording.get()) return
        
        isRecording.set(false)
        // 注意：这里不调用 join()，让线程自己通过循环条件自然退出并清理资源
        // 这样可以彻底避免死锁
        callback = null
    }

    /**
     * 内部清理资源
     */
    private fun releaseRecorderInternal() {
        synchronized(recorderLock) {
            try {
                mRecorder?.let {
                    if (it.state == AudioRecord.STATE_INITIALIZED) {
                        if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            it.stop()
                        }
                    }
                    it.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing recorder", e)
            } finally {
                mRecorder = null
                recordThread = null
                instance = null // 释放单例，下次重新创建
            }
        }
    }

    interface AudioDataCallback {
        fun onAudioData(data: ByteArray, size: Int)
        fun onAudioVolume(db: Double, volume: Int)
    }
}