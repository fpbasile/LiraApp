package com.app.lira.ui.screens.insights

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.app.lira.data.CategoryLook
import com.app.lira.data.CategoryStyle
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.model.Recurring
import com.app.lira.data.remote.SupabaseClient
import com.app.lira.data.repository.CategoryRepository
import com.app.lira.data.repository.CategoryRow
import com.app.lira.data.repository.MonthlyTotal
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.lira.CategoryIconBadge
import com.app.lira.ui.components.lira.EmptyState
import com.app.lira.ui.components.lira.FloatingNavHeight
import com.app.lira.ui.components.lira.LiraScreen
import com.app.lira.ui.components.lira.SegmentedControl
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraMotion
import com.app.lira.ui.theme.LiraType
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Session cache so switching tabs shows the last numbers instantly while a refresh runs.
 * Keyed to the signed-in user — a different account starts clean.
 */
private object InsightsCache {
    var userId: String? = null
    var dataVersion = 0
    val months = mutableStateMapOf<YearMonth, List<InsightEntry>>()
    var recurring by mutableStateOf<List<Recurring>>(emptyList())
    var all by mutableStateOf<List<InsightEntry>?>(null)
    var allTruncated by mutableStateOf(false)
    var allError by mutableStateOf<String?>(null)
    var totals by mutableStateOf<List<MonthlyTotal>>(emptyList())
    var categories by mutableStateOf<List<CategoryRow>>(emptyList())
    var dailyLimit by mutableStateOf<Double?>(null)
    var weeklyLimit by mutableStateOf<Double?>(null)
    var monthlyLimit by mutableStateOf<Double?>(null)
    var hasExtras = false

    /** "Insights entry" motion plays on the first view per session only. */
    var entryPlayed = false

    fun ensureUser() {
        val id = com.app.lira.data.local.DataMode.userKey()
        if (id != userId || dataVersion != com.app.lira.navigation.HomeEvents.dataVersion) {
            userId = id
            dataVersion = com.app.lira.navigation.HomeEvents.dataVersion
            months.clear()
            recurring = emptyList()
            all = null
            allTruncated = false
            totals = emptyList()
            categories = emptyList()
            dailyLimit = null
            weeklyLimit = null
            monthlyLimit = null
            hasExtras = false
        }
    }
}

private const val ALL_LIMIT = 500L

private fun reducedMotion(context: Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/** Cards rise 8dp + fade, 200ms each with a 40ms stagger — only while [enabled]. */
@Composable
private fun Modifier.riseIn(index: Int, enabled: Boolean): Modifier {
    if (!enabled) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        a.animateTo(1f, tween(200, easing = LiraMotion.Standard))
    }
    val dy = with(LocalDensity.current) { 8.dp.toPx() }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * dy
    }
}

@Composable
fun InsightsScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val code = CurrencyPreference.code
    val reduced = remember { reducedMotion(context) }
    val playEntry = remember { !InsightsCache.entryPlayed && !reduced }

    var today by remember { mutableStateOf(LocalDate.now()) }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = remember(monthKey) { YearMonth.parse(monthKey) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trendMode by rememberSaveable { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    var allQuery by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    suspend fun loadAll() {
        InsightsCache.allError = null
        TransactionRepository.getAllTransactions(ALL_LIMIT)
            .onSuccess { rows ->
                InsightsCache.all = rows.mapNotNull { it.toInsightEntry() }
                InsightsCache.allTruncated = rows.size >= ALL_LIMIT
            }
            .onFailure { InsightsCache.allError = it.message ?: "Something went wrong." }
    }

    /** Loads [m] and what it's compared with. [force] refetches cached months; [extras] refreshes the rest. */
    suspend fun load(m: YearMonth, force: Boolean, extras: Boolean) {
        InsightsCache.ensureUser()
        val now = LocalDate.now()
        today = now
        val thisMonth = YearMonth.from(now)
        val needed = buildSet {
            add(m)
            add(m.minusMonths(1))
            if (m == thisMonth) {
                val ws = lastWeekStart(now)
                add(YearMonth.from(ws))
                add(YearMonth.from(ws.plusDays(6)))
            }
        }
        val toFetch = needed.filter { force || it !in InsightsCache.months }
        val doExtras = extras || !InsightsCache.hasExtras
        if (toFetch.isEmpty() && !doExtras) return
        if (InsightsCache.months[m] == null) loading = true
        error = null
        coroutineScope {
            val monthJobs = toFetch.map { ym -> async { ym to TransactionRepository.getTransactionsForMonth(ym) } }
            if (doExtras) {
                val rec = async { RecurringRepository.getRecurring() }
                val totals = async { TransactionRepository.getMonthlyTotals(monthsBack = 6) }
                val cats = async { CategoryRepository.getCategories() }
                val daily = async { SpendingLimitRepository.getDailyLimit().getOrNull() }
                val weekly = async { SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_WEEKLY).getOrNull() }
                val monthly = async { SpendingLimitRepository.getLimit(SpendingLimitRepository.PERIOD_MONTHLY).getOrNull() }
                val all = async { loadAll() }
                rec.await().onSuccess { InsightsCache.recurring = it }
                totals.await().onSuccess { InsightsCache.totals = it }
                cats.await().onSuccess { InsightsCache.categories = it }
                InsightsCache.dailyLimit = daily.await()
                InsightsCache.weeklyLimit = weekly.await()
                InsightsCache.monthlyLimit = monthly.await()
                all.await()
                InsightsCache.hasExtras = true
            }
            monthJobs.awaitAll().forEach { (ym, result) ->
                result.onSuccess { rows -> InsightsCache.months[ym] = rows.mapNotNull { it.toInsightEntry() } }
                    .onFailure { if (ym == m) error = it.message ?: "Something went wrong." }
            }
        }
        loading = false
    }

    LaunchedEffect(month) {
        selected = if (month == YearMonth.from(LocalDate.now())) LocalDate.now() else null
        load(month, force = !initialized, extras = !initialized)
        initialized = true
    }

    // Refresh on return (after editing an entry, logging on Home, …).
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestMonth by rememberUpdatedState(month)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && initialized) scope.launch { load(latestMonth, force = true, extras = true) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Derived data ──
    val cur = InsightsCache.months[month]
    val prev = InsightsCache.months[month.minusMonths(1)]
    val kind = monthKind(month, today)
    val categories = InsightsCache.categories
    val catIds = remember(categories) { categories.associate { it.name to it.id } }
    val catNames = remember(categories) { categories.associate { it.id to it.name } }
    val lookOf: (String?) -> CategoryLook = { name -> CategoryStyle.look(name?.let { catIds[it] }, name) }
    val lookOfRecurring: (Recurring) -> CategoryLook = { r -> CategoryStyle.look(r.category_id, r.category_id?.let { catNames[it] } ?: r.name) }

    val entries = cur.orEmpty()
    val spendMap = remember(entries) { dailySpend(entries) }
    val incomeMap = remember(entries) { dailyIncome(entries) }
    val due = remember(InsightsCache.recurring, month) { recurringDue(InsightsCache.recurring, month) }
    val recurringDays = remember(entries, due) {
        val map = mutableMapOf<LocalDate, Boolean>()
        due.forEach { (d, list) -> map[d] = (map[d] ?: false) || list.any { it.type == TransactionRepository.TYPE_RECEIVED } }
        entries.filter { it.recurringId != null }.forEach { e -> map[e.date] = (map[e.date] ?: false) || e.income }
        map
    }
    val everything = remember(InsightsCache.all, InsightsCache.months.toMap()) {
        (InsightsCache.all.orEmpty() + InsightsCache.months.values.flatten()).distinctBy { it.id }
    }
    val streak = remember(everything, today) { loggingStreak(everything.map { it.date }.toSet(), today) }
    val insights = remember(month, today, cur, prev, streak, due, code) {
        if (cur == null) emptyList()
        else buildInsights(month, today, cur, prev, streak, due.flatMap { (d, l) -> l.map { d to it } }, code)
    }
    val weeklyLimit = InsightsCache.weeklyLimit ?: InsightsCache.dailyLimit?.let { it * 7 }
    val recap = remember(everything, today, weeklyLimit, kind) {
        if (kind == MonthKind.CURRENT) buildWeeklyRecap(everything, today, weeklyLimit) else null
    }
    val heroTotal = entries.filter { it.countsAsSpend }.sumOf { it.amount }
    val comparison = remember(month, today, cur, prev, code) { cur?.let { heroComparison(month, today, it, prev) } }

    LaunchedEffect(cur != null) { if (cur != null) InsightsCache.entryPlayed = true }

    LiraScreen {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Header(
                month = month,
                onPrev = { monthKey = month.minusMonths(1).toString() },
                onNext = { monthKey = month.plusMonths(1).toString() },
                nextEnabled = month < YearMonth.from(today).plusMonths(12),
                onSearch = { allQuery = ""; showAll = true }
            )

            when {
                cur == null && error != null -> EmptyState(
                    Icons.Outlined.Insights, "Couldn't load ${monthWord(month)}", error,
                    Modifier.padding(top = 80.dp), actionLabel = "Try again",
                    onAction = { scope.launch { load(month, force = true, extras = false) } }
                )
                cur == null -> Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                    if (loading || !initialized) CircularProgressIndicator(Modifier.size(28.dp), color = LiraColors.TextSecondary, strokeWidth = 2.dp)
                }
                else -> {
                    var i = 0
                    Hero(
                        total = heroTotal, kind = kind, month = month, comparison = comparison,
                        monthlyLimit = InsightsCache.monthlyLimit, code = code,
                        countUp = playEntry, reduced = reduced,
                        modifier = Modifier.riseIn(i++, playEntry)
                    )

                    if (insights.isNotEmpty()) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            insights.forEach { ins -> InsightRow(ins, Modifier.riseIn(i++, playEntry)) }
                        }
                    }

                    recap?.let {
                        WeeklyRecapCard(it, code, lookOf, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp).riseIn(i++, playEntry))
                    }

                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MonthCalendarCard(
                            month = month, today = today,
                            spend = spendMap, income = incomeMap, recurringDays = recurringDays,
                            selected = selected,
                            onSelect = { d -> selected = if (selected == d) null else d },
                            modifier = Modifier.riseIn(i++, playEntry)
                        )
                        selected?.takeIf { YearMonth.from(it) == month }?.let { day ->
                            DayEntriesCard(
                                date = day, today = today,
                                entries = entries.filter { it.date == day },
                                due = due[day].orEmpty(),
                                dailyLimit = InsightsCache.dailyLimit,
                                code = code,
                                lookOf = lookOf, lookOfRecurring = lookOfRecurring,
                                onEntry = { id -> navController.navigate(Routes.editTransaction(id)) },
                                onRecurring = { id -> navController.navigate(Routes.recurringEdit(id)) },
                                modifier = Modifier.riseIn(i++, playEntry)
                            )
                        }

                        TrendCard(
                            month = month, today = today, kind = kind, cur = entries, prev = prev,
                            totals = InsightsCache.totals, monthlyLimit = InsightsCache.monthlyLimit,
                            mode = trendMode, onMode = { trendMode = it }, code = code, animate = playEntry,
                            modifier = Modifier.riseIn(i++, playEntry)
                        )

                        val cats = remember(entries) { categoryTotals(entries) }
                        if (cats.isNotEmpty()) {
                            CategoryCard(
                                cats = cats,
                                entryCount = entries.count { it.countsAsSpend },
                                daysElapsed = cutoffDay(month, today).coerceAtLeast(1),
                                code = code, lookOf = lookOf, animate = playEntry,
                                onCategory = { name -> allQuery = if (name == UNCATEGORISED) "" else name; showAll = true },
                                modifier = Modifier.riseIn(i++, playEntry)
                            )
                        }

                        val averages = remember(entries, month, today) { weekdayAverages(entries, month, today) }
                        if (completedDates(month, today).size >= 7 && averages.any { (it ?: 0.0) > 0 }) {
                            Panel(Modifier.riseIn(i++, playEntry)) {
                                CardHeader("By day of week", "Average per day, completed days only")
                                WeekdayChart(averages, code)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(FloatingNavHeight + 8.dp))
        }
    }

    if (showAll) {
        AllEntriesSheet(
            entries = InsightsCache.all,
            error = InsightsCache.allError,
            truncated = InsightsCache.allTruncated,
            initialQuery = allQuery,
            lookOf = lookOf,
            onRetry = { scope.launch { loadAll() } },
            onOpen = { id ->
                showAll = false
                navController.navigate(Routes.editTransaction(id))
            },
            onDismiss = { showAll = false }
        )
        LaunchedEffect(Unit) { if (InsightsCache.all == null) loadAll() }
    }
}

// ───────────────────────────── Header & hero ─────────────────────────────

@Composable
private fun Header(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, nextEnabled: Boolean, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Insights", style = LiraType.TitleL, modifier = Modifier.weight(1f).semantics { heading() })
        RoundIcon(Icons.Outlined.Search, "Search all entries", LiraColors.TextSecondary, onClick = onSearch)
        RoundIcon(Icons.Outlined.ChevronLeft, "Previous month", LiraColors.TextSecondary, onClick = onPrev)
        Text(monthName(month), style = LiraType.Label.copy(fontSize = 14.sp), maxLines = 1)
        RoundIcon(
            Icons.Outlined.ChevronRight, "Next month",
            if (nextEnabled) LiraColors.TextSecondary else LiraColors.TextDisabled,
            enabled = nextEnabled, onClick = onNext
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).liraClickable(enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun Hero(
    total: Double,
    kind: MonthKind,
    month: YearMonth,
    comparison: HeroComparison?,
    monthlyLimit: Double?,
    code: String,
    countUp: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier
) {
    val anim = remember { Animatable(if (countUp) 0f else total.toFloat()) }
    LaunchedEffect(total) {
        if (reduced) anim.snapTo(total.toFloat())
        else anim.animateTo(total.toFloat(), tween(500, easing = LiraMotion.Settle))
    }
    val shown = if (anim.isRunning) anim.value.toDouble() else total
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            money(shown, code),
            style = LiraType.AmountXl,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = "${money(total, code)} spent" }
        )
        val line = buildAnnotatedString {
            append(
                when (kind) {
                    MonthKind.CURRENT -> "spent so far"
                    MonthKind.PAST -> "spent in ${monthWord(month)}"
                    MonthKind.FUTURE -> if (total > 0) "logged ahead" else "Nothing logged yet"
                }
            )
            if (comparison != null) {
                append(" · ")
                if (comparison.less) {
                    withStyle(SpanStyle(color = LiraColors.Lichen, fontWeight = FontWeight.SemiBold)) { append("${moneyWhole(comparison.diff, code)} less") }
                } else {
                    withStyle(SpanStyle(color = LiraColors.TextPrimary, fontWeight = FontWeight.SemiBold)) { append("${moneyWhole(comparison.diff, code)} more") }
                }
                append(" ${comparison.tail}")
            }
        }
        Text(line, style = LiraType.BodyM.copy(fontSize = 14.sp, lineHeight = 20.sp, color = LiraColors.TextSecondary))
        if (monthlyLimit != null && monthlyLimit > 0 && total > monthlyLimit && kind != MonthKind.FUTURE) {
            Text(
                "${money(total - monthlyLimit, code)} over your monthly limit",
                style = LiraType.BodyM.copy(fontSize = 14.sp, lineHeight = 20.sp, color = LiraColors.OverLimit, fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

// ───────────────────────────── Written insight ─────────────────────────────

@Composable
private fun InsightRow(insight: WrittenInsight, modifier: Modifier = Modifier) {
    val icon = when (insight.icon) {
        InsightIcon.WEEKEND -> Icons.Outlined.CalendarMonth
        InsightIcon.UP -> Icons.AutoMirrored.Outlined.TrendingUp
        InsightIcon.DOWN -> Icons.AutoMirrored.Outlined.TrendingDown
        InsightIcon.STREAK -> Icons.Outlined.EventAvailable
        InsightIcon.RECURRING -> Icons.Outlined.Repeat
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LiraColors.Surface1).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (insight.positive) LiraColors.Lichen.copy(alpha = 0.16f) else LiraColors.Surface3),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (insight.positive) LiraColors.Lichen else LiraColors.TextPrimary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(insight.headline, style = LiraType.BodyL.copy(fontWeight = FontWeight.SemiBold, lineHeight = 21.sp))
            Text(insight.body, style = LiraType.BodyS.copy(lineHeight = 19.sp))
        }
    }
}

// ───────────────────────────── Trend ─────────────────────────────

@Composable
private fun TrendCard(
    month: YearMonth,
    today: LocalDate,
    kind: MonthKind,
    cur: List<InsightEntry>,
    prev: List<InsightEntry>?,
    totals: List<MonthlyTotal>,
    monthlyLimit: Double?,
    mode: Int,
    onMode: (Int) -> Unit,
    code: String,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val prevMonth = month.minusMonths(1)
    val hasPrev = prev?.any { it.countsAsSpend } == true
    val showMonth = kind != MonthKind.FUTURE
    if (!showMonth && totals.isEmpty()) return
    val effectiveMode = if (!showMonth) 1 else if (totals.isEmpty()) 0 else mode
    Panel(modifier) {
        val subtitle = when {
            effectiveMode == 1 -> "Spent per month, last ${totals.size} months"
            kind == MonthKind.CURRENT -> if (hasPrev) "Running total this month vs ${monthWord(prevMonth)}" else "Running total this month"
            else -> if (hasPrev) "Running total in ${monthWord(month)} vs ${monthWord(prevMonth)}" else "Running total in ${monthWord(month)}"
        }
        CardHeader("Spending trend", subtitle, trailing = if (showMonth && totals.isNotEmpty()) {
            { SegmentedControl(listOf("Month", "6 mo"), effectiveMode, onMode) }
        } else null)
        if (effectiveMode == 0) {
            val current = remember(cur, month, today) { cumulative(cur, month, cutoffDay(month, today)) }
            val previous = remember(prev, prevMonth) {
                if (hasPrev) cumulative(prev.orEmpty(), prevMonth, prevMonth.lengthOfMonth()) else null
            }
            if ((current.lastOrNull() ?: 0.0) <= 0.0 && previous == null) {
                Text("Nothing spent yet ${if (kind == MonthKind.CURRENT) "this month" else "in ${monthWord(month)}"}.", style = LiraType.BodyS)
            } else {
                SpendingTrendChart(
                    current = current,
                    daysInMonth = month.lengthOfMonth(),
                    previous = previous,
                    previousLabel = if (previous != null) prevMonth.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) else null,
                    limit = monthlyLimit?.takeIf { it > 0 },
                    code = code,
                    animate = animate
                )
            }
        } else {
            SixMonthBars(totals, month, code)
            val net = netLine(totals, if (totals.any { it.yearMonth == month }) month else totals.last().yearMonth)
            if (net != null) {
                val line = buildAnnotatedString {
                    append("Income ${money(net.received, code)} · net ")
                    val netText = (if (net.net >= 0) "+" else "−") + money(abs(net.net), code)
                    withStyle(SpanStyle(color = if (net.net >= 0) LiraColors.Lichen else LiraColors.TextPrimary, fontWeight = FontWeight.SemiBold)) { append(netText) }
                    append(" in ${monthWord(net.month)}")
                    net.vsPrevious?.takeIf { abs(it) >= 0.5 }?.let { d ->
                        append(" · ")
                        if (d > 0) withStyle(SpanStyle(color = LiraColors.Lichen, fontWeight = FontWeight.SemiBold)) { append("${moneyWhole(d, code)} better") }
                        else withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = LiraColors.TextPrimary)) { append("${moneyWhole(-d, code)} lower") }
                        append(" than ${monthWord(net.month.minusMonths(1))}")
                    }
                }
                Text(line, style = LiraType.BodyS)
            }
        }
    }
}

// ───────────────────────────── Categories ─────────────────────────────

@Composable
private fun CategoryCard(
    cats: List<Pair<String, Double>>,
    entryCount: Int,
    daysElapsed: Int,
    code: String,
    lookOf: (String?) -> CategoryLook,
    animate: Boolean,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = cats.sumOf { it.second }
    val top = cats.take(3)
    val others = cats.drop(3)
    val othersTotal = others.sumOf { it.second }
    fun pct(v: Double) = (v / total * 100).roundToInt()
    fun colorOf(name: String) = lookOf(name.takeIf { it != UNCATEGORISED }).color

    Panel(modifier) {
        CardHeader("By category", "${entriesWord(entryCount)} · ${moneyWhole(total / daysElapsed, code)} a day on average")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val slices = top.map { colorOf(it.first) to it.second } + (if (othersTotal > 0) listOf(BarIdle to othersTotal) else emptyList())
            val description = (top.map { "${it.first} ${pct(it.second)}%" } + (if (othersTotal > 0) listOf("others ${pct(othersTotal)}%") else emptyList()))
                .joinToString(", ", prefix = "Category share: ")
            CategoryDonut(
                slices = slices,
                centerValue = moneyWhole(total, code),
                centerCaption = if (cats.size == 1) "1 category" else "${cats.size} categories",
                description = description,
                animate = animate
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEach { (name, v) -> LegendLine(colorOf(name), "$name · ${pct(v)}%") }
                if (othersTotal > 0) LegendLine(BarIdle, "${others.size} ${if (others.size == 1) "other" else "others"} · ${pct(othersTotal)}%")
            }
        }
        val max = cats.first().second
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cats.forEach { (name, v) ->
                val look = lookOf(name.takeIf { it != UNCATEGORISED })
                Row(
                    Modifier.fillMaxWidth().height(40.dp).liraClickable { onCategory(name) }
                        .semantics { contentDescription = "$name ${money(v, code)}, ${pct(v)}%. Show entries" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryIconBadge(look.icon, look.color, size = 28.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = LiraType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(
                                buildAnnotatedString {
                                    append(money(v, code))
                                    withStyle(SpanStyle(color = LiraColors.TextTertiary, fontWeight = FontWeight.Medium)) { append(" ${pct(v)}%") }
                                },
                                style = LiraType.AmountXs.copy(fontSize = 13.sp, lineHeight = 16.sp)
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(LiraColors.Line)) {
                            Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(CategoryBar))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendLine(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(text, style = LiraType.BodyS.copy(fontSize = 12.sp, lineHeight = 16.sp, color = LiraColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
