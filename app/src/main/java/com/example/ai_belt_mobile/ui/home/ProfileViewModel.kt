package com.example.ai_belt_mobile.ui.home


import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileViewModel : ViewModel() {

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _bindCode = MutableStateFlow("S5G2UY")
    val bindCode: StateFlow<String> = _bindCode.asStateFlow()

    fun updateUserName(newName: String) {
        _userName.value = newName
    }

    fun updateBindCode(newCode: String) {
        _bindCode.value = newCode
    }

}