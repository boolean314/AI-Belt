package com.example.ai_belt_mobile.network

import com.example.ai_belt_mobile.data.remote.mean
import retrofit2.http.POST

interface AiService {
    @POST("data")
    suspend fun sendRecognition(data: String): mean

}