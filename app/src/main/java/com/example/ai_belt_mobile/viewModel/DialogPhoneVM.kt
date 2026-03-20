package com.example.ai_belt_mobile.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DialogPhoneVM : ViewModel() {

    private val _newPhone = MutableStateFlow("")
    val newPhone: StateFlow<String> = _newPhone.asStateFlow()

    fun updateNewPhone(value: String) {
        _newPhone.value = value
    }

    fun canSubmit(): Boolean {
        return _newPhone.value.trim().isNotEmpty()
    }

    fun validate(): String? {
        val value = _newPhone.value.trim()
        return when {
            value.isEmpty() -> "请输入手机号"
            value.length != 11 -> "手机号长度不正确"
            else -> null
        }
    }
}