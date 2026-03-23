package com.example.ai_belt_mobile.ui.home

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.base.BaseFragment
import com.example.ai_belt_mobile.databinding.DialogNameEditBinding
import com.example.ai_belt_mobile.databinding.DialogPasswordEditBinding
import com.example.ai_belt_mobile.databinding.DialogPhoneEditBinding
import com.example.ai_belt_mobile.databinding.FragmentProfileBinding
import com.example.ai_belt_mobile.ui.activity.ChooseMemberActivity
import com.example.ai_belt_mobile.viewModel.DialogPasswordVM
import com.example.ai_belt_mobile.viewModel.DialogPhoneVM
import com.example.ai_belt_mobile.viewModel.DialogUserNameVM
import com.google.android.material.textview.MaterialTextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ai_belt_mobile.data.local.UserSessionStore
import com.example.ai_belt_mobile.ui.activity.LoginActivity
import kotlinx.coroutines.launch
import kotlin.or

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {
    override val layoutId: Int = R.layout.fragment_profile

    private lateinit var viewModel: ProfileViewModel
    private var currentDialog: Dialog? = null

    override fun initView() {
        super.initView()
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.loadFromSession(requireContext())

        binding.familyMemberCard.setOnClickListener {
            startActivity(Intent(requireContext(), ChooseMemberActivity::class.java))
        }
        binding.familyMemberBtn.setOnClickListener {
            startActivity(Intent(requireContext(), ChooseMemberActivity::class.java))
        }

        binding.bindByCodeButton.setOnClickListener { showBindCodeDialog(showQr = false) }
        binding.bindByQrCodeButton.setOnClickListener { showBindCodeDialog(showQr = true) }

        binding.nameCard.setOnClickListener { showEditNameDialog() }
        binding.nameEditButton.setOnClickListener { showEditNameDialog() }

        binding.homepageEditPasswordCard.setOnClickListener { showEditPasswordDialog() }
        binding.passwordEditProfile.setOnClickListener { showEditPasswordDialog() }

        binding.homepageEditPhoneCard.setOnClickListener { showEditPhoneDialog() }
        binding.phoneEdit.setOnClickListener { showEditPhoneDialog() }
        binding.logoutButton.setOnClickListener {
            UserSessionStore.clear(requireContext())
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish()
        }
    }

    override fun initData() {
        super.initData()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userName.collect { name ->
                    binding.nameText.text = name
                }
            }
        }
    }

    private fun showBindCodeDialog(showQr: Boolean) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_show_bind_code, null, false)

        val bindCodeTv = dialogView.findViewById<MaterialTextView>(R.id.bind_code)
        val bindQrIv = dialogView.findViewById<android.widget.ImageView>(R.id.bind_qr_code)

        val code = viewModel.bindCode.value.trim()
        bindCodeTv.text = if (code.isNotBlank()) code else "--"

        if (showQr && code.isNotBlank()) {
            val bmp = com.example.ai_belt_mobile.utils.QrCodeUtils.generate(code, sizePx = 520)
            if (bmp != null) {
                bindQrIv.visibility = View.VISIBLE
                bindQrIv.setImageBitmap(bmp)
            } else {
                bindQrIv.visibility = View.GONE
            }
        } else {
            bindQrIv.visibility = View.GONE
        }

        val dialog = createStyledDialog(dialogView)
        showStyledDialog(dialog)
    }

    private fun showEditNameDialog() {
        val vm = DialogUserNameVM()
        val dialogBinding = DialogNameEditBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.viewModel = vm
        dialogBinding.lifecycleOwner = viewLifecycleOwner

        vm.updateNewUserName(viewModel.userName.value)
        dialogBinding.editAccount.setText(viewModel.userName.value)
        dialogBinding.editAccount.setSelection(dialogBinding.editAccount.text?.length ?: 0)

        fun refreshBtn() {
            val enabled = vm.canSubmit()
            dialogBinding.ensureButton.isEnabled = enabled
            dialogBinding.ensureButton.alpha = if (enabled) 1f else 0.5f
        }

        dialogBinding.editAccount.doAfterTextChanged {
            vm.updateNewUserName(it?.toString().orEmpty())
            dialogBinding.editAccount.error = null
            refreshBtn()
        }

        val dialog = createStyledDialog(dialogBinding.root)
        dialogBinding.ensureButton.setOnClickListener {
            val err = vm.validate()
            if (err != null) {
                dialogBinding.editAccount.error = err
                return@setOnClickListener
            }
            viewModel.updateUserName(
                context = requireContext(),
                newName = vm.newUserName.value,
                progressButton = dialogBinding.ensureButton
            ) {
                dialog.dismiss()
            }
        }

        showStyledDialog(dialog)
        refreshBtn()
    }

    private fun showEditPasswordDialog() {
        val vm = DialogPasswordVM()
        val dialogBinding = DialogPasswordEditBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.viewModel = vm
        dialogBinding.lifecycleOwner = viewLifecycleOwner

        vm.updateEmail(viewModel.mail.value)
        dialogBinding.editEmail.setText(viewModel.mail.value)
        dialogBinding.editEmail.setSelection(dialogBinding.editEmail.text?.length ?: 0)

        fun refreshBtn() {
            val enabled = vm.canSubmit()
            dialogBinding.ensureButton.isEnabled = enabled
            dialogBinding.ensureButton.alpha = if (enabled) 1f else 0.5f
        }

        dialogBinding.editEmail.doAfterTextChanged {
            vm.updateEmail(it?.toString().orEmpty())
            dialogBinding.editEmail.error = null
            refreshBtn()
        }
        dialogBinding.editVerifyCode.doAfterTextChanged {
            vm.updateVerifyCode(it?.toString().orEmpty())
            dialogBinding.editVerifyCode.error = null
            refreshBtn()
        }
        dialogBinding.editPassword.doAfterTextChanged {
            vm.updateNewPassword(it?.toString().orEmpty())
            dialogBinding.editPassword.error = null
            refreshBtn()
        }

        val dialog = createStyledDialog(dialogBinding.root)

        dialogBinding.GetVerifyCodeButton.setOnClickListener {
            vm.updateEmail(dialogBinding.editEmail.text?.toString().orEmpty())
            val emailErr = vm.validateEmail()
            if (emailErr != null) {
                dialogBinding.editEmail.error = emailErr
                return@setOnClickListener
            }
            viewModel.sendPasswordCode(
                context = requireContext(),
                email = vm.email.value,
                button = dialogBinding.GetVerifyCodeButton
            )
        }

        dialogBinding.ensureButton.setOnClickListener {
            vm.updateEmail(dialogBinding.editEmail.text?.toString().orEmpty())
            vm.updateVerifyCode(dialogBinding.editVerifyCode.text?.toString().orEmpty())
            vm.updateNewPassword(dialogBinding.editPassword.text?.toString().orEmpty())

            when (val err = vm.validateAll()) {
                "请输入邮箱", "邮箱格式不正确" -> {
                    dialogBinding.editEmail.error = err
                    return@setOnClickListener
                }
                "请输入验证码" -> {
                    dialogBinding.editVerifyCode.error = err
                    return@setOnClickListener
                }
                "请输入新密码" -> {
                    dialogBinding.editPassword.error = err
                    return@setOnClickListener
                }
            }

            viewModel.submitPasswordReset(
                context = requireContext(),
                progressButton = dialogBinding.ensureButton,
                email = vm.email.value,
                code = vm.verifyCode.value,
                newPassword = vm.newPassword.value
            ) {
                dialog.dismiss()
            }
        }

        showStyledDialog(dialog)
        refreshBtn()
    }

    private fun showEditPhoneDialog() {
        val vm = DialogPhoneVM()
        val dialogBinding = DialogPhoneEditBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.viewModel = vm
        dialogBinding.lifecycleOwner = viewLifecycleOwner

        vm.updateNewPhone(viewModel.phone.value)
        dialogBinding.editAccount.setText(viewModel.phone.value)
        dialogBinding.editAccount.setSelection(dialogBinding.editAccount.text?.length ?: 0)

        fun refreshBtn() {
            val enabled = vm.canSubmit()
            dialogBinding.ensureButton.isEnabled = enabled
            dialogBinding.ensureButton.alpha = if (enabled) 1f else 0.5f
        }

        dialogBinding.editAccount.doAfterTextChanged {
            vm.updateNewPhone(it?.toString().orEmpty())
            dialogBinding.editAccount.error = null
            refreshBtn()
        }

        val dialog = createStyledDialog(dialogBinding.root)
        dialogBinding.ensureButton.setOnClickListener {
            val err = vm.validate()
            if (err != null) {
                dialogBinding.editAccount.error = err
                return@setOnClickListener
            }
            viewModel.updatePhone(
                context = requireContext(),
                newPhone = vm.newPhone.value,
                progressButton = dialogBinding.ensureButton
            ) {
                dialog.dismiss()
            }
        }

        showStyledDialog(dialog)
        refreshBtn()
    }

    private fun createStyledDialog(contentView: View): AlertDialog {
        return AlertDialog.Builder(requireContext())
            .setView(contentView)
            .create()
    }

    private fun showStyledDialog(dialog: Dialog) {
        currentDialog?.dismiss()
        currentDialog = dialog
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        currentDialog?.dismiss()
        currentDialog = null
        super.onDestroyView()
    }
}