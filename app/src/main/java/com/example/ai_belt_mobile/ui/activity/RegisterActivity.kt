package com.example.ai_belt_mobile.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.databinding.ActivityRegisterBinding
import com.example.ai_belt_mobile.viewModel.RegisterVM
import com.example.ai_belt_mobile.MainActivity
import com.example.ai_belt_mobile.FamilyMainActivity
import com.example.ai_belt_mobile.data.local.UserSessionStore
import kotlin.or
import kotlin.toString

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterVM by viewModels()
    private val identity by lazy { intent.getIntExtra("identity", -1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel.updateIdentity(identity)

        if (identity != 0 && identity != 1) {
            Toast.makeText(this, "身份参数无效，请重新选择", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fun refreshRegisterState() {
            val enabled = viewModel.canSubmit()
            binding.RegisterProgressButton.isEnabled = enabled
            binding.RegisterProgressButton.alpha = if (enabled) 1f else 0.5f
        }

        binding.registerAccount.doAfterTextChanged {
            viewModel.updateAccount(it?.toString().orEmpty())
            binding.registerAccount.error = null
            refreshRegisterState()
        }
        binding.registerName.doAfterTextChanged {
            viewModel.updateName(it?.toString().orEmpty())
            binding.registerName.error = null
            refreshRegisterState()
        }
        binding.registerPassword.doAfterTextChanged {
            viewModel.updatePassword(it?.toString().orEmpty())
            binding.registerPassword.error = null
            refreshRegisterState()
        }
        binding.registerVerify.doAfterTextChanged {
            viewModel.updateVerify(it?.toString().orEmpty())
            binding.registerVerify.error = null
            refreshRegisterState()
        }
        binding.registerPhone.doAfterTextChanged {
            viewModel.updatePhone(it?.toString().orEmpty())
            binding.registerPhone.error = null
            refreshRegisterState()
        }

        binding.btnGetCode.setOnClickListener {
            viewModel.updateAccount(binding.registerAccount.text?.toString().orEmpty())
            val emailErr = viewModel.validateEmail()
            if (emailErr != null) {
                binding.registerAccount.error = emailErr
                return@setOnClickListener
            }
            viewModel.sendVerifyCode(this, binding.btnGetCode)
        }

        binding.RegisterProgressButton.setOnClickListener {
            viewModel.updateAccount(binding.registerAccount.text?.toString().orEmpty())
            viewModel.updateName(binding.registerName.text?.toString().orEmpty())
            viewModel.updatePassword(binding.registerPassword.text?.toString().orEmpty())
            viewModel.updateVerify(binding.registerVerify.text?.toString().orEmpty())
            viewModel.updatePhone(binding.registerPhone.text?.toString().orEmpty())

            when (val err = viewModel.validateAll()) {
                "请输入邮箱", "邮箱格式不正确" -> {
                    binding.registerAccount.error = err
                    return@setOnClickListener
                }
                "请输入用户名" -> {
                    binding.registerName.error = err
                    return@setOnClickListener
                }
                "请输入密码" -> {
                    binding.registerPassword.error = err
                    return@setOnClickListener
                }
                "请输入验证码" -> {
                    binding.registerVerify.error = err
                    return@setOnClickListener
                }
                "请输入手机号" -> {
                    binding.registerPhone.error = err
                    return@setOnClickListener
                }
            }

            viewModel.register(this, binding.RegisterProgressButton) { registerData ->
                UserSessionStore.saveFromRegister(this, registerData)

                val target = if (registerData.identity == 0) {
                    MainActivity::class.java
                } else {
                    FamilyMainActivity::class.java
                }

                startActivity(
                    Intent(this, target).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
        }

        refreshRegisterState()
    }
}