package com.example.ai_belt_mobile

import android.app.Application
import android.util.Log
import com.amap.api.navi.NaviSetting
import com.amap.apis.utils.core.api.AMapUtilCoreApi
import com.example.ai_belt_mobile.network.WebSocketManager
import com.example.ai_belt_mobile.network.WsEvent
import com.example.ai_belt_mobile.voice.SparkChainTTSManager
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class AI_Belt_Application : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    //全局初始化SparkChain
    override fun onCreate() {
        super.onCreate()
        
        // --- 高德地图隐私合规设置 (必须在所有高德SDK初始化前调用) ---
        // 1. 设置包含隐私政策，并展示用户授权弹窗
        NaviSetting.updatePrivacyShow(this, true, true)
        // 2. 设置是否同意用户授权政策
        NaviSetting.updatePrivacyAgree(this, true)
        // 3. 基础库设置允许采集个人及设备信息
        AMapUtilCoreApi.setCollectInfoEnable(true)
        // -------------------------------------------------------------
        
        // 初始化 SparkChain
        // 将耗时的SparkChain初始化移到后台协程，避免阻塞主线程，提高冷启动速度
        appScope.launch {
            Log.d("AI_Belt_Application", "SparkChain 和 TTS 初始化开始 (后台)")
            val config = SparkChainConfig.builder()
                .appID("7e61bc79")
                .apiKey("b6b160d7fec7fdda0cccaa1e7b7ce6c2")
                .apiSecret("NmVmOTcwYjYxY2VjNTBiYmIxOTQ1NWJl")

            val ret = SparkChain.getInst().init(applicationContext, config)
            if (ret == 0) {
                println("SparkChain init success")
            } else {
                println("SparkChain init failed, error code: $ret")
            }

            SparkChainTTSManager.getInstance().init(applicationContext)
            Log.d("AI_Belt_Application", "SparkChain 和 TTS 初始化完成 (后台)")

            WebSocketManager.events.collect { event ->
                if (event !is WsEvent.Message) return@collect

                runCatching {
                    val root = JSONObject(event.text)
                    if (root.optString("type") == "ai_message") {
                        val msg = root.optJSONObject("data")?.optString("Message").orEmpty()
                        if (msg.isNotBlank()) {
                            SparkChainTTSManager.getInstance().speak(msg)
                        }
                    }
                }
            }
        }
    }

}