package com.example.ai_belt_mobile.navigation

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption

class LocationManager(private val context: Context) {

    private var locationClient: LocationClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isLocating = false

    init {
        try {
            LocationClient.setAgreePrivacy(true)
            locationClient = LocationClient(context.applicationContext)
            
            // 配置定位参数
            val option = LocationClientOption().apply {
                locationMode = LocationClientOption.LocationMode.Hight_Accuracy // 高精度模式，结合GPS和网络
                setCoorType("bd09ll") // 极其重要：直接请求百度经纬度坐标，免去后续转换
                scanSpan = 1000 // 1秒定位一次
                isOpenGps = true // 强制打开GPS
                isLocationNotify = true // 可选，设置是否当GPS有效时按照1S/1次频率输出GPS结果
                setIgnoreKillProcess(false) // 定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程
            }
            locationClient?.locOption = option
            
        } catch (e: Exception) {
            Log.e("LocationManager", "百度定位客户端初始化失败", e)
        }
    }

    fun getAccurateLocation(onResult: (Location?) -> Unit) {
        if (isLocating) {
            Log.w("LocationManager", "正在定位中，请勿重复请求")
            return
        }
        
        isLocating = true
        var locationCallback: BDAbstractLocationListener? = null
        
        // 超时机制：10秒内没拿到定位则返回失败
        val timeoutRunnable = Runnable {
            if (isLocating) {
                Log.w("LocationManager", "百度定位超时")
                stop()
                locationCallback?.let { locationClient?.unRegisterLocationListener(it) }
                onResult(null)
            }
        }
        handler.postDelayed(timeoutRunnable, 10000)

        locationCallback = object : BDAbstractLocationListener() {
            override fun onReceiveLocation(bdLocation: BDLocation?) {
                if (bdLocation == null || bdLocation.locType == BDLocation.TypeServerError) {
                    Log.e("LocationManager", "百度定位失败: ${bdLocation?.locType}")
                    return
                }

                // 判断是否是有效定位（GPS、网络定位、离线定位等都是成功的标志）
                val isSuccess = bdLocation.locType == BDLocation.TypeGpsLocation ||
                                bdLocation.locType == BDLocation.TypeNetWorkLocation ||
                                bdLocation.locType == BDLocation.TypeOffLineLocation

                if (isSuccess) {
                    Log.i("LocationManager", "百度定位成功: 精度=${bdLocation.radius}m, 纬度=${bdLocation.latitude}, 经度=${bdLocation.longitude}, 类型=${bdLocation.locType}")
                    
                    // 为了兼容你之前传出去的 Android 原生 Location 对象
                    // 我们把百度直接输出的 bd09ll 经纬度塞进原生 Location 里传出去
                    val androidLocation = Location(android.location.LocationManager.GPS_PROVIDER).apply {
                        latitude = bdLocation.latitude
                        longitude = bdLocation.longitude
                        accuracy = bdLocation.radius
                        speed = bdLocation.speed
                        bearing = bdLocation.direction
                        time = System.currentTimeMillis()
                    }

                    // 拿到位置后立刻停止定位，清除超时
                    handler.removeCallbacks(timeoutRunnable)
                    stop()
                    locationClient?.unRegisterLocationListener(this)
                    onResult(androidLocation)
                }
            }
        }

        locationClient?.registerLocationListener(locationCallback)
        locationClient?.start()
        Log.i("LocationManager", "已启动百度全量定位")
    }

    fun stop() {
        isLocating = false
        try {
            locationClient?.stop()
            Log.i("LocationManager", "已停止百度全量定位")
        } catch (e: Exception) {
            Log.e("LocationManager", "停止百度定位失败", e)
        }
        handler.removeCallbacksAndMessages(null)
    }
}