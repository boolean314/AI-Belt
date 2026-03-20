package com.example.ai_belt_mobile.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ai_belt_mobile.MainActivity
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.databinding.ActivityChooseIdentityBinding

class ChooseIdentityActivity : AppCompatActivity() {

    private lateinit var binding : ActivityChooseIdentityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChooseIdentityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.disabledBtn.setOnClickListener {
            val data = 0    //携带残疾人身份跳转至注册页面
            val intent = Intent(this, RegisterActivity::class.java)
            intent.putExtra("identity", data)
            startActivity(intent)
        }
        binding.familyMemberBtn.setOnClickListener {
            val data = 1    //携带家庭成员身份跳转至注册页面
            val intent = Intent(this, RegisterActivity::class.java)
            intent.putExtra("identity", data)
            startActivity(intent)
        }
        binding.loginEntrance.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}