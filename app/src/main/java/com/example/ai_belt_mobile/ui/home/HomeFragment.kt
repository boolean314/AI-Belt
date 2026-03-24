package com.example.ai_belt_mobile.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.base.BaseFragment
import com.example.ai_belt_mobile.databinding.FragmentHomeBinding
import com.example.ai_belt_mobile.ui.fragment.DeviceScanDialogFragment

import com.google.android.material.card.MaterialCardView
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
import com.example.ai_belt_mobile.data.local.UserSessionStore
import com.example.ai_belt_mobile.network.UserRetrofitClient
import com.example.ai_belt_mobile.network.WebSocketManager
import com.example.ai_belt_mobile.network.WsEvent
import org.json.JSONObject
import kotlin.text.get
import kotlin.toString

class HomeFragment : BaseFragment<FragmentHomeBinding>(), DeviceScanDialogFragment.Callbacks {

    override val layoutId: Int = R.layout.fragment_home
    private lateinit var viewModel: HomeViewModel
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // region BLE模块 - 字段
    private lateinit var cardConnectStatus: MaterialCardView
    private lateinit var tvConnectStatus: TextView
    private var scanTimeoutJob: kotlinx.coroutines.Job? = null

    private val blePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                showScanDialog()
            }
        }
    // endregion

    override fun initView() {
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        initVoiceView()
        initBleView()
        initNavigationView()

        initWebSocketDemoActions()
    }

    // region 导航模块
    private fun initNavigationView() {
        // 初始化导航
        viewModel.initNavigation()
        
        // 导航按钮点击事件
        binding.startNavigationButton.setOnClickListener {
            startNavigation()
        }
    }

    private fun startNavigation() {
        val destination = binding.destinationInput.text.toString().trim()
        if (destination.isEmpty()) {
            showToast("请输入目的地")
            return
        }
        
        // 在Fragment中请求定位权限
        XXPermissions.with(requireActivity())
            .permission(PermissionLists.getAccessFineLocationPermission())
            .permission(PermissionLists.getAccessCoarseLocationPermission())
            .permission(PermissionLists.getAccessBackgroundLocationPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: MutableList<IPermission>,
                    deniedList: MutableList<IPermission>
                ) {
                    val hasPermission = deniedList.isEmpty()
                    
                    // 将权限结果传递给ViewModel
                    viewModel.startNavigation(
                        destination = destination,
                        hasLocationPermission = hasPermission,
                        onNavigationStarted = {
                            showToast("导航开始，前往$destination")
                        },
                        onError = {
                            showToast("导航失败: $it")
                        }
                    )
                }
            })
    }
    // endregion

    override fun initData() {
        observeVoiceState()
        observeBleState()
        observeWsRequestAndReplyLocation()
    }

    // region 语音模块
    @SuppressLint("ClickableViewAccessibility")
    private fun initVoiceView() {
        binding.voiceInputButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下时请求权限并开始识别
                    requestAudioPermission()
                    showVoiceInputPopup()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 抬起或取消时停止识别
                    viewModel.stopVoiceRecognition()
                    hideVoiceInputPopup()
                    true
                }
                else -> false
            }
        }
    }

    private fun observeVoiceState() {
        // 监听识别结果
        scope.launch {
            viewModel.recognitionResult.collect {
                // 这里可以更新UI显示识别结果
                if(it.contains("识别出错"))
                showToast(it)
            }
        }
    }

    private fun requestAudioPermission() {
        XXPermissions.with(requireActivity())
            .permission(PermissionLists.getRecordAudioPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: MutableList<IPermission>,
                    deniedList: MutableList<IPermission>
                ) {
                    if (deniedList.isNotEmpty()) {
                        val doNotAskAgain =
                            XXPermissions.isDoNotAskAgainPermissions(requireActivity(), deniedList)
                        Log.e("HomeFragment", "Audio permission denied")
                        if (doNotAskAgain) {
                            XXPermissions.startPermissionActivity(requireActivity(), deniedList)
                        }
                        return
                    }
                    viewModel.startVoiceRecognition()
                }
            })
    }
    // endregion

    // region BLE模块
    private fun initBleView() {
        val root = binding.root
        cardConnectStatus = root.findViewById(R.id.Belt_status_layout)
        tvConnectStatus = root.findViewById(R.id.Belt_status)

        cardConnectStatus.setOnClickListener {
            when (viewModel.bleState.value) {
                HomeBleState.Disconnected, is HomeBleState.Error -> ensureBlePermissionThenScan()
                is HomeBleState.Connecting, is HomeBleState.Connected -> Unit
            }
        }
    }

    private fun observeBleState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bleState.collect { state ->
                    tvConnectStatus.text = when (state) {
                        HomeBleState.Disconnected -> "未连接设备，点击连接"
                        is HomeBleState.Connecting -> "连接中：${state.name}"
                        is HomeBleState.Connected -> {
                            val batteryText = state.battery?.let { "$it%" } ?: "--%"
                            "剩余电量：$batteryText"
                        }
                        is HomeBleState.Error -> "连接异常：${state.msg}"
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scanDeviceEvents.collect { device ->
                    (childFragmentManager.findFragmentByTag(DeviceScanDialogFragment.TAG) as? DeviceScanDialogFragment)
                        ?.addOrUpdateDevice(device)
                }
            }
        }
    }

    private fun ensureBlePermissionThenScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val granted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (granted) {
            showScanDialog()
        } else {
            blePermissionLauncher.launch(permissions)
        }
    }

    private fun showScanDialog() {
        if (childFragmentManager.findFragmentByTag(DeviceScanDialogFragment.TAG) != null) return
        DeviceScanDialogFragment().show(childFragmentManager, DeviceScanDialogFragment.TAG)
        startScanWithTimeout()
    }

    override fun onDeviceChosen(device: BluetoothDevice) {
        viewModel.connect(device)
    }

    override fun onDialogClosed() {
        viewModel.stopScan()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
    }

    override fun onRefreshRequested() {
        viewModel.stopScan()
        startScanWithTimeout()
    }

    private fun startScanWithTimeout() {
        viewModel.stopScan()
        viewModel.startScan()

        (childFragmentManager.findFragmentByTag(DeviceScanDialogFragment.TAG) as? DeviceScanDialogFragment)
            ?.setRefreshing(true)

        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(10_000L) // 10s
            viewModel.stopScan()
            (childFragmentManager.findFragmentByTag(DeviceScanDialogFragment.TAG) as? DeviceScanDialogFragment)
                ?.setRefreshing(false)
        }
    }

    override fun onDestroyView() {
        viewModel.stopScan()
        // 释放导航相关资源
        viewModel.releaseNavigation()
        super.onDestroyView()
        scope.cancel()
    }

    // endregion

    private fun initWebSocketDemoActions() {
        binding.emergencyButton.setOnClickListener {
            val session = UserSessionStore.get(requireContext())
            if (session == null) {
                showToast("未登录，无法发送SOS")
                return@setOnClickListener
            }

            val sent = WebSocketManager.sendSOS(
                fromId = session.id.toString(),
                toId = null, // 后端按紧急联系人转发
                longitude = "116.4074", // TODO(partner): 替换真实经度
                latitude = "39.9042",   // TODO(partner): 替换真实纬度
                time = System.currentTimeMillis().toString()
            )

            if (!sent) {
                showToast("SOS发送失败：WebSocket未连接")
                return@setOnClickListener
            }

            // 发送成功后，从家属列表里找紧急联系人并直接拨号
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val resp = UserRetrofitClient.instance.getFamilyInfo(session.id)
                    if (resp.code != 200) {
                        showToast("获取家属列表失败：${resp.message}")
                        return@launch
                    }

                    val emergencyPhone = resp.data.firstOrNull { it.isEmergency }?.phone.orEmpty()
                    if (emergencyPhone.isBlank()) {
                        showToast("未设置紧急联系人，无法拨号")
                        return@launch
                    }

                    callEmergencyPhone(emergencyPhone)
                } catch (e: Exception) {
                    showToast("获取紧急联系人失败，请稍后重试")
                }
            }
        }
    }

    private fun callEmergencyPhone(phone: String) {
        val target = phone.trim()
        if (target.isEmpty()) {
            showToast("紧急联系人号码为空")
            return
        }

        XXPermissions.with(requireActivity())
            .permission(PermissionLists.getCallPhonePermission())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: MutableList<IPermission>,
                    deniedList: MutableList<IPermission>
                ) {
                    if (deniedList.isNotEmpty()) {
                        // 无权限时降级到拨号盘，避免误报“获取失败”
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target")))
                        showToast("未授予通话权限，已打开拨号界面")
                        return
                    }

                    try {
                        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$target")))
                    } catch (e: Exception) {
                        showToast("拨号失败：${e.message ?: "请稍后重试"}")
                    }
                }
            })
    }

    private fun observeWsRequestAndReplyLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WebSocketManager.events.collect { event ->
                    if (event !is WsEvent.Message) return@collect

                    try {
                        val root = JSONObject(event.text)
                        if (root.optString("type") != "request") return@collect

                        val session = UserSessionStore.get(requireContext()) ?: return@collect
                        val myId = session.id.toString()
                        val toId = root.optString("toId")
                        if (toId != myId) return@collect

                        val fromFamilyId = root.optString("fromId")
                        if (fromFamilyId.isBlank()) return@collect

                        val ok = WebSocketManager.sendLocation(
                            fromId = myId,
                            toId = fromFamilyId,
                            longitude = "116.4074", // TODO(partner): 替换为真实GPS经度
                            latitude = "39.9042",   // TODO(partner): 替换为真实GPS纬度
                            time = System.currentTimeMillis().toString() // TODO(partner): 替换为真实定位时间
                        )

                        if (ok) {
                            showToast("已响应家属定位请求")
                        } else {
                            showToast("定位响应失败：WebSocket未连接")
                        }
                    } catch (_: Exception) {
                        //忽略非JSON或非协议消息
                    }
                }
            }
        }
    }
}
