package com.app.lira.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Recurring(
    val id: Int? = null,
    val user_id: String,
    val name: String,
    val amount: Double,
    val start_date: String,
    val interval: String, // "daily", "weekly", "monthly", "yearly"
    val occurrences: Int? = null,
    val type: String = "payed", // "payed" or "received"
    val category_id: Int? = null, // only meaningful when type == "payed"
    val last_posted_date: String? = null, // latest occurrence already posted as a transaction
    val created_at: String? = null
)
