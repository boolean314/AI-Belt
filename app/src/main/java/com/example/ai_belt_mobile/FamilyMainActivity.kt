package com.example.ai_belt_mobile

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.ai_belt_mobile.databinding.ActivityFamilyMainBinding
import com.example.ai_belt_mobile.ui.family.FamilyFragment
import com.example.ai_belt_mobile.ui.family.FamilyProfileFragment
import com.example.ai_belt_mobile.ui.home.HomeFragment
import com.example.ai_belt_mobile.ui.home.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class FamilyMainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var binding: ActivityFamilyMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFamilyMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewPager = findViewById(R.id.view_pager)
        bottomNav = findViewById(R.id.bottom_nav)

        setupViewPager()
        setupBottomNav()
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
                0 -> FamilyFragment() as Fragment
                1 -> FamilyProfileFragment() as Fragment
                else -> FamilyFragment() as Fragment
            }
        }
    }
}