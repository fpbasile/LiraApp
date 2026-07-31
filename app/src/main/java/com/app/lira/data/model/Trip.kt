package com.app.lira.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val id: Int? = null,
    val user_id: String,
    val name: String,
    val currencies: List<String> = emptyList(),
    val is_active: Boolean = false,
    val created_at: String? = null
)
