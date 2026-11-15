package com.app.lira.ui.screens.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import com.app.lira.ui.components.lira.EmptyState
import com.app.lira.ui.components.lira.LiraSheet
import com.app.lira.ui.components.lira.LiraTextField
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

private fun groupLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEEE, d MMM" else "d MMM yyyy", Locale.getDefault()))
}

/**
 * Search and browse every entry (replaces the old Transactions screen): free-text search over
 * description, category and payer, an All / Income / Expenses filter, grouped by day.
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
    val today = remember { LocalDate.now() }

    val filtered = remember(entries, query, typeFilter) {
        val q = query.trim()
        entries.orEmpty().filter { e ->
            val typeOk = when (typeFilter) {
                1 -> e.income
                2 -> !e.income
                else -> true
            }
            typeOk && (q.isEmpty() || listOfNotNull(e.title(), e.description, e.categoryName, e.source).any { it.contains(q, ignoreCase = true) })
        }.sortedByDescending { it.dateTime }
    }
    val groups = remember(filtered) { filtered.groupBy { it.date } }

    LiraSheet(onDismiss = onDismiss, title = "All entries") {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiraTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search entries",
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
                        if (query.isBlank() && typeFilter == 0) "Nothing logged yet" else "No entries match",
                        if (query.isBlank()) null else "Try another word, or clear the search.",
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
