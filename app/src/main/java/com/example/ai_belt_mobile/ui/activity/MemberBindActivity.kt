package com.example.ai_belt_mobile.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ai_belt_mobile.FamilyMainActivity
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.databinding.ActivityChooseIdentityBinding
import com.example.ai_belt_mobile.databinding.ActivityMemberBindBinding

class MemberBindActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMemberBindBinding

    //扫码结果回调
    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val code = result.data?.getStringExtra(ScanActivity.EXTRA_SCAN_RESULT).orEmpty()
            if (code.isNotBlank()) {
                binding.bindCode.setText(code)
                binding.bindCodeLayout.error = null
                binding.bindCode.setSelection(code.length)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMemberBindBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.scanQrCard.setOnClickListener {
            scanResultLauncher.launch(Intent(this, ScanActivity::class.java))
        }

        binding.skipBtn.setOnClickListener {
            val intent = Intent(this, FamilyMainActivity::class.java)
            startActivity(intent)
        }
    }
}