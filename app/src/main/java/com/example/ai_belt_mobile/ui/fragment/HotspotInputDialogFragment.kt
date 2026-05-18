package com.example.ai_belt_mobile.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.example.ai_belt_mobile.R

class HotspotInputDialogFragment : DialogFragment() {

    interface HotspotInputListener {
        fun onHotspotCredentialsEntered(ssid: String, password: String)
    }

    private var listener: HotspotInputListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.dialog_hotspot_input, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSsid = view.findViewById<EditText>(R.id.et_hotspot_ssid)
        val etPassword = view.findViewById<EditText>(R.id.et_hotspot_password)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnSend = view.findViewById<Button>(R.id.btn_send)

        btnCancel.setOnClickListener {
            Log.d("HotspotDialog", "Cancel button clicked.")
            dismiss() // Close the dialog
        }

        btnSend.setOnClickListener {
            val ssid = etSsid.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (ssid.isNotEmpty() && password.isNotEmpty()) {
                Log.d("HotspotDialog", "Send button clicked. SSID: $ssid, Password: ${"*".repeat(password.length)}")
                listener?.onHotspotCredentialsEntered(ssid, password)
                dismiss() // Close the dialog
            } else {
                Log.d("HotspotDialog", "SSID or Password is empty.")
                // Optionally, show a toast or error message to the user
                if (ssid.isEmpty()) {
                    etSsid.error = "SSID不能为空"
                }
                if (password.isEmpty()) {
                    etPassword.error = "密码不能为空"
                }
            }
        }
    }

    // This method can be called from the hosting Activity/Fragment to set the listener
    fun setHotspotInputListener(listener: HotspotInputListener) {
        this.listener = listener
    }

    companion object {
        const val TAG = "HotspotInputDialogFragment"
    }
}
