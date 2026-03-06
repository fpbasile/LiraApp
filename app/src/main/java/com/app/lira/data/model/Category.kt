package com.app.lira.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: Int,
    val name: String,
    @SerialName("color_hex") val colorHex: Long

)