package com.example.ai_belt_mobile.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端单例类
 * 用于全局调用网络请求
 */
object RetrofitClient {

    // 基础 URL，根据实际情况修改
    private const val BASE_URL = "http://192.168.1.229:8080/"

    // 超时时间设置
    private const val CONNECT_TIMEOUT = 30L // 连接超时时间，单位秒
    private const val READ_TIMEOUT = 30L    // 读取超时时间，单位秒
    private const val WRITE_TIMEOUT = 30L   // 写入超时时间，单位秒

    // 配置 OkHttpClient
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    // 懒加载 Retrofit 实例
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 懒加载 AiService 实例
    val aiService by lazy {
        retrofit.create(AiService::class.java)
    }
}