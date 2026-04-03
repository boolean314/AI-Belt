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
import com.example.ai_belt_mobile.voice.SparkChainTTSManager
import org.json.JSONObject
import kotlin.text.append

class FamilyFragment : Fragment(R.layout.fragment_family) {

    private lateinit var topCard: MaterialCardView
    // 已删除 topAddressText，不再使用顶部 TextView
    private lateinit var warningText: TextView
    private lateinit var locationText: TextView
    private var boundDisabilityId: String? = null
    private lateinit var emergencyCard: MaterialCardView
    private var lastLocationTime: String? = null
    private var lastIsEmergency: Boolean = false

    // 高德地图相关
    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private var geocodeSearch: GeocodeSearch? = null

    // 记录最近一次定位消息的发送者，便于后续请求优先发给最近在线设备
    private var lastSenderDisabilityId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topCard = view.findViewById(R.id.scan_qr_card)
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

        // 顶部 TextView 删除后，这里把原本顶部的提示分成：
        // - warningText 做总体状态
        // - locationText 显示详细说明
        warningText.text = "暂无紧急情况"
        locationText.text = "点击卡片向残疾人端请求最新地址"

        emergencyCard.setOnClickListener {
            requestLocationFromDisability()
        }

        observeWsEvents()
        loadBoundDisability()
    }

    private fun initGeoCoder() {
        geocodeSearch = GeocodeSearch(requireContext())
        geocodeSearch?.setOnGeocodeSearchListener(object : OnGeocodeSearchListener {
            override fun onGeocodeSearched(result: GeocodeResult?, errorCode: Int) {
            }

            override fun onRegeocodeSearched(result: RegeocodeResult?, errorCode: Int) {
                if (result == null || errorCode != 1000) {
                    // 顶部 TextView 删除后，错误信息放到底部
                    locationText.text = "地址解析失败"
                    return
                }

                val addr = result.regeocodeAddress?.formatAddress.orEmpty()
                val building = result.regeocodeAddress?.building.orEmpty()

                val fullAddress = buildString {
                    append(addr)
                    if (building.isNotBlank()) {
                        append("（").append(building).append("）")
                    }
                }
                val timePart = lastLocationTime?.takeIf { it.isNotBlank() } ?: ""

                // 将解析出来的地址展示在下部的 card 中
                locationText.text = buildString {
                    if (lastIsEmergency) {
                        append("紧急求助！\n")
                    }
                    if (fullAddress.isNotBlank()) {
                        append("地址：").append(fullAddress).append("\n")
                    }
                    if (timePart.isNotBlank()) {
                        append("时间：").append(timePart)
                    }
                }
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
            // 原先写到 topAddressText 的提示，现在也写到底部
            locationText.text = "未登录，无法发送请求"
            return
        }

        val familyId = session.id.toString()
        val targetId = boundDisabilityId ?: lastSenderDisabilityId
        if (targetId.isNullOrBlank()) {
            locationText.text = "未获取到绑定残疾人ID，请稍后重试"
            return
        }
        val ok = WebSocketManager.sendRequest(fromId = familyId, toId = targetId)

        if (ok) {
            locationText.text = "已发送定位请求，等待残疾人端返回..."
        } else {
            locationText.text = "请求发送失败：WebSocket未连接"
            toast("请求发送失败：WebSocket未连接")
        }
    }

    private fun observeWsEvents() {
        if (WebSocketManager.isConnected.value) {
            warningText.text = "WebSocket已连接"
            locationText.text = "点击上方卡片请求定位"
        } else {
            warningText.text = "连接中/已断开"
            locationText.text = "稍后重试"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WebSocketManager.events.collect { event ->
                    when (event) {
                        is WsEvent.Opened -> {
                            warningText.text = "WebSocket已连接"
                            locationText.text = "点击上方卡片请求定位"
                        }

                        is WsEvent.Message -> handleMessage(event.text)

                        is WsEvent.Error -> {
                            warningText.text = "连接异常"
                            locationText.text = "连接异常，请稍后重试"
                        }

                        is WsEvent.Closed -> {
                            warningText.text = "连接已断开"
                            locationText.text = "连接已断开，请稍后重试"
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
                        // 成功提示显示到底部
                        locationText.text = msg
                    }
                }

                "error" -> {
                    val msg = root.optJSONObject("data")?.optString("message").orEmpty()
                    locationText.text = if (msg.isBlank()) "请求失败" else msg
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
                    val timeRaw = data?.optString("time").orEmpty()
                    val address = data?.optString("address").orEmpty()

                    val timeFormatted = formatTimeMillis(timeRaw)

                    lastLocationTime = timeFormatted
                    lastIsEmergency = false

                    // 原先 topAddressText = "已收到最新定位"，
                    // 现在 warningText 已经写了“已收到最新定位”，
                    // 下面 locationText 负责显示地址+时间
                    locationText.text = buildString {
                        if (address.isNotBlank()) {
                            append("地址：").append(address).append("\n")
                        } else {
                            append("地址：暂无（解析中...）\n")
                        }
                        if (timeFormatted.isNotBlank()) {
                            append("时间：").append(timeFormatted)
                        }
                    }

                    val lat = latStr.toDoubleOrNull()
                    val lng = lngStr.toDoubleOrNull()
                    if (lat != null && lng != null) {
                        updateMapLocation(lat, lng)
                    } else {
                        locationText.text = "定位数据无效"
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
                    val timeRaw = data?.optString("time").orEmpty()
                    val address = data?.optString("address").orEmpty()

                    val timeFormatted = formatTimeMillis(timeRaw)

                    lastLocationTime = timeFormatted
                    lastIsEmergency = true

                    locationText.text = buildString {
                        append("紧急求助！\n")
                        if (address.isNotBlank()) {
                            append("地址：").append(address).append("\n")
                        } else {
                            append("地址：暂无（解析中...）\n")
                        }
                        if (timeFormatted.isNotBlank()) {
                            append("时间：").append(timeFormatted)
                        }
                    }

                    val lat = latStr.toDoubleOrNull()
                    val lng = lngStr.toDoubleOrNull()
                    if (lat != null && lng != null) {
                        updateMapLocation(lat, lng)
                    } else {
                        locationText.text = "紧急定位数据无效"
                    }
                }

                "pong" -> {
                    // 心跳不做 UI 提示
                }
            }
        } catch (e: Exception) {
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
                warningText.text = "未登录"
                locationText.text = "未登录，无法获取绑定信息"
                return@launch
            }
            android.util.Log.d("FamilyFragment", "session.id=${session.id}, identity=${session.identity}")

            val resp = UserRetrofitClient.instance.getDisabilityInfo(session.id)
            android.util.Log.d("FamilyFragment", "resp.code=${resp.code}, msg=${resp.message}, data=${resp.data}")

            try {
                val target = resp.data.firstOrNull()
                if (resp.code == 200 && target != null) {
                    boundDisabilityId = target.id.toString()
                    warningText.text = "点击获取定位"
                    locationText.text = "已绑定残疾人：${target.name}（点击上方卡片请求定位）"
                } else {
                    boundDisabilityId = null
                    warningText.text = "暂无绑定残疾人"
                    locationText.text = if (resp.message.isBlank()) "暂无绑定残疾人" else resp.message
                }
            } catch (e: Exception) {
                boundDisabilityId = null
                warningText.text = "获取绑定信息失败"
                locationText.text = "获取绑定残疾人失败，请重试"
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

    private fun formatTimeMillis(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val millis = raw.toLongOrNull() ?: return raw

        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.format(java.util.Date(millis))
        } catch (e: Exception) {
            raw
        }
    }
}