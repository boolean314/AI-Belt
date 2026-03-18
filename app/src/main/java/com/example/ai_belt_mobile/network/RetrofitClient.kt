package com.example.ai_belt_mobile.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Retrofit 客户端单例类
 * 用于全局调用网络请求
 */
object RetrofitClient {

    // 基础 URL，根据实际情况修改
    private const val BASE_URL = "http://your-api-base-url.com/"

    // 懒加载 Retrofit 实例
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 懒加载 AiService 实例
    val aiService by lazy {
        retrofit.create(AiService::class.java)
    }
}