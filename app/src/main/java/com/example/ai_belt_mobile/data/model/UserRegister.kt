package com.example.ai_belt_mobile.data.model

import javax.security.auth.callback.PasswordCallback

data class UserRegister(
    val email: String,
    val name: String,
    val password: String,
    val verify: String,
    val phone: String
)
