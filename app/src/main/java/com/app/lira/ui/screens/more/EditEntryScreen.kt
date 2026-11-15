package com.app.lira.ui.screens.more

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.HomeDataCache
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CategoryRow
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.TripRepository
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class EntrySheet { None, Note, Source, Date, Delete, Currency, CurrencyAll }

/**
 * Edit a logged entry. Same layout as quick-add, prefilled: amount, type, date, note, category.
 * Replaces the old EditTransactionDialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditEntryScreen(navController: NavHostController, transactionId: Int) {
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf<EntryRow?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf(HomeDataCache.categories.map { CategoryRow(it.id, it.name) }) }

    var type by remember { mutableStateOf(TransactionRepository.TYPE_PAYED) }
    var raw by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(true) }
    var categoryId by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var originalDateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var dateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    // Currency is editable so an entry logged in the wrong currency (LBP typed as USD) can be fixed.
    var currency by remember { mutableStateOf<String?>(null) }
    var tripCurrencies by remember { mutableStateOf<List<String>>(emptyList()) }
    var rate by remember { mutableStateOf<Double?>(null) }

    var sheet by remember { mutableStateOf(EntrySheet.None) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(transactionId) {
        launch { CategoryRepository.getCategories().onSuccess { categories = it } }
        MoreData.getEntry(transactionId)
            .onSuccess { e ->
                entry = e
                type = e.type
                raw = rawFromAmount(e.amount, Money.minorDigits(e.currency ?: CurrencyPreference.code))
                categoryId = e.category_id
                note = e.description.orEmpty()
                source = e.source.orEmpty()
                currency = e.currency
                e.trip_id?.let { tid -> launch { TripRepository.getTrip(tid).onSuccess { t -> tripCurrencies = t?.currencies.orEmpty().map { it.uppercase() } } } }
                originalDateTime = MoreData.parseLocalDateTime(e.date)
                dateTime = originalDateTime
            }
            .onFailure { loadError = it.message }
    }

    val home = CurrencyPreference.code
    val code = currency ?: home
    LaunchedEffect(code, home) {
        rate = if (code.equals(home, true)) null else com.app.lira.data.RateCache.rate(code, home)
    }
    val digits = Money.minorDigits(code)
    val isIncome = type == TransactionRepository.TYPE_RECEIVED
    val amount = raw.toDoubleOrNull() ?: 0.0

    fun save() {
        val e = entry ?: return
        if (amount <= 0) return
        scope.launch {
            saving = true
            error = null
            val newDate = dateTime?.takeIf { it != originalDateTime }?.let { MoreData.toIso(it) }
            MoreData.updateEntry(
                id = e.id,
                amount = amount,
                type = type,
                categoryId = if (isIncome) null else categoryId,
                source = if (isIncome) source else null,
                description = note,
                dateIso = newDate,
                currency = currency?.takeUnless { it.equals(home, true) && e.trip_id == null },
                updateCurrency = currency != e.currency
            ).onSuccess { navController.popBackStack() }
                .onFailure { error = it.message ?: "Couldn't update the entry." }
            saving = false
        }
    }

    LiraScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ModalTopBar(
                title = "Edit entry",
                subtitle = originalDateTime?.let { loggedLabel(it) },
                onClose = { navController.popBackStack() },
                trailing = if (entry != null) {
                    { IconCircleButton(Icons.Outlined.DeleteOutline, "Delete entry", { error = null; sheet = EntrySheet.Delete }, background = Color.Transparent, tint = LiraColors.TextSecondary) }
                } else null
            )

            if (entry == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loadError == null) CircularProgressIndicator(color = LiraColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    else EmptyState(Icons.Outlined.ReceiptLong, "Can't open this entry", loadError, actionLabel = "Go back", onAction = { navController.popBackStack() })
                }
                return@Column
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SegmentedControl(
                    options = listOf("Expense", "Income"),
                    selected = if (isIncome) 1 else 0,
                    onSelect = { type = if (it == 1) TransactionRepository.TYPE_RECEIVED else TransactionRepository.TYPE_PAYED },
                    height = 36.dp
                )
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                AmountDisplay(raw, code, income = isIncome)
                val r = rate
                if (r != null && amount > 0) Text(
                    "≈ ${Money.format(amount * r, home)}",
                    style = LiraType.BodyS.copy(color = LiraColors.TextSecondary, fontFeatureSettings = "tnum")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionChip(
                        dateTime?.let { chipDate(it) } ?: "Date",
                        Icons.Outlined.CalendarToday,
                        edited = dateTime != originalDateTime,
                        onClick = { sheet = EntrySheet.Date }
                    )
                    OptionChip(code, Icons.Outlined.Payments, edited = currency != entry?.currency, onClick = { sheet = EntrySheet.Currency })
                    if (isIncome) {
                        OptionChip(source.ifBlank { "From who?" }, Icons.Outlined.Person, edited = source.isNotBlank(), onClick = { sheet = EntrySheet.Source })
                    }
                    OptionChip(note.ifBlank { "Note" }, Icons.Outlined.EditNote, edited = note.isNotBlank(), onClick = { sheet = EntrySheet.Note })
                }
                if (error != null && sheet == EntrySheet.None) MessageLine(error!!)
            }

            if (!isIncome) {
                CategoryChipsRow(categories, categoryId, { categoryId = it }, Modifier.padding(bottom = 12.dp))
            } else {
                Text(
                    "Income has no category.",
                    style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(56.dp).wrapContentHeight()
                )
            }

            Keypad(
                onKey = { key ->
                    error = null
                    // The prefilled amount behaves like a selected field: the first digit replaces it.
                    raw = if (fresh && key !is KeypadKey.Delete) applyKey("", key, digits) else applyKey(raw, key, digits)
                    fresh = false
                },
                topAction = { m -> TallKey(Icons.Outlined.EditNote, "Note", m) { sheet = EntrySheet.Note } },
                bottomAction = { m ->
                    TallKey(Icons.Outlined.Check, "Update", m, primary = true, enabled = amount > 0 && !saving) { save() }
                },
                modifier = Modifier.padding(bottom = 8.dp),
                showDecimal = digits > 0
            )
        }
    }

    when (sheet) {
        EntrySheet.Note -> TextSheet(
            title = "Note", initial = note, placeholder = "e.g. Weekly shop · Monoprix",
            onDone = { note = it; sheet = EntrySheet.None }, onDismiss = { sheet = EntrySheet.None }
        )
        EntrySheet.Source -> TextSheet(
            title = "From who?", initial = source, placeholder = "e.g. Giulia",
            onDone = { source = it; sheet = EntrySheet.None }, onDismiss = { sheet = EntrySheet.None }
        )
        EntrySheet.Date -> LiraDatePickerDialog(
            initial = dateTime?.toLocalDate() ?: LocalDate.now(),
            onPick = { d -> val t = dateTime ?: LocalDateTime.now(); dateTime = d.atTime(t.toLocalTime()) },
            onDismiss = { sheet = EntrySheet.None }
        )
        EntrySheet.Delete -> ConfirmSheet(
            title = "Delete this entry?",
            body = "It comes off your totals and streak. This can't be undone.",
            confirmLabel = "Delete entry",
            loading = saving,
            error = error,
            onConfirm = {
                scope.launch {
                    saving = true
                    error = null
                    TransactionRepository.deleteTransaction(transactionId)
                        .onSuccess { sheet = EntrySheet.None; navController.popBackStack() }
                        .onFailure { error = it.message ?: "Couldn't delete the entry." }
                    saving = false
                }
            },
            onDismiss = { error = null; sheet = EntrySheet.None }
        )
        EntrySheet.Currency -> LiraSheet(onDismiss = { sheet = EntrySheet.None }, title = "Currency") {
            Text(
                "Changing the currency keeps the number — use it when an entry was logged in the wrong one.",
                style = LiraType.BodyS,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            (tripCurrencies + home.uppercase() + code.uppercase()).distinct().forEach { c ->
                ListRow(
                    title = c,
                    subtitle = if (amount > 0) Money.format(amount, c) else null,
                    trailing = if (c.equals(code, true)) { { androidx.compose.material3.Icon(Icons.Outlined.Check, null, tint = LiraColors.TextPrimary) } } else null,
                    onClick = { currency = c.uppercase(); sheet = EntrySheet.None }
                )
            }
            ListRow(title = "Other currency…", titleColor = LiraColors.TextSecondary, onClick = { sheet = EntrySheet.CurrencyAll })
        }
        EntrySheet.CurrencyAll -> com.app.lira.ui.screens.travel.CurrencyPickerSheet(
            selected = code,
            onSelect = { currency = it.uppercase() },
            onDismiss = { sheet = EntrySheet.None }
        )
        EntrySheet.None -> Unit
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** "Logged today at 12:41" / "Logged Mon 9 Nov at 08:10". */
private fun loggedLabel(dt: LocalDateTime): String {
    val rel = Dates.relative(dt.toLocalDate())
    val day = if (rel == "Today" || rel == "Yesterday") rel.lowercase() else rel
    return "Logged $day at ${dt.format(timeFmt)}"
}

/** "Today, 12:41" / "Mon 9 Nov, 12:41". */
private fun chipDate(dt: LocalDateTime): String = Dates.relative(dt.toLocalDate()) + ", " + dt.format(timeFmt)

@Composable
private fun TextSheet(title: String, initial: String, placeholder: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    LiraSheet(onDismiss = onDismiss, title = title) {
        Column(Modifier.imePadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LiraTextField(value = value, onValueChange = { if (it.length <= 120) value = it }, placeholder = placeholder)
            PrimaryButton("Done", { onDone(value.trim()) }, Modifier.fillMaxWidth())
        }
    }
}
