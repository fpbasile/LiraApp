package com.app.lira.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.CategoryLook
import com.app.lira.data.model.Recurring
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.components.lira.TimelineRow
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.CategoryPalette
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Insights card: surface/1, radius 24, padding 20, 16dp between blocks. */
@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    gap: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LiraColors.Surface1).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}

/** Card title + quiet subtitle, as used by every Insights card. */
@Composable
internal fun CardHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = LiraType.BodyL.copy(fontWeight = FontWeight.SemiBold, lineHeight = 21.sp))
            if (subtitle != null) Text(subtitle, style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
        }
        trailing?.invoke()
    }
}

/**
 * Month grid. Each day shows its spend as a small bar (scaled to the month's biggest day),
 * a green dot for income and a ring for recurring entries. Tap a day to see its entries.
 */
@Composable
fun MonthCalendarCard(
    month: YearMonth,
    today: LocalDate,
    spend: Map<LocalDate, Double>,
    income: Map<LocalDate, Double>,
    recurringDays: Map<LocalDate, Boolean>, // date → has an income recurring entry
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxSpend = spend.filterKeys { YearMonth.from(it) == month }.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1 // Monday-first
    val cells: List<LocalDate?> = buildList {
        repeat(leading) { add(null) }
        for (d in 1..month.lengthOfMonth()) add(month.atDay(d))
        while (size % 7 != 0) add(null)
    }
    Panel(modifier) {
        CardHeader("${monthWord(month)} at a glance", "Tap a day to see its entries")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DayOfWeek.entries.forEach {
                    Text(
                        it.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                        style = LiraType.Caption.copy(letterSpacing = 0.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { date ->
                        if (date == null) Spacer(Modifier.weight(1f).height(58.dp))
                        else DayCell(
                            date = date,
                            isToday = date == today,
                            isFuture = date.isAfter(today),
                            isSelected = date == selected,
                            spendFraction = ((spend[date] ?: 0.0) / maxSpend).toFloat(),
                            hasIncome = (income[date] ?: 0.0) > 0,
                            recurring = recurringDays[date],
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem("Spend") { Box(Modifier.size(10.dp, 8.dp).clip(RoundedCornerShape(2.dp)).background(LiraColors.TextSecondary)) }
            LegendItem("Income") { Box(Modifier.size(6.dp).clip(CircleShape).background(LiraColors.Lichen)) }
            LegendItem("Recurring") { RecurringRing(LiraColors.TextSecondary) }
        }
    }
}

@Composable
private fun RecurringRing(color: Color) {
    Box(Modifier.size(6.dp).border(1.2.dp, color, CircleShape))
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isFuture: Boolean,
    isSelected: Boolean,
    spendFraction: Float,
    hasIncome: Boolean,
    recurring: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val label = buildString {
        append(date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())))
        if (spendFraction > 0) append(", spending")
        if (hasIncome) append(", income")
        if (recurring != null) append(", recurring")
    }
    Column(
        modifier.height(58.dp).clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) LiraColors.Surface2 else Color.Transparent)
            .liraClickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; selected = isSelected }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (isToday) LiraColors.TextPrimary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = LiraType.AmountXs.copy(
                    fontSize = 13.sp, lineHeight = 16.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isToday -> LiraColors.Base
                        isFuture -> LiraColors.TextDisabled
                        else -> LiraColors.TextPrimary
                    }
                )
            )
        }
        Box(Modifier.height(14.dp), contentAlignment = Alignment.BottomCenter) {
            if (spendFraction > 0f) Box(
                Modifier.width(16.dp).height((14.dp * spendFraction).coerceAtLeast(2.dp))
                    .clip(RoundedCornerShape(2.dp)).background(LiraColors.TextSecondary)
            )
        }
        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasIncome) Box(Modifier.size(6.dp).clip(CircleShape).background(LiraColors.Lichen))
            if (recurring != null) RecurringRing(if (recurring) LiraColors.Lichen else LiraColors.TextSecondary)
        }
    }
}

private fun recurringRangeLabel(entry: Recurring, date: LocalDate): String {
    fun fmt(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    val start = runCatching { LocalDate.parse(entry.start_date) }.getOrNull()
    val startLabel = start?.let { fmt(it) } ?: entry.start_date
    val end = RecurringRepository.finalOccurrenceDate(entry)
    return if (end != null) {
        val index = RecurringRepository.occurrenceIndex(entry, date)
        val ordinal = if (index != null) "$index of ${entry.occurrences} · " else ""
        "$ordinal$startLabel → ${fmt(end)}"
    } else {
        "Repeats ${entry.interval} · since $startLabel"
    }
}

/**
 * The selected day: summary line (entries · spent · limit words) then its entries, newest last,
 * and any recurring entries still due that day (dashed, grey). Rows open the editor.
 */
@Composable
fun DayEntriesCard(
    date: LocalDate,
    today: LocalDate,
    entries: List<InsightEntry>,
    due: List<Recurring>,
    dailyLimit: Double?,
    code: String,
    lookOf: (categoryName: String?) -> CategoryLook,
    lookOfRecurring: (Recurring) -> CategoryLook,
    onEntry: (Int) -> Unit,
    onRecurring: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spent = entries.filter { it.countsAsSpend }.sumOf { it.amount }
    val received = entries.filter { it.countsAsIncome }.sumOf { it.amount }
    val subtitle = buildAnnotatedString {
        val parts = mutableListOf<String>()
        if (entries.isNotEmpty()) parts += entriesWord(entries.size)
        if (spent > 0) parts += money(spent, code)
        if (received > 0) parts += "+${money(received, code)} in"
        if (due.isNotEmpty()) parts += "${due.size} due"
        if (parts.isEmpty()) parts += if (date.isAfter(today)) "Nothing scheduled" else "Nothing logged"
        append(parts.joinToString(" · "))
        if (spent > 0 && dailyLimit != null && dailyLimit > 0 && !date.isAfter(today)) {
            append(" · ")
            val left = dailyLimit - spent
            if (left >= 0) append("${money(left, code)} under the limit")
            else withStyle(SpanStyle(color = LiraColors.OverLimit, fontWeight = FontWeight.SemiBold)) {
                append("${money(-left, code)} over the limit")
            }
        }
    }
    Panel(modifier, padding = PaddingValues(vertical = 20.dp)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(dayHeading(date), style = LiraType.BodyL.copy(fontWeight = FontWeight.SemiBold, lineHeight = 21.sp))
            Text(subtitle, style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
        }
        if (entries.isNotEmpty() || due.isNotEmpty()) {
            Column {
                entries.sortedBy { it.dateTime }.forEach { e ->
                    val look = if (e.income) CategoryLook(LiraColors.Lichen, Icons.Outlined.Payments) else lookOf(e.categoryName)
                    TimelineRow(
                        time = e.timeLabel(),
                        title = e.title(),
                        subtitle = e.subtitle(),
                        amount = Money.format(e.amount, e.displayCurrency),
                        icon = look.icon,
                        color = look.color,
                        income = e.income,
                        onClick = { onEntry(e.id) }
                    )
                }
                due.forEach { r ->
                    val isIncome = r.type == TransactionRepository.TYPE_RECEIVED
                    val look = if (isIncome) CategoryLook(CategoryPalette.Other, Icons.Outlined.Payments) else lookOfRecurring(r)
                    TimelineRow(
                        time = "Due",
                        title = r.name,
                        subtitle = recurringRangeLabel(r, date),
                        amount = money(r.amount, code),
                        icon = look.icon,
                        color = look.color,
                        income = isIncome,
                        scheduled = true,
                        onClick = r.id?.let { id -> { onRecurring(id) } }
                    )
                }
            }
        }
    }
}
