package com.example.ai_belt_mobile.ui.family

import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.base.BaseFragment
import com.example.ai_belt_mobile.databinding.DialogNameEditBinding
import com.example.ai_belt_mobile.databinding.DialogPasswordEditBinding
import com.example.ai_belt_mobile.databinding.DialogPhoneEditBinding
import com.example.ai_belt_mobile.databinding.FragmentFamilyProfileBinding
import com.example.ai_belt_mobile.ui.activity.ScanActivity
import com.example.ai_belt_mobile.ui.home.ProfileViewModel
import com.example.ai_belt_mobile.viewModel.DialogPasswordVM
import com.example.ai_belt_mobile.viewModel.DialogPhoneVM
import com.example.ai_belt_mobile.viewModel.DialogUserNameVM
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
class FamilyProfileFragment : BaseFragment<FragmentFamilyProfileBinding>() {

    override val layoutId: Int = R.layout.fragment_family_profile

    private lateinit var viewModel: FamilyProfileViewModel
    private var bindDialog: Dialog? = null
    private var dialogBindCodeInput: TextInputEditText? = null

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val code = result.data?.getStringExtra(ScanActivity.EXTRA_SCAN_RESULT).orEmpty()
            if (code.isNotBlank()) {
                dialogBindCodeInput?.setText(code)
                dialogBindCodeInput?.setSelection(code.length)
            }
        }


    override fun initView() {
        super.initView()
        viewModel = ViewModelProvider(this)[FamilyProfileViewModel::class.java]
        binding.viewModel = viewModel

        binding.bindByCodeButton.setOnClickListener { showMemberBindDialog() }
        binding.bindByQrCodeButton.setOnClickListener { showMemberBindDialog() }
        binding.nameEditButton.setOnClickListener { showEditNameDialog() }
        binding.passwordEdit.setOnClickListener { showEditPasswordDialog() }
        binding.phoneEdit.setOnClickListener { showEditPhoneDialog() }
    }

    private fun showMemberBindDialog() {
        if (bindDialog?.isShowing == true) return

        val dialogView: View =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_member_bind, null, false)

        val etBindCode = dialogView.findViewById<TextInputEditText>(R.id.bind_code)
        val btnBind = dialogView.findViewById<MaterialButton>(R.id.bind_btn)
        val scanQrCard = dialogView.findViewById<MaterialCardView>(R.id.scan_qr_card)

        dialogBindCodeInput = etBindCode

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnBind.setOnClickListener {
            val code = etBindCode.text?.toString()?.trim().orEmpty()
            if (code.isEmpty()) {
                etBindCode.error = "请输入绑定码"
                return@setOnClickListener
            }

            dialog.dismiss()
        }


        scanQrCard.setOnClickListener {
            scanLauncher.launch(Intent(requireContext(), ScanActivity::class.java))
        }

        dialog.setOnDismissListener {
            dialogBindCodeInput = null
        }

        bindDialog = dialog
        dialog.show()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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
            viewModel.userName.value = newName
            // TODO: 调用修改用户名接口
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
            // TODO: 调用发送验证码接口
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

            // TODO: 调用修改密码接口
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
            // TODO: 调用修改手机号接口
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
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        bindDialog?.dismiss()
        bindDialog = null
        super.onDestroyView()
    }
}