package com.example.ai_belt_mobile.data.model

data class ChangePassword(
    val userId: Int,
    val oldPassword: String,
    val newPassword: String
)
