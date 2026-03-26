package com.example.ai_belt_mobile.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewListener
import com.amap.api.navi.AMapNaviViewOptions
import com.amap.api.navi.AmapPageType
import com.amap.api.navi.enums.NaviType
import com.example.ai_belt_mobile.voice.SparkChainTTSManager

/**
 * 步行导航Activity (高德地图版)
 */
class WalkNaviActivity : AppCompatActivity(), NavigationManager.BeltNavigationCallback {

    private lateinit var navigationManager: NavigationManager
    private lateinit var mAMapNaviView: AMapNaviView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查定位权限
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("WalkNaviActivity", "定位权限未授予")
            Toast.makeText(this, "需要定位权限进行导航", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // 创建高德导航视图并设置为ContentView
        mAMapNaviView = AMapNaviView(this)
        mAMapNaviView.onCreate(savedInstanceState)
        setContentView(mAMapNaviView)
        mAMapNaviView.viewOptions = AMapNaviViewOptions().apply {
            isRouteListButtonShow = false
            isSettingMenuEnabled = false
            isAutoDrawRoute = true

        }
        mAMapNaviView.setAMapNaviViewListener(object : AMapNaviViewListener {
            override fun onNaviSetting() {
                TODO("Not yet implemented")
            }

            override fun onNaviCancel() {
                finish()

            }

            override fun onNaviBackClick(): Boolean {
                TODO("Not yet implemented")
            }

            override fun onNaviMapMode(p0: Int) {
                TODO("Not yet implemented")
            }

            override fun onNaviTurnClick() {
                TODO("Not yet implemented")
            }

            override fun onNextRoadClick() {
                TODO("Not yet implemented")
            }

            override fun onScanViewButtonClick() {
                TODO("Not yet implemented")
            }

            override fun onLockMap(p0: Boolean) {
                TODO("Not yet implemented")
            }

            override fun onNaviViewLoaded() {
                TODO("Not yet implemented")
            }

            override fun onMapTypeChanged(p0: Int) {
                TODO("Not yet implemented")
            }

            override fun onNaviViewShowMode(p0: Int) {
                TODO("Not yet implemented")
            }

            override fun onStopSpeaking() {
                TODO("Not yet implemented")
            }

            override fun onViewTypeChanged(p0: AmapPageType?) {
                TODO("Not yet implemented")
            }

            override fun onAMapNaviViewExit() {
                TODO("Not yet implemented")
            }

            override fun onStrategyChanged(p0: Int) {
                TODO("Not yet implemented")
            }

            override fun onBroadcastModeChanged(p0: Int) {
                TODO("Not yet implemented")
            }

            override fun onDayAndNightModeChanged(p0: Int) {
                TODO("Not yet implemented")
            }

            override fun onScaleAutoChanged(p0: Boolean) {
                TODO("Not yet implemented")
            }

            override fun onListenToVoiceDuringCallChanged(p0: Boolean) {
                TODO("Not yet implemented")
            }

            override fun onControlMusicVolumeModeChanged(p0: Int) {
                TODO("Not yet implemented")
            }

            override fun onEagleChanged(p0: Boolean) {
                TODO("Not yet implemented")
            }

            override fun onNaviRouteHighlightChange(p0: Long, p1: Int) {
                TODO("Not yet implemented")
            }

        })

        // 初始化导航管理器
        navigationManager = NavigationManager.getInstance(this)
        navigationManager.init()
        navigationManager.beltCallback = this
    }

    // --- BeltNavigationCallback 实现 ---
    override fun onBeltNavigationUpdate(relativeAngle: Float, distance: Int) {
        Log.i("WalkNaviActivity", "=== 收到腰带导航数据 ===")
        Log.i("WalkNaviActivity", "相对偏角: $relativeAngle, 距离: $distance 米")
        if(relativeAngle>30.00||relativeAngle<-30.00){
            // 将 relativeAngle发送给智能腰带 (通过蓝牙)
            val homeViewModel = androidx.lifecycle.ViewModelProvider(this)[com.example.ai_belt_mobile.ui.home.HomeViewModel::class.java]
            val angleString = "ANGLE:$relativeAngle"
            val sent = homeViewModel.sendOnCommand(angleString)
            Log.i("WalkNaviActivity", "发送偏角数据: $angleString, 发送结果: $sent")
        }
    }

    override fun onNaviTextUpdate(text: String) {
        // 导航管理器已经调用了TTS播报
    }

    override fun onNaviStart() {
    }

    override fun onNaviStop() {
        Log.i("WalkNaviActivity", "导航已结束")
        finish()
    }

    // --- 生命周期管理 ---
    override fun onResume() {
        super.onResume()
        mAMapNaviView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mAMapNaviView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mAMapNaviView.onDestroy()
        navigationManager.beltCallback = null
        navigationManager.release()
        Log.i("WalkNaviActivity", "导航页面已销毁，导航已停止")
    }
}