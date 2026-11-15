package com.app.lira.data

import com.app.lira.data.model.Recurring
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** An expense that repeats about monthly but isn't set up as a recurring item yet. */
data class SubscriptionCandidate(
    /** Stable key used for dismissals ("d:netflix|EUR" or "c:12|1349|EUR"). */
    val key: String,
    /** Original casing of the most recent description, or the category name. */
    val name: String,
    /** Most recent amount. */
    val amount: Double,
    val currency: String,
    val categoryId: Int?,
    /** Typical day of the month it's paid on (median). */
    val dayOfMonth: Int,
    /** Last occurrence + 1 month. */
    val nextExpected: LocalDate,
    val lastSeen: LocalDate,
    /** Occurrences in the detected monthly run. */
    val count: Int
)

/**
 * Finds likely monthly subscriptions in plain (non-recurring) expenses. Pure: no I/O, no Android.
 *
 * Rules:
 *  - expenses only (type "payed"), not already produced by a recurring item (recurring_id == null);
 *  - grouped by trimmed, lowercased description + currency; rows with a blank description are
 *    grouped by category + exact amount + currency instead;
 *  - inside a group only amounts within ±5% of the group median count; one row per day;
 *  - the newest chain of occurrences whose consecutive gaps are 25–35 days (one outlier gap
 *    allowed) must have ≥3 occurrences in ≥3 distinct calendar months, with at least two good gaps;
 *  - the latest occurrence must be within the last 45 days;
 *  - candidates matching an existing recurring item (same name ignoring case, or same category with
 *    an amount within 5%) or whose key is in [dismissed] are dropped.
 */
object SubscriptionDetector {
    const val MIN_OCCURRENCES = 3
    const val MIN_GAP_DAYS = 25L
    const val MAX_GAP_DAYS = 35L
    const val MAX_DAYS_SINCE_LAST = 45L
    const val AMOUNT_TOLERANCE = 0.05

    private const val TYPE_PAYED = "payed"

    fun detect(
        txns: List<HomeTxn>,
        recurring: List<Recurring>,
        homeCurrency: String,
        today: LocalDate,
        categoryNames: Map<Int, String> = emptyMap(),
        dismissed: Set<String> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<SubscriptionCandidate> {
        data class Row(val txn: HomeTxn, val date: LocalDate, val currency: String)

        val groups = HashMap<String, MutableList<Row>>()
        for (t in txns) {
            if (t.type != TYPE_PAYED || t.recurring_id != null || t.amount <= 0.0) continue
            val date = t.local(zone)?.toLocalDate() ?: continue
            val currency = (t.currency?.takeIf { it.isNotBlank() } ?: homeCurrency).uppercase()
            val desc = t.description?.trim()?.lowercase().orEmpty()
            val key = when {
                desc.isNotEmpty() -> "d:$desc|$currency"
                t.category_id != null -> "c:${t.category_id}|${Math.round(t.amount * 100)}|$currency"
                else -> continue
            }
            groups.getOrPut(key) { mutableListOf() }.add(Row(t, date, currency))
        }

        val out = mutableListOf<SubscriptionCandidate>()
        for ((key, rows) in groups) {
            if (key in dismissed || rows.size < MIN_OCCURRENCES) continue
            val median = median(rows.map { it.txn.amount })
            val kept = rows.filter { abs(it.txn.amount - median) <= median * AMOUNT_TOLERANCE }
                .sortedWith(compareBy<Row> { it.date }.thenBy { it.txn.date })
                // One row per day: keep the latest entry of that day.
                .associateBy { it.date }.values.sortedBy { it.date }
            if (kept.size < MIN_OCCURRENCES) continue

            val last = kept.last()
            if (ChronoUnit.DAYS.between(last.date, today) > MAX_DAYS_SINCE_LAST) continue

            // Walk back from the newest occurrence while gaps look monthly (one outlier allowed).
            val chain = mutableListOf(last)
            var outliers = 0
            var goodGaps = 0
            for (i in kept.size - 2 downTo 0) {
                val gap = ChronoUnit.DAYS.between(kept[i].date, chain.last().date)
                if (gap in MIN_GAP_DAYS..MAX_GAP_DAYS) {
                    goodGaps++
                } else {
                    if (outliers >= 1) break
                    outliers++
                }
                chain.add(kept[i])
            }
            // A trailing outlier at the old end doesn't add evidence: drop it.
            if (outliers == 1 && chain.size >= 2) {
                val oldestGap = ChronoUnit.DAYS.between(chain[chain.size - 1].date, chain[chain.size - 2].date)
                if (oldestGap !in MIN_GAP_DAYS..MAX_GAP_DAYS) chain.removeAt(chain.size - 1)
            }
            if (chain.size < MIN_OCCURRENCES || goodGaps < 2) continue
            if (chain.map { it.date.year * 12 + it.date.monthValue }.distinct().size < MIN_OCCURRENCES) continue

            val amount = last.txn.amount
            val categoryId = last.txn.category_id
            val name = last.txn.description?.trim()?.takeIf { it.isNotEmpty() }
                ?: categoryId?.let { categoryNames[it] }
                ?: "Subscription"

            val matchesExisting = recurring.any { r ->
                r.name.trim().equals(name, ignoreCase = true) ||
                    (categoryId != null && r.category_id == categoryId && abs(r.amount - amount) <= amount * AMOUNT_TOLERANCE)
            }
            if (matchesExisting) continue

            val day = median(chain.map { it.date.dayOfMonth.toDouble() }).toInt()
            out += SubscriptionCandidate(
                key = key,
                name = name,
                amount = amount,
                currency = last.currency,
                categoryId = categoryId,
                dayOfMonth = day,
                nextExpected = last.date.plusMonths(1),
                lastSeen = last.date,
                count = chain.size
            )
        }
        return out.sortedWith(compareByDescending<SubscriptionCandidate> { it.count }.thenByDescending { it.lastSeen })
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }
}
