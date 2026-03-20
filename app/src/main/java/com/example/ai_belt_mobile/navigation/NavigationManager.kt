package com.example.ai_belt_mobile.navigation

import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeOption
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.walknavi.WalkNavigateHelper
import com.baidu.mapapi.walknavi.adapter.IWEngineInitListener
import com.baidu.mapapi.walknavi.adapter.IWRoutePlanListener
import com.baidu.mapapi.walknavi.model.WalkRoutePlanError
import com.baidu.mapapi.walknavi.params.WalkNaviLaunchParam
import com.baidu.mapapi.walknavi.params.WalkRouteNodeInfo
import com.example.ai_belt_mobile.voice.BaiduTTSManager
import kotlin.jvm.java

/**
 * 导航管理器
 * 使用百度地图SDK实现步行导航功能
 */
class NavigationManager(private val context: Context) {
    private val ttsManager = BaiduTTSManager.getInstance()
    private var geoCoder: GeoCoder? = null
    private var isNavigating = false
    private var walkNavigateHelper: WalkNavigateHelper? = null

    fun init() {
        // 初始化百度地图SDK的相关组件
        geoCoder = GeoCoder.newInstance()

        // 初始化步行导航引擎
        initWalkNaviEngine()
    }

    /**
     * 初始化步行导航引擎
     */
    private fun initWalkNaviEngine() {
        try {
            // 获取步行导航助手实例
            walkNavigateHelper = WalkNavigateHelper.getInstance()

            // 初始化导航引擎
            walkNavigateHelper?.initNaviEngine(context, object : IWEngineInitListener {
                override fun engineInitSuccess() {
                    Log.i("导航", "初始化成功")
                }

                override fun engineInitFail() {
                    Log.e("导航", "初始化失败")
                }
            })
        } catch (e: Exception) {
            Log.e("导航", "初始化步行导航引擎失败", e)
        }
    }



    fun startNavigation(
        startLocation: Location,
        destination: String,
        onNavigationStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        startWalkingNavigation(startLocation, destination, onNavigationStarted, onError)
    }

    /**
     * 启动驾车导航
     */
    fun startDrivingNavigation(
        startLocation: Location,
        destination: String,
        onNavigationStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        // 暂时使用步行导航实现
        startWalkingNavigation(startLocation, destination, onNavigationStarted, onError)
    }

    /**
     * 启动步行导航
     */
    fun startWalkingNavigation(
        startLocation: Location,
        destination: String,
        onNavigationStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        // 在后台线程中执行导航启动，避免阻塞主线程
        Thread {
            try {
                isNavigating = true

                // 1. 地址解析（地理编码）
                Log.i("导航", "正在解析目的地: $destination")
                ttsManager.speak("正在解析目的地")

                // 使用百度地图SDK进行地理编码
                geoCoder?.apply {
                    setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                        override fun onGetGeoCodeResult(result: GeoCodeResult) {
                            if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                                // 地理编码失败
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onError("无法解析目的地地址")
                                    Log.e("导航", "地理编码失败")
                                }
                                return
                            }

                            // 地理编码成功，获取目的地坐标
                            val destinationLatLng = result.location
                            Log.i("导航", "目的地坐标: ${destinationLatLng.latitude}, ${destinationLatLng.longitude}")

                            val startNode = WalkRouteNodeInfo().apply {
                                location = LatLng(startLocation.latitude, startLocation.longitude)
                            }

                            val endNode = WalkRouteNodeInfo().apply {
                                location = destinationLatLng
                            }

                            val param = WalkNaviLaunchParam()
                                .startNodeInfo(startNode)
                                .endNodeInfo(endNode)
                            // 4. 发起算路
                            walkNavigateHelper?.routePlanWithRouteNode(param, object :
                                IWRoutePlanListener {
                                override fun onRoutePlanStart() {
                                    Log.i("导航", "开始算路")
                                }
                                override fun onRoutePlanSuccess() {
                                    Log.i("导航", "步行路线规划成功")
                                    ttsManager.speak("步行路线规划成功，开始导航")

                                    // 5. 跳转导航Activity
                                    val intent = Intent(context, WalkNaviActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)

                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        onNavigationStarted()
                                    }
                                }

                                override fun onRoutePlanFail(errorInfo: WalkRoutePlanError) {
                                    Log.e("导航", "步行路线规划失败: $errorInfo")
                                    ttsManager.speak("步行路线规划失败")
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        onError("路线规划失败: $errorInfo")
                                    }
                                }

                            })
                        }

                        override fun onGetReverseGeoCodeResult(p0: com.baidu.mapapi.search.geocode.ReverseGeoCodeResult) {
                            // 不需要实现
                        }
                    })

                    // 发起地理编码请求
                    geocode(GeoCodeOption().city("").address(destination))
                }

            } catch (e: Exception) {
                // 错误回调，在主线程执行
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError("导航启动失败: ${e.message}")
                    Log.e("导航", "导航启动失败", e)
                }
            }
        }.start()
    }



    fun stopNavigation() {
        try {
            walkNavigateHelper?.quit()
            isNavigating = false
            Log.i("导航", "导航已停止")
            ttsManager.speak("导航已停止")
        } catch (e: Exception) {
            Log.e("导航", "停止导航失败", e)
        }
    }

    fun release() {
        try {
            // 释放百度地图SDK资源
            geoCoder?.destroy()

            // 释放步行导航资源
            walkNavigateHelper?.quit()

            stopNavigation()
        } catch (e: Exception) {
            Log.e("导航", "释放资源失败", e)
        }
    }

    /**
     * 获取步行导航助手实例
     */
    fun getWalkNavigateHelper(): WalkNavigateHelper? {
        return walkNavigateHelper
    }
}