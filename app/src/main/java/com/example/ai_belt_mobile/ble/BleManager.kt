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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

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
        fun onRequestHotspotCredentials() // 新增：请求上层显示热点输入对话框
        fun onMtuReady() // 新增：MTU协商完成后通知上层
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null
    private var isMtuReady = false // 新增：MTU是否就绪

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
        isMtuReady = false // 重置MTU状态
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
    suspend fun write(data: ByteArray): Boolean {
        Log.d("BleManager", "【write】开始写入数据，长度=${data.size}")
        
        val g = gatt ?: run {
            Log.e("BleManager", "【write】失败：gatt null")
            listener.onError("write failed: gatt null")
            return false
        }
        Log.d("BleManager", "【write】gatt 非空")
        
        val service = g.getService(SERVICE_UUID) ?: run {
            Log.e("BleManager", "【write】失败：service null, SERVICE_UUID=$SERVICE_UUID")
            listener.onError("write failed: service null")
            return false
        }
        Log.d("BleManager", "【write】找到服务: ${service.uuid}")
        
        val ch = service.getCharacteristic(WRITE_UUID) ?: run {
            Log.e("BleManager", "【write】失败：characteristic null, WRITE_UUID=$WRITE_UUID")
            listener.onError("write failed: char null")
            return false
        }
        Log.d("BleManager", "【write】找到特征: ${ch.uuid}")
        
        ch.value = data
        pendingWrite = CompletableDeferred()
        val ok = g.writeCharacteristic(ch)
        val textPreview = data.toString(Charsets.UTF_8).take(50)
        Log.d("BleManager", "【write】调用 writeCharacteristic 返回: $ok, 数据预览='$textPreview', 长度=${data.size}")

        return if (ok) {
            Log.d("BleManager", "【write】等待 onCharacteristicWrite 回调...")
            val result = withTimeoutOrNull(5000L) {
                pendingWrite?.await() ?: false
            } ?: false
            Log.d("BleManager", "【write】等待完成，结果: $result")
            result
        } else {
            Log.e("BleManager", "【write】writeCharacteristic 返回 false")
            pendingWrite?.cancel()
            false
        }
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
                // Request a larger MTU to prevent data truncation, credentials will be sent after MTU is ready
                val mtuRequested = g.requestMtu(512)
                Log.d("BLE_MTU", "requestMtu(512) called, result=$mtuRequested")
            } else if (descriptor.uuid == CCCD_UUID) {
                listener.onError("CCCD write failed: $status")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE_MTU", "MTU changed to $mtu")
            } else {
                Log.w("BLE_MTU", "Failed to change MTU to $mtu, status=$status")
            }
            // MTU协商完成（无论成功失败），通知上层可以发送凭据
            isMtuReady = true
            Log.d("BLE_MTU", "Calling listener.onMtuReady()")
            listener.onMtuReady()
            Log.d("BLE_MTU", "listener.onMtuReady() returned")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val value = ch.value ?: ByteArray(0)
            Log.d("BLE_RX_RAW", "uuid=${ch.uuid}, len=${value.size}")
            if (ch.uuid == NOTIFY_UUID) listener.onMessage(value)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d("BLE_TX_CALLBACK", "onCharacteristicWrite uuid=${characteristic.uuid}, status=$status")
            val success = status == BluetoothGatt.GATT_SUCCESS
            pendingWrite?.complete(success)
            pendingWrite = null
            if (!success) {
                listener.onError("writeCharacteristic failed: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun writeText(text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        Log.d("BleManager", "【writeText】准备发送文本 - '$text', 字节长度=${bytes.size}")
        val result = write(bytes)
        Log.d("BleManager", "【writeText】发送结果 - success=$result, text='$text'")
        return result
    }

    @SuppressLint("MissingPermission")
    suspend fun sendHotspotCredentials(ssid: String, password: String): Boolean {
        Log.d("BLE_HOTSPOT", "【sendHotspotCredentials】开始 - ssid=$ssid, password=${password.replace(Regex("."), "*")}")
        val data = "WIFI:$ssid:$password"
        Log.d("BLE_HOTSPOT", "【sendHotspotCredentials】数据: $data, 长度=${data.length}")
        val result = writeText(data)
        Log.d("BLE_HOTSPOT", "【sendHotspotCredentials】结果: $result")
        return result
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        // 这里不走 onError，避免把正常扫描当错误状态
        listener.onScanFound(result.device)
    }
}