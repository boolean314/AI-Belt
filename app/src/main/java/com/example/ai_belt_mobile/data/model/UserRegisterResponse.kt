package com.example.ai_belt_mobile.data.model

data class RegisterResponse(
    val code: Int,
    val message: String,
    val data: RegisterData?
)

data class RegisterData(
    val id: Int,
    val name: String,
    val mail: String,
    val phone: String,
    val identity: Int,
    val code: String,   //绑定码
    val emergency: String
)
