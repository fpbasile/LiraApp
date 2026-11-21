package com.app.lira.ui.screens.home

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.app.lira.data.AppPrefs
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.HomeDataCache
import com.app.lira.data.HomeStats
import com.app.lira.data.HomeStatsRepository
import com.app.lira.data.HomeTxn
import com.app.lira.data.RateCache
import com.app.lira.data.RepeatSuggestion
import com.app.lira.data.WeeklyRecap
import com.app.lira.data.model.Category
import com.app.lira.data.model.Recurring
import com.app.lira.data.model.Trip
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.ReceiptResult
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.data.repository.TripRepository
import com.app.lira.data.repository.toUiCategories
import com.app.lira.ui.components.lira.KeypadKey
import com.app.lira.ui.components.lira.applyKey
import com.app.lira.ui.screens.travel.TravelHandoff
import com.app.lira.ui.screens.travel.TripLocalStore
import com.app.lira.ui.screens.travel.TripMode
import com.app.lira.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** What a save targets: a category (expense) or a payer (income). */
sealed interface SaveTarget {
    data class Cat(val id: Int) : SaveTarget
    data class Payer(val name: String) : SaveTarget
}

/** The settled-with-undo snackbar after a save. */
data class SaveSnack(val title: String, val sub: String?, val over: Boolean, val income: Boolean)

/** The amount text flying from the display into the strip. */
data class Flight(val text: String, val key: Int)

/** Scan review: the quick-add screen pre-filled from a receipt. */
data class ReceiptReview(val merchant: String?, val suggestedCategoryId: Int?)

private class LastSave(
    val tempId: Int,
    val restoreRaw: String,
    val income: Boolean,
    val currencyOverride: String?
) {
    var undone = false
    var snackDismissed = false
}

/**
 * Home / quick-add state. Everything shown on Home (strip, Today sheet, streak, recap, chip order)
 * derives from one list of recent rows ([txns]); saves update it optimistically.
 */
class HomeModel(private val scope: CoroutineScope, val reducedMotion: Boolean) {

    // ── Input ──
    var raw by mutableStateOf("")
    var income by mutableStateOf(false)
    var pickedCategory by mutableStateOf<Int?>(null)
    var pickedPayer by mutableStateOf<String?>(null)
    var note by mutableStateOf("")
    var whenAt by mutableStateOf<LocalDateTime?>(null) // null = now
    var currencyOverride by mutableStateOf<String?>(null)

    // ── Feedback ──
    var flash by mutableStateOf<String?>(null)
    var snack by mutableStateOf<SaveSnack?>(null)
    var flight by mutableStateOf<Flight?>(null)
    var glowBoost by mutableStateOf(false)
    /** Increments on every save (drives the single CONFIRM haptic). */
    var saves by mutableStateOf(0)
    var review by mutableStateOf<ReceiptReview?>(null)
    var scanning by mutableStateOf(false)
    var recap by mutableStateOf<WeeklyRecap?>(null)
    var showRecap by mutableStateOf(false)
    var coach by mutableStateOf(AppPrefs.showFirstLogCoach)

    // ── Data ──
    var categories by mutableStateOf(HomeDataCache.categories)
    var txns by mutableStateOf<List<HomeTxn>>(emptyList())
    var loaded by mutableStateOf(false)
    var dailyLimit by mutableStateOf(HomeDataCache.dailyLimit)
    var weekLimit by mutableStateOf(HomeDataCache.weekLimit)
    var monthLimit by mutableStateOf(HomeDataCache.monthLimit)
    var trip by mutableStateOf<Trip?>(null)
    var recurring by mutableStateOf<List<Recurring>>(emptyList())
    var categoryOrder by mutableStateOf<List<Int>>(emptyList())
    var payers by mutableStateOf<List<String>>(emptyList())
    var repeat by mutableStateOf<RepeatSuggestion?>(null)

    /** A save waiting for the "is this the right currency?" confirmation. */
    var pendingConfirm by mutableStateOf<PendingConfirm?>(null)

    fun confirmPending(useCurrency: String? = null) {
        val p = pendingConfirm ?: return
        pendingConfirm = null
        if (useCurrency != null && !useCurrency.equals(p.currency, true)) {
            setCurrency(useCurrency)
            save(p.target, p.amountOverride, p.descriptionOverride, useCurrency, confirmed = true)
        } else save(p.target, p.amountOverride, p.descriptionOverride, p.currencyOverrideFor, confirmed = true)
    }

    fun cancelPending() { pendingConfirm = null }

    /** Rows saved locally whose count-in hasn't landed yet (hidden from totals during the flight). */
    private val landing = mutableStateListOf<Int>()
    /** temp id (negative) → real id once the insert returns. */
    private val idMap = mutableStateMapOf<Int, Int>()
    private var nextTempId = -1
    private var last: LastSave? = null
    private var saveJob: Job? = null
    private var flashJob: Job? = null
    private var recapChecked = false
    private var tripLoaded = false

    val homeCode: String get() = CurrencyPreference.code
    /** Keypad default: the trip's local currency while a trip runs (travel agent's rules), else home. */
    val defaultCurrency: String get() = trip?.let { TripLocalStore.homeDefaultCurrency(it) } ?: homeCode
    val entryCurrency: String get() = currencyOverride ?: defaultCurrency

    /** Per-day trip budget (home currency) when the trip has a budget and an end date. */
    val tripDailyBudget: Double? get() {
        val t = trip ?: return null
        val budget = TripLocalStore.get(t.id).budget ?: return null
        val total = TripMode.dayOf(t)?.second ?: return null
        return if (total > 0) budget / total else null
    }

    /** The limit "left today" counts against: the trip's daily budget during a trip, else the daily limit. */
    val effectiveDailyLimit: Double get() = tripDailyBudget ?: dailyLimit
    val minorDigits: Int get() = Money.minorDigits(entryCurrency)
    val value: Double get() = raw.toDoubleOrNull() ?: 0.0
    val hasAmount: Boolean get() = value > 0.0

    /** Rows that count right now (excludes ones still in flight). */
    val visibleTxns: List<HomeTxn> by derivedStateOf { txns.filter { it.id !in landing } }

    private val today: LocalDate get() = LocalDate.now()

    val todaySpent: Double by derivedStateOf {
        if (!loaded) HomeDataCache.todaySpent
        else HomeStats.spent(visibleTxns, homeCode, today, today.plusDays(1))
    }
    val weekSpent: Double by derivedStateOf {
        if (!loaded) HomeDataCache.weekSpent
        else HomeStats.weekStart(today).let { HomeStats.spent(visibleTxns, homeCode, it, it.plusDays(7)) }
    }
    val monthSpent: Double by derivedStateOf {
        if (!loaded) HomeDataCache.monthSpent
        else today.withDayOfMonth(1).let { HomeStats.spent(visibleTxns, homeCode, it, it.plusMonths(1)) }
    }
    val loggedDays by derivedStateOf { HomeStats.loggedDays(visibleTxns) }
    val streak: Int by derivedStateOf { HomeStats.streak(loggedDays) }
    val weekLogged: List<Boolean> by derivedStateOf { HomeStats.weekLogged(loggedDays) }

    fun realId(id: Int): Int? = if (id >= 0) id else idMap[id]

    fun category(id: Int?): Category? = id?.let { i -> categories.firstOrNull { it.id == i } }

    /** Category suggested by the amount being typed ("3.20" → Coffee), when history is clear about it. */
    /**
     * Bank balance right now: what the user typed in Wallet, moved by every row logged after it —
     * including the one just saved, before the server has it. Null when no balance is tracked.
     */
    val liveBalance: Double? by derivedStateOf {
        @Suppress("UNUSED_VARIABLE") val v = com.app.lira.data.BalanceRepository.version
        val anchor = com.app.lira.data.BalanceRepository.cached() ?: return@derivedStateOf null
        val zone = java.time.ZoneId.systemDefault()
        val since = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(anchor.at), zone)
        // Home keeps ~120 days of rows; an older balance comes from the Wallet's own fetch.
        if (since.isBefore(LocalDateTime.now().minusDays(110))) return@derivedStateOf com.app.lira.ui.screens.wallet.WalletData.snapshot?.balance
        anchor.amount + txns.sumOf { t ->
            val at = t.local(zone) ?: return@sumOf 0.0
            if (!at.isAfter(since)) 0.0 else {
                val a = HomeStats.homeAmount(t, homeCode) ?: 0.0
                if (t.isIncome) a else -a
            }
        }
    }

    val amountSuggestion: Int? by derivedStateOf {
        if (income || pickedCategory != null || !hasAmount) null
        else HomeStats.categoryForAmount(value, entryCurrency, homeCode, visibleTxns)
    }

    /** Categories ordered by usage (computed on open; never reshuffles while visible). */
    val orderedCategories: List<Category> by derivedStateOf {
        val byId = categories.associateBy { it.id }
        val ordered = categoryOrder.mapNotNull { byId[it] }
        ordered + categories.filter { c -> ordered.none { it.id == c.id } }
    }

    // ───────────────────────────── Loading ─────────────────────────────

    /** Called on every resume. */
    fun refresh() {
        scope.launch {
            val cats = async { CategoryRepository.getCategories() }
            val rows = async { HomeStatsRepository.recent() }
            val daily = async { SpendingLimitRepository.getDailyLimit() }
            val weekly = async { SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_WEEKLY) }
            val monthly = async { SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_MONTHLY) }
            val trips = async { TripRepository.getTrips() }
            val rec = async { RecurringRepository.getRecurring() }

            cats.await().onSuccess { list ->
                categories = list.toUiCategories()
                HomeDataCache.categories = categories
            }
            daily.await().onSuccess { dailyLimit = it; HomeDataCache.dailyLimit = it }
            weekly.await().onSuccess { weekLimit = it; HomeDataCache.weekLimit = it }
            monthly.await().onSuccess { monthLimit = it; HomeDataCache.monthLimit = it }
            trips.await().onSuccess { list ->
                val ended = TripMode.autoEndExpired(list)
                val active = if (ended) null else list.firstOrNull { it.is_active }
                if (tripLoaded && active?.id != trip?.id) currencyOverride = null
                tripLoaded = true
                trip = active
            }
            rec.await().onSuccess { recurring = it }
            consumeHandoff()
            rows.await().onSuccess { server ->
                // Keep local saves still in flight.
                val pending = txns.filter { it.id < 0 && idMap[it.id] == null }
                txns = pending + server
                loaded = true
                val now = LocalDateTime.now()
                categoryOrder = HomeStats.categoryOrder(categories.map { it.id }, server, now)
                payers = HomeStats.payers(server)
                repeat = HomeStats.repeatSuggestion(server, now)
                HomeDataCache.todaySpent = todaySpent
                HomeDataCache.weekSpent = weekSpent
                HomeDataCache.monthSpent = monthSpent
                checkRecap()
            }
            loadRates()
        }
    }

    /** Converter's "Log it": pre-fill the keypad and currency. */
    fun consumeHandoff() {
        val pending = TravelHandoff.consume() ?: return
        clear()
        setCurrency(pending.currency)
        val digits = minorDigits
        val cleaned = pending.amountRaw.replace(',', '.').filter { it.isDigit() || it == '.' }
        val d = cleaned.toDoubleOrNull()
        raw = if (d == null || d <= 0.0) "" else BigDecimal(d).setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /** Fetches rates for every foreign currency in the data and the current entry currency. */
    fun loadRates() {
        scope.launch {
            val home = homeCode
            (HomeStats.foreignCurrencies(txns, home) + entryCurrency.uppercase())
                .filter { !it.equals(home, true) && RateCache.cached(it, home) == null }
                .forEach { RateCache.rate(it, home) }
        }
    }

    private fun checkRecap() {
        if (recapChecked) return
        recapChecked = true
        if (coach) return
        val key = HomeStats.weekKey(today)
        if (AppPrefs.lastRecapWeek == key) return
        AppPrefs.lastRecapWeek = key
        val r = HomeStats.recap(txns, homeCode, weekLimit, dailyLimit) ?: return
        recap = r
        showRecap = true
    }

    // ───────────────────────────── Input ─────────────────────────────

    fun onKey(key: KeypadKey) {
        dismissTransient()
        raw = applyKey(raw, key, minorDigits)
    }

    fun onDoubleZero() {
        dismissTransient()
        if (raw.isEmpty()) return
        repeat(if (Money.isHighDenomination(entryCurrency)) 3 else 2) { raw = applyKey(raw, KeypadKey.Digit('0'), minorDigits) }
    }

    private fun dismissTransient() {
        if (snack != null) snack = null
        last?.snackDismissed = true
        if (showRecap) showRecap = false
        flash = null
    }

    fun setMode(toIncome: Boolean) {
        if (income == toIncome) return
        income = toIncome
        pickedCategory = null
        pickedPayer = null
        flash = null
    }

    fun setCurrency(code: String) {
        trip?.id?.let { id -> if (trip?.currencies?.any { it.equals(code, true) } == true) TripLocalStore.rememberPicked(id, code) }
        currencyOverride = if (code.equals(defaultCurrency, true)) null else code.uppercase()
        if (minorDigits == 0 && raw.contains('.')) raw = raw.substringBefore('.')
        val dot = raw.indexOf('.')
        if (dot >= 0 && raw.length - dot - 1 > minorDigits) raw = raw.substring(0, dot + 1 + minorDigits)
        loadRates()
    }

    /** Fresh expense quick-add (Home tapped while on Home, widget quick-add). */
    fun clear() {
        raw = ""
        income = false
        pickedCategory = null
        pickedPayer = null
        note = ""
        whenAt = null
        currencyOverride = null
        review = null
        flash = null
        snack = null
    }

    fun flashNote(text: String) {
        flash = text
        flashJob?.cancel()
        flashJob = scope.launch { delay(2600); if (flash == text) flash = null }
    }

    fun tapCategory(id: Int) {
        if (hasAmount) save(SaveTarget.Cat(id))
        else {
            pickedCategory = if (pickedCategory == id) null else id
            snack = null
        }
    }

    fun tapPayer(name: String) {
        if (hasAmount) save(SaveTarget.Payer(name))
        else {
            pickedPayer = if (pickedPayer.equals(name, true)) null else name
            snack = null
        }
    }

    val ready: Boolean get() = hasAmount && (if (income) pickedPayer != null else pickedCategory != null)

    fun tapSave() {
        when {
            hasAmount && !income && pickedCategory != null -> save(SaveTarget.Cat(pickedCategory!!))
            hasAmount && income && pickedPayer != null -> save(SaveTarget.Payer(pickedPayer!!))
            hasAmount -> flashNote(if (income) "Pick who paid you first" else "Pick a category first")
        }
    }

    fun repeatLast() {
        val r = repeat ?: return
        save(SaveTarget.Cat(r.categoryId), amountOverride = r.amount, descriptionOverride = r.description, currencyOverrideFor = r.currency ?: homeCode)
    }

    // ───────────────────────────── Save ─────────────────────────────

    fun save(
        target: SaveTarget,
        amountOverride: Double? = null,
        descriptionOverride: String? = null,
        currencyOverrideFor: String? = null,
        confirmed: Boolean = false
    ) {
        val amount = amountOverride ?: value
        if (amount <= 0.0) return
        val isIncome = target is SaveTarget.Payer
        val currency = currencyOverrideFor ?: entryCurrency
        val home = homeCode
        val activeTrip = trip
        val homeAmt = HomeStats.homeAmount(HomeTxn(0, amount, "", "", currency = currency), home) ?: 0.0
        // Wrong-currency guard: a foreign amount that converts to something huge is almost always
        // typed in another currency (e.g. 100,000 meant in LBP, saved as USD). Ask before saving.
        if (!confirmed && !currency.equals(home, true) && homeAmt > maxOf(effectiveDailyLimit * 20, 1000.0)) {
            pendingConfirm = PendingConfirm(target, amountOverride, descriptionOverride, currencyOverrideFor, amount, currency, homeAmt)
            return
        }
        val todayBefore = todaySpent
        val weekBefore = weekSpent
        val description = (descriptionOverride ?: note).trim().ifBlank { null }
        val zone = ZoneId.systemDefault()
        val dateIso = (whenAt ?: LocalDateTime.now()).atZone(zone).toOffsetDateTime().toString()
        val categoryId = (target as? SaveTarget.Cat)?.id
        val payer = (target as? SaveTarget.Payer)?.name
        // Trips keep their currency + trip_id on every entry (as before); home entries store no currency.
        val storedCurrency = when {
            activeTrip != null -> currency
            currency.equals(home, true) -> null
            else -> currency
        }
        val type = if (isIncome) TransactionRepository.TYPE_RECEIVED else TransactionRepository.TYPE_PAYED
        val tempId = nextTempId--
        val txn = HomeTxn(
            id = tempId, amount = amount, type = type, date = dateIso, category_id = categoryId,
            source = payer, description = description, currency = storedCurrency, trip_id = activeTrip?.id
        )

        // Snackbar copy, decided now.
        val amtText = Money.format(amount, currency)
        val title = if (isIncome) {
            "+$amtText" + (if (payer.equals("salary", true)) " salary" else " from $payer") + " saved"
        } else "${category(categoryId)?.name ?: "Entry"} · $amtText saved"
        val after = todayBefore + homeAmt
        val limitToday = effectiveDailyLimit
        val over = !isIncome && limitToday > 0 && after > limitToday + 0.004
        val sub = if (over) {
            val overBy = Money.format(after - limitToday, home, trimZeros = true)
            val wl = weekLimit
            when {
                wl == null -> "That’s $overBy over today."
                wl - (weekBefore + homeAmt) >= 0 -> "That’s $overBy over today — still ${Money.format(wl - (weekBefore + homeAmt), home)} left this week."
                else -> "That’s $overBy over today — and ${Money.format(weekBefore + homeAmt - wl, home)} over this week."
            }
        } else liveBalance?.let { b ->
            val afterBal = b + if (isIncome) homeAmt else -homeAmt
            "${Money.format(afterBal, home)} left on your account."
        }

        val l = LastSave(tempId, if (amountOverride != null) "" else raw, isIncome, currencyOverride)
        last = l

        // Keypad resets at once; the next entry can start immediately.
        raw = ""
        pickedCategory = null
        pickedPayer = null
        note = ""
        whenAt = null
        review = null
        flash = null
        snack = null
        if (showRecap) showRecap = false
        if (coach) { coach = false; AppPrefs.showFirstLogCoach = false }

        saves++
        txns = listOf(txn) + txns
        if (!reducedMotion) landing.add(tempId)
        flight = if (reducedMotion) null else Flight((if (isIncome) "+" else "") + amtText, tempId)

        saveJob?.cancel()
        saveJob = scope.launch {
            if (!reducedMotion) {
                launch { delay(430); if (flight?.key == tempId) flight = null }
                delay(if (isIncome) 100 else 360)
                landing.remove(tempId)
                glowBoost = true
                launch { delay(600); glowBoost = false }
                delay(if (isIncome) 320 else 540)
            } else {
                glowBoost = false
            }
            if (raw.isEmpty() && !l.snackDismissed && !l.undone) snack = SaveSnack(title, sub, over, isIncome)
            delay(4000)
            if (last === l) { snack = null; last = null }
        }

        HomeStatsRepository.writeScope.launch {
            val result = HomeStatsRepository.insert(
                amount = amount, type = type, dateIso = dateIso, categoryId = categoryId, source = payer,
                description = description, tripId = activeTrip?.id, currency = storedCurrency
            )
            result.onSuccess { id ->
                idMap[tempId] = id
                if (l.undone) TransactionRepository.deleteTransaction(id)
            }.onFailure { e ->
                txns = txns.filterNot { it.id == tempId }
                landing.remove(tempId)
                if (!l.undone) {
                    if (last === l) { saveJob?.cancel(); snack = null; last = null }
                    if (raw.isEmpty() && l.restoreRaw.isNotEmpty()) { raw = l.restoreRaw; setMode(l.income) }
                    flashNote(e.message ?: "Couldn't save")
                }
            }
        }
    }

    fun undo() {
        val l = last ?: return
        last = null
        l.undone = true
        saveJob?.cancel()
        snack = null
        flight = null
        glowBoost = false
        txns = txns.filterNot { it.id == l.tempId }
        landing.remove(l.tempId)
        setMode(l.income)
        currencyOverride = l.currencyOverride
        raw = l.restoreRaw
        val real = idMap[l.tempId]
        if (real != null) HomeStatsRepository.writeScope.launch { TransactionRepository.deleteTransaction(real) }
    }

    // ───────────────────────────── Today sheet edits ─────────────────────────────

    /** Deletes a row right away; the caller offers undo via [restore]. */
    fun delete(t: HomeTxn) {
        val id = realId(t.id) ?: return
        txns = txns.filterNot { it.id == t.id }
        HomeStatsRepository.writeScope.launch {
            TransactionRepository.deleteTransaction(id).onFailure { e ->
                txns = (listOf(t) + txns).sortedByDescending { it.date }
                flashNote(e.message ?: "Couldn't delete")
            }
        }
    }

    fun restore(t: HomeTxn) {
        val tempId = nextTempId--
        val copy = t.copy(id = tempId)
        txns = (listOf(copy) + txns).sortedByDescending { it.local() }
        HomeStatsRepository.writeScope.launch {
            HomeStatsRepository.reinsert(t)
                .onSuccess { idMap[tempId] = it }
                .onFailure { txns = txns.filterNot { it.id == tempId } }
        }
    }

    // ───────────────────────────── Scan ─────────────────────────────

    fun applyReceipt(receipt: ReceiptResult) {
        clearForScan()
        receipt.amount?.let { a ->
            val digits = minorDigits
            val s = BigDecimal(a).setScale(digits, RoundingMode.HALF_UP).toPlainString()
            raw = s
        }
        val merchant = receipt.merchant ?: receipt.description
        note = merchant.orEmpty()
        val suggested = receipt.suggestedCategory?.let { s -> categories.firstOrNull { it.name.equals(s, true) } }?.id
        pickedCategory = suggested
        receipt.date?.let { d ->
            val date = try { LocalDate.parse(d.take(10)) } catch (e: DateTimeParseException) { null }
            whenAt = when {
                date == null -> null
                date == LocalDate.now() -> LocalDateTime.now().withSecond(0).withNano(0)
                date.isAfter(LocalDate.now()) -> null
                else -> date.atTime(12, 0)
            }
        }
        review = ReceiptReview(receipt.merchant, suggested)
    }

    private fun clearForScan() {
        income = false
        pickedPayer = null
        snack = null
        flash = null
        showRecap = false
    }
}


/** A save held back by the wrong-currency guard. */
data class PendingConfirm(
    val target: SaveTarget,
    val amountOverride: Double?,
    val descriptionOverride: String?,
    val currencyOverrideFor: String?,
    val amount: Double,
    val currency: String,
    val homeAmount: Double
)
