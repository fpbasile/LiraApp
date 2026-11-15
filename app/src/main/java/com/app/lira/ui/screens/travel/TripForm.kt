package com.app.lira.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.model.Trip
import com.app.lira.ui.components.lira.GhostButton
import com.app.lira.ui.components.lira.LiraSheet
import com.app.lira.ui.components.lira.LiraTextField
import com.app.lira.ui.components.lira.PrimaryButton
import com.app.lira.ui.components.lira.SecondaryButton
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Editable trip fields shared by Start-a-trip and the trip's edit sheet. */
class TripFormState(
    name: String = "",
    currencies: List<String> = emptyList(),
    start: LocalDate? = LocalDate.now(),
    end: LocalDate? = null,
    budgetRaw: String = "",
    defaultLocal: Boolean = true
) {
    var name by mutableStateOf(name)
    var currencies by mutableStateOf(currencies)
    var start by mutableStateOf(start)
    var end by mutableStateOf(end)
    var budgetRaw by mutableStateOf(budgetRaw)
    var defaultLocal by mutableStateOf(defaultLocal)

    val budget: Double? get() = budgetRaw.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    /** Error to show, or null when the form can be saved. */
    fun validate(): String? = when {
        name.isBlank() -> "Give the trip a name"
        currencies.isEmpty() -> "Add at least one currency"
        else -> null
    }

    fun extras(previous: TripExtras = TripExtras()) =
        previous.copy(start = start, end = end, budget = budget, defaultLocal = defaultLocal)

    companion object {
        fun from(trip: Trip, extras: TripExtras) = TripFormState(
            name = trip.name,
            currencies = trip.currencies,
            start = extras.start ?: TripMode.startDate(trip),
            end = extras.end,
            budgetRaw = extras.budget?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "",
            defaultLocal = extras.defaultLocal
        )
    }
}

/** Name · currencies · dates · budget · home default. No submit button — callers add their own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripForm(state: TripFormState, homeCode: String, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    var pickingDates by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        LiraTextField(
            value = state.name,
            onValueChange = { state.name = it.take(60) },
            placeholder = "e.g. Japan & Seoul",
            label = "Name"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Currencies")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.currencies.forEach { code ->
                    CurrencyTag(code, onRemove = { state.currencies = state.currencies - code })
                }
                Row(
                    Modifier.height(36.dp).clip(CircleShape).border(1.dp, Color(0x24FFFFFF), CircleShape)
                        .liraClickable { picking = true }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Text("Add", style = LiraType.Label.copy(color = LiraColors.TextSecondary))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Dates")
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(LiraColors.Surface2)
                        .liraClickable { pickingDates = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.CalendarToday, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(18.dp))
                    val label = formatTripDates(state.start, state.end)
                    Text(
                        label ?: "Pick dates",
                        style = LiraType.BodyM.copy(fontSize = 14.sp, color = if (label != null) LiraColors.TextPrimary else LiraColors.TextTertiary),
                        maxLines = 1
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.padding(bottom = 6.dp)) {
                    FieldLabel("Budget")
                    Text(" · optional", style = LiraType.Label.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                }
                LiraTextField(
                    value = state.budgetRaw,
                    onValueChange = { v -> state.budgetRaw = sanitizeAmount(v) },
                    placeholder = Money.symbol(homeCode) + "0",
                    keyboardType = KeyboardType.Decimal,
                    trailing = {
                        if (state.budgetRaw.isNotEmpty()) {
                            Text(Money.symbol(homeCode), style = LiraType.BodyM.copy(color = LiraColors.TextTertiary, fontFamily = NumberFont))
                        }
                    }
                )
            }
        }

        val first = state.currencies.firstOrNull()
        if (first != null) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Default to $first on Home", style = LiraType.BodyM)
                    val second = state.currencies.getOrNull(1)
                    Text(
                        if (second != null) "Switches to $second when you pick it once" else "Home's keypad starts in $first",
                        style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary)
                    )
                }
                Switch(
                    checked = state.defaultLocal,
                    onCheckedChange = { state.defaultLocal = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LiraColors.Base,
                        checkedTrackColor = LiraColors.TextPrimary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = LiraColors.TextTertiary,
                        uncheckedTrackColor = LiraColors.Surface3,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
        }
    }

    if (picking) {
        CurrencyPickerSheet(
            selected = state.currencies.lastOrNull() ?: "",
            onSelect = { code -> if (code !in state.currencies) state.currencies = state.currencies + code },
            onDismiss = { picking = false }
        )
    }
    if (pickingDates) {
        TripDateRangeDialog(
            start = state.start,
            end = state.end,
            onConfirm = { s, e -> state.start = s; state.end = e; pickingDates = false },
            onDismiss = { pickingDates = false }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = LiraType.Label.copy(fontSize = 12.sp, color = LiraColors.TextSecondary))
}

/** "¥ JPY ×" tag. */
@Composable
fun CurrencyTag(code: String, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier.height(36.dp).clip(CircleShape).background(LiraColors.Surface2)
            .padding(start = 12.dp, end = if (onRemove != null) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val flag = com.app.lira.util.Flags.forCurrency(code)
        if (flag != null) Text(flag, style = LiraType.BodyL.copy(fontSize = 18.sp))
        else CurrencyCatalog.symbolOrNull(code)?.let {
            Text(it, style = LiraType.Label.copy(fontFamily = NumberFont, color = LiraColors.TextSecondary))
        }
        Text(code, style = LiraType.Label.copy(fontWeight = FontWeight.SemiBold))
        if (onRemove != null) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).liraClickable(onClick = onRemove)
                    .semantics { contentDescription = "Remove $code" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Close, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(14.dp)) }
        }
    }
}

/** Keeps digits and one decimal separator. */
internal fun sanitizeAmount(v: String): String {
    val sb = StringBuilder()
    var dot = false
    v.forEach { c ->
        when {
            c.isDigit() -> sb.append(c)
            (c == '.' || c == ',') && !dot -> { dot = true; sb.append('.') }
        }
    }
    return sb.toString().take(12)
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDateRangeDialog(
    start: LocalDate?,
    end: LocalDate?,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.toUtcMillis(),
        initialSelectedEndDateMillis = end?.toUtcMillis()
    )
    val colors = DatePickerDefaults.colors(
        containerColor = LiraColors.Surface1,
        selectedDayContainerColor = LiraColors.TextPrimary,
        selectedDayContentColor = LiraColors.Base,
        todayDateBorderColor = LiraColors.TextSecondary,
        todayContentColor = LiraColors.TextPrimary,
        dayInSelectionRangeContainerColor = LiraColors.Surface3,
        dayInSelectionRangeContentColor = LiraColors.TextPrimary
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            GhostButton("Done", onClick = {
                onConfirm(state.selectedStartDateMillis?.toUtcDate(), state.selectedEndDateMillis?.toUtcDate())
            }, color = LiraColors.TextPrimary)
        },
        dismissButton = { GhostButton("Cancel", onClick = onDismiss) },
        colors = colors
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            showModeToggle = false,
            colors = colors
        )
    }
}

/** Small confirm sheet (delete trip, end trip). */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false,
    error: String? = null
) {
    LiraSheet(onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(body, style = LiraType.BodyM.copy(color = LiraColors.TextSecondary))
            if (error != null) Text(error, style = LiraType.BodyS.copy(color = LiraColors.TextPrimary))
            PrimaryButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), loading = loading)
            SecondaryButton("Cancel", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
