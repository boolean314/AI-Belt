package com.example.ai_belt_mobile.network

import com.example.ai_belt_mobile.data.model.ResultResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserService {

    @POST("auth/login")     //用于登录
    suspend fun login() : ResultResponse

    @POST("auth/register")      //用于注册
    suspend fun register() : ResultResponse

    @POST("auth/send-code")     //用于获取验证码
    suspend fun sendCode() : ResultResponse

    @POST("auth/change-password")       //用于更改密码
    suspend fun changePassword() : ResultResponse

    @GET("disability/{id}/family")      //用于残疾人获取家属列表
    suspend fun getFamilyInfo(@Path("id") id: Int) : ResultResponse

    @POST("disability/bind")    //用于家属绑定残疾人
    suspend fun bindFamily() : ResultResponse

    @POST("disability/emergency")   //用于残疾人设定紧急联系人
    suspend fun setEmergencyContact() : ResultResponse

    @PUT("users/profile")   //用于更新profile里面的name和phone
    suspend fun updateProfile() : ResultResponse

}