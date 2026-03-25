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
import com.example.ai_belt_mobile.navigation.WalkNaviActivity
import com.example.ai_belt_mobile.network.UserRetrofitClient
import com.example.ai_belt_mobile.network.WebSocketManager
import com.example.ai_belt_mobile.network.WsEvent
import org.json.JSONObject
import kotlin.code
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
    private var sosHoldJob: Job? = null
    private var sosTriggered = false
    private val SOS_HOLD_DURATION_MS = 1000L
    private var lastEmergencyDialogTs = 0L

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
                            // 启动导航页面
                            val intent = Intent(requireContext(), WalkNaviActivity::class.java)
                            intent.putExtra("destination", destination)
                            startActivity(intent)
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
        observeBleEmergency()
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
                if (it.contains("识别出错"))
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

    @SuppressLint("ClickableViewAccessibility")
    private fun initWebSocketDemoActions() {
        binding.sosHoldProgress.progress = 0

        binding.emergencyButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startSosHold()
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    endSosHold(cancelIfNotTriggered = true)
                    true
                }

                else -> false
            }
        }
    }

    private fun startSosHold() {
        sosHoldJob?.cancel()
        sosTriggered = false
        binding.sosHoldProgress.progress = 0

        sosHoldJob = viewLifecycleOwner.lifecycleScope.launch {
            val start = android.os.SystemClock.elapsedRealtime()

            while (true) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - start
                val ratio = (elapsed.toFloat() / SOS_HOLD_DURATION_MS).coerceIn(0f, 1f)
                binding.sosHoldProgress.progress = (ratio * 100).toInt()

                if (elapsed >= SOS_HOLD_DURATION_MS) {
                    sosTriggered = true
                    triggerSosAction() // 这里调用你现有SOS发送+拨号逻辑
                    break
                }
                kotlinx.coroutines.delay(16L)
            }
        }
    }

    private fun triggerSosAction() {
        val session = UserSessionStore.get(requireContext())
        if (session == null) {
            showToast("未登录，无法发送SOS")
            return
        }

        val locationManager =
            com.example.ai_belt_mobile.navigation.LocationManager(requireContext())
        locationManager.getAccurateLocation { location ->
            // 定位失败也继续发送：经纬度置空
            val longitude = location?.longitude?.toString().orEmpty()
            val latitude = location?.latitude?.toString().orEmpty()

            if (location == null) {
                showToast("定位失败，已发送空定位并继续拨号")
            }

            val sent = WebSocketManager.sendSOS(
                fromId = session.id.toString(),
                toId = null, // 后端按紧急联系人转发
                longitude = longitude,
                latitude = latitude,
                time = System.currentTimeMillis().toString()
            )

            if (!sent) {
                showToast("SOS发送失败：WebSocket未连接")
                return@getAccurateLocation
            }

            // 无论定位是否成功，只要SOS发送成功就继续找紧急联系人拨号
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
                } catch (_: Exception) {
                    showToast("获取紧急联系人失败，请稍后重试")
                }
            }
        }
    }

    private fun endSosHold(cancelIfNotTriggered: Boolean) {
        if (cancelIfNotTriggered && !sosTriggered) {
            showToast("已取消SOS")
            sosHoldJob?.cancel()
            sosHoldJob = null
            binding.sosHoldProgress.progress = 0
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
        val locationManager =
            com.example.ai_belt_mobile.navigation.LocationManager(requireContext())
        locationManager.getAccurateLocation { location ->
            val lng = location?.longitude?.toString().orEmpty()
            val lat = location?.latitude?.toString().orEmpty()

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
                                longitude = lng, // 定位失败时为空字符串
                                latitude = lat,  // 定位失败时为空字符串
                                time = System.currentTimeMillis().toString()
                            )

                            if (ok) {
                                if (location == null) showToast("定位失败，已返回空定位")
                                else showToast("已响应家属定位请求")
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

    private fun observeBleEmergency() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bleEmergencyEvents.collect {
                    // 简单防抖，避免设备短时间重复上报导致连续弹窗
                    val now = System.currentTimeMillis()
                    if (now - lastEmergencyDialogTs < 2000) return@collect
                    lastEmergencyDialogTs = now
                    showEmergencyHelpDialog()
                }
            }
        }
    }

    private fun showEmergencyHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("紧急提醒")
            .setMessage("检测到紧急情况，是否需要帮助？")
            .setCancelable(false)
            .setPositiveButton("需要帮助") { _, _ ->
                // TODO(voice): 这里接语音播报/语音确认流程（负责语音的同学补）
                // TODO: 这里可触发 SOS 发送 / 呼叫紧急联系人等联动
                showToast("已确认需要帮助")
            }
            .setNegativeButton("暂时不用") { _, _ ->
                // TODO(voice): 这里可接“已拒绝帮助”的语音反馈
                showToast("已记录：暂时不需要帮助")
            }
            .show()
    }
}
