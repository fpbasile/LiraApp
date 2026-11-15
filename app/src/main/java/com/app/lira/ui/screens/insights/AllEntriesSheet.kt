package com.app.lira.ui.screens.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.CategoryLook
import com.app.lira.data.CurrencyPreference
import com.app.lira.ui.components.lira.CategoryChip
import com.app.lira.ui.components.lira.EmptyState
import com.app.lira.ui.components.lira.LiraSheet
import com.app.lira.ui.components.lira.LiraTextField
import com.app.lira.ui.components.lira.OptionChip
import com.app.lira.ui.components.lira.SectionLabel
import com.app.lira.ui.components.lira.SegmentedControl
import com.app.lira.ui.components.lira.TimelineRow
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

private fun groupLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEEE, d MMM" else "d MMM yyyy", Locale.getDefault()))
}

/** Period filter options, in chip order. */
private enum class Period(val label: String) {
    ALL("All time"), MONTH("This month"), QUARTER("Last 3 months"), YEAR("This year");

    /** First day included, or null for no lower bound. "Last 3 months" is rolling (today minus 3 months). */
    fun start(today: LocalDate): LocalDate? = when (this) {
        ALL -> null
        MONTH -> today.withDayOfMonth(1)
        QUARTER -> today.minusMonths(3)
        YEAR -> today.withDayOfYear(1)
    }
}

/**
 * How a search query is matched against an entry's amount (in addition to the usual text match).
 *
 * The query counts as an amount when it is a plain number with at most one decimal separator:
 * "12", "12.5", "12,50" ("." and "," are both accepted; a leading currency symbol and spaces are
 * ignored, so "€ 12,50" works too).
 * - **Whole number** ("12"): matches every amount whose whole part is that number, i.e. 12.00–12.99.
 *   People tend to remember "about 12", not the cents.
 * - **With decimals** ("12.5", "12,50"): matches amounts within ±1% of the value (never tighter than
 *   ±0.005, so "0.50" still matches exactly 0.50), tolerating rounding and small mis-remembering.
 */
private class AmountQuery(private val value: Double, private val whole: Boolean) {
    fun matches(amount: Double): Boolean {
        val a = abs(amount)
        return if (whole) floor(a) == value else abs(a - value) <= maxOf(value * 0.01, 0.005)
    }

    companion object {
        private val pattern = Regex("^\\d+([.,]\\d+)?$")

        fun parse(raw: String): AmountQuery? {
            val q = raw.trim().trimStart { !it.isDigit() && !it.isLetter() }.replace(" ", "")
            if (!pattern.matches(q)) return null
            val v = q.replace(',', '.').toDoubleOrNull() ?: return null
            return AmountQuery(v, whole = q.none { it == '.' || it == ',' })
        }
    }
}

/** Filtered rows plus the summary numbers for them (sums are home-currency only). */
private class FilterResult(
    val rows: List<InsightEntry>,
    val spent: Double,
    val received: Double,
    val hasSpent: Boolean,
    val hasReceived: Boolean,
    val foreignCount: Int
)

/**
 * Search and browse every entry (replaces the old Transactions screen): free-text search over
 * description, category and payer (plus amount, see [AmountQuery]), an All / Income / Expenses
 * filter, a period filter and a multi-select category filter, grouped by day.
 *
 * The category filter only applies to expenses: it's hidden and ignored while "Income" is selected,
 * and with "All" selected, picking categories narrows the list to those expenses.
 * While any search or filter is active, a summary line shows the count and the totals in the home
 * currency; entries in other currencies stay in the list but are left out of the sums.
 */
@Composable
fun AllEntriesSheet(
    entries: List<InsightEntry>?,
    error: String?,
    truncated: Boolean,
    initialQuery: String,
    lookOf: (categoryName: String?) -> CategoryLook,
    onRetry: () -> Unit,
    onOpen: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var typeFilter by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf(Period.ALL) }
    var selectedCats by remember { mutableStateOf(emptySet<String>()) }
    val today = remember { LocalDate.now() }
    val homeCode = CurrencyPreference.code

    // Categories used by expenses, most used first (ties alphabetical, so the order is stable).
    val categories = remember(entries) {
        entries.orEmpty().asSequence().filter { !it.income }
            .groupingBy { it.categoryName ?: UNCATEGORISED }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }
    val categoriesActive = typeFilter != 1 && selectedCats.isNotEmpty()
    val filterActive = query.isNotBlank() || typeFilter != 0 || period != Period.ALL || categoriesActive

    val result = remember(entries, query, typeFilter, period, selectedCats, homeCode) {
        val q = query.trim()
        val amountQuery = AmountQuery.parse(q)
        val start = period.start(today)
        val cats = if (typeFilter != 1) selectedCats else emptySet()
        val rows = entries.orEmpty().filter { e ->
            val typeOk = when (typeFilter) {
                1 -> e.income
                2 -> !e.income
                else -> true
            }
            typeOk &&
                (start == null || !e.date.isBefore(start)) &&
                (cats.isEmpty() || (!e.income && (e.categoryName ?: UNCATEGORISED) in cats)) &&
                (q.isEmpty() || amountQuery?.matches(e.amount) == true ||
                    listOfNotNull(e.title(), e.description, e.categoryName, e.source).any { it.contains(q, ignoreCase = true) })
        }.sortedByDescending { it.dateTime }
        var spent = 0.0
        var received = 0.0
        var hasSpent = false
        var hasReceived = false
        var foreign = 0
        rows.forEach { e ->
            when {
                !e.inHomeCurrency -> foreign++
                e.income -> { received += e.amount; hasReceived = true }
                else -> { spent += e.amount; hasSpent = true }
            }
        }
        FilterResult(rows, spent, received, hasSpent, hasReceived, foreign)
    }
    val filtered = result.rows
    val groups = remember(filtered) { filtered.groupBy { it.date } }

    LiraSheet(onDismiss = onDismiss, title = "All entries") {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiraTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search entries or amounts",
                    leadingIcon = Icons.Outlined.Search,
                    trailing = if (query.isNotEmpty()) {
                        {
                            Box(
                                Modifier.size(32.dp).liraClickable { query = "" }.semantics { contentDescription = "Clear search" },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Outlined.Close, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                        }
                    } else null
                )
                SegmentedControl(listOf("All", "Income", "Expenses"), typeFilter, { typeFilter = it })
            }
            LazyRow(
                Modifier.padding(top = 4.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Period.entries, key = { it.name }) { p ->
                    OptionChip(p.label, null, edited = p == period, onClick = { period = p })
                }
            }
            if (typeFilter != 1 && categories.size > 1) {
                LazyRow(
                    Modifier.padding(top = 10.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedCats.isNotEmpty()) item(key = "clear") {
                        OptionChip("Clear", Icons.Outlined.Close, edited = false, onClick = { selectedCats = emptySet() })
                    }
                    items(categories, key = { it }) { name ->
                        val look = lookOf(name.takeIf { it != UNCATEGORISED })
                        val selected = name in selectedCats
                        CategoryChip(
                            label = name,
                            icon = look.icon,
                            color = look.color,
                            selected = selected,
                            onClick = { selectedCats = if (selected) selectedCats - name else selectedCats + name }
                        )
                    }
                }
            }
            if (filterActive && entries != null) {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp)) {
                    val parts = buildList {
                        add(entriesWord(filtered.size))
                        if (result.hasSpent) add("spent ${money(result.spent, homeCode)}")
                        if (result.hasReceived) add("received ${money(result.received, homeCode)}")
                    }
                    Text(parts.joinToString(" · "), style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
                    if (result.foreignCount > 0) {
                        Text(
                            "${entriesWord(result.foreignCount)} in other currencies not included in the totals.",
                            style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary)
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    entries == null && error == null -> CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(28.dp), color = LiraColors.TextSecondary, strokeWidth = 2.dp
                    )
                    entries == null -> EmptyState(
                        Icons.Outlined.ReceiptLong, "Couldn't load your entries", error,
                        Modifier.align(Alignment.Center), actionLabel = "Try again", onAction = onRetry
                    )
                    filtered.isEmpty() -> EmptyState(
                        Icons.Outlined.Search,
                        if (!filterActive) "Nothing logged yet" else "No entries match",
                        if (!filterActive) null else "Try another search, or loosen the filters.",
                        Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth()) {
                        groups.forEach { (date, rows) ->
                            item(key = "h_$date") {
                                SectionLabel(groupLabel(date, today), Modifier.padding(top = 8.dp))
                            }
                            items(rows, key = { it.id }) { e ->
                                val look = if (e.income) CategoryLook(LiraColors.Lichen, Icons.Outlined.Payments) else lookOf(e.categoryName)
                                TimelineRow(
                                    time = e.timeLabel(),
                                    title = e.title(),
                                    subtitle = e.subtitle(),
                                    amount = Money.format(e.amount, e.displayCurrency),
                                    icon = look.icon,
                                    color = look.color,
                                    income = e.income,
                                    onClick = { onOpen(e.id) }
                                )
                            }
                        }
                        if (truncated) item(key = "more") {
                            Text(
                                "Showing your latest ${entries.orEmpty().size} entries.",
                                style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}
