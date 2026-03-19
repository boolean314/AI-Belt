package com.example.ai_belt_mobile.ui.home

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.launch

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {
    override val layoutId: Int = R.layout.fragment_profile

    private lateinit var viewModel: ProfileViewModel
    private var currentDialog: Dialog? = null

    override fun initView() {
        super.initView()
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        binding.familyMemberCard.setOnClickListener {
            val intent = Intent(requireContext(), ChooseMemberActivity::class.java)
            startActivity(intent)
        }
        binding.familyMemberBtn.setOnClickListener {
            val intent = Intent(requireContext(), ChooseMemberActivity::class.java)
            startActivity(intent)
        }
        binding.bindByCodeButton.setOnClickListener { showBindCodeDialog(showQr = false) }
        binding.bindByQrCodeButton.setOnClickListener { showBindCodeDialog(showQr = true) }
        binding.nameCard.setOnClickListener { showEditNameDialog() }
        binding.nameEditButton.setOnClickListener { showEditNameDialog() }
        binding.homepageEditPasswordCard.setOnClickListener { showEditPasswordDialog() }
        binding.passwordEditProfile.setOnClickListener { showEditPasswordDialog() }
        binding.homepageEditPhoneCard.setOnClickListener { showEditPhoneDialog() }
        binding.phoneEdit.setOnClickListener { showEditPhoneDialog() }
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
        val dialogView: View =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_show_bind_code, null, false)

        val bindCodeTv = dialogView.findViewById<MaterialTextView>(R.id.bind_code)
        val bindQrIv = dialogView.findViewById<android.widget.ImageView>(R.id.bind_qr_code)

        val bindCode = viewModel.bindCode.value.trim()
        bindCodeTv.text = if (bindCode.isNotBlank()) bindCode else "--"

        if (showQr && bindCode.isNotBlank()) {
            bindQrIv.visibility = View.VISIBLE
            val qrBitmap = com.example.ai_belt_mobile.utils.QrCodeUtils.generate(bindCode, sizePx = 520)
            if (qrBitmap != null) {
                bindQrIv.setImageBitmap(qrBitmap)
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

        val dialog = createStyledDialog(dialogBinding.root)
        dialogBinding.ensureButton.setOnClickListener {
            val newName = vm.newUserName.value?.trim().orEmpty()
            if (newName.isEmpty()) {
                dialogBinding.editAccount.error = "请输入新用户名"
                return@setOnClickListener
            }
            viewModel.updateUserName(newName)
            //从后台获取
            dialog.dismiss()
        }

        showStyledDialog(dialog)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showEditPasswordDialog() {
        val vm = DialogPasswordVM()
        val dialogBinding = DialogPasswordEditBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.viewModel = vm
        dialogBinding.lifecycleOwner = viewLifecycleOwner

        val dialog = createStyledDialog(dialogBinding.root)

        dialogBinding.GetVerifyCodeButton.setOnClickListener {
            val email = vm.email.value?.trim().orEmpty()
            if (email.isEmpty()) {
                dialogBinding.editEmail.error = "请输入邮箱"
                return@setOnClickListener
            }
            //从后台获取
        }

        dialogBinding.ensureButton.setOnClickListener {
            val email = vm.email.value?.trim().orEmpty()
            val code = vm.verifyCode.value?.trim().orEmpty()
            val newPwd = vm.newPassword.value?.trim().orEmpty()

            if (email.isEmpty()) {
                dialogBinding.editEmail.error = "请输入邮箱"
                return@setOnClickListener
            }
            if (code.isEmpty()) {
                dialogBinding.editVerifyCode.error = "请输入验证码"
                return@setOnClickListener
            }
            if (newPwd.isEmpty()) {
                dialogBinding.editPassword.error = "请输入新密码"
                return@setOnClickListener
            }

            //密码
            dialog.dismiss()
        }

        showStyledDialog(dialog)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showEditPhoneDialog() {
        val vm = DialogPhoneVM()
        val dialogBinding = DialogPhoneEditBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.viewModel = vm
        dialogBinding.lifecycleOwner = viewLifecycleOwner

        val dialog = createStyledDialog(dialogBinding.root)
        dialogBinding.ensureButton.setOnClickListener {
            val newPhone = vm.newPhone.value?.trim().orEmpty()
            if (newPhone.isEmpty()) {
                dialogBinding.editAccount.error = "请输入手机号"
                return@setOnClickListener
            }
            //手机号
            dialog.dismiss()
        }

        showStyledDialog(dialog)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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