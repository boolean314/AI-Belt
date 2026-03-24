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
import com.baidu.mapapi.utils.CoordinateConverter
import com.baidu.mapapi.walknavi.WalkNavigateHelper
import com.baidu.mapapi.walknavi.adapter.IWEngineInitListener
import com.baidu.mapapi.walknavi.adapter.IWRoutePlanListener
import com.baidu.mapapi.walknavi.adapter.IWTTSPlayer
import com.baidu.mapapi.walknavi.model.WalkRoutePlanError
import com.baidu.mapapi.walknavi.params.WalkNaviLaunchParam
import com.baidu.mapapi.walknavi.params.WalkRouteNodeInfo
import com.example.ai_belt_mobile.voice.BaiduTTSManager
import kotlin.jvm.java

/**
 * 导航管理器
 * 使用百度地图SDK实现步行导航功能
 */
class NavigationManager private constructor(private val context: Context) {
    private val ttsManager = BaiduTTSManager.getInstance()
    private var geoCoder: GeoCoder? = null
    private var isNavigating = false
    private var walkNavigateHelper: WalkNavigateHelper? = null
    private var isEngineInitialized = false

    companion object {
        @Volatile
        private var instance: NavigationManager? = null

        fun getInstance(context: Context): NavigationManager {
            return instance ?: synchronized(this) {
                instance ?: NavigationManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

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

            // 设置底层 TTS 播报监听器，迫使引擎吐出导航文本
            walkNavigateHelper?.setTTsPlayer(object : IWTTSPlayer {
                override fun playTTSText(text: String?, b: Boolean): Int {
                    text?.let {
                        Log.i("NavigationManager_TTS", "底层引擎主动要求播报: $it")
                        // 我们直接用自己的讯飞/百度语音去读它
                        ttsManager.speak(it)
                    }
                    return 0 // 返回0表示播放成功，告诉引擎我们接管了
                }
            })

            // 初始化导航引擎
            walkNavigateHelper?.initNaviEngine(context, object : IWEngineInitListener {
                override fun engineInitSuccess() {
                    Log.i("NavigationManager", "初始化成功")
                    isEngineInitialized = true
                }

                override fun engineInitFail() {
                    Log.e("NavigationManager", "初始化失败")
                    isEngineInitialized = false
                }
            })
        } catch (e: Exception) {
            Log.e("NavigationManager", "初始化步行导航引擎失败", e)
            isEngineInitialized = false
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
                ttsManager.speak("正在解析目的地")
                // 检查是否正在导航
                if (isNavigating) {
                    Log.e("NavigationManager", "导航已在进行中")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onError("导航已在进行中")
                    }
                    return@Thread
                }
                
                isNavigating = true

                // 每次导航前都重新初始化导航引擎，确保使用新的目的地参数
                Log.i("NavigationManager", "重新初始化导航引擎，目的地: $destination")
                
                // 在主线程中停止之前的导航并初始化新的导航引擎
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    // 先停止之前的导航（如果有）
                    try {
                        walkNavigateHelper?.quit()
                        Log.i("NavigationManager", "已停止之前的导航")
                    } catch (e: Exception) {
                        Log.e("NavigationManager", "停止导航失败", e)
                    }
                    
                    // 重置状态
                    isEngineInitialized = false
                    walkNavigateHelper = null
                    Log.i("NavigationManager", "已重置导航状态")
                    
                    // 初始化新的导航引擎
                    initWalkNaviEngine()
                }
                
                // 等待引擎初始化完成
                Thread.sleep(2000) // 增加等待时间，确保引擎完全初始化
                
                if (!isEngineInitialized) {
                    Log.e("NavigationManager", "导航引擎初始化失败")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onError("导航引擎初始化失败")
                    }
                    isNavigating = false
                    return@Thread
                }

                // 1. 地址解析（地理编码）
                Log.i("NavigationManager", "正在解析目的地: $destination")


                // 重新创建GeoCoder实例，避免缓存问题
                geoCoder?.destroy()
                geoCoder = GeoCoder.newInstance()
                Log.i("NavigationManager", "已重新创建GeoCoder实例")

                // 使用百度地图SDK进行地理编码
                geoCoder?.apply {
                    setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                        override fun onGetGeoCodeResult(result: GeoCodeResult) {
                            if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                                // 地理编码失败
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onError("无法解析目的地地址")
                                    Log.e("NavigationManager", "地理编码失败")
                                }
                                isNavigating = false
                                return
                            }

                            // 地理编码成功，获取目的地坐标
                            val destinationLatLng = result.location
                            Log.i("NavigationManager", "目的地坐标: ${destinationLatLng.latitude}, ${destinationLatLng.longitude}")
                            // 添加详细的地理编码结果日志
                            Log.i("NavigationManager", "解析出的地址: ${result.address}")


                            // 因为 LocationManager 现在使用百度定位 SDK，返回的已经是 BD-09 坐标
                            // 所以不需要再做坐标转换了，直接使用即可
                            val startLatLng = LatLng(startLocation.latitude, startLocation.longitude)
                            
                            Log.i("NavigationManager", "起点坐标 (BD-09): ${startLatLng.latitude}, ${startLatLng.longitude}")

                            val startNode = WalkRouteNodeInfo().apply {
                                location = startLatLng
                            }

                            val endNode = WalkRouteNodeInfo().apply {
                                location = destinationLatLng
                            }

                            val param = WalkNaviLaunchParam()
                                .startNodeInfo(startNode)
                                .endNodeInfo(endNode)
                                
                            // 注意：百度步行导航不支持像驾车导航那样的纯代码模拟导航。
                            // 如果你在使用外部的“模拟定位软件”，必须确保软件不断地向系统注入 GPS 信号。
                            // 另外，百度步行导航需要真实的移动轨迹和一定的速度才会触发转弯回调，
                            // 模拟位置时如果跳跃过大或没有方向（Bearing）信息，引擎会认为定位漂移而不抛出指引。

                            // 4. 发起算路
                            Log.i("NavigationManager", "发起路线规划请求")
                            walkNavigateHelper?.routePlanWithRouteNode(param, object :
                                IWRoutePlanListener {
/**
 * 重写算路开始回调方法
 * 当导航开始进行路线规划时调用此方法
 */
                                override fun onRoutePlanStart() {
    // 输出日志信息，标记算路开始
                                    Log.i("NavigationManager", "开始算路")
                                }
                                override fun onRoutePlanSuccess() {
                                    Log.i("NavigationManager", "步行路线规划成功")
                                    ttsManager.speak("路线规划成功")

                                    // 5. 跳转导航Activity
                                    val intent = Intent(context, WalkNaviActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    // 传递目的地信息
                                    intent.putExtra("destination", destination)
                                    context.startActivity(intent)
                                    Log.i("NavigationManager", "已启动导航Activity，目的地: $destination")

                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        onNavigationStarted()
                                    }
                                }

                                override fun onRoutePlanFail(errorInfo: WalkRoutePlanError) {
                                    Log.e("NavigationManager", "步行路线规划失败: $errorInfo")
                                    ttsManager.speak("步行路线规划失败")
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        onError("路线规划失败: $errorInfo")
                                    }
                                    isNavigating = false
                                }

                            })
                        }

                        override fun onGetReverseGeoCodeResult(p0: com.baidu.mapapi.search.geocode.ReverseGeoCodeResult) {
                            // 不需要实现
                        }
                    })

                    // 发起地理编码请求
                    Log.i("NavigationManager", "发起地理编码请求，地址: $destination")
                    geocode(GeoCodeOption().city("").address(destination))
                }

            } catch (e: Exception) {
                // 错误回调，在主线程执行
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError("导航启动失败: ${e.message}")
                    Log.e("NavigationManager", "导航启动失败", e)
                }
                isNavigating = false
            }
        }.start()
    }



    fun stopNavigation() {
        try {
            walkNavigateHelper?.quit()
            isNavigating = false
            isEngineInitialized = false // 标记引擎已退出
            Log.i("NavigationManager", "导航已停止")
            ttsManager.speak("导航已停止")
        } catch (e: Exception) {
            Log.e("NavigationManager", "停止导航失败", e)
        }
    }

    fun release() {
        try {
            // 释放百度地图SDK资源
            geoCoder?.destroy()
            geoCoder = null

            // 释放步行导航资源
            walkNavigateHelper?.quit()
            walkNavigateHelper = null
            isEngineInitialized = false

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