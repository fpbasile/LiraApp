package com.app.lira.ui.screens.more

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.HomeDataCache
import com.app.lira.data.model.Recurring
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CategoryRow
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class RecurringSheet { None, Name, Interval, End, Date, Delete }

/**
 * Add / edit a recurring payment or income. Reuses the quick-add layout: amount on top,
 * option chips and category chips in the middle, keypad at the bottom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringEditScreen(navController: NavHostController, recurringId: Int?) {
    val scope = rememberCoroutineScope()
    val code = CurrencyPreference.code
    val digits = Money.minorDigits(code)
    val today = remember { LocalDate.now() }

    var original by remember { mutableStateOf<Recurring?>(null) }
    var loading by remember { mutableStateOf(recurringId != null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var type by remember { mutableStateOf(TransactionRepository.TYPE_PAYED) }
    var raw by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(false) }
    var interval by remember { mutableStateOf("monthly") }
    var start by remember { mutableStateOf(today) }
    var occurrences by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Int?>(null) }
    var categories by remember {
        mutableStateOf(HomeDataCache.categories.map { CategoryRow(it.id, it.name) })
    }

    var sheet by remember { mutableStateOf(RecurringSheet.None) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recurringId) {
        launch { CategoryRepository.getCategories().onSuccess { categories = it } }
        if (recurringId != null) {
            RecurringRepository.getRecurring()
                .onSuccess { list ->
                    val r = list.firstOrNull { it.id == recurringId }
                    if (r == null) loadError = "This recurring item no longer exists."
                    else {
                        original = r
                        type = r.type
                        raw = rawFromAmount(r.amount, digits)
                        fresh = true
                        interval = r.interval
                        start = runCatching { LocalDate.parse(r.start_date) }.getOrDefault(today)
                        occurrences = r.occurrences
                        name = r.name
                        categoryId = r.category_id
                    }
                }
                .onFailure { loadError = it.message }
            loading = false
        }
    }

    val isIncome = type == TransactionRepository.TYPE_RECEIVED
    val amount = raw.toDoubleOrNull() ?: 0.0

    fun save() {
        if (amount <= 0) return
        if (name.isBlank()) { error = "Give it a name first."; sheet = RecurringSheet.Name; return }
        scope.launch {
            saving = true
            error = null
            val cat = if (isIncome) null else categoryId
            val o = original
            val result = if (o != null) {
                RecurringRepository.updateRecurring(o, name.trim(), amount, start.toString(), interval, occurrences, type, cat)
            } else {
                RecurringRepository.addRecurring(name.trim(), amount, start.toString(), interval, occurrences, type, cat).map { }
            }
            result.onSuccess {
                // Anything already due gets posted right away instead of waiting for the next cold start.
                RecurringRepository.postDueOccurrences()
                navController.popBackStack()
            }.onFailure { error = it.message ?: "Couldn't save." }
            saving = false
        }
    }

    LiraScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ModalTopBar(
                title = if (recurringId == null) "New recurring" else "Edit recurring",
                onClose = { navController.popBackStack() },
                trailing = if (original != null) {
                    { IconCircleButton(Icons.Outlined.DeleteOutline, "Delete recurring", { error = null; sheet = RecurringSheet.Delete }, background = Color.Transparent, tint = LiraColors.TextSecondary) }
                } else null
            )

            if (loading || loadError != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(color = LiraColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    else EmptyState(Icons.Outlined.Repeat, "Can't open this", loadError, actionLabel = "Go back", onAction = { navController.popBackStack() })
                }
                return@Column
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SegmentedControl(
                    options = listOf("Payment", "Income"),
                    selected = if (isIncome) 1 else 0,
                    onSelect = { type = if (it == 1) TransactionRepository.TYPE_RECEIVED else TransactionRepository.TYPE_PAYED },
                    height = 36.dp
                )
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
            ) {
                AmountDisplay(raw, code, income = isIncome)
                AmountCaption(describe(interval, start, occurrences, today))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionChip(Schedule.intervalLabel(interval), Icons.Outlined.Repeat, edited = true, onClick = { sheet = RecurringSheet.Interval })
                    OptionChip("From " + Dates.short(start), Icons.Outlined.CalendarToday, edited = true, onClick = { sheet = RecurringSheet.Date })
                    OptionChip(
                        occurrences?.let { n -> "Ends after $n" } ?: "No end date",
                        Icons.Outlined.EventBusy, edited = occurrences != null, onClick = { sheet = RecurringSheet.End }
                    )
                    OptionChip(name.ifBlank { if (isIncome) "Who pays?" else "Name" }, Icons.Outlined.EditNote, edited = name.isNotBlank(), onClick = { sheet = RecurringSheet.Name })
                }
                if (error != null && sheet == RecurringSheet.None) MessageLine(error!!)
            }

            if (!isIncome) {
                CategoryChipsRow(categories, categoryId, { categoryId = it }, Modifier.padding(bottom = 12.dp), allowNone = true)
            } else {
                Text(
                    "Income lands on Home as a + entry when it's due.",
                    style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(56.dp).wrapContentHeight()
                )
            }

            Keypad(
                onKey = { key ->
                    error = null
                    raw = if (fresh && key !is KeypadKey.Delete) applyKey("", key, digits) else applyKey(raw, key, digits)
                    fresh = false
                },
                topAction = { m ->
                    TallKey(Icons.Outlined.Repeat, Schedule.intervalLabel(interval), m) {
                        val all = RecurringRepository.INTERVALS
                        interval = all[(all.indexOf(interval) + 1).mod(all.size)]
                    }
                },
                bottomAction = { m -> SaveKey(m, ready = amount > 0 && !saving) { save() } },
                modifier = Modifier.padding(bottom = 8.dp),
                showDecimal = digits > 0
            )
        }
    }

    when (sheet) {
        RecurringSheet.Name -> NameSheet(
            initial = name,
            isIncome = isIncome,
            error = error,
            onDone = { name = it; error = null; sheet = RecurringSheet.None },
            onDismiss = { sheet = RecurringSheet.None }
        )
        RecurringSheet.Interval -> LiraSheet(onDismiss = { sheet = RecurringSheet.None }, title = "How often?") {
            RecurringRepository.INTERVALS.forEach { option ->
                ListRow(
                    title = Schedule.intervalLabel(option),
                    subtitle = describe(option, start, null, today).removePrefix("Posts "),
                    trailing = if (option == interval) { { Icon(Icons.Outlined.Check, "Selected", tint = LiraColors.TextPrimary) } } else null,
                    onClick = { interval = option; sheet = RecurringSheet.None }
                )
            }
        }
        RecurringSheet.End -> EndSheet(
            start = start,
            interval = interval,
            initial = occurrences,
            onDone = { occurrences = it; sheet = RecurringSheet.None },
            onDismiss = { sheet = RecurringSheet.None }
        )
        RecurringSheet.Date -> LiraDatePickerDialog(start, onPick = { start = it }, onDismiss = { sheet = RecurringSheet.None })
        RecurringSheet.Delete -> {
            val o = original
            ConfirmSheet(
                title = "Delete “${o?.name.orEmpty()}”?",
                body = "Nothing new will be logged for it. Entries already logged stay where they are.",
                confirmLabel = "Delete recurring",
                loading = saving,
                error = error,
                onConfirm = {
                    val id = o?.id ?: return@ConfirmSheet
                    scope.launch {
                        saving = true
                        error = null
                        RecurringRepository.deleteRecurring(id)
                            .onSuccess { sheet = RecurringSheet.None; navController.popBackStack() }
                            .onFailure { error = it.message ?: "Couldn't delete." }
                        saving = false
                    }
                },
                onDismiss = { error = null; sheet = RecurringSheet.None }
            )
        }
        RecurringSheet.None -> Unit
    }
}

/** "Posts on the 15th of every month, starting Sunday" */
private fun describe(interval: String, start: LocalDate, occurrences: Int?, today: LocalDate): String {
    val cadence = when (interval) {
        "daily" -> "every day"
        "weekly" -> "every " + Dates.weekday(start)
        "yearly" -> "every " + Dates.short(start)
        else -> "on the " + Dates.ordinal(start.dayOfMonth) + " of every month"
    }
    val from = when {
        start == today -> "starting today"
        start == today.plusDays(1) -> "starting tomorrow"
        start.isAfter(today) && start.isBefore(today.plusDays(7)) -> "starting " + Dates.weekday(start)
        start.isBefore(today) -> "since " + Dates.long(start)
        else -> "starting " + Dates.short(start)
    }
    val times = occurrences?.let { if (it == 1) ", once" else ", $it times" } ?: ""
    return "Posts $cadence, $from$times"
}

@Composable
private fun NameSheet(initial: String, isIncome: Boolean, error: String?, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    LiraSheet(onDismiss = onDismiss, title = if (isIncome) "Who pays?" else "What is it?") {
        Column(Modifier.imePadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LiraTextField(
                value = value,
                onValueChange = { if (it.length <= 60) value = it },
                placeholder = if (isIncome) "e.g. Salary" else "e.g. Rent, Spotify"
            )
            if (error != null && value.isBlank()) MessageLine(error)
            PrimaryButton("Done", { onDone(value.trim()) }, Modifier.fillMaxWidth(), enabled = value.isNotBlank())
        }
    }
}

@Composable
private fun EndSheet(start: LocalDate, interval: String, initial: Int?, onDone: (Int?) -> Unit, onDismiss: () -> Unit) {
    var limited by remember { mutableStateOf(initial != null) }
    var count by remember { mutableStateOf(initial ?: 12) }
    LiraSheet(onDismiss = onDismiss, title = "When does it end?") {
        ListRow(
            title = "No end date",
            subtitle = "Keeps going until you delete it",
            trailing = if (!limited) { { Icon(Icons.Outlined.Check, "Selected", tint = LiraColors.TextPrimary) } } else null,
            onClick = { limited = false }
        )
        ListRow(
            title = "After a number of times",
            subtitle = if (limited) "Last one on " + Dates.long(Schedule.final(start, interval, count) ?: start) else "e.g. a 12-month contract",
            trailing = if (limited) { { Icon(Icons.Outlined.Check, "Selected", tint = LiraColors.TextPrimary) } } else null,
            onClick = { limited = true }
        )
        if (limited) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                IconCircleButton(Icons.Outlined.Remove, "Fewer", { if (count > 1) count-- }, background = LiraColors.Surface2)
                Text(
                    if (count == 1) "1 time" else "$count times",
                    style = LiraType.AmountS.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 110.dp)
                )
                IconCircleButton(Icons.Outlined.Add, "More", { if (count < 999) count++ }, background = LiraColors.Surface2)
            }
        }
        PrimaryButton(
            "Done", { onDone(if (limited) count else null) },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}
