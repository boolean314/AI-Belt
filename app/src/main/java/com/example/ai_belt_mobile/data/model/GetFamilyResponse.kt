package com.example.ai_belt_mobile.data.model

data class GetFamilyResponse(
    val code: Int,
    val message: String,
    val data: List<FamilyMember>
)

data class FamilyMember(
    val id: Int,
    val name: String,
    val phone: String,
    val isEmergency: Boolean
)
