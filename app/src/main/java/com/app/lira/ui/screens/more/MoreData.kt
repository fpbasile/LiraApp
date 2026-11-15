package com.app.lira.ui.screens.more

import com.app.lira.data.AppPrefs
import com.app.lira.data.HomeDataCache
import com.app.lira.data.model.Recurring
import com.app.lira.data.local.DataMode
import com.app.lira.data.local.LocalDb
import com.app.lira.data.local.LocalTxn
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CategoryRow
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.toUiCategories
import com.app.lira.widget.WidgetRefresher
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** One transaction row with its raw category id (TransactionDetail only carries the name). */
@Serializable
data class EntryRow(
    val id: Int,
    val amount: Double,
    val type: String,
    val source: String? = null,
    val description: String? = null,
    val date: String,
    val category_id: Int? = null,
    val recurring_id: Int? = null,
    val trip_id: Int? = null,
    val currency: String? = null
)

@Serializable
private data class IdRow(val id: Int)

/**
 * Data helpers for the More area that the shared repositories don't have yet. Kept here (instead
 * of editing the repositories) so parallel work on the data layer doesn't collide.
 */
object MoreData {
    private val client = SupabaseClient.client

    suspend fun getEntry(id: Int): Result<EntryRow> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) {
            val t = LocalDb.transaction(id) ?: return@withContext Result.failure(Exception("This entry no longer exists."))
            return@withContext Result.success(
                EntryRow(t.id, t.amount, t.type, t.source, t.description, t.date, t.category_id, t.recurring_id, t.trip_id, t.currency)
            )
        }
        try {
            val row = client.postgrest["transactions"].select(
                Columns.raw("id, amount, type, source, description, date, category_id, recurring_id, trip_id, currency")
            ) {
                filter { eq("id", id) }
                limit(1)
            }.decodeList<EntryRow>().firstOrNull() ?: return@withContext Result.failure(Exception("This entry no longer exists."))
            Result.success(row)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    /** Full edit of an entry: amount, type, category, source, note and (optionally) its date. */
    suspend fun updateEntry(
        id: Int,
        amount: Double,
        type: String,
        categoryId: Int?,
        source: String?,
        description: String?,
        dateIso: String?,
        currency: String? = null,
        updateCurrency: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) {
            LocalDb.updateTransaction(id) {
                it.copy(
                    amount = amount, type = type, category_id = categoryId,
                    source = source?.trim()?.takeIf { s -> s.isNotBlank() },
                    description = description?.trim()?.takeIf { d -> d.isNotBlank() },
                    date = dateIso ?: it.date,
                    currency = if (updateCurrency) currency else it.currency
                )
            }
            WidgetRefresher.refresh()
            return@withContext Result.success(Unit)
        }
        try {
            client.postgrest["transactions"].update({
                if (updateCurrency) set("currency", currency)
                set("amount", amount)
                set("type", type)
                set("category_id", categoryId)
                set("source", source?.trim()?.takeIf { it.isNotBlank() })
                set("description", description?.trim()?.takeIf { it.isNotBlank() })
                if (dateIso != null) set("date", dateIso)
            }) {
                filter { eq("id", id) }
            }
            WidgetRefresher.refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    /** How many of the current user's entries use [categoryId] (all time). */
    suspend fun countEntriesInCategory(categoryId: Int): Result<Int> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) return@withContext Result.success(LocalDb.transactions().count { it.category_id == categoryId })
        val userId = client.auth.currentUserOrNull()?.id ?: return@withContext Result.failure(Exception("You're not signed in."))
        try {
            val rows = client.postgrest["transactions"].select(Columns.list("id")) {
                filter {
                    eq("user_id", userId)
                    eq("category_id", categoryId)
                }
            }.decodeList<IdRow>()
            Result.success(rows.size)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    /**
     * Deletes a category. The user's entries and recurring items that used it move to "Other"
     * (an existing category called Other, or no category if there isn't one) so the delete isn't
     * blocked by the foreign key.
     */
    suspend fun deleteCategoryMovingEntries(categoryId: Int, all: List<CategoryRow>): Result<Unit> = withContext(Dispatchers.IO) {
        val other = all.firstOrNull { it.id != categoryId && it.name.trim().equals("other", ignoreCase = true) }?.id
        if (DataMode.isLocal) {
            LocalDb.updateTransactions({ it.category_id == categoryId }) { it.copy(category_id = other) }
            LocalDb.updateRecurringWhere({ it.category_id == categoryId }) { it.copy(category_id = other) }
            LocalDb.deleteCategory(categoryId)
            WidgetRefresher.refresh()
            return@withContext Result.success(Unit)
        }
        val userId = client.auth.currentUserOrNull()?.id ?: return@withContext Result.failure(Exception("You're not signed in."))
        try {
            client.postgrest["transactions"].update({ set("category_id", other) }) {
                filter {
                    eq("user_id", userId)
                    eq("category_id", categoryId)
                }
            }
            client.postgrest["recurring"].update({ set("category_id", other) }) {
                filter {
                    eq("user_id", userId)
                    eq("category_id", categoryId)
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception(friendly(e)))
        }
        CategoryRepository.deleteCategory(categoryId).onFailure {
            return@withContext Result.failure(Exception("Couldn't delete it — other entries still use this category."))
        }
        WidgetRefresher.refresh()
        Result.success(Unit)
    }

    /** Keeps the in-memory Home snapshot in step after category edits. */
    fun publishCategories(rows: List<CategoryRow>) {
        HomeDataCache.categories = rows.toUiCategories()
    }

    /** Clears per-user, on-device state after sign-out / data deletion. */
    fun clearLocalUserState() {
        AppPrefs.onboardingDone = false
        AppPrefs.showFirstLogCoach = false
        HomeDataCache.categories = emptyList()
        HomeDataCache.todaySpent = 0.0
        HomeDataCache.todayReceived = 0.0
        HomeDataCache.dailyLimit = SpendingLimitRepository.DEFAULT_DAILY_LIMIT
        HomeDataCache.weekSpent = 0.0
        HomeDataCache.weekLimit = null
        HomeDataCache.monthSpent = 0.0
        HomeDataCache.monthLimit = null
    }

    /** Consecutive days with at least one entry, ending today (or yesterday if nothing yet today). */
    fun streak(entries: List<TransactionDetail>, today: LocalDate = LocalDate.now()): Int {
        val days = entries.mapNotNull { parseLocalDateTime(it.date)?.toLocalDate() }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var n = 0
        while (day in days) { n++; day = day.minusDays(1) }
        return n
    }

    /** CSV of entries, oldest first. */
    fun toCsv(entries: List<TransactionDetail>, homeCurrency: String): String {
        fun esc(s: String?): String {
            val v = s.orEmpty()
            return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        }
        val sb = StringBuilder("date,type,amount,currency,category,note,from\n")
        entries.sortedBy { it.date }.forEach { e ->
            val dt = parseLocalDateTime(e.date)
            sb.append(esc(dt?.toString() ?: e.date)).append(',')
                .append(if (e.type == "received") "income" else "expense").append(',')
                .append(String.format(java.util.Locale.US, "%.2f", e.amount)).append(',')
                .append(esc(e.currency ?: homeCurrency)).append(',')
                .append(esc(e.category?.name)).append(',')
                .append(esc(e.description)).append(',')
                .append(esc(e.source)).append('\n')
        }
        return sb.toString()
    }

    /** Transaction timestamps come back as ISO offset strings; shown in the device zone. */
    fun parseLocalDateTime(iso: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? = try {
        OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDateTime()
    } catch (e: DateTimeParseException) {
        try { LocalDateTime.parse(iso) } catch (e2: DateTimeParseException) {
            try { LocalDate.parse(iso.take(10)).atStartOfDay() } catch (e3: Exception) { null }
        }
    }

    fun toIso(local: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String =
        ZonedDateTime.of(local, zone).toOffsetDateTime().toString()

    private fun friendly(e: Throwable): String = when (e) {
        is PostgrestRestException -> e.error
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}

// ───────────────────────────── Recurring schedule maths ─────────────────────────────

internal object Schedule {
    fun step(date: LocalDate, interval: String): LocalDate = when (interval) {
        "daily" -> date.plusDays(1)
        "weekly" -> date.plusWeeks(1)
        "yearly" -> date.plusYears(1)
        else -> date.plusMonths(1)
    }

    /** Next occurrence that hasn't been posted yet (today or later), or null when the series has ended. */
    fun next(r: Recurring, today: LocalDate = LocalDate.now()): LocalDate? {
        val start = runCatching { LocalDate.parse(r.start_date) }.getOrNull() ?: return null
        val lastPosted = r.last_posted_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        var date = start
        var count = 0
        var guard = 0
        while ((date.isBefore(today) || (lastPosted != null && !date.isAfter(lastPosted))) && guard < 20000) {
            date = step(date, r.interval)
            count++
            guard++
        }
        val occ = r.occurrences
        if (occ != null && count >= occ) return null
        return date
    }

    fun final(start: LocalDate, interval: String, occurrences: Int?): LocalDate? {
        if (occurrences == null) return null
        var d = start
        repeat((occurrences - 1).coerceAtLeast(0)) { d = step(d, interval) }
        return d
    }

    /** Monthly-equivalent amount, for the summary line. */
    fun monthly(amount: Double, interval: String): Double = when (interval) {
        "daily" -> amount * 365.0 / 12.0
        "weekly" -> amount * 52.0 / 12.0
        "yearly" -> amount / 12.0
        else -> amount
    }

    fun intervalLabel(interval: String): String = interval.replaceFirstChar { it.uppercase() }

    /** "Monthly · 15th", "Weekly · Mon", "Yearly · 3 Mar", "Daily". */
    fun cadence(interval: String, start: LocalDate): String = when (interval) {
        "daily" -> "Daily"
        "weekly" -> "Weekly · " + start.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        "yearly" -> "Yearly · " + Dates.short(start)
        else -> "Monthly · " + Dates.ordinal(start.dayOfMonth)
    }
}
