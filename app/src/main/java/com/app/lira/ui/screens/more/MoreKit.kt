package com.app.lira.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.CategoryStyle
import com.app.lira.data.repository.CategoryRow
import com.app.lira.ui.components.lira.CategoryChip
import com.app.lira.ui.components.lira.GhostButton
import com.app.lira.ui.components.lira.IconCircleButton
import com.app.lira.ui.components.lira.LiraSheet
import com.app.lira.ui.components.lira.PrimaryButton
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/*
 * Small building blocks shared by the More-area screens (More, Categories, Recurring, Edit entry)
 * and the auth/onboarding flow. Built on the shared Lira components.
 */

/** Grouped list card (surface/1, radius 20, 12dp side margin). */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LiraColors.Surface1),
        content = content
    )
}

/** Settings row inside a [GroupCard]: icon · title · value · chevron. */
@Composable
internal fun NavRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    chevron: Boolean = true,
    titleColor: Color = LiraColors.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).liraClickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(20.dp))
        Text(title, style = LiraType.BodyM.copy(color = titleColor), modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = LiraType.BodyM.copy(fontSize = 14.sp, color = LiraColors.TextSecondary, fontFeatureSettings = "tnum"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(20.dp))
    }
}

/** Header for full-screen editors: close on the left, centred title (+ subtitle), optional action on the right. */
@Composable
internal fun ModalTopBar(
    title: String,
    onClose: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircleButton(Icons.Outlined.Close, "Close", onClose, background = Color.Transparent)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = LiraType.BodyL.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            if (subtitle != null) Text(subtitle, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), maxLines = 1)
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

/** Quiet message line. Errors aren't red (red is reserved for over-limit) — an icon and words carry it. */
@Composable
internal fun MessageLine(text: String, modifier: Modifier = Modifier, positive: Boolean = false) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LiraColors.Surface2).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Outlined.Info, null,
            tint = if (positive) LiraColors.Lichen else LiraColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(text, style = LiraType.BodyS.copy(color = LiraColors.TextPrimary))
    }
}

/** Confirmation sheet for destructive actions. */
@Composable
internal fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    loading: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    LiraSheet(onDismiss = { if (!loading) onDismiss() }, title = title) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(body, style = LiraType.BodyM.copy(color = LiraColors.TextSecondary))
            if (error != null) MessageLine(error)
            PrimaryButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), loading = loading)
            GhostButton("Cancel", { if (!loading) onDismiss() }, Modifier.fillMaxWidth())
        }
    }
}

/** Horizontally scrolling category chips (quick-add style). Tapping the selected chip again clears it when [allowNone]. */
@Composable
internal fun CategoryChipsRow(
    categories: List<CategoryRow>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false
) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id }) { cat ->
            val look = CategoryStyle.look(cat.id, cat.name)
            CategoryChip(
                label = cat.name,
                icon = look.icon,
                color = look.color,
                selected = cat.id == selectedId,
                onClick = { onSelect(if (allowNone && cat.id == selectedId) null else cat.id) }
            )
        }
    }
}

/** Material date picker in Lira colours. Works in UTC millis like the M3 picker expects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiraDatePickerDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            GhostButton("Done", {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }, color = LiraColors.TextPrimary)
        },
        dismissButton = { GhostButton("Cancel", onDismiss) }
    ) {
        DatePicker(state = state)
    }
}

/** Centered helper text under an amount. */
@Composable
internal fun AmountCaption(text: String) {
    Text(text, style = LiraType.BodyS, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
}

// ───────────────────────────── Date helpers ─────────────────────────────

internal object Dates {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    /** "Today", "Tomorrow", "Yesterday", "Mon 16 Nov", or "3 Mar 2027" when not this year. */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date == today.minusDays(1) -> "Yesterday"
        date.year != today.year -> date.format(dayMonthYear)
        else -> date.format(weekdayDayMonth)
    }

    fun short(date: LocalDate): String = date.format(dayMonth)

    fun long(date: LocalDate): String = date.format(dayMonthYear)

    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault())

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}

/** Raw keypad string for an existing amount ("34.8" → "34.80" for EUR, "1200" for JPY). */
internal fun rawFromAmount(amount: Double, minorDigits: Int): String {
    if (amount <= 0.0) return ""
    val s = String.format(Locale.US, "%.${minorDigits}f", amount)
    return if (minorDigits > 0 && s.endsWith("." + "0".repeat(minorDigits))) s.substringBefore('.') else s
}

/** Settings row with a switch; the whole row toggles. */
@Composable
internal fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).liraClickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LiraType.BodyM)
            if (subtitle != null) Text(subtitle, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = LiraColors.Base,
                checkedTrackColor = LiraColors.Lichen,
                checkedBorderColor = LiraColors.Lichen,
                uncheckedThumbColor = LiraColors.TextTertiary,
                uncheckedTrackColor = LiraColors.Surface3,
                uncheckedBorderColor = LiraColors.Surface4
            )
        )
    }
}
