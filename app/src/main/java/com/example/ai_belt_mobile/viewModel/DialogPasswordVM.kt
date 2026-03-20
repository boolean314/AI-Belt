package com.example.ai_belt_mobile.viewModel

import android.util.Patterns
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DialogPasswordVM : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _verifyCode = MutableStateFlow("")
    val verifyCode: StateFlow<String> = _verifyCode.asStateFlow()

    private val _newPassword = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword.asStateFlow()

    fun updateEmail(value: String) { _email.value = value }
    fun updateVerifyCode(value: String) { _verifyCode.value = value }
    fun updateNewPassword(value: String) { _newPassword.value = value }

    fun canSubmit(): Boolean {
        return _email.value.trim().isNotEmpty() &&
                _verifyCode.value.trim().isNotEmpty() &&
                _newPassword.value.trim().isNotEmpty()
    }

    fun validateEmail(): String? {
        val value = _email.value.trim()
        return when {
            value.isEmpty() -> "请输入邮箱"
            !Patterns.EMAIL_ADDRESS.matcher(value).matches() -> "邮箱格式不正确"
            else -> null
        }
    }

    fun validateAll(): String? {
        validateEmail()?.let { return it }
        if (_verifyCode.value.trim().isEmpty()) return "请输入验证码"
        if (_newPassword.value.trim().isEmpty()) return "请输入新密码"
        return null
    }
}