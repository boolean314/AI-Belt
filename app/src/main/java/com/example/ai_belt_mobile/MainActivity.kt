package com.example.ai_belt_mobile

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.ai_belt_mobile.network.WebSocketManager
import com.example.ai_belt_mobile.ui.home.HomeFragment
import com.example.ai_belt_mobile.ui.home.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ai_belt_mobile.network.WsEvent
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private var wsConnectedToastShown = false

    override fun onStart() {
        super.onStart()
        val session = com.example.ai_belt_mobile.data.local.UserSessionStore.get(this)
        if (session != null) {
            WebSocketManager.connect(session.id, session.identity)
        }
    }

    override fun onStop() {
        super.onStop()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        viewPager = findViewById(R.id.view_pager)
        bottomNav = findViewById(R.id.bottom_nav)
        
        setupViewPager()
        setupBottomNav()

        observeWsConnectionTip()
    }
    
    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> bottomNav.selectedItemId = R.id.nav_home
                    1 -> bottomNav.selectedItemId = R.id.nav_profile
                }
            }
        })
    }
    
    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> viewPager.currentItem = 0
                R.id.nav_profile -> viewPager.currentItem = 1
            }
            true
        }
    }
    
    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment() as Fragment
                1 -> ProfileFragment() as Fragment
                else -> HomeFragment() as Fragment
            }
        }
    }

    private fun observeWsConnectionTip() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WebSocketManager.events.collect { event ->
                    when (event) {
                        is WsEvent.Opened -> {
                            if (!wsConnectedToastShown) {
                                Toast.makeText(this@MainActivity, "WebSocket连接成功", Toast.LENGTH_SHORT).show()
                                wsConnectedToastShown = true
                            }
                        }
                        is WsEvent.Error -> {
                            Toast.makeText(this@MainActivity, "WebSocket连接失败", Toast.LENGTH_SHORT).show()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}