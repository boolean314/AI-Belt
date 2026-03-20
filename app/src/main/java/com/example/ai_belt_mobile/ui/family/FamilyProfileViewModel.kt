package com.example.ai_belt_mobile.ui.family


import android.content.Context
import android.util.Patterns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd.CircularProgressButton
import com.example.ai_belt_mobile.data.local.UserSessionStore
import com.example.ai_belt_mobile.data.model.BindDisability
import com.example.ai_belt_mobile.data.model.ForgetPassword
import com.example.ai_belt_mobile.data.model.GetVerifyCode
import com.example.ai_belt_mobile.data.model.UpdateProfile
import com.example.ai_belt_mobile.network.UserRetrofitClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.code
import kotlin.compareTo

class FamilyProfileViewModel : ViewModel() {

    private val _userId = MutableStateFlow(-1)
    val userId: StateFlow<Int> = _userId.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _mail = MutableStateFlow("")
    val mail: StateFlow<String> = _mail.asStateFlow()

    private val _identity = MutableStateFlow(-1)
    val identity: StateFlow<Int> = _identity.asStateFlow()

    private var sendCodeJob: Job? = null
    private var submitJob: Job? = null

    fun loadFromSession(context: Context) {
        val session = UserSessionStore.get(context) ?: return
        _userId.value = session.id
        _userName.value = session.name
        _phone.value = session.phone
        _mail.value = session.mail
        _identity.value = session.identity
    }

    fun updateUserName(
        context: Context,
        newName: String,
        progressButton: CircularProgressButton,
        onDone: (() -> Unit)? = null
    ) {
        val name = newName.trim()
        if (name.isEmpty()) {
            Toast.makeText(context, "请输入新用户名", Toast.LENGTH_SHORT).show()
            return
        }
        if (_userId.value < 0) {
            Toast.makeText(context, "用户信息不存在，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        progressButton.progress = CircularProgressButton.INDETERMINATE_STATE_PROGRESS

        viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.updateProfile(
                    UpdateProfile(
                        userId = _userId.value,
                        name = name,
                        phone = _phone.value
                    )
                )
                if (resp.code == 200 && resp.data != null) {
                    _userName.value = resp.data.name
                    UserSessionStore.save(
                        context = context,
                        id = resp.data.id,
                        phone = resp.data.phone,
                        name = resp.data.name,
                        mail = resp.data.mail,
                        identity = resp.data.identity,
                        code = resp.data.code,
                        emergency = resp.data.emergency
                    )

                    progressButton.progress = CircularProgressButton.SUCCESS_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(800)
                    onDone?.invoke()
                } else {
                    progressButton.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(1200)
                    progressButton.progress = CircularProgressButton.IDLE_STATE_PROGRESS
                }
            } catch (e: Exception) {
                progressButton.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                Toast.makeText(context, "修改用户名失败，请重试", Toast.LENGTH_SHORT).show()
                delay(1200)
                progressButton.progress = CircularProgressButton.IDLE_STATE_PROGRESS
            }
        }
    }

    fun updatePhone(
        context: Context,
        newPhone: String,
        progressButton: CircularProgressButton,
        onDone: (() -> Unit)? = null
    ) {
        val phone = newPhone.trim()
        if (phone.isEmpty()) {
            Toast.makeText(context, "请输入手机号", Toast.LENGTH_SHORT).show()
            return
        }
        if (_userId.value < 0) {
            Toast.makeText(context, "用户信息不存在，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        progressButton.progress = CircularProgressButton.INDETERMINATE_STATE_PROGRESS

        viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.updateProfile(
                    UpdateProfile(
                        userId = _userId.value,
                        name = _userName.value,
                        phone = phone
                    )
                )
                if (resp.code == 200 && resp.data != null) {
                    _phone.value = resp.data.phone
                    UserSessionStore.save(
                        context = context,
                        id = resp.data.id,
                        phone = resp.data.phone,
                        name = resp.data.name,
                        mail = resp.data.mail,
                        identity = resp.data.identity,
                        code = resp.data.code,
                        emergency = resp.data.emergency
                    )

                    progressButton.progress = CircularProgressButton.SUCCESS_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(800)
                    onDone?.invoke()
                } else {
                    progressButton.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(1200)
                    progressButton.progress = CircularProgressButton.IDLE_STATE_PROGRESS
                }
            } catch (e: Exception) {
                progressButton.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                Toast.makeText(context, "修改手机号失败，请重试", Toast.LENGTH_SHORT).show()
                delay(1200)
                progressButton.progress = CircularProgressButton.IDLE_STATE_PROGRESS
            }
        }
    }

    fun sendPasswordCode(
        context: Context,
        email: String,
        button: MaterialButton
    ) {
        if (sendCodeJob?.isActive == true) return

        val emailValue = email.trim()
        if (emailValue.isEmpty()) {
            Toast.makeText(context, "请输入邮箱", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
            Toast.makeText(context, "邮箱格式不正确", Toast.LENGTH_SHORT).show()
            return
        }

        button.isEnabled = false
        button.text = "发送中..."

        sendCodeJob = viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.sendCode(GetVerifyCode(mail = emailValue))
                if (resp.code == 200) {
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    startCountDown(button, 60)
                } else {
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    button.isEnabled = true
                    button.text = "获取验证码"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "验证码发送失败，请重试", Toast.LENGTH_SHORT).show()
                button.isEnabled = true
                button.text = "获取验证码"
            }
        }
    }

    fun submitPasswordReset(
        context: Context,
        progressButton: CircularProgressButton,
        email: String,
        code: String,
        newPassword: String,
        onDone: (() -> Unit)? = null
    ) {
        if (submitJob?.isActive == true) return

        val emailValue = email.trim()
        val codeValue = code.trim()
        val pwdValue = newPassword.trim()

        if (emailValue.isEmpty()) {
            Toast.makeText(context, "请输入邮箱", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
            Toast.makeText(context, "邮箱格式不正确", Toast.LENGTH_SHORT).show()
            return
        }
        if (codeValue.isEmpty()) {
            Toast.makeText(context, "请输入验证码", Toast.LENGTH_SHORT).show()
            return
        }
        if (pwdValue.isEmpty()) {
            Toast.makeText(context, "请输入新密码", Toast.LENGTH_SHORT).show()
            return
        }

        progressButton.progress = CircularProgressButton.INDETERMINATE_STATE_PROGRESS

        submitJob = viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.forgetPassword(
                    ForgetPassword(
                        email = emailValue,
                        password = pwdValue,
                        code = codeValue
                    )
                )
                if (resp.code == 200) {
                    progressButton.progress = CircularProgressButton.SUCCESS_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(800)
                    onDone?.invoke()
                } else {
                    progressButton.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                    Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                    delay(1200)
                    progressButton.progress = CircularProgressButton.IDLE_STATE_PROGRESS
                }
            } catch (e: Exception) {
                progressButton.progress = CircularProgressButton.ERROR_STATE_PROGRESS
                Toast.makeText(context, "修改密码失败，请重试", Toast.LENGTH_SHORT).show()
                delay(1200)
                progressButton.progress = CircularProgressButton.IDLE_STATE_PROGRESS
            }
        }
    }

    fun bindDisability(
        context: Context,
        bindCode: String,
        onDone: (() -> Unit)? = null
    ) {
        val code = bindCode.trim()
        if (code.isEmpty()) {
            Toast.makeText(context, "请输入绑定码", Toast.LENGTH_SHORT).show()
            return
        }
        if (_userId.value < 0) {
            Toast.makeText(context, "用户信息不存在，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                val resp = UserRetrofitClient.instance.bindFamily(
                    BindDisability(
                        id = _userId.value,
                        bindCode = code
                    )
                )
                Toast.makeText(context, resp.message, Toast.LENGTH_SHORT).show()
                if (resp.code == 200) {
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "绑定失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCountDown(button: MaterialButton, seconds: Int) {
        viewModelScope.launch {
            for (i in seconds downTo 1) {
                button.text = "${i}s"
                delay(1000)
            }
            button.text = "获取验证码"
            button.isEnabled = true
        }
    }
}