package com.app.lira.data.repository

import com.app.lira.data.remote.SupabaseClient
import com.app.lira.widget.WidgetRefresher
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

@Serializable
private data class NewTransaction(
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: Int? = null,
    val amount: Double,
    val type: String,
    val source: String? = null,
    val description: String? = null,
    val date: String,
    @SerialName("recurring_id") val recurringId: Int? = null
)

@Serializable
private data class TransactionAmountRow(val amount: Double)

@Serializable
private data class AmountTypeDateRow(val amount: Double, val type: String, val date: String)

data class MonthlyTotal(val yearMonth: YearMonth, val spent: Double, val received: Double)

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
    val category: TransactionCategoryRef? = null,
    val recurring_id: Int? = null
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
    ): Result<Unit> = insert(amount, type, categoryId, source, description, OffsetDateTime.now().toString(), recurringId = null)

    /** Same as [addTransaction] but backdated to [date] and linked to [recurringId] (used to auto-post due recurring entries). */
    suspend fun addTransactionOnDate(
        amount: Double,
        type: String,
        date: LocalDate,
        categoryId: Int? = null,
        source: String? = null,
        description: String? = null,
        recurringId: Int? = null
    ): Result<Unit> {
        val zone = ZoneId.systemDefault()
        return insert(amount, type, categoryId, source, description, date.atStartOfDay(zone).toOffsetDateTime().toString(), recurringId)
    }

    private suspend fun insert(
        amount: Double,
        type: String,
        categoryId: Int?,
        source: String?,
        description: String?,
        dateIso: String,
        recurringId: Int?
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
                    date = dateIso,
                    recurringId = recurringId
                )
            )
            WidgetRefresher.refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    suspend fun updateTransaction(
        id: Int,
        amount: Double,
        categoryId: Int?,
        source: String?,
        description: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            client.postgrest["transactions"].update({
                set("amount", amount)
                set("category_id", categoryId)
                set("source", source?.takeIf { it.isNotBlank() })
                set("description", description?.takeIf { it.isNotBlank() })
            }) {
                filter { eq("id", id) }
            }
            WidgetRefresher.refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    suspend fun deleteTransaction(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            client.postgrest["transactions"].delete { filter { eq("id", id) } }
            WidgetRefresher.refresh()
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
                .select(Columns.raw("id, amount, type, source, description, date, recurring_id, category(name)")) {
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

    /** All transactions for the current user, newest first, with category names embedded (bounded to [limit] rows). */
    suspend fun getAllTransactions(limit: Long = 500): Result<List<TransactionDetail>> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val rows = client.postgrest["transactions"]
                .select(Columns.raw("id, amount, type, source, description, date, recurring_id, category(name)")) {
                    filter { eq("user_id", userId) }
                    order("date", Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<TransactionDetail>()

            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /** All transactions in [yearMonth] for the current user, newest first, with category names embedded. */
    suspend fun getTransactionsForMonth(yearMonth: YearMonth): Result<List<TransactionDetail>> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val zone = ZoneId.systemDefault()
            val (start, end) = dateRange(zone, yearMonth.atDay(1), yearMonth.plusMonths(1).atDay(1))
            val rows = client.postgrest["transactions"]
                .select(Columns.raw("id, amount, type, source, description, date, recurring_id, category(name)")) {
                    filter {
                        eq("user_id", userId)
                        gte("date", start)
                        lt("date", end)
                    }
                    order("date", Order.DESCENDING)
                }
                .decodeList<TransactionDetail>()

            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /**
     * Spent/received totals per month for the last [monthsBack] months (oldest first, including
     * the current month), fetched as a single ranged query and aggregated client-side.
     */
    suspend fun getMonthlyTotals(monthsBack: Int): Result<List<MonthlyTotal>> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val zone = ZoneId.systemDefault()
            val endMonth = YearMonth.now(zone)
            val startMonth = endMonth.minusMonths((monthsBack - 1).toLong())
            val (start, end) = dateRange(zone, startMonth.atDay(1), endMonth.plusMonths(1).atDay(1))

            val rows = client.postgrest["transactions"].select(Columns.list("amount", "type", "date")) {
                filter {
                    eq("user_id", userId)
                    gte("date", start)
                    lt("date", end)
                }
            }.decodeList<AmountTypeDateRow>()

            val byMonth = rows.groupBy { parseYearMonth(it.date) }
            val totals = (0 until monthsBack).map { offset ->
                val month = startMonth.plusMonths(offset.toLong())
                val monthRows = byMonth[month].orEmpty()
                MonthlyTotal(
                    yearMonth = month,
                    spent = monthRows.filter { it.type == TYPE_PAYED }.sumOf { it.amount },
                    received = monthRows.filter { it.type == TYPE_RECEIVED }.sumOf { it.amount }
                )
            }
            Result.success(totals)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    private fun parseYearMonth(isoDate: String): YearMonth? = try {
        YearMonth.from(OffsetDateTime.parse(isoDate))
    } catch (e: DateTimeParseException) {
        try {
            YearMonth.from(LocalDateTime.parse(isoDate))
        } catch (e2: DateTimeParseException) {
            null
        }
    }

    /** Sum of today's "payed" transactions for the current user, in the device's local day. */
    suspend fun getTodaySpent(): Result<Double> = sumByType(TYPE_PAYED, todayRange())

    /** Sum of today's "received" transactions for the current user, in the device's local day. */
    suspend fun getTodayReceived(): Result<Double> = sumByType(TYPE_RECEIVED, todayRange())

    /** Sum of this (Monday-starting) week's "payed" transactions for the current user. */
    suspend fun getWeekSpent(): Result<Double> = sumByType(TYPE_PAYED, weekRange())

    /** Sum of this (Monday-starting) week's "received" transactions for the current user. */
    suspend fun getWeekReceived(): Result<Double> = sumByType(TYPE_RECEIVED, weekRange())

    /** Sum of this calendar month's "payed" transactions for the current user. */
    suspend fun getMonthSpent(): Result<Double> = sumByType(TYPE_PAYED, monthRange())

    /** Sum of this calendar month's "received" transactions for the current user. */
    suspend fun getMonthReceived(): Result<Double> = sumByType(TYPE_RECEIVED, monthRange())

    private suspend fun sumByType(type: String, range: Pair<String, String>): Result<Double> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))
        val (start, end) = range

        return@withContext try {
            val rows = client.postgrest["transactions"].select(Columns.list("amount")) {
                filter {
                    eq("user_id", userId)
                    eq("type", type)
                    gte("date", start)
                    lt("date", end)
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
        return dateRange(zone, today, today.plusDays(1))
    }

    /** [start, end) bounds of the current Monday-starting week in the device's zone. */
    private fun weekRange(): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val startOfWeek = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return dateRange(zone, startOfWeek, startOfWeek.plusWeeks(1))
    }

    /** [start, end) bounds of the current calendar month in the device's zone. */
    private fun monthRange(): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val startOfMonth = LocalDate.now(zone).withDayOfMonth(1)
        return dateRange(zone, startOfMonth, startOfMonth.plusMonths(1))
    }

    private fun dateRange(zone: ZoneId, start: LocalDate, end: LocalDate): Pair<String, String> =
        start.atStartOfDay(zone).toOffsetDateTime().toString() to end.atStartOfDay(zone).toOffsetDateTime().toString()

    private fun friendlyDbError(e: Throwable): String = when (e) {
        is PostgrestRestException -> e.error
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}
