package com.example.ai_belt_mobile

import android.app.Application
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig

class AI_Belt_Application : Application() {
    //全局初始化SparkChain
    override fun onCreate() {
        super.onCreate()
        // 初始化 SparkChain
        val config = SparkChainConfig.builder()
            .appID("7e61bc79") // 替换为你的 appId
            .apiKey("b6b160d7fec7fdda0cccaa1e7b7ce6c2") // 替换为你的 apiKey
            .apiSecret("NmVmOTcwYjYxY2VjNTBiYmIxOTQ1NWJl") // 替换为你的 apiSecret

        val ret = SparkChain.getInst().init(applicationContext, config)
        if (ret == 0) {
            println("SparkChain init success")
        } else {
            println("SparkChain init failed, error code: $ret")
        }
    }
}