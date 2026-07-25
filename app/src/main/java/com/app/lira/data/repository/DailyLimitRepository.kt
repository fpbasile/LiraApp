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
private data class DailyLimitAmountRow(val amount: Double)

@Serializable
private data class DailyLimitIdRow(val id: Int)

@Serializable
private data class NewDailyLimit(
    @SerialName("user_id") val userId: String,
    val amount: Double,
    val period: String
)

object DailyLimitRepository {

    const val DEFAULT_DAILY_LIMIT = 50.0
    private const val PERIOD_DAILY = "daily"

    private val client = SupabaseClient.client

    /** The user's configured daily spending limit, or [DEFAULT_DAILY_LIMIT] if they haven't set one. */
    suspend fun getDailyLimit(): Result<Double> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val rows = client.postgrest["daily_limit"].select(Columns.list("amount")) {
                filter {
                    eq("user_id", userId)
                    eq("period", PERIOD_DAILY)
                }
                limit(1)
            }.decodeList<DailyLimitAmountRow>()

            Result.success(rows.firstOrNull()?.amount ?: DEFAULT_DAILY_LIMIT)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /** Creates or updates the user's daily limit row (there's no known unique constraint to rely on for a real upsert). */
    suspend fun setDailyLimit(amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val existing = client.postgrest["daily_limit"].select(Columns.list("id")) {
                filter {
                    eq("user_id", userId)
                    eq("period", PERIOD_DAILY)
                }
                limit(1)
            }.decodeList<DailyLimitIdRow>()

            val existingId = existing.firstOrNull()?.id
            if (existingId != null) {
                client.postgrest["daily_limit"].update({
                    set("amount", amount)
                }) {
                    filter { eq("id", existingId) }
                }
            } else {
                client.postgrest["daily_limit"].insert(
                    NewDailyLimit(userId = userId, amount = amount, period = PERIOD_DAILY)
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
