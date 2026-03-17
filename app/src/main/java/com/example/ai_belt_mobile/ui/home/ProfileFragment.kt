package com.example.ai_belt_mobile.ui.home

import androidx.lifecycle.ViewModelProvider
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.base.BaseFragment
import com.example.ai_belt_mobile.databinding.FragmentProfileBinding

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {
    override val layoutId: Int = R.layout.fragment_profile

    private lateinit var viewModel: ProfileViewModel

    override fun initView() {
        super.initView()
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        binding.viewModel = viewModel
    }
}