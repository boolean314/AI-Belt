package com.example.ai_belt_mobile.ui.home

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import com.example.ai_belt_mobile.ble.BleManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HomeBleState {
    data object Disconnected : HomeBleState
    data class Connecting(val name: String) : HomeBleState
    data class Connected(val name: String, val battery: Int?) : HomeBleState
    data class Error(val msg: String) : HomeBleState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<HomeBleState>(HomeBleState.Disconnected)
    val uiState = _uiState.asStateFlow()

    private val _scanDeviceEvents = MutableSharedFlow<BluetoothDevice>(extraBufferCapacity = 32)
    val scanDeviceEvents = _scanDeviceEvents.asSharedFlow()

    private var currentDeviceName: String = "设备"
    private lateinit var bleManager: BleManager

    private val bleListener = object : BleManager.Listener {
        override fun onScanFound(device: BluetoothDevice) {
            _scanDeviceEvents.tryEmit(device)
        }

        override fun onConnected() {
            _uiState.value = HomeBleState.Connected(currentDeviceName, null)
            requestBattery()
        }

        override fun onDisconnected() {
            _uiState.value = HomeBleState.Disconnected
        }

        override fun onMessage(bytes: ByteArray) {
            val battery = parseBattery(bytes) ?: return
            val old = _uiState.value
            if (old is HomeBleState.Connected) {
                _uiState.value = old.copy(battery = battery)
            }
        }

        override fun onError(msg: String) {
            _uiState.value = HomeBleState.Error(msg)
        }
    }

    init {
        bleManager = BleManager(app.applicationContext, bleListener)
    }

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        currentDeviceName = device.name ?: device.address ?: "设备"
        _uiState.value = HomeBleState.Connecting(currentDeviceName)
        bleManager.stopScan()
        bleManager.connect(device, getApplication<Application>().applicationContext)
    }

    private fun requestBattery() {
        bleManager.write("BAT?\n".toByteArray())
    }

    fun disconnect() = bleManager.disconnect()

    private fun parseBattery(bytes: ByteArray): Int? {
        val text = bytes.toString(Charsets.UTF_8).trim()
        Regex("(?i)(?:BAT|BATT|BATTERY)\\s*[:=]\\s*(\\d{1,3})")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it.coerceIn(0, 100) }
        text.toIntOrNull()?.let { return it.coerceIn(0, 100) }
        if (bytes.size == 1) {
            val v = bytes[0].toInt() and 0xFF
            if (v in 0..100) return v
        }
        return null
    }

    override fun onCleared() {
        bleManager.disconnect()
        super.onCleared()
    }
}