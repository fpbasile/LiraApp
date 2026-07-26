package com.app.lira.data.repository

import com.app.lira.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class LimitAmountRow(val amount: Double)

@Serializable
private data class LimitIdRow(val id: Int)

@Serializable
private data class NewLimit(
    @SerialName("user_id") val userId: String,
    val amount: Double,
    val period: String
)

object SpendingLimitRepository {

    const val PERIOD_DAILY = "daily"
    const val PERIOD_WEEKLY = "weekly"
    const val PERIOD_MONTHLY = "monthly"
    val PERIODS = listOf(PERIOD_DAILY, PERIOD_WEEKLY, PERIOD_MONTHLY)

    const val DEFAULT_DAILY_LIMIT = 50.0

    private val client = SupabaseClient.client

    /** The user's configured daily spending limit, or [DEFAULT_DAILY_LIMIT] if they haven't set one. */
    suspend fun getDailyLimit(): Result<Double> = getLimit(PERIOD_DAILY).mapCatching { it ?: DEFAULT_DAILY_LIMIT }

    suspend fun setDailyLimit(amount: Double): Result<Unit> = setLimit(PERIOD_DAILY, amount)

    /** Null means the user hasn't set a limit for this [period] yet — there's no assumed default beyond daily. */
    suspend fun getLimit(period: String): Result<Double?> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val rows = client.postgrest["daily_limit"].select(Columns.list("amount")) {
                filter {
                    eq("user_id", userId)
                    eq("period", period)
                }
                limit(1)
            }.decodeList<LimitAmountRow>()

            Result.success(rows.firstOrNull()?.amount)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /** Creates or updates the user's limit row for [period] (no known unique constraint to rely on for a real upsert). */
    suspend fun setLimit(period: String, amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val existing = client.postgrest["daily_limit"].select(Columns.list("id")) {
                filter {
                    eq("user_id", userId)
                    eq("period", period)
                }
                limit(1)
            }.decodeList<LimitIdRow>()

            val existingId = existing.firstOrNull()?.id
            if (existingId != null) {
                client.postgrest["daily_limit"].update({
                    set("amount", amount)
                }) {
                    filter { eq("id", existingId) }
                }
            } else {
                client.postgrest["daily_limit"].insert(
                    NewLimit(userId = userId, amount = amount, period = period)
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    private fun friendlyDbError(e: Throwable): String = when (e) {
        is PostgrestRestException -> e.error
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}
