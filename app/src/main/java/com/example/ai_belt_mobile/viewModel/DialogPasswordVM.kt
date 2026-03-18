package com.example.ai_belt_mobile.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DialogPasswordVM : ViewModel() {

    val email = MutableLiveData<String>()
    val verifyCode = MutableLiveData<String>()
    val newPassword = MutableLiveData<String>()

}