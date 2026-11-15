package com.app.lira.ui.screens.travel

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.app.lira.data.model.Trip
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.TripRepository
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

// ───────────────────────────── Rates ─────────────────────────────

/** A rate table: 1 [base] = rates[code] code. */
data class RateTable(val base: String, val rates: Map<String, Double>, val date: String, val fetchedAt: Long) {
    fun rate(to: String): Double? = if (to.equals(base, ignoreCase = true)) 1.0 else rates[to.uppercase()]

    /** Converts [amount] in [from] into [to] using this table (works for any pair via the base). */
    fun convert(amount: Double, from: String, to: String): Double? {
        val f = rate(from) ?: return null
        val t = rate(to) ?: return null
        if (f == 0.0) return null
        return amount / f * t
    }
}

/**
 * Whole-table exchange rates from the keyless currency-api CDN (same source as
 * [com.app.lira.data.repository.CurrencyConverterRepository]), cached so the converter and the
 * trip view can convert many pairs locally. Supports historical dates ("rate on each day").
 */
object TravelRates {
    private val http by lazy { HttpClient(Android) }
    private val json = Json { ignoreUnknownKeys = true }
    private val latest = ConcurrentHashMap<String, RateTable>()
    private val dated = ConcurrentHashMap<String, RateTable>()
    private const val FRESH_MS = 10 * 60 * 1000L

    /** Cached latest table for [base] if we have one (may be stale). */
    fun cached(base: String): RateTable? = latest[base.uppercase()]

    suspend fun latest(base: String): Result<RateTable> {
        val key = base.uppercase()
        latest[key]?.let { if (System.currentTimeMillis() - it.fetchedAt < FRESH_MS) return Result.success(it) }
        val fetched = fetch(key, "latest")
        fetched.onSuccess { latest[key] = it }
        // Offline: fall back to a stale table rather than nothing.
        return if (fetched.isFailure) latest[key]?.let { Result.success(it) } ?: fetched else fetched
    }

    /** Rates published for [date]; falls back to the latest table (e.g. today's file isn't out yet). */
    suspend fun onDate(base: String, date: LocalDate): Result<RateTable> {
        val key = base.uppercase()
        if (!date.isBefore(LocalDate.now())) return latest(key)
        val cacheKey = "$key|$date"
        dated[cacheKey]?.let { return Result.success(it) }
        val fetched = fetch(key, date.toString())
        fetched.onSuccess { dated[cacheKey] = it }
        return if (fetched.isSuccess) fetched else latest(key)
    }

    private suspend fun fetch(base: String, version: String): Result<RateTable> = withContext(Dispatchers.IO) {
        val lower = base.lowercase()
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$version/v1/currencies/$lower.json",
            "https://$version.currency-api.pages.dev/v1/currencies/$lower.json"
        )
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val response = http.get(url)
                if (!response.status.isSuccess()) { lastError = Exception("HTTP ${response.status.value}"); continue }
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val date = root["date"]?.jsonPrimitive?.content ?: version
                val table = (root[lower] as? JsonObject) ?: run { lastError = Exception("No rates for $base"); null } ?: continue
                val rates = table.mapNotNull { (k, v) -> runCatching { v.jsonPrimitive.doubleOrNull }.getOrNull()?.let { k.uppercase() to it } }.toMap()
                return@withContext Result.success(RateTable(base, rates, date, System.currentTimeMillis()))
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(Exception("Couldn't load exchange rates. Check your connection.", lastError))
    }
}

// ───────────────────────────── Local trip extras ─────────────────────────────

/**
 * Trip details the `trips` table has no columns for (dates, budget, home default), kept
 * on-device per trip id. Budget is in the home currency.
 */
data class TripExtras(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val budget: Double? = null,
    val defaultLocal: Boolean = true,
    val lastCurrency: String? = null
)

object TripLocalStore {
    private const val PREFS = "lira_trip_extras"
    private var prefs: SharedPreferences? = null
    private val cache = mutableStateMapOf<Int, TripExtras>()

    /** Idempotent; screens call it with LocalContext so it works without app-level init. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (k, v) ->
            val id = k.toIntOrNull() ?: return@forEach
            (v as? String)?.let { cache[id] = decode(it) }
        }
    }

    fun get(tripId: Int?): TripExtras = tripId?.let { cache[it] } ?: TripExtras()

    fun set(tripId: Int, extras: TripExtras) {
        cache[tripId] = extras
        prefs?.edit()?.putString(tripId.toString(), encode(extras))?.apply()
    }

    fun remove(tripId: Int) {
        cache.remove(tripId)
        prefs?.edit()?.remove(tripId.toString())?.apply()
    }

    /** Home calls this when the user picks a currency during a trip, so it becomes the default. */
    fun rememberPicked(tripId: Int, code: String) = set(tripId, get(tripId).copy(lastCurrency = code.uppercase()))

    /**
     * Currency Home should default to while [trip] is active: the last one picked on this trip,
     * else its first currency. Null when the user turned "Default to … on Home" off.
     */
    fun homeDefaultCurrency(trip: Trip): String? {
        val extras = get(trip.id)
        if (!extras.defaultLocal) return null
        return extras.lastCurrency?.takeIf { it in trip.currencies } ?: trip.currencies.firstOrNull()
    }

    private fun encode(e: TripExtras) =
        listOf(e.start?.toString().orEmpty(), e.end?.toString().orEmpty(), e.budget?.toString().orEmpty(), e.defaultLocal.toString(), e.lastCurrency.orEmpty())
            .joinToString("|")

    private fun decode(s: String): TripExtras {
        val p = s.split('|')
        fun at(i: Int) = p.getOrNull(i)?.takeIf { it.isNotBlank() }
        return TripExtras(
            start = at(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            end = at(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            budget = at(2)?.toDoubleOrNull(),
            defaultLocal = at(3)?.toBooleanStrictOrNull() ?: true,
            lastCurrency = at(4)
        )
    }
}

// ───────────────────────────── Trip helpers ─────────────────────────────

@Serializable
private data class TripUpdate(val name: String, val currencies: List<String>)

/** Renames a trip / changes its currencies (TripRepository has no update yet). */
suspend fun TripRepository.updateTrip(id: Int, name: String, currencies: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
    if (com.app.lira.data.local.DataMode.isLocal) {
        com.app.lira.data.local.LocalDb.updateTrip(id) { it.copy(name = name, currencies = currencies) }
        return@withContext Result.success(Unit)
    }
    try {
        SupabaseClient.client.postgrest["trips"].update(TripUpdate(name, currencies)) { filter { eq("id", id) } }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(
            Exception(
                when (e) {
                    is PostgrestRestException -> e.error
                    is HttpRequestException, is IOException -> "Can't reach the server. Check your internet connection."
                    else -> "Something went wrong. Please try again."
                }
            )
        )
    }
}

object TripMode {
    /** First day of the trip: the chosen start date, else the day it was created. */
    fun startDate(trip: Trip): LocalDate? =
        TripLocalStore.get(trip.id).start ?: trip.created_at?.let { iso ->
            runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        }

    /** (day n, of total) — total is null without an end date. Null before the trip starts. */
    fun dayOf(trip: Trip, today: LocalDate = LocalDate.now()): Pair<Int, Int?>? {
        val start = startDate(trip) ?: return null
        if (today.isBefore(start)) return null
        val end = TripLocalStore.get(trip.id).end
        val n = ChronoUnit.DAYS.between(start, today).toInt() + 1
        val total = end?.let { ChronoUnit.DAYS.between(start, it).toInt() + 1 }
        return (if (total != null) n.coerceAtMost(total) else n) to total
    }

    /** Days left including today, or null without an end date. */
    fun daysToGo(trip: Trip, today: LocalDate = LocalDate.now()): Int? {
        val end = TripLocalStore.get(trip.id).end ?: return null
        val from = startDate(trip)?.takeIf { it.isAfter(today) } ?: today
        return (ChronoUnit.DAYS.between(from, end).toInt() + 1).coerceAtLeast(0)
    }

    /** Ends the active trip if its end date has passed ("ends on its own"). Returns true if it ended one. */
    suspend fun autoEndExpired(trips: List<Trip>, today: LocalDate = LocalDate.now()): Boolean {
        val active = trips.firstOrNull { it.is_active } ?: return false
        val end = TripLocalStore.get(active.id).end ?: return false
        if (!today.isAfter(end)) return false
        return TripRepository.setActiveTrip(null).isSuccess
    }
}

/** Short date range like "3 – 14 Apr" / "28 Mar – 4 Apr" / "3 Apr" (start only). */
fun formatTripDates(start: LocalDate?, end: LocalDate?, long: Boolean = false): String? {
    if (start == null && end == null) return null
    val month = if (long) "MMMM" else "MMM"
    fun fmt(d: LocalDate, withMonth: Boolean, withYear: Boolean): String {
        val pattern = buildString {
            append("d")
            if (withMonth) append(" $month")
            if (withYear) append(" yyyy")
        }
        return d.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
    }
    val thisYear = LocalDate.now().year
    return when {
        start != null && end != null -> {
            val sameMonth = start.year == end.year && start.month == end.month
            val showYear = end.year != thisYear
            if (sameMonth) "${fmt(start, false, false)} – ${fmt(end, true, showYear)}"
            else "${fmt(start, true, start.year != end.year)} – ${fmt(end, true, showYear)}"
        }
        start != null -> "From ${fmt(start, true, start.year != thisYear)}"
        else -> "Until ${fmt(end!!, true, end.year != thisYear)}"
    }
}

// ───────────────────────────── Trip totals ─────────────────────────────

data class CurrencyTotal(
    val code: String,
    val original: Double,
    /** In the home currency; null when no rate could be found. */
    val converted: Double?,
    val days: Int,
    val beforeLeaving: Boolean
)

data class TripTotals(
    val byCurrency: List<CurrencyTotal>,
    /** Category name (null = uncategorised) → home-currency total, largest first. */
    val byCategory: List<Pair<String?, Double>>,
    val total: Double,
    val missingRates: Boolean,
    /** Entries whose converted value is wildly above the rest — usually the wrong currency. */
    val suspicious: List<Pair<TransactionDetail, Double>> = emptyList()
)

/**
 * Sums a trip's spending into the home currency, converting each entry at the rate published
 * on the day it was logged. Entries without a currency are in the home currency.
 */
suspend fun computeTripTotals(txns: List<TransactionDetail>, home: String, tripStart: LocalDate?): TripTotals {
    val spending = txns.filter { it.type == TransactionRepository.TYPE_PAYED }
    val zone = ZoneId.systemDefault()
    fun dayOf(t: TransactionDetail): LocalDate = runCatching {
        OffsetDateTime.parse(t.date).atZoneSameInstant(zone).toLocalDate()
    }.getOrElse { runCatching { java.time.LocalDateTime.parse(t.date).toLocalDate() }.getOrDefault(LocalDate.now()) }

    val homeCode = home.uppercase()
    val tables = mutableMapOf<LocalDate, RateTable?>()
    var missing = false
    val converted = spending.map { t ->
        val code = (t.currency ?: homeCode).uppercase()
        val day = dayOf(t)
        val inHome = if (code == homeCode) t.amount else {
            if (day !in tables) tables[day] = TravelRates.onDate(homeCode, day).getOrNull()
            val table = tables[day]
            table?.rate(code)?.takeIf { it > 0 }?.let { t.amount / it }
        }
        if (inHome == null) missing = true
        Triple(t, code to day, inHome)
    }

    val byCurrency = converted.groupBy { it.second.first }.map { (code, rows) ->
        val days = rows.map { it.second.second }.distinct()
        CurrencyTotal(
            code = code,
            original = rows.sumOf { it.first.amount },
            converted = if (rows.any { it.third == null }) null else rows.sumOf { it.third ?: 0.0 },
            days = days.size,
            beforeLeaving = tripStart != null && days.all { it.isBefore(tripStart) }
        )
    }.sortedByDescending { it.converted ?: 0.0 }

    val byCategory = converted.filter { it.third != null }
        .groupBy { it.first.category?.name }
        .map { (name, rows) -> name to rows.sumOf { it.third ?: 0.0 } }
        .sortedByDescending { it.second }

    // Outliers: a converted value 10× the typical entry and over 500 in the home currency.
    val values = converted.mapNotNull { it.third }.sorted()
    val median = if (values.isEmpty()) 0.0 else values[values.size / 2]
    val suspicious = converted.filter { (_, _, v) -> v != null && v > maxOf(500.0, median * 10) }
        .map { it.first to (it.third ?: 0.0) }
        .sortedByDescending { it.second }

    return TripTotals(byCurrency, byCategory, converted.sumOf { it.third ?: 0.0 }, missing, suspicious)
}

// ───────────────────────────── Hand-off to Home ─────────────────────────────

/** An amount the converter wants logged; Home consumes it (pre-fills keypad + currency). */
data class PendingLog(val amountRaw: String, val currency: String)

object TravelHandoff {
    var pendingLog by mutableStateOf<PendingLog?>(null)

    /** Returns and clears the pending amount. */
    fun consume(): PendingLog? = pendingLog.also { pendingLog = null }
}
