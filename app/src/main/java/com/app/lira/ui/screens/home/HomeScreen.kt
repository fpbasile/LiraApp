package com.app.lira.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.app.lira.data.CategoryStyle
import com.app.lira.data.HomeDataCache
import com.app.lira.data.RateCache
import com.app.lira.data.repository.ReceiptRepository
import com.app.lira.navigation.HomeEvents
import com.app.lira.navigation.Routes
import com.app.lira.navigation.navigateToTab
import com.app.lira.ui.components.lira.AmountDisplay
import com.app.lira.ui.components.lira.CategoryChip
import com.app.lira.ui.components.lira.FloatingNavHeight
import com.app.lira.ui.components.lira.Keypad
import com.app.lira.ui.components.lira.LiraScreen
import com.app.lira.ui.components.lira.OptionChip
import com.app.lira.ui.components.lira.SaveKey
import com.app.lira.ui.components.lira.ScanKey
import com.app.lira.ui.components.lira.SegmentedControl
import com.app.lira.ui.components.lira.TopStrip
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.screens.travel.TravelHandoff
import com.app.lira.ui.screens.travel.TripLocalStore
import com.app.lira.ui.screens.travel.TripMode
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraMotion
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import com.app.lira.util.compressReceiptImage
import com.app.lira.util.createReceiptImageUri
import com.app.lira.widget.WidgetActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Middle { Scanning, Recap, Entry }

private val stripDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/**
 * Home = quick add. Amount first → tapping a category saves instantly; category first → the chip
 * stays selected and Save logs it. Top strip opens Today; Scan opens the camera and lands back
 * here pre-filled. While a trip runs, entries default to its currency and carry its trip id.
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduced = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    remember { TripLocalStore.init(context); 0 }
    val model = remember { HomeModel(scope, reduced) }
    val haptic = LocalHapticFeedback.current

    // Refresh on every resume so edits elsewhere show up when coming back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Single CONFIRM tick per save.
    LaunchedEffect(model.saves) { if (model.saves > 0) haptic.performHapticFeedback(HapticFeedbackType.Confirm) }

    // Rate for the current entry currency (conversion line).
    LaunchedEffect(model.entryCurrency, model.homeCode) { model.loadRates() }

    var showToday by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var showNewPayer by remember { mutableStateOf(false) }
    var showWhen by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }

    // Tapping Home while on Home clears the keypad.
    val clearRequests = HomeEvents.clearRequests
    var seenClear by remember { mutableIntStateOf(clearRequests) }
    LaunchedEffect(clearRequests) {
        if (clearRequests != seenClear) { seenClear = clearRequests; model.clear() }
    }

    // ── Scan ──
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    fun processReceipt(uri: Uri) {
        scope.launch {
            model.scanning = true
            try {
                val base64 = withContext(Dispatchers.IO) { compressReceiptImage(context, uri) }
                ReceiptRepository.parseReceipt(base64, "image/jpeg", model.categories.map { it.name })
                    .onSuccess { model.applyReceipt(it) }
                    .onFailure { model.flashNote(it.message ?: "Couldn't read the receipt") }
            } catch (e: Exception) {
                model.flashNote("Couldn't process the photo")
            }
            model.scanning = false
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) processReceipt(uri)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processReceipt(uri)
    }
    fun launchCamera() {
        val uri = createReceiptImageUri(context)
        pendingUri = uri
        cameraLauncher.launch(uri)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else model.flashNote("Camera permission is needed to scan a receipt")
    }
    fun beginCapture() {
        showCamera = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Widget: scan goes straight to the camera (speed is the point); quick-add = fresh expense entry.
    val pendingScan by WidgetActions.pendingScanReceipt
    LaunchedEffect(pendingScan) {
        if (pendingScan) {
            WidgetActions.pendingScanReceipt.value = false
            beginCapture()
        }
    }
    val pendingQuickAdd by WidgetActions.pendingQuickAdd
    LaunchedEffect(pendingQuickAdd) {
        if (pendingQuickAdd) {
            WidgetActions.pendingQuickAdd.value = false
            showToday = false; showCategories = false; showCurrency = false; showNote = false; showNewPayer = false
            model.clear()
            model.showRecap = false
        }
    }
    // Converter's "Log it".
    val handoff = TravelHandoff.pendingLog
    LaunchedEffect(handoff) { if (handoff != null) model.consumeHandoff() }

    // ── Derived ──
    val home = model.homeCode
    val limit = model.effectiveDailyLimit
    val left = limit - model.todaySpent
    val over = limit > 0 && left < -0.004
    val trip = model.trip
    val foreign = !model.entryCurrency.equals(home, true)

    LiraScreen(
        glowIntensity = when { model.glowBoost -> 1.9f; over -> 0.4f; else -> 1f },
        glowTint = if (trip != null) TravelTint else LiraColors.Cedar
    ) {
        Column(Modifier.fillMaxSize()) {
            if (trip != null && model.review == null) {
                val day = TripMode.dayOf(trip)
                TripBanner(
                    name = trip.name,
                    dayLabel = day?.let { (n, total) -> if (total != null) "Day $n of $total" else "Day $n" },
                    currency = model.entryCurrency,
                    onClick = { trip.id?.let { navController.navigate(Routes.tripDetail(it)) } }
                )
            }
            when {
                model.review != null -> ReceiptHeader(model.review?.merchant, onRetake = { showCamera = true })
                model.coach -> WelcomeHeader(onSetLimit = { navController.navigateToTab(Routes.MORE) })
                else -> {
                    val weekLimit = model.weekLimit
                    val sub = when {
                        over && weekLimit != null -> {
                            val wl = weekLimit - model.weekSpent
                            if (wl >= 0) "${Money.format(wl, home)} left this week" else "${Money.format(-wl, home)} over this week"
                        }
                        model.tripDailyBudget != null -> "Trip budget · ${Money.format(limit, home)} a day"
                        else -> "of ${Money.format(limit, home, trimZeros = true)} · ${LocalDate.now().format(stripDate)}"
                    }
                    TopStrip(
                        leftAmount = left,
                        limit = limit,
                        currencyCode = home,
                        periodWord = "today",
                        subLine = sub,
                        streakDays = model.streak,
                        weekLogged = model.weekLogged,
                        onClick = { showToday = true }
                    )
                }
            }

            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                SegmentedControl(
                    options = listOf("Expense", "Income"),
                    selected = if (model.income) 1 else 0,
                    onSelect = { model.setMode(it == 1) },
                    height = 36.dp
                )
            }

            // ── Amount area ──
            val density = LocalDensity.current
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(Unit) {
                        // Horizontal swipe on the amount flips Expense ↔ Income.
                        var acc = 0f
                        val threshold = with(density) { 72.dp.toPx() }
                        detectHorizontalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = {
                                if (acc < -threshold) model.setMode(true) else if (acc > threshold) model.setMode(false)
                            }
                        ) { _, dx -> acc += dx }
                    }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val middle = when {
                    model.scanning -> Middle.Scanning
                    model.showRecap && model.recap != null -> Middle.Recap
                    else -> Middle.Entry
                }
                AnimatedContent(
                    targetState = middle,
                    transitionSpec = { fadeIn(tween(LiraMotion.STANDARD)) togetherWith fadeOut(tween(LiraMotion.STANDARD)) },
                    label = "middle"
                ) { m ->
                    when (m) {
                        Middle.Scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ReadingReceipt() }
                        Middle.Recap -> Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                        ) {
                            model.recap?.let { r ->
                                RecapCard(r, home, { model.category(it) }, onDismiss = { model.showRecap = false })
                            }
                            Text("Start typing to log — the recap moves to Insights", style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
                        }
                        Middle.Entry -> EntryArea(model, foreign, home, onWhen = { showWhen = true }, onCurrency = { showCurrency = true }, onNote = { showNote = true })
                    }
                }
                model.flight?.let { f -> key(f.key) { FlyingAmount(f.text) } }
            }

            // ── Chips ──
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!model.income) {
                    val ordered = model.orderedCategories
                    val suggested = model.review?.suggestedCategoryId
                    val top = buildList {
                        suggested?.let { id -> model.category(id)?.let { add(it) } }
                        model.pickedCategory?.let { id -> model.category(id)?.let { if (none { c -> c.id == it.id }) add(it) } }
                        ordered.forEach { c -> if (size < 5 && none { it.id == c.id }) add(c) }
                    }.toMutableList()
                    // Amount-based nudge: highlighted in place; if it's not among the visible chips,
                    // it takes the last slot so the others don't move under the thumb.
                    val byAmount = if (suggested == null) model.amountSuggestion else null
                    if (byAmount != null && top.none { it.id == byAmount }) {
                        model.category(byAmount)?.let { c -> if (top.isNotEmpty()) top[top.lastIndex] = c else top.add(c) }
                    }
                    top.forEach { c ->
                        val look = CategoryStyle.look(c.id, c.name)
                        CategoryChip(
                            label = if (c.id == suggested) "${c.name} · suggested" else c.name,
                            icon = look.icon,
                            color = look.color,
                            hinted = c.id == byAmount,
                            selected = model.pickedCategory == c.id,
                            onClick = { model.tapCategory(c.id) }
                        )
                    }
                    MoreChip("More", Icons.Outlined.GridView) { showCategories = true }
                } else {
                    val payers = buildList {
                        model.pickedPayer?.let { add(it) }
                        model.payers.forEach { p -> if (size < 4 && none { it.equals(p, true) }) add(p) }
                    }
                    payers.forEach { p -> PayerChip(p, selected = model.pickedPayer.equals(p, true), onClick = { model.tapPayer(p) }) }
                    MoreChip("New", NewIcon) { showNewPayer = true }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── Keypad ──
            Keypad(
                onKey = { model.onKey(it) },
                topAction = { m -> ScanKey(m) { showCamera = true } },
                bottomAction = { m ->
                    Box(m) {
                        SaveKey(Modifier.fillMaxSize(), ready = model.ready) { model.tapSave() }
                        // Not ready: still explain why ("Pick a category first").
                        if (!model.ready) Box(Modifier.matchParentSize().liraClickable { model.tapSave() })
                    }
                },
                showDecimal = model.minorDigits > 0,
                onDoubleZero = { model.onDoubleZero() },
                zerosLabel = if (Money.isHighDenomination(model.entryCurrency)) "000" else "00"
            )
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.navigationBarsPadding().height(FloatingNavHeight))
        }
    }

    // ── Sheets & overlays ──
    if (showToday) {
        TodaySheet(
            model = model,
            onEdit = { id -> showToday = false; navController.navigate(Routes.editTransaction(id)) },
            onLogExpense = { showToday = false; model.setMode(false) },
            onDismiss = { showToday = false }
        )
    }
    if (showCategories) {
        CategorySheet(
            categories = model.orderedCategories,
            amountText = if (model.hasAmount) Money.format(model.value, model.entryCurrency) else null,
            selectedId = model.pickedCategory,
            onPick = { id ->
                showCategories = false
                if (model.hasAmount || model.pickedCategory != id) model.tapCategory(id)
            },
            onEdit = { showCategories = false; navController.navigate(Routes.MANAGE_CATEGORIES) },
            onCreated = { c ->
                model.categories = model.categories + c
                HomeDataCache.categories = model.categories
                showCategories = false
                model.tapCategory(c.id)
            },
            onDismiss = { showCategories = false }
        )
    }
    if (showCurrency) {
        CurrencySheet(
            home = home,
            selected = model.entryCurrency,
            tripCurrencies = trip?.currencies.orEmpty(),
            tripActive = trip != null,
            onPick = { showCurrency = false; model.setCurrency(it) },
            onStartTrip = { showCurrency = false; navController.navigate(Routes.TRIP_START) },
            onDismiss = { showCurrency = false }
        )
    }
    if (showNote) {
        TextEntrySheet(
            title = "Note",
            initial = model.note,
            placeholder = if (model.income) "What was it for?" else "What was it?",
            confirmLabel = "Done",
            onConfirm = { model.note = it; showNote = false },
            onDismiss = { showNote = false }
        )
    }
    if (showNewPayer) {
        TextEntrySheet(
            title = "Who paid you?",
            initial = "",
            placeholder = "Name, or Salary",
            confirmLabel = if (model.hasAmount) "Save" else "Add",
            onConfirm = { name ->
                showNewPayer = false
                if (name.isNotBlank()) {
                    model.payers = (listOf(name) + model.payers).distinctBy { it.lowercase() }
                    if (model.hasAmount) model.save(SaveTarget.Payer(name)) else model.pickedPayer = name
                }
            },
            onDismiss = { showNewPayer = false },
            suggestions = if (model.payers.none { isSalary(it) }) listOf("Salary") else emptyList()
        )
    }
    if (showWhen) {
        WhenPicker(
            initial = model.whenAt,
            onPicked = { model.whenAt = it; showWhen = false },
            onDismiss = { showWhen = false }
        )
    }
    model.pendingConfirm?.let { p ->
        WrongCurrencySheet(
            pending = p,
            home = home,
            alternatives = (trip?.currencies.orEmpty().map { it.uppercase() } + home.uppercase())
                .distinct().filterNot { it.equals(p.currency, true) },
            onKeep = { model.confirmPending() },
            onSwitch = { model.confirmPending(it) },
            onDismiss = { model.cancelPending() }
        )
    }
    if (showCamera) {
        ScanCameraScreen(
            onShutter = { beginCapture() },
            onGallery = { showCamera = false; galleryLauncher.launch("image/*") },
            onClose = { showCamera = false }
        )
    }
}

/** Repeat chip · amount · hint (or conversion line / snackbar) · option chips. */
@Composable
private fun EntryArea(
    model: HomeModel,
    foreign: Boolean,
    home: String,
    onWhen: () -> Unit,
    onCurrency: () -> Unit,
    onNote: () -> Unit
) {
    val snack = model.snack
    var lastSnack by remember { mutableStateOf<SaveSnack?>(null) }
    if (snack != null) lastSnack = snack
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        val repeat = model.repeat
        val showRepeat = !model.income && model.raw.isEmpty() && snack == null && model.flight == null &&
            model.review == null && !model.coach && repeat != null &&
            (repeat.currency ?: home).equals(model.entryCurrency, true)
        if (showRepeat && repeat != null) {
            RepeatChip(repeat, model.category(repeat.categoryId), repeat.currency ?: home) { model.repeatLast() }
        }

        // Travel mode: the trip's currencies are always visible and one tap to switch, so an
        // amount typed in LBP can't silently go in as USD.
        val tripCodes = model.trip?.let { t -> (t.currencies.map { it.uppercase() } + home.uppercase()).distinct() }.orEmpty()
        if (tripCodes.size >= 2 && model.review == null) {
            SegmentedControl(
                options = tripCodes,
                selected = tripCodes.indexOfFirst { it.equals(model.entryCurrency, true) }.coerceAtLeast(-1),
                onSelect = { model.setCurrency(tripCodes[it]) },
                height = 36.dp
            )
        }

        AmountDisplay(raw = model.raw, currencyCode = model.entryCurrency, income = model.income)

        if (snack == null) {
            val flash = model.flash
            when {
                flash != null -> HintText(flash, LiraColors.TextPrimary)
                model.coach && model.raw.isEmpty() -> Text(
                    "Type what you just spent, then tap a category.\nThat’s the whole app.",
                    style = LiraType.BodyM.copy(color = LiraColors.TextSecondary),
                    textAlign = TextAlign.Center
                )
                model.review != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(14.dp))
                    Text("Total from receipt · tap the keypad to correct", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                }
                foreign && model.hasAmount -> ConversionLine(model.value, model.entryCurrency, home, RateCache.cached(model.entryCurrency, home))
                else -> {
                    val hint = when {
                        !model.hasAmount -> if (model.income) "Type an amount, tap who paid you" else "Type an amount, tap a category"
                        model.income && model.pickedPayer != null -> "Save as income from ${model.pickedPayer}"
                        !model.income && model.pickedCategory != null -> "Save to log as ${model.category(model.pickedCategory)?.name ?: "this category"}"
                        model.income -> "Tap who paid you to save"
                        else -> "Tap a category to save"
                    }
                    HintText(hint, if (model.hasAmount) LiraColors.TextSecondary else LiraColors.TextTertiary)
                }
            }
            if (!model.coach) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    OptionChip(whenLabel(model.whenAt), Icons.Outlined.Schedule, edited = model.whenAt != null, onClick = onWhen)
                    OptionChip(
                        model.entryCurrency, Icons.Outlined.Payments,
                        edited = model.currencyOverride != null || model.trip != null,
                        onClick = onCurrency,
                        modifier = if (model.trip != null) Modifier.border(1.dp, TravelTint.copy(alpha = 0.55f), CircleShape) else Modifier
                    )
                    OptionChip(
                        model.note.ifBlank { "Note" }, Icons.Outlined.EditNote,
                        edited = model.note.isNotBlank(), onClick = onNote,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = snack != null,
            enter = if (model.reducedMotion) fadeIn(tween(0)) else fadeIn(tween(200)) + slideInVertically(tween(200, easing = LiraMotion.Standard)) { it / 3 },
            exit = fadeOut(tween(if (model.reducedMotion) 0 else 200))
        ) {
            lastSnack?.let { SaveSnackbar(it, onUndo = { model.undo() }, reducedMotion = model.reducedMotion) }
        }
    }
}

@Composable
private fun HintText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text, style = LiraType.BodyS.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.heightIn(min = 18.dp)
    )
}

/** The saved amount flies up toward the strip, shrinking and fading (60–420 ms). */
@Composable
private fun FlyingAmount(text: String) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(360, easing = LiraMotion.Emphasized)) }
    val rise = with(LocalDensity.current) { 260.dp.toPx() }
    Text(
        text,
        style = LiraType.AmountXl.copy(fontSize = 64.sp, lineHeight = 64.sp),
        maxLines = 1,
        modifier = Modifier.graphicsLayer {
            translationY = -rise * p.value
            val s = 1f - 0.75f * p.value
            scaleX = s
            scaleY = s
            alpha = 1f - p.value
        }
    )
}
