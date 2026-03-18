package com.example.ai_belt_mobile.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.databinding.ActivityChooseMemberBinding
import com.example.ai_belt_mobile.ui.adapter.MemberItem
import com.example.ai_belt_mobile.ui.adapter.MemberListAdapter

class ChooseMemberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseMemberBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChooseMemberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupMemberList()
    }

    private fun setupMemberList() {
        val memberItems = listOf(
            MemberItem(name = "王姨", phone = "13710153872"),
            MemberItem(name = "李叔", phone = "13800001111"),
            MemberItem(name = "张叔", phone = "13922223333")
        )

        binding.memberList.layoutManager = LinearLayoutManager(this)
        binding.memberList.adapter = MemberListAdapter(
            data = memberItems,
            onItemClick = { item ->
                // TODO: 点击成员逻辑
            },
            onEmergencyClick = { item ->
                // TODO: 紧急按钮逻辑
            }
        )
    }
}