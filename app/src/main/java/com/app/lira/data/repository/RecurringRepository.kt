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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException

@Serializable
private data class NewRecurring(
    val user_id: String,
    val name: String,
    val amount: Double,
    val start_date: String,
    val interval: String,
    val occurrences: Int? = null,
    val type: String,
    val category_id: Int? = null
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
        occurrences: Int?,
        type: String,
        categoryId: Int?
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
                    occurrences = occurrences,
                    type = type,
                    category_id = if (type == TransactionRepository.TYPE_PAYED) categoryId else null
                )
            ) { select() }.decodeSingle<Recurring>()
            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /**
     * Updates [original] with the given fields. If a schedule-affecting field changed
     * (start_date/interval/occurrences/type), `last_posted_date` is bumped to today — the old
     * schedule's history stays untouched (past transactions aren't rewritten), and posting simply
     * resumes under the new schedule from today onward instead of trying to reconcile occurrence
     * dates computed under the old schedule against the new one.
     */
    suspend fun updateRecurring(
        original: Recurring,
        name: String,
        amount: Double,
        startDate: String,
        interval: String,
        occurrences: Int?,
        type: String,
        categoryId: Int?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val id = original.id ?: return@withContext Result.failure(Exception("Missing id"))
        val scheduleChanged = startDate != original.start_date ||
            interval != original.interval ||
            occurrences != original.occurrences ||
            type != original.type

        return@withContext try {
            client.postgrest["recurring"].update({
                set("name", name)
                set("amount", amount)
                set("start_date", startDate)
                set("interval", interval)
                set("occurrences", occurrences)
                set("type", type)
                set("category_id", if (type == TransactionRepository.TYPE_PAYED) categoryId else null)
                if (scheduleChanged) {
                    set("last_posted_date", LocalDate.now().toString())
                }
            }) {
                filter { eq("id", id) }
            }
            Result.success(Unit)
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

    /**
     * Posts a real transaction for every occurrence of every recurring entry that is due
     * (start_date <= today) and hasn't been posted yet (tracked via `last_posted_date`), then
     * advances `last_posted_date` past the last one posted. Safe to call repeatedly — entries
     * with nothing new due are skipped.
     */
    suspend fun postDueOccurrences(): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))

        return@withContext try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val entries = client.postgrest["recurring"].select {
                filter { eq("user_id", userId) }
            }.decodeList<Recurring>()

            entries.forEach { entry ->
                val id = entry.id ?: return@forEach
                val due = pendingOccurrences(entry, today)
                if (due.isEmpty()) return@forEach

                due.forEach { date ->
                    TransactionRepository.addTransactionOnDate(
                        amount = entry.amount,
                        type = entry.type,
                        date = date,
                        categoryId = if (entry.type == TransactionRepository.TYPE_PAYED) entry.category_id else null,
                        source = if (entry.type == TransactionRepository.TYPE_RECEIVED) entry.name else null,
                        description = if (entry.type == TransactionRepository.TYPE_PAYED) entry.name else null,
                        recurringId = id
                    )
                }

                client.postgrest["recurring"].update({
                    set("last_posted_date", due.last().toString())
                }) {
                    filter { eq("id", id) }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyDbError(e)))
        }
    }

    /**
     * Expands [recurring]'s start_date/interval/occurrences forward and returns whichever
     * *not-yet-posted* occurrence dates land inside [yearMonth] — i.e. this is a "still due"
     * preview, not a full history. Once an occurrence has been posted as a real transaction
     * (tracked via `last_posted_date`), it's excluded here so the calendar doesn't show both a
     * "due" marker and the actual transaction for the same day. Bounded to avoid an unbounded
     * loop for an indefinite (occurrences == null) recurrence with a very old start_date.
     */
    fun occurrencesInMonth(recurring: Recurring, yearMonth: YearMonth): List<LocalDate> {
        val start = parseLocalDate(recurring.start_date) ?: return emptyList()
        val lastPosted = recurring.last_posted_date?.let { parseLocalDate(it) }
        val monthStart = yearMonth.atDay(1)
        val monthEndExclusive = yearMonth.plusMonths(1).atDay(1)

        val result = mutableListOf<LocalDate>()
        var date = start
        var count = 0
        var iterations = 0
        val maxIterations = 5000

        while (date.isBefore(monthEndExclusive) &&
            (recurring.occurrences == null || count < recurring.occurrences) &&
            iterations < maxIterations
        ) {
            val alreadyPosted = lastPosted != null && !date.isAfter(lastPosted)
            if (!date.isBefore(monthStart) && !alreadyPosted) {
                result.add(date)
            }
            date = stepDate(date, recurring.interval)
            count++
            iterations++
        }
        return result
    }

    /**
     * Occurrence dates of [recurring] that are due (<= [today]) but land after `last_posted_date`
     * (or after start_date if nothing's been posted yet) — i.e. still need to be posted as
     * transactions. Same bounded-loop shape as [occurrencesInMonth], just windowed by "today"
     * instead of a calendar month.
     */
    private fun pendingOccurrences(recurring: Recurring, today: LocalDate): List<LocalDate> {
        val start = parseLocalDate(recurring.start_date) ?: return emptyList()
        val lastPosted = recurring.last_posted_date?.let { parseLocalDate(it) }

        val result = mutableListOf<LocalDate>()
        var date = start
        var count = 0
        var iterations = 0
        val maxIterations = 5000

        while (!date.isAfter(today) &&
            (recurring.occurrences == null || count < recurring.occurrences) &&
            iterations < maxIterations
        ) {
            if (lastPosted == null || date.isAfter(lastPosted)) {
                result.add(date)
            }
            date = stepDate(date, recurring.interval)
            count++
            iterations++
        }
        return result
    }

    /** The date of [recurring]'s last occurrence, or null if it repeats indefinitely (occurrences == null). */
    fun finalOccurrenceDate(recurring: Recurring): LocalDate? {
        val occurrences = recurring.occurrences ?: return null
        val start = parseLocalDate(recurring.start_date) ?: return null
        var date = start
        repeat((occurrences - 1).coerceAtLeast(0)) { date = stepDate(date, recurring.interval) }
        return date
    }

    /** 1-based position of [date] within [recurring]'s occurrence sequence, or null if [date] isn't one of its occurrence dates. */
    fun occurrenceIndex(recurring: Recurring, date: LocalDate): Int? {
        val start = parseLocalDate(recurring.start_date) ?: return null
        var current = start
        var index = 1
        var iterations = 0
        val maxIterations = 5000
        while (!current.isAfter(date) && iterations < maxIterations) {
            if (current == date) return index
            current = stepDate(current, recurring.interval)
            index++
            iterations++
        }
        return null
    }

    private fun stepDate(date: LocalDate, interval: String): LocalDate = when (interval) {
        "daily" -> date.plusDays(1)
        "weekly" -> date.plusWeeks(1)
        "monthly" -> date.plusMonths(1)
        "yearly" -> date.plusYears(1)
        else -> date.plusMonths(1)
    }

    private fun parseLocalDate(isoDate: String): LocalDate? = try {
        LocalDate.parse(isoDate)
    } catch (e: DateTimeParseException) {
        null
    }

    private fun friendlyDbError(e: Throwable): String = when (e) {
        is PostgrestRestException -> e.error
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}
