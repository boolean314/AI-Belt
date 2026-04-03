package com.example.ai_belt_mobile.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.UUID
import kotlin.and
import kotlin.toString

class BleManager(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onScanFound(device: BluetoothDevice)
        fun onConnected()
        fun onDisconnected()
        fun onMessage(bytes: ByteArray)
        fun onError(msg: String)
        fun onNotifyReady() // 新增：CCCD写成功后通知上层
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val WRITE_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val NOTIFY_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onError("scan failed: $errorCode")
        }

    }

    @SuppressLint("MissingPermission")
    fun startScan() {
//        val filter = ScanFilter.Builder()
//            .setServiceUuid(ParcelUuid(SERVICE_UUID))
//            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        scanner?.startScan(null, settings, scanCallback)
            ?: listener.onError("BLE scanner unavailable")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, appContext: Context) {
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        listener.onDisconnected()
    }

    @SuppressLint("MissingPermission")
    fun write(data: ByteArray) {
        val g = gatt ?: run {
            listener.onError("write failed: gatt null")
            return
        }
        val service = g.getService(SERVICE_UUID) ?: run {
            listener.onError("write failed: service null")
            return
        }
        val ch = service.getCharacteristic(WRITE_UUID) ?: run {
            listener.onError("write failed: char null")
            return
        }
        ch.value = data
        val ok = g.writeCharacteristic(ch)
        Log.d("BLE_TX", "send=${data.toString(Charsets.UTF_8)} len=${data.size}, ok=$ok")
    }

    @SuppressLint("MissingPermission")
    fun writeText(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onConnected()
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener.onDisconnected()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("discoverServices failed: $status")
                return
            }
            val service = g.getService(SERVICE_UUID) ?: run {
                listener.onError("service not found: $SERVICE_UUID")
                return
            }
            val notifyChar = service.getCharacteristic(NOTIFY_UUID) ?: run {
                listener.onError("notify char not found: $NOTIFY_UUID")
                return
            }
            Log.d("BLE_DBG", "notifyChar=${notifyChar.uuid}, props=0x${notifyChar.properties.toString(16)}")
            notifyChar.descriptors.forEach { d ->
                Log.d("BLE_DBG", "desc=${d.uuid}")
            }

            g.setCharacteristicNotification(notifyChar, true)

            val cccd = notifyChar.getDescriptor(CCCD_UUID) ?: run {
                listener.onError("CCCD not found")
                return
            }

            val props = notifyChar.properties
            val useIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            val useNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0

            cccd.value = when {
                useNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                useIndicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> {
                    listener.onError("char has no NOTIFY/INDICATE property")
                    return
                }
            }

            val ok = g.writeDescriptor(cccd)
            Log.d("BLE_SUB", "write CCCD start ok=$ok, mode=${if (useNotify) "NOTIFY" else "INDICATE"}")
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d("BLE_SUB", "onDescriptorWrite uuid=${descriptor.uuid}, status=$status")
            if (descriptor.uuid == CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                listener.onNotifyReady()
            } else if (descriptor.uuid == CCCD_UUID) {
                listener.onError("CCCD write failed: $status")
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val value = ch.value ?: ByteArray(0)
            Log.d("BLE_RX_RAW", "uuid=${ch.uuid}, len=${value.size}")
            if (ch.uuid == NOTIFY_UUID) listener.onMessage(value)
        }


    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        // 这里不走 onError，避免把正常扫描当错误状态
        listener.onScanFound(result.device)
    }
}