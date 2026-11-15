package com.app.lira.data

import androidx.compose.runtime.mutableStateMapOf
import com.app.lira.data.model.Recurring
import com.app.lira.data.local.DataMode
import com.app.lira.data.local.LocalDb
import com.app.lira.data.local.LocalTxn
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.CurrencyConverterRepository
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.widget.WidgetRefresher
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** Light transaction row used by Home (quick add, strip, Today sheet, streak, weekly recap). */
@Serializable
data class HomeTxn(
    val id: Int,
    val amount: Double,
    val type: String,
    val date: String,
    val category_id: Int? = null,
    val source: String? = null,
    val description: String? = null,
    val currency: String? = null,
    val trip_id: Int? = null,
    val recurring_id: Int? = null
) {
    val isIncome: Boolean get() = type == TransactionRepository.TYPE_RECEIVED

    /** Local date-time in the device zone (null if the stored date can't be parsed). */
    fun local(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? = parseLocal(date, zone)

    companion object {
        fun parseLocal(iso: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? = try {
            OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            try { LocalDateTime.parse(iso) } catch (e2: DateTimeParseException) {
                try { LocalDate.parse(iso.take(10)).atStartOfDay() } catch (e3: Exception) { null }
            }
        }
    }
}

@Serializable
private data class NewHomeTxn(
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: Int? = null,
    val amount: Double,
    val type: String,
    val source: String? = null,
    val description: String? = null,
    val date: String,
    @SerialName("recurring_id") val recurringId: Int? = null,
    @SerialName("trip_id") val tripId: Int? = null,
    val currency: String? = null
)

@Serializable
private data class HomeIdRow(val id: Int)

/**
 * Home's own queries: one ranged fetch of recent rows (everything on Home is derived from it
 * client-side), and an insert that returns the new id so a save can be undone.
 */
object HomeStatsRepository {
    private val client = SupabaseClient.client

    /**
     * App-wide scope for writes started from Home, so a save or an undo isn't cancelled when the
     * user leaves the Home tab mid-request.
     */
    val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val COLUMNS = Columns.list(
        "id", "amount", "type", "date", "category_id", "source", "description", "currency", "trip_id", "recurring_id"
    )

    /** Rows from the last [days] days (local zone), newest first. */
    suspend fun recent(days: Long = 120): Result<List<HomeTxn>> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).minusDays(days).atStartOfDay(zone).toInstant()
            return@withContext Result.success(LocalDb.transactionsBetween(start, null).take(3000).map {
                HomeTxn(it.id, it.amount, it.type, it.date, it.category_id, it.source, it.description, it.currency, it.trip_id, it.recurring_id)
            })
        }
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))
        try {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).minusDays(days).atStartOfDay(zone).toOffsetDateTime().toString()
            val rows = client.postgrest["transactions"].select(COLUMNS) {
                filter {
                    eq("user_id", userId)
                    gte("date", start)
                }
                order("date", Order.DESCENDING)
                limit(3000)
            }.decodeList<HomeTxn>()
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    /** Inserts a transaction and returns its id. Refreshes the home-screen widget. */
    suspend fun insert(
        amount: Double,
        type: String,
        dateIso: String,
        categoryId: Int? = null,
        source: String? = null,
        description: String? = null,
        tripId: Int? = null,
        currency: String? = null,
        recurringId: Int? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (DataMode.isLocal) {
            val id = LocalDb.insertTransaction(
                LocalTxn(0, amount, type, dateIso, categoryId, source?.takeIf { it.isNotBlank() },
                    description?.takeIf { it.isNotBlank() }, currency, tripId, recurringId)
            )
            WidgetRefresher.refresh()
            return@withContext Result.success(id)
        }
        val userId = client.auth.currentUserOrNull()?.id
            ?: return@withContext Result.failure(Exception("You're not signed in."))
        try {
            val row = client.postgrest["transactions"].insert(
                NewHomeTxn(
                    userId = userId,
                    categoryId = categoryId,
                    amount = amount,
                    type = type,
                    source = source?.takeIf { it.isNotBlank() },
                    description = description?.takeIf { it.isNotBlank() },
                    date = dateIso,
                    recurringId = recurringId,
                    tripId = tripId,
                    currency = currency
                )
            ) { select(Columns.list("id")) }.decodeSingle<HomeIdRow>()
            WidgetRefresher.refresh()
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    /** Re-inserts a deleted row (undo of a delete). Returns the new id. */
    suspend fun reinsert(t: HomeTxn): Result<Int> = insert(
        amount = t.amount, type = t.type, dateIso = t.date, categoryId = t.category_id, source = t.source,
        description = t.description, tripId = t.trip_id, currency = t.currency, recurringId = t.recurring_id
    )

    private fun friendly(e: Throwable): String = when (e) {
        is PostgrestRestException -> e.error
        is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}

/** Exchange rates fetched for Home, cached for the session. Observable. */
object RateCache {
    private val rates = mutableStateMapOf<String, Double>()

    fun cached(from: String, to: String): Double? =
        if (from.equals(to, ignoreCase = true)) 1.0 else rates["${from.uppercase()}>${to.uppercase()}"]

    suspend fun rate(from: String, to: String): Double? {
        cached(from, to)?.let { return it }
        val r = CurrencyConverterRepository.convert(1.0, from, to).getOrNull()?.rates?.get(to.uppercase()) ?: return null
        rates["${from.uppercase()}>${to.uppercase()}"] = r
        if (r != 0.0) rates["${to.uppercase()}>${from.uppercase()}"] = 1.0 / r
        return r
    }
}

data class WeeklyRecap(
    val start: LocalDate,
    val end: LocalDate,
    val spent: Double,
    val limit: Double?,
    val perDay: List<Double>,
    val topCategoryId: Int?,
    val topCategoryAmount: Double,
    val loggedDays: Int,
    val loggedPerDay: List<Boolean>,
    /** e.g. (categoryId, -0.2) = 20% less on that category than the week before. */
    val change: Pair<Int, Double>?
)

data class RepeatSuggestion(
    val categoryId: Int,
    val amount: Double,
    val description: String?,
    val currency: String?
)

data class UpcomingEntry(val date: LocalDate, val recurring: Recurring)

/** Pure computations over [HomeTxn] lists. */
object HomeStats {

    fun weekKey(date: LocalDate): String =
        "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Amount in the home currency, or null while the rate for a foreign row isn't known yet. */
    fun homeAmount(t: HomeTxn, home: String): Double? {
        val cur = t.currency
        if (cur == null || cur.equals(home, ignoreCase = true)) return t.amount
        return RateCache.cached(cur, home)?.let { t.amount * it }
    }

    /** Spending (home currency) of rows whose local date is in [from, toExclusive). */
    fun spent(txns: List<HomeTxn>, home: String, from: LocalDate, toExclusive: LocalDate): Double =
        txns.sumOf { t ->
            if (t.isIncome) 0.0 else {
                val d = t.local()?.toLocalDate()
                if (d != null && !d.isBefore(from) && d.isBefore(toExclusive)) homeAmount(t, home) ?: 0.0 else 0.0
            }
        }

    /** Foreign currencies present in [txns] (need a rate to count toward home totals). */
    fun foreignCurrencies(txns: List<HomeTxn>, home: String): Set<String> =
        txns.mapNotNull { it.currency?.uppercase() }.filter { it != home.uppercase() }.toSet()

    /** Days with at least one entry the user logged themselves (auto-posted recurring rows don't count). */
    fun loggedDays(txns: List<HomeTxn>): Set<LocalDate> =
        txns.filter { it.recurring_id == null }.mapNotNull { it.local()?.toLocalDate() }.toSet()

    /** Consecutive days with ≥1 logged entry, ending today (or yesterday if today has none yet). */
    fun streak(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    /** This week Mon..Sun: whether each day has a logged entry. */
    fun weekLogged(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): List<Boolean> {
        val start = weekStart(today)
        return (0 until 7).map { start.plusDays(it.toLong()) in days }
    }

    /** Recap of the week before the one containing [today]; null when nothing was logged. */
    fun recap(txns: List<HomeTxn>, home: String, weekLimit: Double?, dailyLimit: Double, today: LocalDate = LocalDate.now()): WeeklyRecap? {
        val start = weekStart(today).minusWeeks(1)
        val end = start.plusDays(7)
        val inWeek = txns.filter { t -> t.local()?.toLocalDate()?.let { !it.isBefore(start) && it.isBefore(end) } == true }
        if (inWeek.isEmpty()) return null
        val perDay = (0 until 7).map { i -> spent(inWeek, home, start.plusDays(i.toLong()), start.plusDays(i + 1L)) }
        val byCat = categoryTotals(inWeek, home)
        val top = byCat.maxByOrNull { it.value }
        val logged = loggedDays(inWeek)
        val loggedPerDay = (0 until 7).map { start.plusDays(it.toLong()) in logged }

        // Biggest relative change vs the week before, on a category that mattered (≥ 5 in either week).
        val prevStart = start.minusWeeks(1)
        val prev = categoryTotals(
            txns.filter { t -> t.local()?.toLocalDate()?.let { !it.isBefore(prevStart) && it.isBefore(start) } == true }, home
        )
        val change = (byCat.keys + prev.keys).mapNotNull { id ->
            val now = byCat[id] ?: 0.0
            val before = prev[id] ?: 0.0
            if (before < 5.0 || (now < 5.0 && before < 5.0)) null
            else id to (now - before) / before
        }.filter { abs(it.second) >= 0.1 }.maxByOrNull { abs(it.second) }

        return WeeklyRecap(
            start = start,
            end = end.minusDays(1),
            spent = perDay.sum(),
            limit = weekLimit ?: (dailyLimit * 7).takeIf { it > 0 },
            perDay = perDay,
            topCategoryId = top?.key,
            topCategoryAmount = top?.value ?: 0.0,
            loggedDays = logged.size,
            loggedPerDay = loggedPerDay,
            change = change
        )
    }

    private fun categoryTotals(txns: List<HomeTxn>, home: String): Map<Int, Double> =
        txns.filter { !it.isIncome && it.category_id != null }
            .groupBy { it.category_id!! }
            .mapValues { (_, rows) -> rows.sumOf { homeAmount(it, home) ?: 0.0 } }

    /**
     * Category ids ordered by usage, weighted toward entries logged around this time of day.
     * Categories never used keep their original order at the end.
     */
    fun categoryOrder(categoryIds: List<Int>, txns: List<HomeTxn>, now: LocalDateTime = LocalDateTime.now()): List<Int> {
        val score = HashMap<Int, Double>()
        txns.forEach { t ->
            val id = t.category_id ?: return@forEach
            if (t.isIncome) return@forEach
            val local = t.local() ?: return@forEach
            var hourDiff = abs(local.hour - now.hour)
            if (hourDiff > 12) hourDiff = 24 - hourDiff
            val timeBonus = if (hourDiff <= 2) 1.5 else 0.0
            val recency = if (local.toLocalDate().isAfter(now.toLocalDate().minusDays(30))) 1.0 else 0.5
            score[id] = (score[id] ?: 0.0) + recency + timeBonus
        }
        return categoryIds.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Int>> { score[it.value] ?: 0.0 }.thenBy { it.index })
            .map { it.value }
    }

    /**
     * Category the typed [amount] most likely belongs to, from past expenses in the same currency:
     * close amounts (within ~15%, exact matches count double) logged recently and around this time
     * of day. Null unless one category clearly dominates — a wrong nudge is worse than none.
     */
    fun categoryForAmount(
        amount: Double,
        currency: String,
        home: String,
        txns: List<HomeTxn>,
        now: LocalDateTime = LocalDateTime.now()
    ): Int? {
        if (amount <= 0.0) return null
        val score = HashMap<Int, Double>()
        txns.forEach { t ->
            val id = t.category_id ?: return@forEach
            if (t.isIncome || t.recurring_id != null || t.amount <= 0.0) return@forEach
            if (!(t.currency ?: home).equals(currency, ignoreCase = true)) return@forEach
            val r = kotlin.math.ln(amount / t.amount) / 0.15
            if (abs(r) > 2.0) return@forEach
            var w = kotlin.math.exp(-r * r)
            if (abs(amount - t.amount) < 0.005) w *= 2.0
            val local = t.local()
            if (local != null) {
                var h = abs(local.hour - now.hour); if (h > 12) h = 24 - h
                if (h <= 2) w *= 1.3
                if (local.toLocalDate().isBefore(now.toLocalDate().minusDays(45))) w *= 0.6
            }
            score[id] = (score[id] ?: 0.0) + w
        }
        val total = score.values.sum()
        val best = score.maxByOrNull { it.value } ?: return null
        return if (best.value >= 1.5 && best.value / total >= 0.6) best.key else null
    }

    /** Distinct payers ("from who") of recent income, most frequent first. */
    fun payers(txns: List<HomeTxn>): List<String> =
        txns.filter { it.isIncome && !it.source.isNullOrBlank() }
            .groupBy { it.source!!.trim().lowercase() }
            .map { (_, rows) -> rows.first().source!!.trim() to rows.size }
            .sortedByDescending { it.second }
            .map { it.first }

    /**
     * "Repeat last" suggestion: the most repeated expense (same category, amount, note) around
     * this time of day over the last 60 days, falling back to the most recent expense.
     */
    fun repeatSuggestion(txns: List<HomeTxn>, now: LocalDateTime = LocalDateTime.now()): RepeatSuggestion? {
        val since = now.toLocalDate().minusDays(60)
        val expenses = txns.filter { !it.isIncome && it.category_id != null && it.recurring_id == null }
        if (expenses.isEmpty()) return null
        val groups = expenses.filter { it.local()?.toLocalDate()?.isAfter(since) == true }
            .groupBy { Triple(it.category_id, it.amount, it.description?.trim()?.lowercase()) }
        val best = groups.values.map { rows ->
            val nearNow = rows.count { r ->
                val h = r.local()?.hour ?: return@count false
                var d = abs(h - now.hour); if (d > 12) d = 24 - d
                d <= 2
            }
            rows to rows.size + nearNow * 1.5
        }.filter { it.first.size >= 2 }.maxByOrNull { it.second }?.first?.first()
        val pick = best ?: expenses.first()
        return RepeatSuggestion(pick.category_id!!, pick.amount, pick.description, pick.currency)
    }

    /** Recurring entries still to come in (today, today + [days]]. */
    fun upcoming(recurring: List<Recurring>, today: LocalDate = LocalDate.now(), days: Long = 7): List<UpcomingEntry> {
        val end = today.plusDays(days)
        val months = listOf(YearMonth.from(today), YearMonth.from(end)).distinct()
        return recurring.flatMap { r ->
            months.flatMap { m -> RecurringRepository.occurrencesInMonth(r, m) }
                .filter { it.isAfter(today) && !it.isAfter(end) }
                .distinct()
                .map { UpcomingEntry(it, r) }
        }.sortedBy { it.date }
    }
}
