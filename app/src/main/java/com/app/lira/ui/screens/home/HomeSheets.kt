package com.app.lira.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.AppPrefs
import com.app.lira.data.CategoryStyle
import com.app.lira.data.RateCache
import com.app.lira.data.model.Category
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CurrencyConverterRepository
import com.app.lira.ui.components.lira.CategoryIconBadge
import com.app.lira.ui.components.lira.GhostButton
import com.app.lira.ui.components.lira.LiraSheet
import com.app.lira.ui.components.lira.LiraTextField
import com.app.lira.ui.components.lira.PrimaryButton
import com.app.lira.ui.components.lira.SectionLabel
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

// ───────────────────────────── All categories ─────────────────────────────

/** "Pick a category" (Home-Categories): 4-column grid ordered by use, plus New. */
@Composable
internal fun CategorySheet(
    categories: List<Category>,
    amountText: String?,
    selectedId: Int?,
    onPick: (Int) -> Unit,
    onEdit: () -> Unit,
    onCreated: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LiraSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (creating) "New category" else "Pick a category", style = LiraType.TitleM)
                if (!creating) {
                    if (amountText != null) Row {
                        Text("Saves ", style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
                        Text(amountText, style = LiraType.BodyS.copy(color = LiraColors.TextPrimary, fontFeatureSettings = "tnum"))
                        Text(" right away", style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
                    } else Text("Stays selected — Save logs it", style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
                }
            }
            if (!creating) GhostButton("Edit", onEdit)
        }
        if (creating) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiraTextField(name, { name = it; error = null }, placeholder = "e.g. Gym", label = "Name")
                if (error != null) Text(error!!, style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GhostButton("Cancel", { creating = false; name = "" })
                    PrimaryButton(
                        "Add category",
                        onClick = {
                            val n = name.trim()
                            if (n.isEmpty()) return@PrimaryButton
                            scope.launch {
                                saving = true
                                CategoryRepository.addCategory(n)
                                    .onSuccess { row ->
                                        creating = false
                                        name = ""
                                        onCreated(Category(id = row.id, name = row.name, colorHex = 0xFF9E9EA4L))
                                    }
                                    .onFailure { error = it.message ?: "Couldn't add category" }
                                saving = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank(),
                        loading = saving
                    )
                }
            }
        } else {
            val cells = categories.map { it as Category? } + listOf(null) // null = New
            Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { cat ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                    .liraClickable { if (cat == null) creating = true else onPick(cat.id) }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (cat == null) {
                                    Box(Modifier.size(52.dp).border(1.5.dp, LiraColors.TextDisabled, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Add, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(24.dp))
                                    }
                                    Text("New", style = LiraType.Label.copy(fontSize = 12.sp, color = LiraColors.TextSecondary))
                                } else {
                                    val look = CategoryStyle.look(cat.id, cat.name)
                                    Box(
                                        Modifier.then(if (cat.id == selectedId) Modifier.border(1.5.dp, LiraColors.TextPrimary, CircleShape) else Modifier),
                                        contentAlignment = Alignment.Center
                                    ) { CategoryIconBadge(look.icon, look.color, size = 52.dp) }
                                    Text(
                                        cat.name, style = LiraType.Label.copy(fontSize = 12.sp), textAlign = TextAlign.Center,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text(
                "Ordered by how often you use them. The first five also sit above the keypad.",
                style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}

// ───────────────────────────── Currency ─────────────────────────────

private val COMMON = listOf("EUR", "USD", "GBP", "JPY", "CHF", "CAD", "AUD", "CNY", "LBP", "AED")

private fun currencyName(code: String, names: Map<String, String>): String =
    names[code] ?: runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)

/** Currency for this entry only (Home-Currency). Home first, trip currencies, pinned, then common. */
@Composable
internal fun CurrencySheet(
    home: String,
    selected: String,
    tripCurrencies: List<String>,
    tripActive: Boolean,
    onPick: (String) -> Unit,
    onStartTrip: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        CurrencyConverterRepository.getSupportedCurrencies().onSuccess { map ->
            // The API also lists crypto/metals; keep ISO 4217 three-letter codes only.
            names = map.filterKeys { it.length == 3 }
        }
    }
    val base = remember(home, tripCurrencies) {
        (listOf(home) + tripCurrencies + AppPrefs.pinnedCurrencies + COMMON).map { it.uppercase() }.distinct()
    }
    // Fetch rates for the visible short list.
    LaunchedEffect(base) { base.filter { it != home }.forEach { RateCache.rate(home, it) } }

    val list = if (query.isBlank()) base else {
        val q = query.trim().lowercase()
        val all = (names.keys.ifEmpty { Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() } + base).distinct()
        all.filter { it.lowercase().contains(q) || currencyName(it, names).lowercase().contains(q) }.sorted().take(60)
    }
    val total = names.size.takeIf { it > 0 } ?: Currency.getAvailableCurrencies().size

    LiraSheet(onDismiss = onDismiss, title = "Currency") {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
            LiraTextField(query, { query = it }, placeholder = "Search $total currencies", leadingIcon = Icons.Outlined.Search)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            item { SectionLabel("This entry only", Modifier.padding(top = 4.dp)) }
            items(list, key = { it }) { code ->
                val sub = when {
                    code == home -> "Home currency"
                    code in tripCurrencies.map { it.uppercase() } -> "Trip currency" + (RateCache.cached(home, code)?.let { " · 1 $home = ${rateText(it)} $code" } ?: "")
                    else -> RateCache.cached(home, code)?.let { "1 $home = ${rateText(it)} $code" } ?: currencyName(code, names)
                }
                Row(
                    Modifier.fillMaxWidth().height(60.dp).liraClickable { onPick(code) }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
                        val sym = Money.symbol(code)
                        Text(if (sym.length <= 2) sym else code.take(1), style = LiraType.AmountXs.copy(fontSize = 17.sp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row {
                            Text(currencyName(code, names).replaceFirstChar { it.titlecase(Locale.getDefault()) }, style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text(" $code", style = LiraType.BodyM.copy(color = LiraColors.TextTertiary))
                        }
                        Text(sub, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), maxLines = 1)
                    }
                    if (code.equals(selected, true)) Icon(Icons.Outlined.Check, "Selected", tint = LiraColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
            if (!tripActive && query.isBlank()) item {
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(LiraColors.Surface2).liraClickable(onClick = onStartTrip).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(TravelTint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Flight, null, tint = TravelTint, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Travelling?", style = LiraType.BodyM.copy(fontWeight = FontWeight.SemiBold))
                        Text("Start a trip and every entry defaults to the local currency.", style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun rateText(r: Double): String = when {
    r >= 100 -> "%,.2f".format(r)
    r >= 1 -> "%.2f".format(r)
    else -> "%.4f".format(r)
}

// ───────────────────────────── Text input sheets ─────────────────────────────

/** One-field sheet (Note, new payer). */
@Composable
internal fun TextEntrySheet(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    suggestions: List<String> = emptyList()
) {
    var text by remember { mutableStateOf(initial) }
    LiraSheet(onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LiraTextField(text, { text = it }, placeholder = placeholder)
            if (suggestions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollSafe()) {
                    suggestions.take(6).forEach { s ->
                        Box(Modifier.height(36.dp).clip(CircleShape).background(LiraColors.Surface2).liraClickable { text = s }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(s, style = LiraType.Label.copy(color = LiraColors.TextSecondary), maxLines = 1)
                        }
                    }
                }
            }
            PrimaryButton(confirmLabel, { onConfirm(text.trim()) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Modifier.horizontalScrollSafe(): Modifier =
    this.horizontalScroll(androidx.compose.foundation.rememberScrollState())

// ───────────────────────────── Date & time ─────────────────────────────

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** "Now" · "Today, 12:38" · "Yesterday, 18:00" · "12 Nov, 09:00". */
internal fun whenLabel(at: LocalDateTime?): String {
    if (at == null) return "Now"
    val today = LocalDate.now()
    val day = when (at.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> at.toLocalDate().format(dayMonth)
    }
    return "$day, ${at.format(hm)}"
}

/** Date picker then time picker (old behaviour). [onPicked] gets the combined date-time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhenPicker(initial: LocalDateTime?, onPicked: (LocalDateTime?) -> Unit, onDismiss: () -> Unit) {
    val start = initial ?: LocalDateTime.now()
    var date by remember { mutableStateOf<LocalDate?>(null) }
    if (date == null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                GhostButton("Next", {
                    date = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: start.toLocalDate()
                }, color = LiraColors.TextPrimary)
            },
            dismissButton = {
                Row {
                    GhostButton("Now", { onPicked(null) })
                    GhostButton("Cancel", onDismiss)
                }
            }
        ) { DatePicker(state = state) }
    } else {
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = LiraColors.Surface1,
            shape = RoundedCornerShape(28.dp),
            title = { Text("What time?", style = LiraType.TitleM) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
            confirmButton = {
                GhostButton("Done", { onPicked(LocalDateTime.of(date!!, LocalTime.of(state.hour, state.minute))) }, color = LiraColors.TextPrimary)
            },
            dismissButton = { GhostButton("Cancel", onDismiss) }
        )
    }
}

/**
 * Wrong-currency guard: "$100,000 is about €86,045 — right currency?" Offers the trip's other
 * currencies (with what the same number would be worth) or keeping it as typed.
 */
@Composable
internal fun WrongCurrencySheet(
    pending: PendingConfirm,
    home: String,
    alternatives: List<String>,
    onKeep: () -> Unit,
    onSwitch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val rates = remember(alternatives, home) { mutableStateMapOf<String, Double?>() }
    LaunchedEffect(alternatives, home) {
        alternatives.forEach { code -> rates[code] = RateCache.cached(code, home) ?: RateCache.rate(code, home) }
    }
    com.app.lira.ui.components.lira.LiraSheet(onDismiss = onDismiss, title = "Right currency?") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${com.app.lira.util.Money.format(pending.amount, pending.currency)} is about " +
                    "${com.app.lira.util.Money.format(pending.homeAmount, home, trimZeros = true)}. That’s a lot for one entry — was it in another currency?",
                style = com.app.lira.ui.theme.LiraType.BodyM.copy(color = com.app.lira.ui.theme.LiraColors.TextSecondary)
            )
            alternatives.forEach { code ->
                val r = rates[code]
                com.app.lira.ui.components.lira.ListRow(
                    title = "It was ${com.app.lira.util.Money.format(pending.amount, code)}",
                    subtitle = r?.let { "≈ ${com.app.lira.util.Money.format(pending.amount * it, home)}" } ?: "Loading rate…",
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(com.app.lira.ui.theme.LiraColors.Surface2),
                    onClick = { onSwitch(code) }
                )
            }
            com.app.lira.ui.components.lira.GhostButton(
                "Keep ${pending.currency.uppercase()} — save ${com.app.lira.util.Money.format(pending.amount, pending.currency)}",
                onKeep,
                Modifier.fillMaxWidth()
            )
        }
    }
}
