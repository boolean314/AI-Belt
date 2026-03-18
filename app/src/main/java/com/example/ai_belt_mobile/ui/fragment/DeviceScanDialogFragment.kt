package com.example.ai_belt_mobile.ui.fragment

import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.ui.adapter.DeviceScanAdapter
import com.google.android.material.button.MaterialButton
import kotlin.collections.containsKey
import kotlin.text.clear
import kotlin.text.set

class DeviceScanDialogFragment : DialogFragment() {

    interface Callbacks {
        fun onDeviceChosen(device: BluetoothDevice)
        fun onDialogClosed()
        fun onRefreshRequested()
    }

    private val devices = linkedMapOf<String, BluetoothDevice>()
    private var adapter: DeviceScanAdapter? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    companion object {
        const val TAG = "DeviceScanDialogFragment"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentView: View =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_device_scan, null, false)

        val rvDevices = contentView.findViewById<RecyclerView>(R.id.rvDevices)
        val btnCancel = contentView.findViewById<MaterialButton>(R.id.btnCancel)

        swipeRefreshLayout = contentView.findViewById(R.id.swipeRefreshDevices)

        adapter = DeviceScanAdapter { device ->
            (parentFragment as? Callbacks)?.onDeviceChosen(device)
            dismissAllowingStateLoss()
        }

        rvDevices.layoutManager = LinearLayoutManager(requireContext())
        rvDevices.adapter = adapter
        adapter?.submitDevices(devices.values.toList())

        swipeRefreshLayout?.setOnRefreshListener {
            clearDevices()
            (parentFragment as? DeviceScanDialogFragment.Callbacks)?.onRefreshRequested()
        }

        btnCancel.setOnClickListener { dismissAllowingStateLoss() }

        return AlertDialog.Builder(requireContext())
            .setView(contentView)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        (parentFragment as? Callbacks)?.onDialogClosed()
    }

    fun addOrUpdateDevice(device: BluetoothDevice) {
        val address = safeAddress(device)
        if (address.isEmpty()) return
        if (devices.containsKey(address)) return

        devices[address] = device
        adapter?.addIfAbsent(device)
        swipeRefreshLayout?.isRefreshing = false
    }

    private fun safeAddress(device: BluetoothDevice): String {
        return try {
            device.address ?: ""
        } catch (_: SecurityException) {
            ""
        }
    }

    private fun clearDevices() {
        devices.clear()
        adapter?.submitDevices(emptyList())
    }

    fun setRefreshing(refreshing: Boolean) {
        swipeRefreshLayout?.isRefreshing = refreshing
    }
}