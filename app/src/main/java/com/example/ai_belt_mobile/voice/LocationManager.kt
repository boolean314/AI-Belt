package com.example.ai_belt_mobile.voice

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists

class LocationManager(private val context: Context) {
    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private var currentLocation: Location? = null

    fun requestLocationPermission(callback: (Boolean) -> Unit) {
        XXPermissions.with(context)
            .permission(PermissionLists.getAccessFineLocationPermission())
            .permission(PermissionLists.getAccessCoarseLocationPermission())
            .permission(PermissionLists.getAccessBackgroundLocationPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: MutableList<com.hjq.permissions.permission.base.IPermission>,
                    deniedList: MutableList<com.hjq.permissions.permission.base.IPermission>
                ) {
                    callback(deniedList.isEmpty())
                }
            })
    }

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