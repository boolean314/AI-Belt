package com.example.ai_belt_mobile.data.local

import android.content.Context
import com.example.ai_belt_mobile.data.model.LoginData
import com.example.ai_belt_mobile.data.model.RegisterData

data class UserSession(
    val id: Int,
    val phone: String,
    val name: String,
    val mail: String,
    val identity: Int,
    val code: String?,
    val emergency: String?
)

object UserSessionStore {
    private const val PREF = "user_session_pref"
    private const val K_ID = "id"
    private const val K_PHONE = "phone"
    private const val K_NAME = "name"
    private const val K_MAIL = "mail"
    private const val K_IDENTITY = "identity"
    private const val K_CODE = "code"
    private const val K_EMERGENCY = "emergency"

    fun saveFromLogin(context: Context, data: LoginData) {
        save(
            context = context,
            id = data.id,
            phone = data.phone,
            name = data.name,
            mail = data.mail,
            identity = data.identity,
            code = data.code,
            emergency = data.emergency
        )
    }

    fun saveFromRegister(context: Context, data: RegisterData) {
        save(
            context = context,
            id = data.id,
            phone = data.phone,
            name = data.name,
            mail = data.mail,
            identity = data.identity,
            code = data.code,
            emergency = data.emergency
        )
    }

    fun save(
        context: Context,
        id: Int,
        phone: String,
        name: String,
        mail: String,
        identity: Int,
        code: String?,
        emergency: String?
    ) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(K_ID, id)
            .putString(K_PHONE, phone)
            .putString(K_NAME, name)
            .putString(K_MAIL, mail)
            .putInt(K_IDENTITY, identity)
            .putString(K_CODE, code)
            .putString(K_EMERGENCY, emergency)
            .apply()
    }

    fun get(context: Context): UserSession? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val id = sp.getInt(K_ID, -1)
        if (id < 0) return null
        return UserSession(
            id = id,
            phone = sp.getString(K_PHONE, "").orEmpty(),
            name = sp.getString(K_NAME, "").orEmpty(),
            mail = sp.getString(K_MAIL, "").orEmpty(),
            identity = sp.getInt(K_IDENTITY, -1),
            code = sp.getString(K_CODE, null),
            emergency = sp.getString(K_EMERGENCY, null)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}