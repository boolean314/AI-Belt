package com.example.ai_belt_mobile.ui.home

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.base.BaseFragment
import com.example.ai_belt_mobile.ui.fragment.DeviceScanDialogFragment
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment(), DeviceScanDialogFragment.Callbacks {

    override val layoutId: Int = R.layout.fragment_home

    private val viewModel: HomeViewModel by lazy {
        ViewModelProvider(this)[HomeViewModel::class.java]
    }

    private lateinit var cardConnectStatus: MaterialCardView
    private lateinit var tvConnectStatus: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) showScanDialog()
        }

    override fun initView() {
        val root = requireView()
        cardConnectStatus = root.findViewById(R.id.Belt_status_layout)
        tvConnectStatus = root.findViewById(R.id.Belt_status)

        cardConnectStatus.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is HomeBleState.Connected || state is HomeBleState.Connecting) return@setOnClickListener
            ensureBlePermissionThenScan()
        }
    }

    override fun initData() {
        observeUiState()
        observeScanEvents()
    }

    override fun onDeviceChosen(device: BluetoothDevice) {
        viewModel.connect(device)
    }

    override fun onDialogClosed() {
        viewModel.stopScan()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    tvConnectStatus.text = when (state) {
                        HomeBleState.Disconnected -> "未连接设备，点击连接"
                        is HomeBleState.Connecting -> "连接中：${state.name}"
                        is HomeBleState.Connected -> "已连接：${state.name}  电量：${state.battery?.let { "$it%" } ?: "--%"}"
                        is HomeBleState.Error -> "连接异常：${state.msg}"
                    }
                }
            }
        }
    }

    private fun observeScanEvents() {
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
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val granted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (granted) showScanDialog() else permissionLauncher.launch(permissions)
    }

    private fun showScanDialog() {
        if (childFragmentManager.findFragmentByTag(DeviceScanDialogFragment.TAG) != null) return
        DeviceScanDialogFragment().show(childFragmentManager, DeviceScanDialogFragment.TAG)
        viewModel.startScan()
    }

    override fun onDestroyView() {
        viewModel.stopScan()
        super.onDestroyView()
    }
}