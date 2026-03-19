package com.example.ai_belt_mobile.repository

import android.util.Log
import com.example.ai_belt_mobile.data.remote.mean
import com.example.ai_belt_mobile.network.RetrofitClient

class SpeakToAiRep {
    suspend fun sendRecognition(data: String): mean {
        return try {
            RetrofitClient.aiService.sendRecognition(data)
        } catch (e: Exception) {
            Log.e("SpeakToAiRep", "sendRecognition: $e", e)
            mean("", "", "")
        }
    }
}