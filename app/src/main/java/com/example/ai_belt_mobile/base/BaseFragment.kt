package com.example.ai_belt_mobile.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment

abstract class BaseFragment<VB : ViewDataBinding> : Fragment() {
    
    abstract val layoutId: Int
    protected lateinit var binding: VB
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner
        initView()
        initData()
    }
    
    open fun initView() {}
    
    open fun initData() {}
    
    private var voiceInputPopup: PopupWindow? = null
    
    fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    fun showVoiceInputPopup() {
        // 创建弹窗视图
        val popupView = LayoutInflater.from(requireContext()).inflate(android.R.layout.simple_list_item_1, null)
        val textView = popupView.findViewById<TextView>(android.R.id.text1)
        textView.text = "正在输入..."
        textView.textSize = 18f
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        
        // 创建并显示弹窗
        voiceInputPopup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        voiceInputPopup?.showAtLocation(requireView(), android.view.Gravity.CENTER, 0, 0)
    }
    
    fun hideVoiceInputPopup() {
        voiceInputPopup?.dismiss()
        voiceInputPopup = null
    }
}