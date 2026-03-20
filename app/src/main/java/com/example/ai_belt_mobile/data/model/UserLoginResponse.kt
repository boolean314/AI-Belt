package com.example.ai_belt_mobile.data.model

data class LoginResponse(
    val code: Int,
    val message: String,
    val data: LoginData?
)

data class LoginData(
    val id: Int,
    val phone: String,
    val name: String,
    val mail: String,
    val identity: Int,
    val code: String?,
    val emergency: String?
)