package com.app.lira.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.CategoryStyle
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.model.Trip
import com.app.lira.data.repository.TransactionDetail
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.TripRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.lira.BudgetRing
import com.app.lira.ui.components.lira.EmptyState
import com.app.lira.ui.components.lira.GhostButton
import com.app.lira.ui.components.lira.IconCircleButton
import com.app.lira.ui.components.lira.LiraScreen
import com.app.lira.ui.components.lira.LiraSheet
import com.app.lira.ui.components.lira.PrimaryButton
import com.app.lira.ui.components.lira.ProgressLine
import com.app.lira.ui.components.lira.SectionLabel
import com.app.lira.ui.components.lira.SegmentedControl
import com.app.lira.ui.components.lira.TimelineRow
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BarFill = Color(0xFFD4D4D8)

/** Trip view (Trip board): totals in the home currency, by currency, by category, quick converter, entries. */
@Composable
fun TripScreen(navController: NavHostController, tripId: Int) {
    val context = LocalContext.current
    remember { TripLocalStore.init(context) }
    val scope = rememberCoroutineScope()
    val home = CurrencyPreference.code

    var trip by remember { mutableStateOf<Trip?>(null) }
    var txns by remember { mutableStateOf<List<TransactionDetail>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var totals by remember { mutableStateOf<TripTotals?>(null) }
    var editing by remember { mutableStateOf(false) }
    var addingCurrency by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun refresh() {
        TripRepository.getTrip(tripId).onSuccess { t ->
            trip = t
            if (t != null && t.is_active && TripMode.autoEndExpired(listOf(t))) {
                TripRepository.getTrip(tripId).onSuccess { trip = it }
            }
        }.onFailure { e -> error = e.message }
        TransactionRepository.getAllTransactions()
            .onSuccess { all -> txns = all.filter { it.trip_id == tripId } }
            .onFailure { e -> error = e.message }
        loading = false
    }

    LaunchedEffect(tripId) { refresh() }
    val extras = TripLocalStore.get(tripId)
    LaunchedEffect(txns, home, trip?.id, extras.start) {
        val t = trip ?: return@LaunchedEffect
        totals = computeTripTotals(txns, home, TripMode.startDate(t))
    }

    fun setActive(active: Boolean) {
        scope.launch {
            busy = true
            TripRepository.setActiveTrip(if (active) tripId else null).onFailure { e -> error = e.message }
            refresh()
            busy = false
        }
    }

    LiraScreen(glowTint = TravelTint, glowIntensity = if (trip?.is_active == true) 1f else 0.4f) {
        val t = trip
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LiraColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            t == null -> Column(Modifier.fillMaxSize()) {
                BackRow(onBack = { navController.popBackStack() }, onEdit = null)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Outlined.Luggage,
                        if (error != null) "Couldn't load this trip" else "This trip no longer exists",
                        error,
                        actionLabel = if (error != null) "Try again" else null,
                        onAction = if (error != null) ({ loading = true; scope.launch { refresh() }; Unit }) else null
                    )
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                BackRow(onBack = { navController.popBackStack() }, onEdit = { editing = true })
                TripHeader(t, home)
                SummaryRow(t, totals, home)
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (error != null) Text(error!!, style = LiraType.BodyS, modifier = Modifier.padding(horizontal = 4.dp))
                    totals?.suspicious?.takeIf { it.isNotEmpty() }?.let { list ->
                        CheckEntriesCard(list, home) { id -> navController.navigate(Routes.editTransaction(id)) }
                    }
                    ByCurrencyCard(totals, home, hasEntries = txns.any { it.type == TransactionRepository.TYPE_PAYED })
                    totals?.byCategory?.takeIf { it.isNotEmpty() }?.let { ByCategoryCard(it, home) }
                    QuickConverterCard(t, home)
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(
                            if (t.is_active) "End trip" else "Make active",
                            filled = true,
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { setActive(!t.is_active) }
                        BigButton("Add currency", filled = false, icon = { Icon(Icons.Outlined.Add, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(18.dp)) }, modifier = Modifier.weight(1f)) {
                            addingCurrency = true
                        }
                    }
                }
                EntriesSection(txns, home, onOpen = { navController.navigate(Routes.editTransaction(it)) })
                Spacer(Modifier.navigationBarsPadding().height(32.dp))
            }
        }
    }

    val current = trip
    if (addingCurrency && current != null) {
        CurrencyPickerSheet(
            selected = current.currencies.lastOrNull() ?: home,
            onSelect = { code ->
                if (code !in current.currencies) scope.launch {
                    TripRepository.updateTrip(tripId, current.name, current.currencies + code)
                        .onSuccess { refresh() }.onFailure { e -> error = e.message }
                }
            },
            onDismiss = { addingCurrency = false }
        )
    }

    if (editing && current != null) {
        val form = remember(current) { TripFormState.from(current, TripLocalStore.get(tripId)) }
        var formError by remember { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(false) }
        LiraSheet(onDismiss = { editing = false }, title = "Edit trip") {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                TripForm(form, home)
                if (formError != null) Text(formError!!, style = LiraType.BodyS.copy(color = LiraColors.TextPrimary))
                PrimaryButton("Save", {
                    val problem = form.validate()
                    if (problem != null) { formError = problem; return@PrimaryButton }
                    saving = true
                    scope.launch {
                        TripRepository.updateTrip(tripId, form.name.trim(), form.currencies)
                            .onSuccess {
                                TripLocalStore.set(tripId, form.extras(TripLocalStore.get(tripId)))
                                editing = false
                                refresh()
                            }
                            .onFailure { e -> formError = e.message }
                        saving = false
                    }
                }, Modifier.fillMaxWidth(), loading = saving)
                GhostButton("Delete trip", { editing = false; confirmDelete = true }, Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmDelete && current != null) {
        var deleteError by remember { mutableStateOf<String?>(null) }
        ConfirmSheet(
            title = "Delete “${current.name}”?",
            body = "The trip goes away. Its entries stay in your history, just unlinked from it.",
            confirmLabel = "Delete trip",
            loading = busy,
            error = deleteError,
            onDismiss = { if (!busy) confirmDelete = false },
            onConfirm = {
                busy = true
                scope.launch {
                    TripRepository.deleteTrip(tripId)
                        .onSuccess {
                            TripLocalStore.remove(tripId)
                            confirmDelete = false
                            navController.popBackStack()
                        }
                        .onFailure { e -> deleteError = e.message }
                    busy = false
                }
            }
        )
    }
}

@Composable
private fun BackRow(onBack: () -> Unit, onEdit: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, background = Color.Transparent)
        Spacer(Modifier.weight(1f))
        if (onEdit != null) IconCircleButton(Icons.Outlined.Edit, "Edit trip", onEdit, background = Color.Transparent, tint = LiraColors.TextSecondary)
    }
}

@Composable
private fun TripHeader(trip: Trip, home: String) {
    val extras = TripLocalStore.get(trip.id)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.FlightTakeoff, null, tint = if (trip.is_active) TravelTint else LiraColors.TextTertiary, modifier = Modifier.size(16.dp))
            Text(
                if (trip.is_active) activeLabel(trip) else "Not active",
                style = LiraType.Label.copy(fontWeight = FontWeight.SemiBold, color = if (trip.is_active) TravelTint else LiraColors.TextTertiary)
            )
        }
        Text(com.app.lira.util.Flags.tripLabel(trip.name), style = LiraType.TitleL)
        Text(
            listOfNotNull(
                formatTripDates(extras.start ?: TripMode.startDate(trip), extras.end, long = true),
                trip.currencies.joinToString(", ").ifEmpty { null },
                "totals in $home"
            ).joinToString(" · "),
            style = LiraType.BodyS.copy(color = LiraColors.TextTertiary)
        )
    }
}

@Composable
private fun SummaryRow(trip: Trip, totals: TripTotals?, home: String) {
    val budget = TripLocalStore.get(trip.id).budget
    val spent = totals?.total
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (spent == null) "…" else Money.format(spent, home),
                style = LiraType.AmountXl.copy(fontSize = 44.sp, lineHeight = 44.sp),
                maxLines = 1
            )
            if (budget != null && spent != null) {
                val left = budget - spent
                Text(
                    buildAnnotatedString {
                        append("spent of ${Money.format(budget, home, trimZeros = true)} · ")
                        if (left >= 0) withStyle(SpanStyle(color = LiraColors.Lichen, fontWeight = FontWeight.SemiBold)) { append("${Money.format(left, home)} left") }
                        else withStyle(SpanStyle(color = LiraColors.OverLimit, fontWeight = FontWeight.SemiBold)) { append("${Money.format(-left, home)} over budget") }
                    },
                    style = LiraType.BodyS.copy(color = LiraColors.TextSecondary)
                )
                val toGo = TripMode.daysToGo(trip)
                if (left > 0 && toGo != null && toGo > 0) {
                    Text(
                        "About ${Money.format(left / toGo, home)} a day for the ${if (toGo == 1) "last day" else "$toGo days to go"}",
                        style = LiraType.BodyS.copy(color = LiraColors.TextTertiary)
                    )
                }
            } else {
                Text(
                    if (totals?.missingRates == true) "spent so far · some rates unavailable offline" else "spent so far",
                    style = LiraType.BodyS.copy(color = LiraColors.TextSecondary)
                )
            }
        }
        if (budget != null && spent != null) {
            val p = (spent / budget).toFloat()
            BudgetRing(p, size = 96.dp, stroke = 8.dp) {
                Text("${(p * 100).toInt()}%", style = LiraType.AmountXs.copy(fontSize = 15.sp))
            }
        }
    }
}

@Composable
private fun TripCard(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LiraColors.Surface1).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, style = LiraType.BodyL.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            if (note != null) Text(note, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), textAlign = TextAlign.End)
        }
        content()
    }
}

@Composable
private fun ByCurrencyCard(totals: TripTotals?, home: String, hasEntries: Boolean) {
    TripCard("By currency", note = if (hasEntries) "converted at the rate on each day" else null) {
        when {
            !hasEntries -> Text("Nothing logged on this trip yet.", style = LiraType.BodyS)
            totals == null -> Text("Converting…", style = LiraType.BodyS)
            else -> totals.byCurrency.forEach { c ->
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundGlyph(CurrencyCatalog.glyph(c.code))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(CurrencyCatalog.name(c.code), style = LiraType.BodyM.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val detail = when {
                            c.beforeLeaving -> "before leaving"
                            c.days == 1 -> "1 day"
                            else -> "${c.days} days"
                        }
                        Text(
                            "${Money.format(c.original, c.code)} · $detail",
                            style = LiraType.BodyS.copy(fontSize = 12.sp, fontFamily = NumberFont, fontFeatureSettings = "tnum", color = LiraColors.TextTertiary)
                        )
                    }
                    Text(c.converted?.let { Money.format(it, home) } ?: "—", style = LiraType.AmountXs)
                }
            }
        }
    }
}

@Composable
private fun ByCategoryCard(rows: List<Pair<String?, Double>>, home: String) {
    val max = rows.maxOf { it.second }.coerceAtLeast(0.01)
    TripCard("By category") {
        rows.forEach { (name, amount) ->
            val look = CategoryStyle.look(null, name)
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(look.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(look.icon, null, tint = look.color, modifier = Modifier.size(15.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(name ?: "Uncategorised", style = LiraType.Label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.format(amount, home), style = LiraType.Label.copy(fontFamily = NumberFont, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                    }
                    ProgressLine((amount / max).toFloat(), color = BarFill)
                }
            }
        }
    }
}

@Composable
private fun QuickConverterCard(trip: Trip, home: String) {
    val options = remember(trip.currencies, home) { (trip.currencies + home).distinct() }
    var from by remember(options) { mutableStateOf(options.first()) }
    var to by remember(options) { mutableStateOf(if (options.first() == home) options.getOrElse(1) { home } else home) }
    var raw by remember { mutableStateOf("") }
    var table by remember { mutableStateOf<RateTable?>(TravelRates.cached(home)) }
    var offline by remember { mutableStateOf(false) }

    LaunchedEffect(home) {
        TravelRates.latest(home).onSuccess { table = it; offline = false }.onFailure { offline = table == null }
    }
    val amount = raw.toDoubleOrNull() ?: 0.0
    val converted = table?.convert(amount, from, to)

    TripCard("Quick converter") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ConverterField(from) {
                BasicTextField(
                    value = raw,
                    onValueChange = { raw = sanitizeAmount(it) },
                    singleLine = true,
                    textStyle = LiraType.AmountM.copy(textAlign = TextAlign.End),
                    cursorBrush = SolidColor(LiraColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (raw.isEmpty()) Text(Money.format(0.0, from, trimZeros = true), style = LiraType.AmountM.copy(color = LiraColors.TextDisabled))
                            inner()
                        }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = LiraColors.Line)
                IconCircleButton(Icons.Outlined.SwapVert, "Swap currencies", { val f = from; from = to; to = f }, background = LiraColors.Surface3, size = 44.dp)
                HorizontalDivider(Modifier.weight(1f), color = LiraColors.Line)
            }
            ConverterField(to) {
                Text(
                    when {
                        converted != null -> Money.format(converted, to)
                        offline -> "Offline"
                        else -> "…"
                    },
                    style = LiraType.AmountM.copy(color = LiraColors.TextSecondary),
                    maxLines = 1
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (options.size > 1) {
                    SegmentedControl(options, options.indexOf(from).coerceAtLeast(0), onSelect = { i ->
                        val pick = options[i]
                        if (pick == to) to = from
                        from = pick
                    })
                }
                Spacer(Modifier.weight(1f))
                table?.let {
                    Text(
                        "Rates " + java.time.Instant.ofEpochMilli(it.fetchedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = LiraType.BodyS.copy(fontSize = 11.sp, color = LiraColors.TextTertiary)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConverterField(code: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LiraColors.Surface2).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(code, style = LiraType.Label.copy(fontWeight = FontWeight.SemiBold, color = LiraColors.TextSecondary))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { content() }
    }
}

@Composable
private fun BigButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier.height(52.dp).clip(CircleShape).background(if (filled) LiraColors.Surface2 else Color.Transparent)
            .liraClickable(enabled, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        icon?.invoke()
        Text(text, style = LiraType.BodyM.copy(fontWeight = FontWeight.SemiBold, color = if (enabled) LiraColors.TextPrimary else LiraColors.TextDisabled))
    }
}

@Composable
private fun EntriesSection(txns: List<TransactionDetail>, home: String, onOpen: (Int) -> Unit) {
    if (txns.isEmpty()) return
    val zone = ZoneId.systemDefault()
    fun parse(t: TransactionDetail) = runCatching { OffsetDateTime.parse(t.date).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
    val byDay = remember(txns) {
        txns.map { it to parse(it) }
            .sortedByDescending { it.second }
            .groupBy { it.second?.toLocalDate() }
    }
    Spacer(Modifier.height(20.dp))
    SectionLabel("Entries")
    byDay.forEach { (day, rows) ->
        Text(
            dayLabel(day),
            style = LiraType.Label.copy(color = LiraColors.TextSecondary),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp)
        )
        rows.forEach { (t, at) ->
            val income = t.type == TransactionRepository.TYPE_RECEIVED
            val catName = t.category?.name
            val look = CategoryStyle.look(null, catName ?: if (income) "income" else null)
            TimelineRow(
                time = at?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                title = t.description?.takeIf { it.isNotBlank() } ?: catName ?: if (income) (t.source ?: "Income") else "Expense",
                subtitle = listOfNotNull(if (income) t.source?.let { "from $it" } else catName, t.currency?.takeIf { it != home }).joinToString(" · ").ifEmpty { if (income) "Income" else "Expense" },
                amount = Money.format(t.amount, t.currency ?: home),
                icon = look.icon,
                color = look.color,
                income = income,
                onClick = { onOpen(t.id) }
            )
        }
    }
}

private fun dayLabel(day: LocalDate?): String {
    if (day == null) return "Unknown date"
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, d MMM" else "d MMM yyyy"))
    }
}


/** "These look off": entries that convert to a huge amount, usually logged in the wrong currency. */
@Composable
private fun CheckEntriesCard(rows: List<Pair<com.app.lira.data.repository.TransactionDetail, Double>>, home: String, onOpen: (Int) -> Unit) {
    TripCard("Check these entries") {
        Text(
            "They convert to much more than the rest — often an amount typed in one currency but saved in another. Tap one to fix its currency.",
            style = LiraType.BodyS
        )
        rows.take(8).forEach { (t, inHome) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LiraColors.Surface2)
                    .liraClickable { onOpen(t.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.description ?: t.category?.name ?: "Entry", style = LiraType.BodyM, maxLines = 1)
                    Text(
                        Money.format(t.amount, t.currency ?: home) + " · " + t.date.take(10),
                        style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary)
                    )
                }
                Text("≈ " + Money.formatCompact(inHome, home, threshold = 100_000.0), style = LiraType.AmountXs)
            }
        }
    }
}
