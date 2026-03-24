package com.example.ai_belt_mobile.data.model

data class GetDisabilityResponse(
    val code: Int,
    val message: String,
    val data: List<GetDisabilityData>
)

data class GetDisabilityData(
    val id: Int,
    val name: String,
    val phone: String,
    val emergency: String
)
