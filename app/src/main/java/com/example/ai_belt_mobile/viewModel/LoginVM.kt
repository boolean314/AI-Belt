package com.example.ai_belt_mobile.viewModel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd.CircularProgressButton
import com.example.ai_belt_mobile.data.model.LoginData
import com.example.ai_belt_mobile.data.model.UserLogin
import com.example.ai_belt_mobile.network.UserRetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginVM : ViewModel() {
    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    fun updatePhone(value: String) {
        _phone.value = value
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun canLogin(): Boolean {
        return _phone.value.trim().isNotEmpty() && _password.value.trim().isNotEmpty()
    }

    fun login(
        context: Context,
        button: CircularProgressButton,
        onSuccess: ((data: LoginData) -> Unit)? = null
    ) {
        val phoneValue = _phone.value.trim()
        val passwordValue = _password.value.trim()

        if (phoneValue.isEmpty()) {
            Toast.makeText(context, "请输入手机号", Toast.LENGTH_SHORT).show()
            return
        }
        if (passwordValue.isEmpty()) {
            Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
            return
        }

        button.progress = CircularProgressButton.INDETERMINATE_STATE_PROGRESS

        viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.login(
                    UserLogin(phone = phoneValue, password = passwordValue)
                )

                if (resp.code == 200 && resp.data != null) {
                    val data = resp.data
                    if (data.identity != 0 && data.identity != 1) {
                        button.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                        Toast.makeText(context, "身份数据异常：${data.identity}", Toast.LENGTH_SHORT).show()
                        delay(1200)
                        button.progress = CircularProgressButton.IDLE_STATE_PROGRESS
                        return@launch
                    }
                    button.progress = CircularProgressButton.SUCCESS_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(800)
                    onSuccess?.invoke(data)
                } else {
                    button.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(1200)
                    button.progress = CircularProgressButton.IDLE_STATE_PROGRESS
                }
            } catch (e: Exception) {
                button.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                Toast.makeText(context, "网络开小差了，请重试", Toast.LENGTH_SHORT).show()
                delay(1200)
                button.progress = CircularProgressButton.IDLE_STATE_PROGRESS
            }
        }
    }
}