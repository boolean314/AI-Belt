package com.example.ai_belt_mobile.navigation

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Message
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.walknavi.WalkNavigateHelper
import com.baidu.mapapi.walknavi.adapter.IWRouteGuidanceListener
import com.baidu.mapapi.walknavi.model.IWRouteIconInfo
import com.baidu.mapapi.walknavi.model.RouteGuideKind
import com.baidu.mapapi.walknavi.model.WalkSimpleMapInfo
import com.example.ai_belt_mobile.voice.BaiduTTSManager

/**
 * 步行导航Activity
 * 用于显示导航界面和处理导航状态
 */
class WalkNaviActivity : AppCompatActivity() {

    private lateinit var walkNavigateHelper: WalkNavigateHelper
    private val ttsManager = BaiduTTSManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 获取步行导航助手实例
        walkNavigateHelper = WalkNavigateHelper.getInstance()

        // 2. 创建导航视图并设置为内容视图
        val view = walkNavigateHelper.onCreate(this)
        setContentView(view)

        // 3. 设置导航状态监听器
        setRouteGuidanceListener()

        // 4. 开始步行导航
        startWalkNavi()
    }

    /**
     * 设置导航状态监听器
     */
    private fun setRouteGuidanceListener() {
        walkNavigateHelper.setRouteGuidanceListener(
            this,
            object : IWRouteGuidanceListener {

                override fun onRoadGuideTextUpdate(
                    p0: CharSequence?,
                    p1: CharSequence?
                ) {
                    val text = p1?.toString() ?: p0?.toString()
                    text?.let {
                        Log.i("导航", "导航指令: $it")
                        ttsManager.speak(it)
                    }
                }

                // ✅ CharSequence版本
                override fun onRemainDistanceUpdate(p0: CharSequence?) {
                    Log.i("导航", "剩余距离: $p0")
                }

                // ✅ Int版本（必须写）
                override fun onRemainDistanceUpdate(p0: Int) {
                    Log.i("导航", "剩余距离(int): $p0")
                }

                // ✅ CharSequence版本
                override fun onRemainTimeUpdate(p0: CharSequence?) {
                    Log.i("导航", "剩余时间: $p0")
                }

                // ✅ Int版本（必须写）
                override fun onRemainTimeUpdate(p0: Int) {
                    Log.i("导航", "剩余时间(int): $p0")
                }

                override fun onArriveDest() {
                    Log.i("导航", "已到达目的地")
                    ttsManager.speak("已到达目的地")
                }

                override fun onReRouteComplete() {
                    ttsManager.speak("路线已更新")
                }

                override fun onRouteGuideIconUpdate(p0: Drawable?) {}

                override fun onRouteGuideKind(p0: RouteGuideKind?) {}

                override fun onRouteGuideIconInfoUpdate(p0: IWRouteIconInfo?) {}

                override fun onGpsStatusChange(p0: CharSequence?, p1: Drawable?) {}

                override fun onRouteFarAway(p0: CharSequence?, p1: Drawable?) {}

                override fun onRoutePlanYawing(p0: CharSequence?, p1: Drawable?) {}

                override fun onIndoorEnd(p0: Message?) {}

                override fun onFinalEnd(p0: Message?) {}

                override fun onVibrate() {}

                override fun onNaviLocationUpdate() {}

                override fun onSimpleMapInfoUpdate(p0: WalkSimpleMapInfo?) {}
            }
        )
    }

    /**
     * 开始步行导航
     */
    private fun startWalkNavi() {
        try {
            walkNavigateHelper.startWalkNavi(this)
            Log.i("导航", "步行导航已开始")
        } catch (e: Exception) {
            Log.e("导航", "开始步行导航失败", e)
            ttsManager.speak("开始步行导航失败")
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复导航
        walkNavigateHelper.resume()
    }

    override fun onPause() {
        super.onPause()
        // 暂停导航
        walkNavigateHelper.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 退出导航
        walkNavigateHelper.quit()
    }
}