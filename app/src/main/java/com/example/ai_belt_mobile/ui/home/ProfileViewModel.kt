package com.example.ai_belt_mobile.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel : ViewModel() {

    val userName = MutableLiveData<String>()
    val bindCode = MutableLiveData("S5G2UY") // TODO: 登录后从后台拉取并赋值

}