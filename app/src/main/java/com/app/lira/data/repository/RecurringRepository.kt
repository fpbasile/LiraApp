package com.app.lira.data.repository

import com.app.lira.data.model.Recurring
import com.app.lira.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

@Serializable
private data class NewRecurring(
    val user_id: String,
    val name: String,
    val amount: Double,
    val start_date: String,
    val interval: String,
    val occurrences: Int? = null
)

object RecurringRepository {

    val INTERVALS = listOf("daily", "weekly", "monthly", "yearly")

    private val client = SupabaseClient.client

    suspend fun getRecurring(): Result<List<Recurring>> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val rows = client.postgrest["recurring"].select {
                filter { eq("user_id", userId) }
                order("start_date", Order.DESCENDING)
            }.decodeList<Recurring>()
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    suspend fun addRecurring(
        name: String,
        amount: Double,
        startDate: String,
        interval: String,
        occurrences: Int?
    ): Result<Recurring> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val inserted = client.postgrest["recurring"].insert(
                NewRecurring(
                    user_id = userId,
                    name = name,
                    amount = amount,
                    start_date = startDate,
                    interval = interval,
                    occurrences = occurrences
                )
            ) { select() }.decodeSingle<Recurring>()
            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    suspend fun deleteRecurring(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            client.postgrest["recurring"].delete { filter { eq("id", id) } }
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
