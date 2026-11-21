package com.app.lira.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.CashFlow
import com.app.lira.data.IncomeKind
import com.app.lira.data.IncomeKinds
import com.app.lira.data.model.Recurring
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.screens.more.MessageLine
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A stable income being typed — saved as a "received" recurring item. */
data class IncomeDraft(
    val name: String,
    val amount: Double,
    /** "monthly" or "weekly". */
    val interval: String,
    /** Day of month (1–31) for monthly, ISO weekday (1 = Monday) for weekly. */
    val day: Int
) {
    /** First payday strictly after today — today's pay is assumed to be in the balance already. */
    fun startDate(today: LocalDate = LocalDate.now()): LocalDate =
        if (interval == "weekly") {
            var d = today.plusDays(1)
            while (d.dayOfWeek.value != day) d = d.plusDays(1)
            d
        } else CashFlow.nextDayOfMonth(day, today)

    companion object {
        fun from(r: Recurring): IncomeDraft {
            val start = runCatching { LocalDate.parse(r.start_date) }.getOrDefault(LocalDate.now())
            val weekly = r.interval == "weekly"
            return IncomeDraft(r.name, r.amount, if (weekly) "weekly" else "monthly", if (weekly) start.dayOfWeek.value else start.dayOfMonth)
        }
    }
}

/** "on the 5th" / "every Friday". */
fun scheduleLabel(interval: String, day: Int): String =
    if (interval == "weekly") "Every ${DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
    else if (day >= 31) "Monthly · last day" else "Monthly · on the ${ordinal(day)}"

fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

/** Accepts "1 234,50", "1234.5", "1,234.50". */
fun parseAmount(text: String): Double? {
    var t = text.trim().replace(" ", "").replace(" ", "").replace(" ", "")
    if (t.isEmpty()) return null
    val lastComma = t.lastIndexOf(','); val lastDot = t.lastIndexOf('.')
    t = when {
        lastComma > lastDot -> t.replace(".", "").replace(',', '.')
        else -> t.replace(",", "")
    }
    return t.toDoubleOrNull()?.takeIf { it >= 0 }
}

/** Round icon for an income kind. */
@Composable
fun KindBadge(kind: IncomeKind, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(kind.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) { Icon(kind.icon, null, tint = kind.color, modifier = Modifier.size(size * 0.5f)) }
}

/**
 * Add / edit a stable income: kind, name, amount, how often and which day.
 * Used by onboarding (drafts kept until "Continue") and by the Wallet (saved at once).
 */
@Composable
fun IncomeSheet(
    home: String,
    initial: IncomeDraft?,
    presetKind: IncomeKind? = null,
    saving: Boolean = false,
    error: String? = null,
    onSave: (IncomeDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: presetKind?.takeIf { it != IncomeKinds.Other }?.label ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.let { Money.format(it, home, trimZeros = true).filter { c -> c.isDigit() || c == '.' || c == ',' } } ?: "") }
    var weekly by remember { mutableStateOf(initial?.interval == "weekly") }
    var day by remember { mutableIntStateOf(initial?.day ?: if (presetKind == IncomeKinds.Caf || presetKind == IncomeKinds.Apl) 5 else 1) }
    var localError by remember { mutableStateOf<String?>(null) }
    val kind = IncomeKinds.of(name)

    LiraSheet(onDismiss = onDismiss, title = if (initial == null) "Add income" else "Edit income") {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Kind picks — set the name in one tap.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IncomeKinds.picks.forEach { k ->
                    val sel = kind == k && name.isNotBlank()
                    KindPill(k, sel) {
                        name = if (k == IncomeKinds.Other) "" else k.label
                        if (k == IncomeKinds.Caf || k == IncomeKinds.Apl) { weekly = false; day = 5 }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KindBadge(kind, 48.dp)
                LiraTextField(
                    value = name, onValueChange = { name = it.take(40); localError = null },
                    placeholder = "e.g. Salary, CAF, APL…", label = "Name", modifier = Modifier.weight(1f)
                )
            }

            LiraTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == ' ' }.take(12); localError = null },
                placeholder = "0", label = "Amount (${Money.symbol(home)})",
                keyboardType = KeyboardType.Decimal
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("When it lands", style = LiraType.Label.copy(color = LiraColors.TextSecondary))
                SegmentedControl(
                    options = listOf("Monthly", "Weekly"),
                    selected = if (weekly) 1 else 0,
                    onSelect = { i ->
                        val w = i == 1
                        if (w != weekly) { weekly = w; day = if (w) 5 else 1 }
                    },
                    height = 36.dp, background = LiraColors.Surface2
                )
                if (weekly) WeekdayPicker(day) { day = it } else MonthDayPicker(day) { day = it }
                val preview = IncomeDraft(name, 0.0, if (weekly) "weekly" else "monthly", day).startDate()
                Text(
                    "Next one: ${preview.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${preview.dayOfMonth} ${preview.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" +
                        " · logged for you that day",
                    style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary)
                )
            }

            (localError ?: error)?.let { MessageLine(it) }

            PrimaryButton(if (initial == null) "Add" else "Save", {
                val amount = parseAmount(amountText)
                when {
                    name.isBlank() -> localError = "Give it a name."
                    amount == null || amount <= 0 -> localError = "Enter the amount you receive."
                    else -> onSave(IncomeDraft(name.trim(), amount, if (weekly) "weekly" else "monthly", day))
                }
            }, Modifier.fillMaxWidth(), loading = saving)
            if (onDelete != null) GhostButton("Remove this income", onDelete, Modifier.fillMaxWidth(), color = LiraColors.TextSecondary)
        }
    }
}

@Composable
fun KindPill(kind: IncomeKind, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(CircleShape)
            .background(if (selected) kind.color.copy(alpha = 0.16f) else LiraColors.Surface2)
            .then(if (selected) Modifier.border(1.dp, kind.color.copy(alpha = 0.6f), CircleShape) else Modifier)
            .liraClickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(kind.icon, null, tint = kind.color, modifier = Modifier.size(18.dp))
        Text(kind.label, style = LiraType.Label.copy(fontSize = 14.sp, color = if (selected) LiraColors.TextPrimary else LiraColors.TextSecondary))
    }
}

/** 1–31 as a calendar-like grid; 31 means "last day of the month". */
@Composable
private fun MonthDayPicker(selected: Int, onPick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LiraColors.Surface2).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        (1..31).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    val sel = d == selected
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(CircleShape)
                            .background(if (sel) LiraColors.TextPrimary else Color.Transparent)
                            .liraClickable { onPick(d) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            d.toString(),
                            style = LiraType.BodyM.copy(
                                fontFamily = NumberFont, fontSize = 15.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sel) LiraColors.Base else LiraColors.TextSecondary
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (selected >= 29) {
            Text(
                if (selected == 31) "Last day of every month" else "Short months use their last day",
                style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun WeekdayPicker(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { d ->
            val sel = d == selected
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) LiraColors.TextPrimary else LiraColors.Surface2)
                    .liraClickable { onPick(d) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2),
                    style = LiraType.Label.copy(color = if (sel) LiraColors.Base else LiraColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/** Amount field sheet for "what's on your account right now". */
@Composable
fun BalanceSheet(home: String, current: Double?, saving: Boolean, onSave: (Double) -> Unit, onClear: (() -> Unit)?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current?.let { String.format(Locale.US, "%.2f", it).removeSuffix(".00") } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var negative by remember { mutableStateOf((current ?: 0.0) < 0) }
    LiraSheet(onDismiss = onDismiss, title = "Bank balance") {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Open your bank app once and type what it shows. From then on Lira moves it with every entry — no waiting for card payments to clear.",
                style = LiraType.BodyM.copy(color = LiraColors.TextSecondary)
            )
            LiraTextField(
                value = text.removePrefix("-"),
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == ' ' }.take(14); error = null },
                placeholder = "0", label = "On your account now (${Money.symbol(home)})",
                keyboardType = KeyboardType.Decimal
            )
            SegmentedControl(options = listOf("In credit", "Overdrawn"), selected = if (negative) 1 else 0, onSelect = { negative = it == 1 }, height = 36.dp, background = LiraColors.Surface2)
            error?.let { MessageLine(it) }
            PrimaryButton("Save balance", {
                val v = parseAmount(text.removePrefix("-"))
                if (v == null) error = "Enter the amount your bank shows." else onSave(if (negative) -v else v)
            }, Modifier.fillMaxWidth(), loading = saving)
            if (onClear != null) GhostButton("Stop tracking my balance", onClear, Modifier.fillMaxWidth())
        }
    }
}
