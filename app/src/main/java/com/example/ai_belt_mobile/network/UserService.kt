package com.example.ai_belt_mobile.network

import com.example.ai_belt_mobile.data.model.BindDisability
import com.example.ai_belt_mobile.data.model.BindDisabilityResponse
import com.example.ai_belt_mobile.data.model.ChangePassword
import com.example.ai_belt_mobile.data.model.ChangePasswordResponse
import com.example.ai_belt_mobile.data.model.ForgetPassword
import com.example.ai_belt_mobile.data.model.ForgetPasswordResponse
import com.example.ai_belt_mobile.data.model.GetDisabilityResponse
import com.example.ai_belt_mobile.data.model.GetFamily
import com.example.ai_belt_mobile.data.model.GetFamilyResponse
import com.example.ai_belt_mobile.data.model.GetVerifyCode
import com.example.ai_belt_mobile.data.model.GetVerifyCodeResponse
import com.example.ai_belt_mobile.data.model.LoginResponse
import com.example.ai_belt_mobile.data.model.RegisterResponse
import com.example.ai_belt_mobile.data.model.SetEmergency
import com.example.ai_belt_mobile.data.model.SetEmergencyResponse
import com.example.ai_belt_mobile.data.model.UpdateProfile
import com.example.ai_belt_mobile.data.model.UpdateProfileResponse
import com.example.ai_belt_mobile.data.model.UserLogin
import com.example.ai_belt_mobile.data.model.UserRegister
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserService {

    @POST("auth/login")     //用于登录
    suspend fun login(
        @Body body: UserLogin
    ) : LoginResponse

    @POST("auth/register")      //用于注册
    suspend fun register(
        @Body body: UserRegister
    ) : RegisterResponse

    @POST("auth/send-code")     //用于获取验证码
    suspend fun sendCode(
        @Body body: GetVerifyCode
    ) : GetVerifyCodeResponse

    @POST("auth/change-password")       //用于更改密码
    suspend fun changePassword(
        @Body body: ChangePassword
    ) : ChangePasswordResponse

    @POST("auth/forget-password")     //用于忘记密码
    suspend fun forgetPassword(
        @Body body: ForgetPassword
    ) : ForgetPasswordResponse

    @GET("disability/{id}/family")      //用于残疾人获取家属列表
    suspend fun getFamilyInfo(
        @Path("id") id: Int,
    ) : GetFamilyResponse

    @POST("disability/bind")    //用于家属绑定残疾人
    suspend fun bindFamily(
        @Body body: BindDisability
    ) : BindDisabilityResponse

    @POST("disability/emergency")   //用于残疾人设定紧急联系人
    suspend fun setEmergencyContact(
        @Body body: SetEmergency
    ) : SetEmergencyResponse

    @PUT("users/profile")   //用于更新profile里面的name和phone
    suspend fun updateProfile(
        @Body body: UpdateProfile
    ) : UpdateProfileResponse

    @GET("family/{id}/disability")      //用于家属获取绑定的残疾人信息
    suspend fun getDisabilityInfo(
        @Path("id") id: Int,
    ) : GetDisabilityResponse
}