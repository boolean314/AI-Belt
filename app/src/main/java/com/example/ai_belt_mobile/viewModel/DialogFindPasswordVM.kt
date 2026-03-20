package com.example.ai_belt_mobile.viewModel

import android.content.Context
import android.util.Patterns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd.CircularProgressButton
import com.example.ai_belt_mobile.data.model.ForgetPassword
import com.example.ai_belt_mobile.data.model.GetVerifyCode
import com.example.ai_belt_mobile.network.UserRetrofitClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DialogFindPasswordVM : ViewModel() {

    private val _mail = MutableStateFlow("")
    val mail : StateFlow<String> = _mail.asStateFlow()

    private val _code = MutableStateFlow("")
    val code : StateFlow<String> = _code.asStateFlow()

    private val _password = MutableStateFlow("")
    val password : StateFlow<String> = _password.asStateFlow()

    private var sendCodeJob: Job? = null
    private var submitJob: Job? = null

    fun updateEmail(value: String) {
        _mail.value = value
    }

    fun updateCode(value: String) {
        _code.value = value
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun canSubmit(): Boolean {
        return _mail.value.trim().isNotEmpty() &&
                _code.value.trim().isNotEmpty() &&
                _password.value.trim().isNotEmpty()
    }

    fun validateEmail(): String? {
        val value = _mail.value.trim()
        return when {
            value.isEmpty() -> "请输入邮箱"
            !Patterns.EMAIL_ADDRESS.matcher(value).matches() -> "邮箱格式不正确"
            else -> null
        }
    }

    fun validateAll(): String? {
        validateEmail()?.let { return it }
        if (_code.value.trim().isEmpty()) return "请输入验证码"
        if (_password.value.trim().isEmpty()) return "请输入新密码"
        return null
    }

    fun sendVerifyCode(
        context: Context,
        sendButton: MaterialButton
    ) {
        if (sendCodeJob?.isActive == true) return

        val emailErr = validateEmail()
        if (emailErr != null) {
            Toast.makeText(context, emailErr, Toast.LENGTH_SHORT).show()
            return
        }

        sendButton.isEnabled = false
        sendButton.text = "发送中..."

        sendCodeJob = viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.sendCode(
                    GetVerifyCode(mail = _mail.value.trim())
                )
                if (resp.code == 200) {
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    startCountDown(sendButton, 60)
                } else {
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    sendButton.isEnabled = true
                    sendButton.text = "获取"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "验证码发送失败，请重试", Toast.LENGTH_SHORT).show()
                sendButton.isEnabled = true
                sendButton.text = "获取"
            }
        }
    }

    fun submitForgetPassword(
        context: Context,
        progressButton: CircularProgressButton,
        onSuccess: (() -> Unit)? = null
    ) {
        if (submitJob?.isActive == true) return

        val err = validateAll()
        if (err != null) {
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            return
        }

        progressButton.progress = CircularProgressButton.INDETERMINATE_STATE_PROGRESS

        submitJob = viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.forgetPassword(
                    ForgetPassword(
                        email = _mail.value.trim(),
                        password = _password.value.trim(),
                        code = _code.value.trim()
                    )
                )
                if (resp.code == 200) {
                    progressButton.progress = CircularProgressButton.SUCCESS_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(800)
                    onSuccess?.invoke()
                } else {
                    progressButton.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(1200)
                    progressButton.progress = CircularProgressButton.IDLE_STATE_PROGRESS
                }
            } catch (e: Exception) {
                progressButton.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                Toast.makeText(context, "网络开小差了，请重试", Toast.LENGTH_SHORT).show()
                delay(1200)
                progressButton.progress = CircularProgressButton.IDLE_STATE_PROGRESS
            }
        }
    }

    private fun startCountDown(button: MaterialButton, seconds: Int) {
        viewModelScope.launch {
            for (i in seconds downTo 1) {
                button.text = "${i}s"
                delay(1000)
            }
            button.text = "获取"
            button.isEnabled = true
        }
    }
}