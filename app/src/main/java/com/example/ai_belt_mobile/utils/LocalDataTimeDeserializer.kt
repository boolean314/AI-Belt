package com.example.ai_belt_mobile.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocalDataTimeDeserializer : JsonDeserializer<LocalDateTime> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime {
        val dateTimeString = json?.asString
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")  // 适应后端的时间格式
        return LocalDateTime.parse(dateTimeString, formatter)
    }
}