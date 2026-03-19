package com.example.ai_belt_mobile.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoginVM : ViewModel() {
    private val _phone = MutableStateFlow("")
    val phone : StateFlow<String> = _phone.asStateFlow()
    private val _password = MutableStateFlow("")
    val password : StateFlow<String> = _password.asStateFlow()
}