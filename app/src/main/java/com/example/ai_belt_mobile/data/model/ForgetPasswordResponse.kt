package com.example.ai_belt_mobile.data.model

data class ForgetPasswordResponse(
    val code: Int,
    val message: String,
    val data: ForgetPasswordData?
)

data class ForgetPasswordData(
    val id: Int,
    val phone: String,
    val name: String,
    val mail: String,
    val identity: Int,
    val code: String,
    val emergency: String
)
