package com.example.ai_belt_mobile.navigation

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log


class LocationManager(private val context: Context) {
    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private var currentLocation: Location? = null

    // 移除了requestLocationPermission方法，权限请求由Fragment处理


    fun getCurrentLocation(onLocationReceived: (Location?) -> Unit) {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLocation = location
                onLocationReceived(location)
                locationManager.removeUpdates(this)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // 尝试获取最后已知位置
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (lastLocation != null) {
                currentLocation = lastLocation
                onLocationReceived(lastLocation)
            } else {
                // 注册位置监听器
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    locationListener!!
                )
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    locationListener!!
                )
            }
        } catch (e: SecurityException) {
            Log.e("LocationManager", "权限异常: ${e.message}")
            onLocationReceived(null)
        }
    }

    fun stopLocationUpdates() {
        locationListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (e: SecurityException) {
                Log.e("LocationManager", "权限异常: ${e.message}")
            }
        }
    }
}
