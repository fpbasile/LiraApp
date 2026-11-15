package com.app.lira.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.CategoryStyle
import com.app.lira.data.HomeStats
import com.app.lira.data.HomeTxn
import com.app.lira.data.UpcomingEntry
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.components.lira.AnimatedMoneyText
import com.app.lira.ui.components.lira.BudgetRing
import com.app.lira.ui.components.lira.CategoryIconBadge
import com.app.lira.ui.components.lira.EmptyState
import com.app.lira.ui.components.lira.LiraSheet
import com.app.lira.ui.components.lira.ProgressLine
import com.app.lira.ui.components.lira.SegmentedControl
import com.app.lira.ui.components.lira.UndoSnackbar
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraMotion
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
private val monthName = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private data class PeriodView(val label: String, val word: String, val spent: Double, val limit: Double?)

/** Today sheet: Day/Week/Month ring, period cards, timeline (tap = edit, swipe left = delete), coming up. */
@Composable
internal fun TodaySheet(
    model: HomeModel,
    onEdit: (Int) -> Unit,
    onLogExpense: () -> Unit,
    onDismiss: () -> Unit
) {
    var period by remember { mutableIntStateOf(0) }
    var deleted by remember { mutableStateOf<HomeTxn?>(null) }
    LaunchedEffect(deleted) { if (deleted != null) { delay(LiraMotion.UNDO_WINDOW_MS); deleted = null } }

    val home = model.homeCode
    val today = LocalDate.now()
    val views = listOf(
        PeriodView("Today", "today", model.todaySpent, model.effectiveDailyLimit.takeIf { it > 0 }),
        PeriodView("This week", "this week", model.weekSpent, model.weekLimit),
        PeriodView(today.format(monthName), "this month", model.monthSpent, model.monthLimit)
    )
    val current = views[period]
    val rangeStart = when (period) { 0 -> today; 1 -> HomeStats.weekStart(today); else -> today.withDayOfMonth(1) }
    val rangeEnd = when (period) { 0 -> today.plusDays(1); 1 -> rangeStart.plusDays(7); else -> rangeStart.plusMonths(1) }
    val entries = model.visibleTxns.filter { t ->
        t.local()?.toLocalDate()?.let { !it.isBefore(rangeStart) && it.isBefore(rangeEnd) } == true
    }.sortedByDescending { it.local() }
    val upcoming = remember(model.recurring) { HomeStats.upcoming(model.recurring) }

    LiraSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Today", style = LiraType.TitleM.copy(fontSize = 22.sp))
                            Text(today.format(longDate), style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
                        }
                        SegmentedControl(listOf("Day", "Week", "Month"), period, { period = it })
                    }
                }
                item { BigRing(current, home) }
                item {
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        views.filterIndexed { i, _ -> i != period }.forEach { v -> PeriodCard(v, home, Modifier.weight(1f)) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Entries", style = LiraType.Label.copy(fontWeight = FontWeight.SemiBold, color = LiraColors.TextSecondary), modifier = Modifier.weight(1f))
                        if (entries.isNotEmpty()) Text(
                            "${entries.size} · ${Money.format(current.spent, home)} spent",
                            style = LiraType.BodyS.copy(color = LiraColors.TextTertiary, fontFeatureSettings = "tnum")
                        )
                    }
                }
                if (entries.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.EditNote,
                            title = if (period == 0) "Nothing logged today" else "Nothing logged ${current.word}",
                            body = current.limit?.let { "Your ${Money.format(it, home, trimZeros = true)} is untouched." },
                            actionLabel = "Log an expense",
                            onAction = onLogExpense,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                } else {
                    items(entries, key = { it.id }) { t ->
                        SwipeToDelete(onDelete = {
                            model.delete(t)
                            deleted = t
                        }) {
                            EntryRow(
                                t, model, home,
                                timeLabel = t.local()?.let { when (period) { 0 -> it.format(hm); 1 -> it.format(weekday); else -> it.format(dayMonth) } } ?: "",
                                onClick = { model.realId(t.id)?.let(onEdit) }
                            )
                        }
                    }
                }
                if (upcoming.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                            Text("Coming up this week", style = LiraType.Label.copy(fontWeight = FontWeight.SemiBold, color = LiraColors.TextSecondary), modifier = Modifier.weight(1f))
                            Text("${upcoming.size} scheduled", style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
                        }
                    }
                    items(upcoming, key = { "u${it.recurring.id}-${it.date}" }) { u -> UpcomingRow(u, model, home) }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            AnimatedVisibility(
                visible = deleted != null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 }
            ) {
                val d = deleted
                if (d != null) UndoSnackbar(
                    title = "Entry deleted",
                    subtitle = entryTitle(d, model),
                    onUndo = { model.restore(d); deleted = null },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BigRing(v: PeriodView, home: String) {
    val limit = v.limit
    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        BudgetRing(if (limit != null && limit > 0) (v.spent / limit).toFloat() else 0f, size = 168.dp, stroke = 12.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (limit != null) {
                    val left = limit - v.spent
                    val over = left < -0.004
                    AnimatedMoneyText(kotlin.math.abs(left), home, LiraType.AmountL.copy(color = if (over) LiraColors.OverLimit else LiraColors.Lichen), trimZeros = true)
                    Text(if (over) "over ${v.word}" else "left ${v.word}", style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
                    Text(
                        "${Money.format(v.spent, home, trimZeros = true)} of ${Money.format(limit, home, trimZeros = true)}",
                        style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary, fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    AnimatedMoneyText(v.spent, home, LiraType.AmountL, trimZeros = true)
                    Text("spent ${v.word}", style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
                    Text("No limit set", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PeriodCard(v: PeriodView, home: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(LiraColors.Surface2).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(v.label, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
        val limit = v.limit
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (limit != null) {
                val left = limit - v.spent
                Text(Money.format(kotlin.math.abs(left), home), style = LiraType.AmountS.copy(fontSize = 20.sp, color = if (left < 0) LiraColors.OverLimit else LiraColors.TextPrimary), maxLines = 1)
                Text(if (left < 0) "over" else "left", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextSecondary))
            } else {
                Text(Money.format(v.spent, home), style = LiraType.AmountS.copy(fontSize = 20.sp), maxLines = 1)
                Text("spent", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextSecondary))
            }
        }
        if (limit != null && limit > 0) ProgressLine((v.spent / limit).toFloat())
    }
}

private fun entryTitle(t: HomeTxn, model: HomeModel): String {
    val cat = model.category(t.category_id)?.name
    return when {
        !t.description.isNullOrBlank() -> t.description
        t.isIncome -> t.source?.let { if (isSalary(it)) it else "From $it" } ?: "Income"
        else -> cat ?: "Expense"
    }
}

private fun entrySubtitle(t: HomeTxn, model: HomeModel): String {
    val cat = model.category(t.category_id)?.name
    val parts = mutableListOf<String>()
    if (t.isIncome) {
        if (!t.description.isNullOrBlank() && !t.source.isNullOrBlank()) parts += "From ${t.source}" else parts += "Income"
    } else {
        parts += if (!t.description.isNullOrBlank()) cat ?: "Expense" else "Expense"
    }
    if (t.recurring_id != null) parts += "Recurring"
    return parts.joinToString(" · ")
}

@Composable
private fun EntryRow(t: HomeTxn, model: HomeModel, home: String, timeLabel: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(LiraColors.Surface1).liraClickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(timeLabel, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary, fontFeatureSettings = "tnum"), modifier = Modifier.width(44.dp), maxLines = 1)
        if (t.isIncome) PayerBadge(t.source ?: "+", size = 36.dp)
        else {
            val cat = model.category(t.category_id)
            val look = CategoryStyle.look(cat?.id, cat?.name)
            CategoryIconBadge(look.icon, look.color, 36.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entryTitle(t, model), style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entrySubtitle(t, model), style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (t.isIncome) Text("+", style = LiraType.AmountXs.copy(color = LiraColors.Lichen))
            Text(Money.format(t.amount, t.currency ?: home), style = LiraType.AmountXs)
        }
    }
}

@Composable
private fun UpcomingRow(u: UpcomingEntry, model: HomeModel, home: String) {
    val r = u.recurring
    val income = r.type == TransactionRepository.TYPE_RECEIVED
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(u.date.format(weekday), style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), modifier = Modifier.width(44.dp))
        Box(Modifier.size(34.dp).border(1.dp, LiraColors.TextDisabled, CircleShape), contentAlignment = Alignment.Center) {
            val icon = if (income) Icons.Outlined.Payments else model.category(r.category_id).let { CategoryStyle.look(it?.id, it?.name).icon }
            Icon(icon, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.name, style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium, color = LiraColors.TextTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Scheduled · ${r.interval}", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), maxLines = 1)
        }
        Text((if (income) "+" else "") + Money.format(r.amount, home), style = LiraType.AmountXs.copy(color = LiraColors.TextTertiary))
    }
}

/** Drag left to reveal Delete; past 40% of the width deletes. Neutral grey — never red. */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val reveal = with(LocalDensity.current) { 84.dp.toPx() }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.coerceAtLeast(1) }) {
        Box(Modifier.matchParentSize().background(LiraColors.Surface4)) {
            Column(
                Modifier.align(Alignment.CenterEnd).width(84.dp).fillMaxHeight()
                    .liraClickable { scope.launch { offset.animateTo(-width.toFloat(), tween(160)); onDelete() } }
                    .semantics { contentDescription = "Delete entry" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(20.dp))
                Text("Delete", style = LiraType.Caption.copy(color = LiraColors.TextPrimary, letterSpacing = 0.sp))
            }
        }
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(-width.toFloat(), 0f)) } },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when {
                            -offset.value > width * 0.4f -> { offset.animateTo(-width.toFloat(), tween(160)); onDelete() }
                            -offset.value > reveal * 0.6f -> offset.animateTo(-reveal, tween(LiraMotion.STANDARD))
                            else -> offset.animateTo(0f, tween(LiraMotion.STANDARD))
                        }
                    }
                )
        ) { content() }
    }
}
