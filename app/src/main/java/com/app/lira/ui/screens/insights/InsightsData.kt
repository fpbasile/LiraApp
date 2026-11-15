package com.app.lira.ui.screens.insights

import com.app.lira.data.CurrencyPreference
import com.app.lira.data.model.Recurring
import com.app.lira.data.repository.MonthlyTotal
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.util.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Insights data: a flattened view of transactions plus every number the screen shows.
 * Everything here is computed from real rows — when there isn't enough data for a statement,
 * the function returns null and the card simply doesn't appear.
 */

/** One transaction, parsed into the device's local time. */
data class InsightEntry(
    val id: Int,
    val amount: Double,
    val income: Boolean,
    val dateTime: LocalDateTime,
    val categoryName: String?,
    val description: String?,
    val source: String?,
    val recurringId: Int?,
    val currency: String?
) {
    val date: LocalDate get() = dateTime.toLocalDate()

    /**
     * Trip entries are stored in the trip's currency; they're listed but kept out of home-currency
     * totals so a ¥3,000 dinner doesn't count as €3,000.
     */
    val inHomeCurrency: Boolean get() = currency == null || currency.equals(CurrencyPreference.code, ignoreCase = true)

    val countsAsSpend: Boolean get() = !income && inHomeCurrency
    val countsAsIncome: Boolean get() = income && inHomeCurrency

    val displayCurrency: String get() = currency?.takeIf { it.isNotBlank() } ?: CurrencyPreference.code

    fun title(): String = if (income) {
        source?.takeIf { it.isNotBlank() } ?: description?.takeIf { it.isNotBlank() } ?: "Income"
    } else {
        description?.takeIf { it.isNotBlank() } ?: categoryName ?: "Expense"
    }

    fun subtitle(): String {
        val base = if (income) {
            val desc = description?.takeIf { it.isNotBlank() && !source.isNullOrBlank() }
            listOfNotNull("Income", desc).joinToString(" · ")
        } else {
            val hasDesc = !description.isNullOrBlank()
            when {
                hasDesc -> categoryName ?: UNCATEGORISED
                else -> "Expense"
            }
        }
        val extra = listOfNotNull(
            if (recurringId != null) "Recurring" else null,
            if (!inHomeCurrency) currency?.uppercase() else null
        )
        return (listOf(base) + extra).joinToString(" · ")
    }

    fun timeLabel(): String = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
}

const val UNCATEGORISED = "Uncategorised"

fun TransactionDetail.toInsightEntry(): InsightEntry? {
    val dt = parseLocalDateTime(date) ?: return null
    return InsightEntry(
        id = id,
        amount = amount,
        income = type == TransactionRepository.TYPE_RECEIVED,
        dateTime = dt,
        categoryName = category?.name,
        description = description,
        source = source,
        recurringId = recurring_id,
        currency = currency
    )
}

/** Timestamps come back as offset date-times (usually UTC); show them in the device's zone. */
internal fun parseLocalDateTime(iso: String): LocalDateTime? =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso) }.getOrNull()
        ?: runCatching { LocalDate.parse(iso.take(10)).atStartOfDay() }.getOrNull()

// ───────────────────────────── Formatting helpers ─────────────────────────────

/** Whole-unit money for comparisons and chart labels ("€96"). */
internal fun moneyWhole(value: Double, code: String = CurrencyPreference.code): String =
    Money.format(value.roundToLong().toDouble(), code, trimZeros = true)

internal fun money(value: Double, code: String = CurrencyPreference.code): String = Money.format(value, code)

internal fun monthName(ym: YearMonth, today: LocalDate = LocalDate.now()): String =
    if (ym.year == today.year) ym.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
    else ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

/** Bare month name, used inside sentences ("than this point in October"). */
internal fun monthWord(ym: YearMonth): String = ym.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))

internal fun dayHeading(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))

internal fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

internal fun weekRangeLabel(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month) "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))}"
    else "${shortDate(start)} – ${shortDate(end)}"

internal fun entriesWord(n: Int) = if (n == 1) "1 entry" else "$n entries"

// ───────────────────────────── Month maths ─────────────────────────────

enum class MonthKind { PAST, CURRENT, FUTURE }

fun monthKind(ym: YearMonth, today: LocalDate): MonthKind {
    val now = YearMonth.from(today)
    return when {
        ym < now -> MonthKind.PAST
        ym > now -> MonthKind.FUTURE
        else -> MonthKind.CURRENT
    }
}

/** Last day of [ym] that's included in "so far" totals (today for the current month). */
internal fun cutoffDay(ym: YearMonth, today: LocalDate): Int = when (monthKind(ym, today)) {
    MonthKind.CURRENT -> today.dayOfMonth
    MonthKind.PAST -> ym.lengthOfMonth()
    MonthKind.FUTURE -> 0
}

/** Days of [ym] that are fully over (yesterday and before for the current month). */
internal fun completedDates(ym: YearMonth, today: LocalDate): List<LocalDate> {
    val last = when (monthKind(ym, today)) {
        MonthKind.PAST -> ym.lengthOfMonth()
        MonthKind.CURRENT -> today.dayOfMonth - 1
        MonthKind.FUTURE -> 0
    }
    return (1..last).map { ym.atDay(it) }
}

internal fun List<InsightEntry>.spendUpTo(day: Int): Double =
    filter { it.countsAsSpend && it.date.dayOfMonth <= day }.sumOf { it.amount }

/** Spend per category name, largest first. */
fun categoryTotals(entries: List<InsightEntry>, upToDay: Int = 31): List<Pair<String, Double>> =
    entries.filter { it.countsAsSpend && it.date.dayOfMonth <= upToDay }
        .groupBy { it.categoryName ?: UNCATEGORISED }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
        .toList()
        .filter { it.second > 0 }
        .sortedByDescending { it.second }

fun dailySpend(entries: List<InsightEntry>): Map<LocalDate, Double> =
    entries.filter { it.countsAsSpend }.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } }

fun dailyIncome(entries: List<InsightEntry>): Map<LocalDate, Double> =
    entries.filter { it.countsAsIncome }.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } }

/** Cumulative spend per day of [ym], up to [throughDay] (inclusive). Index 0 = day 1. */
fun cumulative(entries: List<InsightEntry>, ym: YearMonth, throughDay: Int): List<Double> {
    val perDay = DoubleArray(ym.lengthOfMonth())
    entries.filter { it.countsAsSpend }.forEach { e ->
        val d = e.date.dayOfMonth
        if (d in 1..perDay.size) perDay[d - 1] += e.amount
    }
    var acc = 0.0
    return (0 until throughDay.coerceIn(0, perDay.size)).map { acc += perDay[it]; acc }
}

/** Average spend per weekday (Mon..Sun) over completed days only; null where no such day passed yet. */
fun weekdayAverages(entries: List<InsightEntry>, ym: YearMonth, today: LocalDate): List<Double?> {
    val dates = completedDates(ym, today)
    val spend = dailySpend(entries)
    return DayOfWeek.entries.map { dow ->
        val days = dates.filter { it.dayOfWeek == dow }
        if (days.isEmpty()) null else days.sumOf { spend[it] ?: 0.0 } / days.size
    }
}

// ───────────────────────────── Hero line ─────────────────────────────

data class HeroComparison(val diff: Double, val less: Boolean, val tail: String)

/** "€96 less than this point in October" — null without a previous month to compare with. */
fun heroComparison(month: YearMonth, today: LocalDate, cur: List<InsightEntry>, prev: List<InsightEntry>?): HeroComparison? {
    if (prev == null) return null
    val kind = monthKind(month, today)
    if (kind == MonthKind.FUTURE) return null
    val prevMonth = month.minusMonths(1)
    val prevAll = prev.filter { it.countsAsSpend }
    if (prevAll.isEmpty()) return null
    val day = cutoffDay(month, today)
    val curTotal = cur.spendUpTo(day)
    val prevTotal = if (kind == MonthKind.CURRENT) prev.spendUpTo(minOf(day, prevMonth.lengthOfMonth())) else prev.spendUpTo(31)
    val diff = curTotal - prevTotal
    if (abs(diff) < 0.5) return null
    val tail = if (kind == MonthKind.CURRENT) "than this point in ${monthWord(prevMonth)}" else "than ${monthWord(prevMonth)}"
    return HeroComparison(abs(diff), diff < 0, tail)
}

// ───────────────────────────── Written insights ─────────────────────────────

enum class InsightIcon { WEEKEND, UP, DOWN, STREAK, RECURRING }

data class WrittenInsight(val key: String, val icon: InsightIcon, val headline: String, val body: String, val positive: Boolean = false)

data class Streak(val days: Int, val since: LocalDate)

/** Consecutive days with at least one entry, ending today (or yesterday if today isn't logged yet). */
fun loggingStreak(loggedDates: Set<LocalDate>, today: LocalDate): Streak? {
    var day = if (today in loggedDates) today else today.minusDays(1)
    if (day !in loggedDates) return null
    var count = 0
    var since = day
    while (day in loggedDates && count < 3660) {
        since = day
        count++
        day = day.minusDays(1)
    }
    return Streak(count, since)
}

private fun ratioWords(r: Double): String {
    val rounded = (r * 2).roundToInt() / 2.0
    val n = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    return when {
        r < rounded - 0.05 -> "almost $n×"
        r > rounded + 0.05 -> "over $n×"
        else -> "$n×"
    }
}

fun buildInsights(
    month: YearMonth,
    today: LocalDate,
    cur: List<InsightEntry>,
    prev: List<InsightEntry>?,
    streak: Streak?,
    upcoming: List<Pair<LocalDate, Recurring>>,
    code: String = CurrencyPreference.code
): List<WrittenInsight> {
    val kind = monthKind(month, today)
    if (kind == MonthKind.FUTURE) return emptyList()
    val out = mutableListOf<WrittenInsight>()
    val periodWord = if (kind == MonthKind.CURRENT) "this month" else "in ${monthWord(month)}"

    // 1 · Weekends vs weekdays (completed days only).
    run<Unit> {
        val dates = completedDates(month, today)
        val weekend = dates.filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
        val weekdays = dates - weekend.toSet()
        if (weekend.size < 2 || weekdays.size < 5) return@run
        val spend = dailySpend(cur)
        val weAvg = weekend.sumOf { spend[it] ?: 0.0 } / weekend.size
        val wdAvg = weekdays.sumOf { spend[it] ?: 0.0 } / weekdays.size
        if (weAvg <= 0 || wdAvg <= 0) return@run
        val r = weAvg / wdAvg
        val body = "${moneyWhole(weAvg, code)} a day on Saturdays and Sundays, ${moneyWhole(wdAvg, code)} on weekdays $periodWord."
        if (r >= 1.3) {
            out.add(WrittenInsight("weekend", InsightIcon.WEEKEND, "Weekends cost you ${ratioWords(r)} your weekdays", body))
        } else if (r <= 1 / 1.3) {
            out.add(WrittenInsight("weekend", InsightIcon.WEEKEND, "Weekdays cost you ${ratioWords(1 / r)} your weekends", body))
        }
    }

    // 2 · Category up / down against last month at the same point.
    run<Unit> {
        if (prev == null) return@run
        val day = cutoffDay(month, today)
        val prevMonth = month.minusMonths(1)
        val prevDay = if (kind == MonthKind.CURRENT) minOf(day, prevMonth.lengthOfMonth()) else 31
        val curCats = categoryTotals(cur, day).toMap()
        val prevCats = categoryTotals(prev, prevDay).toMap()
        val curTotal = curCats.values.sum()
        val prevTotal = prevCats.values.sum()
        if (curTotal <= 0 || prevTotal <= 0) return@run
        if (cur.count { it.countsAsSpend && it.date.dayOfMonth <= day } < 3) return@run
        val minDiff = 0.03 * maxOf(curTotal, prevTotal)
        val changes = curCats.mapNotNull { (name, now) ->
            val before = prevCats[name] ?: return@mapNotNull null
            if (before < 0.05 * prevTotal) return@mapNotNull null
            val diff = now - before
            val pct = diff / before * 100
            if (abs(pct) < 10 || abs(diff) < minDiff) null else Triple(name, now, before)
        }
        val curTail = if (kind == MonthKind.CURRENT) "so far" else "in ${monthWord(month)}"
        val prevTail = if (kind == MonthKind.CURRENT) "at this point in ${monthWord(prevMonth)}" else "in ${monthWord(prevMonth)}"
        fun insight(t: Triple<String, Double, Double>, up: Boolean): WrittenInsight {
            val pct = (abs(t.second - t.third) / t.third * 100).roundToInt()
            return WrittenInsight(
                key = "cat_${if (up) "up" else "down"}",
                icon = if (up) InsightIcon.UP else InsightIcon.DOWN,
                headline = "${t.first} is ${if (up) "up" else "down"} $pct%",
                body = "${money(t.second, code)} $curTail, against ${money(t.third, code)} $prevTail.",
                positive = !up
            )
        }
        changes.filter { it.second > it.third }.maxByOrNull { it.second - it.third }?.let { out.add(insight(it, up = true)) }
        changes.filter { it.second < it.third }.maxByOrNull { it.third - it.second }?.let { out.add(insight(it, up = false)) }
    }

    // 3 · Logging streak (current month only — it's about now).
    if (kind == MonthKind.CURRENT && streak != null && streak.days >= 3) {
        val since = streak.since.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
        out += WrittenInsight(
            "streak", InsightIcon.STREAK, "${streak.days} days logged in a row",
            "Every day since $since. Nice rhythm.", positive = true
        )
    }

    // 4 · Recurring payments still to come this month.
    if (kind == MonthKind.CURRENT) {
        val payments = upcoming.filter { (d, r) -> d.isAfter(today) && r.type == TransactionRepository.TYPE_PAYED }
        if (payments.isNotEmpty()) {
            val total = payments.sumOf { it.second.amount }
            val names = payments.sortedBy { it.first }.take(3).joinToString(", ") { (d, r) -> "${r.name} on ${shortDate(d)}" }
            val more = if (payments.size > 3) ", and ${payments.size - 3} more" else ""
            out += WrittenInsight(
                "recurring", InsightIcon.RECURRING,
                "${money(total, code)} in recurring payments still to come",
                "$names$more."
            )
        }
    }
    return out
}

/** Not-yet-posted recurring occurrences in [ym], by date. */
fun recurringDue(recurring: List<Recurring>, ym: YearMonth): Map<LocalDate, List<Recurring>> {
    val map = mutableMapOf<LocalDate, MutableList<Recurring>>()
    recurring.forEach { r -> RecurringRepository.occurrencesInMonth(r, ym).forEach { map.getOrPut(it) { mutableListOf() }.add(r) } }
    return map
}

// ───────────────────────────── Weekly recap ─────────────────────────────

data class WeeklyRecap(
    val start: LocalDate,
    val end: LocalDate,
    val spent: Double,
    val limit: Double?,
    val perDay: List<Double>, // Mon..Sun
    val topCategory: Pair<String, Double>?,
    val logged: List<Boolean>, // Mon..Sun
    val weekendShare: Int?
)

/** Monday of the last fully completed week before [today]. */
fun lastWeekStart(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

/** Recap of the last completed week; null when nothing at all was logged that week. */
fun buildWeeklyRecap(entries: List<InsightEntry>, today: LocalDate, weeklyLimit: Double?): WeeklyRecap? {
    val start = lastWeekStart(today)
    val end = start.plusDays(6)
    val week = entries.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    if (week.isEmpty()) return null
    val spendEntries = week.filter { it.countsAsSpend }
    val perDay = (0..6).map { i -> spendEntries.filter { it.date == start.plusDays(i.toLong()) }.sumOf { it.amount } }
    val spent = perDay.sum()
    val top = categoryTotals(spendEntries).firstOrNull()
    val logged = (0..6).map { i -> week.any { it.date == start.plusDays(i.toLong()) } }
    val share = if (spent > 0) ((perDay[5] + perDay[6]) / spent * 100).roundToInt() else null
    return WeeklyRecap(start, end, spent, weeklyLimit?.takeIf { it > 0 }, perDay, top, logged, share)
}

// ───────────────────────────── Six-month view ─────────────────────────────

data class NetLine(val month: YearMonth, val received: Double, val net: Double, val vsPrevious: Double?)

fun netLine(totals: List<MonthlyTotal>, month: YearMonth): NetLine? {
    val idx = totals.indexOfFirst { it.yearMonth == month }.takeIf { it >= 0 } ?: return null
    val t = totals[idx]
    if (t.spent == 0.0 && t.received == 0.0) return null
    val net = t.received - t.spent
    val prev = totals.getOrNull(idx - 1)?.takeIf { it.spent != 0.0 || it.received != 0.0 }
    return NetLine(month, t.received, net, prev?.let { net - (it.received - it.spent) })
}
