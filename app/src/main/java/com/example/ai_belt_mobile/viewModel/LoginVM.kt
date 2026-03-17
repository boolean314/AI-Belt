package com.example.ai_belt_mobile.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginVM : ViewModel() {
    val phone = MutableLiveData<String>()
    val password = MutableLiveData<String>()
}