package com.example.ai_belt_mobile.ui.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.example.ai_belt_mobile.MainActivity
import com.example.ai_belt_mobile.FamilyMainActivity
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.data.local.UserSessionStore
import com.example.ai_belt_mobile.databinding.ActivityLoginBinding
import com.example.ai_belt_mobile.viewModel.DialogFindPasswordVM
import com.example.ai_belt_mobile.viewModel.LoginVM
import kotlin.getValue
import kotlin.or
import kotlin.toString

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginVM by viewModels()
    private val forgetPasswordVM: DialogFindPasswordVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fun refreshLoginBtn() {
            val enabled = viewModel.canLogin()
            binding.LoginProgressButton.isEnabled = enabled
            binding.LoginProgressButton.alpha = if (enabled) 1f else 0.5f
        }

        binding.loginPhone.doAfterTextChanged {
            viewModel.updatePhone(it?.toString().orEmpty())
            refreshLoginBtn()
        }
        binding.loginPassword.doAfterTextChanged {
            viewModel.updatePassword(it?.toString().orEmpty())
            refreshLoginBtn()
        }

        binding.LoginProgressButton.setOnClickListener {
            viewModel.updatePhone(binding.loginPhone.text?.toString().orEmpty())
            viewModel.updatePassword(binding.loginPassword.text?.toString().orEmpty())

            viewModel.login(this, binding.LoginProgressButton) { loginData ->
                UserSessionStore.saveFromLogin(this, loginData)
                val target = if (loginData.identity == 0) {
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

        binding.registerEntrance.setOnClickListener {
            startActivity(Intent(this, ChooseIdentityActivity::class.java))
        }
        binding.forgetEntrance.setOnClickListener {
            showFindPasswordDialog()
        }

        refreshLoginBtn()
    }

    private fun showFindPasswordDialog() {
        val vm = forgetPasswordVM
        val dialogBinding = com.example.ai_belt_mobile.databinding.DialogFindPasswordBinding
            .inflate(LayoutInflater.from(this))

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        fun refreshSubmitButtonState() {
            val enabled = vm.canSubmit()
            dialogBinding.ForgetProgressButton.isEnabled = enabled
            dialogBinding.ForgetProgressButton.alpha = if (enabled) 1f else 0.5f
        }

        dialogBinding.forgetAccount.doAfterTextChanged { text ->
            vm.updateEmail(text?.toString().orEmpty())
            dialogBinding.forgetAccount.error = null
            refreshSubmitButtonState()
        }

        dialogBinding.forgetVerify.doAfterTextChanged { text ->
            vm.updateCode(text?.toString().orEmpty())
            dialogBinding.forgetVerify.error = null
            refreshSubmitButtonState()
        }

        dialogBinding.forgetPassword.doAfterTextChanged { text ->
            vm.updatePassword(text?.toString().orEmpty())
            dialogBinding.forgetPassword.error = null
            refreshSubmitButtonState()
        }

        dialogBinding.btnGetCode.setOnClickListener {
            vm.updateEmail(dialogBinding.forgetAccount.text?.toString().orEmpty())

            val emailErr = vm.validateEmail()
            if (emailErr != null) {
                dialogBinding.forgetAccount.error = emailErr
                return@setOnClickListener
            }

            vm.sendVerifyCode(this, dialogBinding.btnGetCode)
        }

        dialogBinding.ForgetProgressButton.setOnClickListener {
            vm.updateEmail(dialogBinding.forgetAccount.text?.toString().orEmpty())
            vm.updateCode(dialogBinding.forgetVerify.text?.toString().orEmpty())
            vm.updatePassword(dialogBinding.forgetPassword.text?.toString().orEmpty())

            when (val err = vm.validateAll()) {
                "请输入邮箱", "邮箱格式不正确" -> {
                    dialogBinding.forgetAccount.error = err
                    return@setOnClickListener
                }
                "请输入验证码" -> {
                    dialogBinding.forgetVerify.error = err
                    return@setOnClickListener
                }
                "请输入新密码" -> {
                    dialogBinding.forgetPassword.error = err
                    return@setOnClickListener
                }
            }

            vm.submitForgetPassword(this, dialogBinding.ForgetProgressButton) {
                dialog.dismiss()
            }
        }

        refreshSubmitButtonState()
        dialog.show()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}