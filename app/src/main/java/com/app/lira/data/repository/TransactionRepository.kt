package com.app.lira.data.repository

import com.app.lira.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@Serializable
private data class NewTransaction(
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: Int? = null,
    val amount: Double,
    val type: String,
    val source: String? = null,
    val description: String? = null,
    val date: String
)

@Serializable
private data class TransactionAmountRow(val amount: Double)

@Serializable
data class TransactionCategoryRef(val name: String)

@Serializable
data class TransactionDetail(
    val id: Int,
    val amount: Double,
    val type: String,
    val source: String? = null,
    val description: String? = null,
    val date: String,
    val category: TransactionCategoryRef? = null
)

object TransactionRepository {

    // The DB enforces a check constraint on `type`, so these are the only valid values.
    const val TYPE_PAYED = "payed"
    const val TYPE_RECEIVED = "received"

    private val client = SupabaseClient.client

    suspend fun addTransaction(
        amount: Double,
        type: String,
        categoryId: Int? = null,
        source: String? = null,
        description: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            client.postgrest["transactions"].insert(
                NewTransaction(
                    userId = userId,
                    categoryId = categoryId,
                    amount = amount,
                    type = type,
                    source = source?.takeIf { it.isNotBlank() },
                    description = description?.takeIf { it.isNotBlank() },
                    date = OffsetDateTime.now().toString()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /** Today's transactions for the current user, newest first, with each one's category name embedded. */
    suspend fun getTodayTransactions(): Result<List<TransactionDetail>> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val (startOfDay, endOfDay) = todayRange()
            val rows = client.postgrest["transactions"]
                .select(Columns.raw("id, amount, type, source, description, date, category(name)")) {
                    filter {
                        eq("user_id", userId)
                        gte("date", startOfDay)
                        lt("date", endOfDay)
                    }
                    order("date", Order.DESCENDING)
                }
                .decodeList<TransactionDetail>()

            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /** Sum of today's "payed" transactions for the current user, in the device's local day. */
    suspend fun getTodaySpent(): Result<Double> = sumTodayByType(TYPE_PAYED)

    /** Sum of today's "received" transactions for the current user, in the device's local day. */
    suspend fun getTodayReceived(): Result<Double> = sumTodayByType(TYPE_RECEIVED)

    private suspend fun sumTodayByType(type: String): Result<Double> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val (startOfDay, endOfDay) = todayRange()
            val rows = client.postgrest["transactions"].select(Columns.list("amount")) {
                filter {
                    eq("user_id", userId)
                    eq("type", type)
                    gte("date", startOfDay)
                    lt("date", endOfDay)
                }
            }.decodeList<TransactionAmountRow>()

            Result.success(rows.sumOf { it.amount })
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /** [start, end) bounds of "today" in the device's zone, formatted the same way [addTransaction] stores `date`. */
    private fun todayRange(): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toOffsetDateTime().toString()
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime().toString()
        return startOfDay to endOfDay
    }

    private fun friendlyDbError(e: Throwable): String = when (e) {
        is PostgrestRestException -> e.error
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}
