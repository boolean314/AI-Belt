package com.example.ai_belt_mobile.ui.home

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.base.BaseFragment
import com.example.ai_belt_mobile.databinding.FragmentHomeBinding
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    override val layoutId: Int = R.layout.fragment_home

    private lateinit var viewModel: HomeViewModel
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun initView() {
        super.initView()
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        binding.viewModel = viewModel

        // 初始化语音按钮长按事件
        binding.voiceInputButton.setOnLongClickListener {
            requestAudioPermission()
            true
        }

        // 监听识别结果
        scope.launch {
            viewModel.recognitionResult.collect {
                Log.d("HomeFragment", "Recognition result: $it")
                // 这里可以更新UI显示识别结果
            }
        }
    }

    private fun requestAudioPermission() {
        XXPermissions.with(requireActivity())
            .permission(PermissionLists.getRecordAudioPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                    val allGranted = deniedList.isEmpty()
                    if (!allGranted) {
                        // 判断请求失败的权限是否被用户勾选了不再询问的选项
                        val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                        Log.e("HomeFragment", "Permission denied")
                        if (doNotAskAgain) {
                            XXPermissions.startPermissionActivity(requireActivity(), deniedList)
                        }
                        return
                    }
                    // 权限请求成功，开始语音识别
                    startVoiceRecognition()
                }
            })
    }

    private fun startVoiceRecognition() {
        // 开始语音识别
        viewModel.startVoiceRecognition()

        // 设置按钮释放事件
        binding.voiceInputButton.setOnClickListener {
            stopVoiceRecognition()
        }
    }

    private fun stopVoiceRecognition() {
        // 停止语音识别
        viewModel.stopVoiceRecognition()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}