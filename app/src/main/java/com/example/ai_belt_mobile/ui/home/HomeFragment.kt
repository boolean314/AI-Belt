package com.example.ai_belt_mobile.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.location.Location
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
import com.example.ai_belt_mobile.voice.BaiduTTSManager
import com.example.ai_belt_mobile.voice.LocationManager
import com.example.ai_belt_mobile.voice.NavigationManager
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

class HomeFragment : BaseFragment<FragmentHomeBinding>(), DeviceScanDialogFragment.Callbacks {

    override val layoutId: Int = R.layout.fragment_home
    private lateinit var viewModel: HomeViewModel
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // region BLE模块 - 字段
    private lateinit var cardConnectStatus: MaterialCardView
    private lateinit var tvConnectStatus: TextView
    private var scanTimeoutJob: kotlinx.coroutines.Job? = null

    // region 导航模块 - 字段
    private lateinit var locationManager: LocationManager
    private lateinit var navigationManager: NavigationManager
    private lateinit var ttsManager: BaiduTTSManager
    
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
    }

    // region 导航模块
    private fun initNavigationView() {
        // 初始化管理器
        locationManager = LocationManager(requireContext())
        navigationManager = NavigationManager(requireContext())
        ttsManager = BaiduTTSManager.getInstance()
        
        // 初始化TTS
        ttsManager.init(requireContext())
        // 初始化导航
        navigationManager.init()
        
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
        
        // 请求定位权限
        locationManager.requestLocationPermission {
            if (it) {
                // 获取当前位置
                locationManager.getCurrentLocation { location ->
                    if (location != null) {
                        // 开始导航
                        navigationManager.startNavigation(
                            startLocation = location,
                            destination = destination,
                            onNavigationStarted = {
                                showToast("导航开始，前往$destination")
                            },
                            onError = {
                                showToast("导航失败: $it")
                            }
                        )
                    } else {
                        showToast("无法获取当前位置")
                    }
                }
            } else {
                showToast("需要定位权限才能导航")
            }
        }
    }
    // endregion

    override fun initData() {
        observeVoiceState()
        observeBleState()
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
        locationManager.stopLocationUpdates()
        navigationManager.release()
        ttsManager.release()
        super.onDestroyView()
        scope.cancel()
    }

    // endregion
}