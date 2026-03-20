package com.example.ai_belt_mobile.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DialogUserNameVM : ViewModel() {

    private val _newUserName = MutableStateFlow("")
    val newUserName: StateFlow<String> = _newUserName.asStateFlow()

    fun updateNewUserName(value: String) {
        _newUserName.value = value
    }

    fun canSubmit(): Boolean {
        return _newUserName.value.trim().isNotEmpty()
    }

    fun validate(): String? {
        return if (_newUserName.value.trim().isEmpty()) "请输入新用户名" else null
    }
}