package com.app.lira.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Payed(
    val id: Long? = null,
    val amount: Double,
    val date: String, // "YYYY-MM-DD"
    val time: String, // "HH:mm:ss"
    val description: String?,
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: Long
)