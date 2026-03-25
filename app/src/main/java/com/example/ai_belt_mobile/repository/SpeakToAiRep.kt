package com.example.ai_belt_mobile.repository

import android.util.Log
import com.example.ai_belt_mobile.data.remote.RecognitionRequest
import com.example.ai_belt_mobile.data.remote.AiResponse
import com.example.ai_belt_mobile.network.RetrofitClient

class SpeakToAiRep {
    suspend fun sendRecognition(result: RecognitionRequest): AiResponse {
        return try {
            RetrofitClient.aiService.sendRecognition(result)
        } catch (e: Exception) {
            Log.e("SpeakToAiRep", "sendRecognition: $e", e)
            AiResponse(0, "", null)
        }
    }
}