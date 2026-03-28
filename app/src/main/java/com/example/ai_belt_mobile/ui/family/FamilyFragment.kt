package com.example.ai_belt_mobile.ui.family

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ai_belt_mobile.R
import com.example.ai_belt_mobile.data.local.UserSessionStore
import com.example.ai_belt_mobile.network.UserRetrofitClient
import com.example.ai_belt_mobile.network.WebSocketManager
import com.example.ai_belt_mobile.network.WsEvent
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener
import com.amap.api.services.geocoder.GeocodeResult
import org.json.JSONObject

class FamilyFragment : Fragment(R.layout.fragment_family) {

    private lateinit var topCard: MaterialCardView
    private lateinit var topAddressText: TextView
    private lateinit var warningText: TextView
    private lateinit var locationText: TextView
    private var boundDisabilityId: String? = null
    private lateinit var emergencyCard: MaterialCardView
    
    // 高德地图相关
    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private var geocodeSearch: GeocodeSearch? = null

    // 记录最近一次定位消息的发送者，便于后续请求优先发给最近在线设备
    private var lastSenderDisabilityId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topCard = view.findViewById(R.id.scan_qr_card)
        topAddressText = view.findViewById(R.id.top_address_text)
        warningText = view.findViewById(R.id.warning_text)
        locationText = view.findViewById(R.id.location_text)
        emergencyCard = view.findViewById(R.id.warning_card)
        mapView = view.findViewById(R.id.mapView)
        
        // 初始化地图
        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        aMap.mapType = AMap.MAP_TYPE_NORMAL
        
        // 初始化地理编码器
        initGeoCoder()

        updateEmergencyStyle(isEmergency = false)

        warningText.text = "暂无紧急情况"
        locationText.text = "暂未收到定位"
        topAddressText.text = "点击向残疾人端请求最新地址"

        topAddressText.setOnClickListener {
            requestLocationFromDisability()
        }

        observeWsEvents()
        loadBoundDisability()
    }
    
    private fun initGeoCoder() {
        geocodeSearch = GeocodeSearch(requireContext())
        geocodeSearch?.setOnGeocodeSearchListener(object : OnGeocodeSearchListener {
            override fun onGeocodeSearched(result: GeocodeResult?, errorCode: Int) {
                // 正向地理编码回调，不需要处理
            }

            override fun onRegeocodeSearched(result: RegeocodeResult?, errorCode: Int) {
                if (result == null || errorCode != 1000) {
                    topAddressText.text = "地址解析失败"
                    return
                }
                
                // 解析成功，显示详细地址
                topAddressText.text = result.regeocodeAddress.formatAddress + "\n(" + result.regeocodeAddress.building + ")"
            }
        })
    }
    
    private fun updateMapLocation(lat: Double, lng: Double) {
        val latLng = LatLng(lat, lng)
        val latLonPoint = LatLonPoint(lat, lng)
        
        // 清除旧的标记
        aMap.clear()
        
        // 添加新的标记点
        val markerOptions = MarkerOptions()
            .position(latLng)
        aMap.addMarker(markerOptions)
        
        // 移动地图视角到该点，并缩放
        aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(latLng, 18f))
        
        // 发起逆地理编码请求获取中文地址
        val query = RegeocodeQuery(latLonPoint, 200f, GeocodeSearch.AMAP)
        geocodeSearch?.getFromLocationAsyn(query)
    }

    private fun requestLocationFromDisability() {
        val session = UserSessionStore.get(requireContext())
        if (session == null) {
            toast("未登录，无法发送请求")
            return
        }

        val familyId = session.id.toString()
        val targetId = boundDisabilityId ?: lastSenderDisabilityId
        if (targetId.isNullOrBlank()) {
            topAddressText.text = "未获取到绑定残疾人ID，请稍后重试"
            return
        }
        val ok = WebSocketManager.sendRequest(fromId = familyId, toId = targetId)

        if (ok) {
            topAddressText.text = "已发送定位请求，等待残疾人端返回..."
        } else {
            topAddressText.text = "请求发送失败：WebSocket未连接"
            toast("请求发送失败：WebSocket未连接")
        }
    }

    private fun observeWsEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WebSocketManager.events.collect { event ->
                    when (event) {
                        is WsEvent.Opened -> {
                            topAddressText.text = "WebSocket已连接，点击上方请求定位"
                        }

                        is WsEvent.Message -> handleMessage(event.text)

                        is WsEvent.Error -> {
                            topAddressText.text = "连接异常，请稍后重试"
                        }

                        is WsEvent.Closed -> {
                            topAddressText.text = "连接已断开"
                        }
                    }
                }
            }
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val root = JSONObject(raw)
            when (root.optString("type")) {
                "success" -> {
                    val msg = root.optJSONObject("data")?.optString("message").orEmpty()
                    if (msg.isNotBlank()) {
                        topAddressText.text = msg
                    }
                }

                "error" -> {
                    val msg = root.optJSONObject("data")?.optString("message").orEmpty()
                    topAddressText.text = if (msg.isBlank()) "请求失败" else msg
                }

                "location" -> {
                    updateEmergencyStyle(isEmergency = false)
                    warningText.text = "已收到最新定位"
                    val fromId = root.optString("fromId")
                    if (fromId.isNotBlank()) {
                        lastSenderDisabilityId = fromId
                    }

                    val data = root.optJSONObject("data")
                    val lngStr = data?.optString("longitude").orEmpty()
                    val latStr = data?.optString("latitude").orEmpty()
                    val time = data?.optString("time").orEmpty()
                    val address = data?.optString("address").orEmpty() // 可能为空

                    warningText.text = "已收到最新定位"
                    locationText.text = buildString {
                        append("经度: ").append(lngStr).append("\n")
                        append("纬度: ").append(latStr).append("\n")
                        append("时间: ").append(time)
                    }

                    val lat = latStr.toDoubleOrNull()
                    val lng = lngStr.toDoubleOrNull()

                    if (lat != null && lng != null) {
                        updateMapLocation(lat, lng)
                    } else {
                        topAddressText.text = "定位数据无效"
                    }
                }

                "SOS" -> {
                    updateEmergencyStyle(isEmergency = true)
                    warningText.text = "紧急情况！"
                    val fromId = root.optString("fromId")
                    if (fromId.isNotBlank()) {
                        lastSenderDisabilityId = fromId
                    }

                    val data = root.optJSONObject("data")
                    val lngStr = data?.optString("longitude").orEmpty()
                    val latStr = data?.optString("latitude").orEmpty()
                    val time = data?.optString("time").orEmpty()

                    warningText.text = "紧急情况！"
                    locationText.text = buildString {
                        append("SOS经度: ").append(lngStr).append("\n")
                        append("SOS纬度: ").append(latStr).append("\n")
                        append("时间: ").append(time)
                    }
                    
                    val lat = latStr.toDoubleOrNull()
                    val lng = lngStr.toDoubleOrNull()

                    if (lat != null && lng != null) {
                        updateMapLocation(lat, lng)
                    } else {
                        topAddressText.text = "紧急定位数据无效"
                    }
                }

                "pong" -> {
                    // 心跳响应，忽略即可
                }
            }
            } catch (e: Exception) {
                // JSON格式异常可按需打日志
                e.printStackTrace()
            }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun loadBoundDisability() {
        viewLifecycleOwner.lifecycleScope.launch {
            val session = UserSessionStore.get(requireContext())
            if (session == null) {
                topAddressText.text = "未登录，无法获取绑定信息"
                return@launch
            }
            android.util.Log.d("FamilyFragment", "session.id=${session?.id}, identity=${session?.identity}")

            val resp = UserRetrofitClient.instance.getDisabilityInfo(session!!.id)
            android.util.Log.d("FamilyFragment", "resp.code=${resp.code}, msg=${resp.message}, data=${resp.data}")

            try {
                val target = resp.data.firstOrNull() // List通常一个，这里直接取第一个
                if (resp.code == 200 && target != null) {
                    boundDisabilityId = target.id.toString()
                    topAddressText.text = "已绑定残疾人：${target.name}（点击请求定位）"
                } else {
                    boundDisabilityId = null
                    topAddressText.text = if (resp.message.isBlank()) "暂无绑定残疾人" else resp.message
                }
            } catch (e: Exception) {
                boundDisabilityId = null
                topAddressText.text = "获取绑定残疾人失败，请重试"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    private fun updateEmergencyStyle(isEmergency: Boolean) {
        val cardColor = if (isEmergency) {
            ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.white)
        }

        val textColor = if (isEmergency) {
            ContextCompat.getColor(requireContext(), android.R.color.white)
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.black)
        }

        emergencyCard.setCardBackgroundColor(cardColor)
        warningText.setTextColor(textColor)
    }
}