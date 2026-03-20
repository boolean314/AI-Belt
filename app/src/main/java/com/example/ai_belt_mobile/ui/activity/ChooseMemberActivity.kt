package com.example.ai_belt_mobile.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.databinding.ActivityChooseMemberBinding
import com.example.ai_belt_mobile.ui.adapter.MemberItem
import com.example.ai_belt_mobile.ui.adapter.MemberListAdapter
import com.example.ai_belt_mobile.viewModel.ChooseMemberViewModel
import kotlinx.coroutines.launch
import kotlin.getValue

class ChooseMemberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseMemberBinding
    private val viewModel: ChooseMemberViewModel by viewModels()
    private lateinit var adapter: MemberListAdapter

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

        adapter = MemberListAdapter(
            onItemClick = { /* 可扩展详情页 */ },
            onEmergencyClick = { item -> viewModel.switchEmergency(this, item) }
        )
        binding.memberList.layoutManager = LinearLayoutManager(this)
        binding.memberList.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.members.collect { list ->
                    adapter.submitList(list)
                }
            }
        }

        viewModel.loadMembers(this)
    }
}