package com.example.ai_belt_mobile.data.model

import javax.security.auth.callback.PasswordCallback

data class UserRegister(
    val email: String,
    val name: String,
    val password: String,
    val code: String,   //邮箱发过来的验证码
    val phone: String,
    val identity: Int,   //注册页面的上一个页面选择的身份 【0】残疾人 【1】家属
    val emergency: String   //注册的时候为空
)
