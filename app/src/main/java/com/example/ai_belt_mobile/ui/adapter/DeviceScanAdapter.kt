package com.example.ai_belt_mobile.ui.adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ai_belt_mobile.R

class DeviceScanAdapter(
    private val onClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceScanAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()

    fun submitDevices(list: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(list)
        notifyDataSetChanged()
    }

    fun addIfAbsent(device: BluetoothDevice) {
        val addr = safeAddress(device)
        if (addr.isNotEmpty() && devices.any { safeAddress(it) == addr }) return
        devices.add(device)
        notifyItemInserted(devices.lastIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return DeviceViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(
        itemView: View,
        private val onClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvDeviceAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)

        fun bind(device: BluetoothDevice) {
            tvDeviceName.text = safeName(device)
            tvDeviceAddress.text = safeAddress(device).ifEmpty { "地址不可用" }
            itemView.setOnClickListener { onClick(device) }
        }
    }

    companion object {
        @SuppressLint("MissingPermission")
        private fun safeName(device: BluetoothDevice): String {
            return try {
                device.name ?: "未知设备"
            } catch (_: SecurityException) {
                "未知设备"
            }
        }

        @SuppressLint("MissingPermission")
        private fun safeAddress(device: BluetoothDevice): String {
            return try {
                device.address ?: ""
            } catch (_: SecurityException) {
                ""
            }
        }
    }
}