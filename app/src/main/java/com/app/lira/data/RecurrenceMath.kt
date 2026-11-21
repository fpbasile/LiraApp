package com.app.lira.data

import java.time.LocalDate

/** Date maths for recurring items, free of any network code (unit-tested). */
object RecurrenceMath {
    /**
     * The [n]th occurrence after [start], counted from the start (not step by step) so a
     * monthly item on the 31st stays on the last day of each month instead of drifting to the 28th.
     */
    fun nth(start: LocalDate, interval: String, n: Long): LocalDate = when (interval) {
        "daily" -> start.plusDays(n)
        "weekly" -> start.plusWeeks(n)
        "monthly" -> start.plusMonths(n)
        "yearly" -> start.plusYears(n)
        else -> start.plusMonths(n)
    }

    /** Last occurrence date, or null when it repeats forever. */
    fun finalDate(start: LocalDate, interval: String, occurrences: Int?): LocalDate? =
        occurrences?.let { nth(start, interval, (it - 1).coerceAtLeast(0).toLong()) }
}
