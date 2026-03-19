package com.example.ai_belt_mobile.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DialogPhoneVM : ViewModel() {

    private val _newPhone = MutableStateFlow("")
    val newPhone: StateFlow<String> = _newPhone.asStateFlow()

}