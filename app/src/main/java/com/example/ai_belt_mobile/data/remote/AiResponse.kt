package com.example.ai_belt_mobile.data.remote

data class AiResponse(
    val code: Int,
    val message: String,
    val mean: Mean?
)