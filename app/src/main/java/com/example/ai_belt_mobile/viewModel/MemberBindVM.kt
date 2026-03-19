package com.example.ai_belt_mobile.viewModel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemberBindVM : ViewModel() {

    private val _bindCode = MutableStateFlow("")
    val bindCode: StateFlow<String> = _bindCode.asStateFlow()

}