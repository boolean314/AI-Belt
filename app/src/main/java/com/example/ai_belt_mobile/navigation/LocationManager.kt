package com.example.ai_belt_mobile.navigation

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener

class LocationManager(private val context: Context) {

    private var locationClient: AMapLocationClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isLocating = false

    init {
        try {
            // 注意: 在调用 AMapLocationClient 之前，确保在 Application 中已调用隐私合规接口
            AMapLocationClient.updatePrivacyShow(context.applicationContext, true, true)
            AMapLocationClient.updatePrivacyAgree(context.applicationContext, true)

            locationClient = AMapLocationClient(context.applicationContext)
            
            // 配置定位参数
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy // 高精度模式
                isOnceLocation = true // 获取一次定位结果
                isOnceLocationLatest = true // 获取最近3s内精度最高的一次定位结果
                isNeedAddress = true // 设置是否返回地址信息（默认返回地址信息）
                httpTimeOut = 10000 // 超时时间
            }
            locationClient?.setLocationOption(option)
            
        } catch (e: Exception) {
            Log.e("LocationManager", "高德定位客户端初始化失败", e)
        }
    }

    fun getAccurateLocation(onResult: (Location?) -> Unit) {
        if (isLocating) {
            Log.w("LocationManager", "正在定位中，请勿重复请求")
            return
        }
        
        isLocating = true
        var locationCallback: AMapLocationListener? = null
        
        // 超时机制：10秒内没拿到定位则返回失败
        val timeoutRunnable = Runnable {
            if (isLocating) {
                Log.w("LocationManager", "高德定位超时")
                stop()
                locationCallback?.let { locationClient?.unRegisterLocationListener(it) }
                onResult(null)
            }
        }
        handler.postDelayed(timeoutRunnable, 10000)

        locationCallback = object : AMapLocationListener {
            override fun onLocationChanged(amapLocation: AMapLocation?) {
                if (amapLocation == null || amapLocation.errorCode != 0) {
                    Log.e("LocationManager", "高德定位失败: ${amapLocation?.errorCode}, 错误信息: ${amapLocation?.errorInfo}")
                    // 继续等待或直接失败？因为设置了单次定位，这里直接返回null
                    handler.removeCallbacks(timeoutRunnable)
                    stop()
                    locationClient?.unRegisterLocationListener(this)
                    onResult(null)
                    return
                }

                // 定位成功
                Log.i("LocationManager", "高德定位成功: 精度=${amapLocation.accuracy}m, 纬度=${amapLocation.latitude}, 经度=${amapLocation.longitude}, 地址=${amapLocation.address}")
                
                // 将高德输出的 GCJ-02 经纬度塞进原生 Location 里传出去
                val androidLocation = Location(android.location.LocationManager.GPS_PROVIDER).apply {
                    latitude = amapLocation.latitude
                    longitude = amapLocation.longitude
                    accuracy = amapLocation.accuracy
                    speed = amapLocation.speed
                    bearing = amapLocation.bearing
                    time = System.currentTimeMillis()
                }

                // 拿到位置后立刻停止定位，清除超时
                handler.removeCallbacks(timeoutRunnable)
                stop()
                locationClient?.unRegisterLocationListener(this)
                onResult(androidLocation)
            }
        }

        locationClient?.setLocationListener(locationCallback)
        locationClient?.startLocation()
        Log.i("LocationManager", "已启动高德定位")
    }

    fun stop() {
        isLocating = false
        try {
            locationClient?.stopLocation()
            Log.i("LocationManager", "已停止高德定位")
        } catch (e: Exception) {
            Log.e("LocationManager", "停止高德定位失败", e)
        }
        handler.removeCallbacksAndMessages(null)
    }
}