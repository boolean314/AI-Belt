package com.example.ai_belt_mobile.network

import com.example.ai_belt_mobile.data.remote.AiResponse
import com.example.ai_belt_mobile.data.remote.RecognitionRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AiService {
    @POST("/speak/detect")
    suspend fun sendRecognition(@Body result: RecognitionRequest): AiResponse

}