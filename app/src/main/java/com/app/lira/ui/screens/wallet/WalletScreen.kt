package com.app.lira.ui.screens.wallet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.BalanceRepository
import com.app.lira.data.CashFlow
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.HomeStats
import com.app.lira.data.HomeTxn
import com.app.lira.data.IncomeKinds
import com.app.lira.data.WalletSnapshot
import com.app.lira.data.model.Recurring
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.navigation.HomeEvents
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraMotion
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Loads everything the Wallet needs. Shared with Home's balance pill. */
object WalletData {
    var snapshot by mutableStateOf<WalletSnapshot?>(null)
        private set
    var recurring by mutableStateOf<List<Recurring>>(emptyList())
        private set

    suspend fun refresh() {
        val home = CurrencyPreference.code
        val anchor = BalanceRepository.cached() ?: BalanceRepository.load()
        val rec = RecurringRepository.getRecurring().getOrNull() ?: recurring
        val txns: List<HomeTxn> = com.app.lira.data.HomeStatsRepository.recent(CashFlow.daysNeeded(anchor)).getOrNull() ?: emptyList()
        recurring = rec
        snapshot = CashFlow.snapshot(anchor, rec, txns, home) { t, h -> HomeStats.homeAmount(t, h) }
    }

    /** Fresh anchor from the server (another phone may have adjusted it), then recompute. */
    suspend fun refreshRemote() {
        BalanceRepository.load()
        refresh()
    }
}

private val Mint = Color(0xFF8FC79C)

@Composable
fun WalletScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val home = CurrencyPreference.code
    val snap = WalletData.snapshot

    var editing by remember { mutableStateOf<Recurring?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editBalance by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { WalletData.refreshRemote() }
    LaunchedEffect(HomeEvents.dataVersion, BalanceRepository.version) { WalletData.refresh() }

    fun saveIncome(draft: IncomeDraft, original: Recurring?) {
        scope.launch {
            saving = true; error = null
            val start = if (original != null && IncomeDraft.from(original).let { it.interval == draft.interval && it.day == draft.day }) original.start_date
            else draft.startDate().toString()
            val res = if (original == null) RecurringRepository.addRecurring(draft.name, draft.amount, start, draft.interval, null, TransactionRepository.TYPE_RECEIVED, null).map { }
            else RecurringRepository.updateRecurring(original, draft.name, draft.amount, start, draft.interval, null, TransactionRepository.TYPE_RECEIVED, null)
            saving = false
            res.onSuccess { adding = false; editing = null; WalletData.refresh() }.onFailure { error = it.message }
        }
    }

    LiraScreen(glowTint = Color(0xFF2F5A44), glowIntensity = 1.2f) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(bottom = FloatingNavHeight + 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wallet", style = LiraType.TitleL, modifier = Modifier.weight(1f))
                IconCircleButton(Icons.Outlined.Add, "Add income", { error = null; adding = true }, background = LiraColors.Surface1, size = 44.dp)
            }

            if (snap == null) {
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    Text("Counting…", style = LiraType.BodyM.copy(color = LiraColors.TextTertiary))
                }
                return@Column
            }

            BalanceHero(snap, home, onAdjust = { editBalance = true })
            Spacer(Modifier.height(20.dp))

            if (snap.incomes.isEmpty()) {
                EmptyIncome(onAdd = { adding = true })
            } else {
                PaydayCard(snap, home)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Until payday",
                        snap.perDayUntilPayday?.let { Money.format(it, home, trimZeros = true) } ?: "—",
                        snap.perDayUntilPayday?.let { "a day · ${snap.daysToPayday} days left" } ?: "Add your balance",
                        Modifier.weight(1f),
                        valueColor = if (snap.perDayUntilPayday == 0.0) LiraColors.OverLimit else LiraColors.TextPrimary
                    )
                    StatTile(
                        "End of month",
                        snap.endOfMonth?.let { Money.format(it, home, trimZeros = true) } ?: "—",
                        if (snap.endOfMonth != null) "after incomes and bills" else "Add your balance",
                        Modifier.weight(1f),
                        valueColor = when {
                            snap.endOfMonth == null -> LiraColors.TextPrimary
                            snap.endOfMonth < 0 -> LiraColors.OverLimit
                            else -> LiraColors.TextPrimary
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            MonthCard(snap, home)

            Spacer(Modifier.height(24.dp))
            SectionLabel("Stable income")
            IncomeList(snap, home, onEdit = { error = null; editing = it }, onAdd = { error = null; adding = true })
            Text(
                "Lira logs each one on its day, so your balance and totals stay right without opening your bank.",
                style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }

    if (adding) {
        IncomeSheet(home, null, saving = saving, error = error, onSave = { saveIncome(it, null) }, onDismiss = { if (!saving) adding = false })
    }
    editing?.let { r ->
        IncomeSheet(
            home, IncomeDraft.from(r), saving = saving, error = error,
            onSave = { saveIncome(it, r) },
            onDelete = {
                scope.launch {
                    r.id?.let { RecurringRepository.deleteRecurring(it) }
                    editing = null
                    WalletData.refresh()
                }
            },
            onDismiss = { if (!saving) editing = null }
        )
    }
    if (editBalance) {
        BalanceSheet(
            home, snap?.balance, saving,
            onSave = { v -> scope.launch { saving = true; BalanceRepository.set(v); saving = false; editBalance = false; WalletData.refresh() } },
            onClear = if (snap?.balance != null) ({ scope.launch { BalanceRepository.clear(); editBalance = false; WalletData.refresh() } }) else null,
            onDismiss = { if (!saving) editBalance = false }
        )
    }
}

// ───────────────────────────── Hero ─────────────────────────────

@Composable
private fun BalanceHero(snap: WalletSnapshot, home: String, onAdjust: () -> Unit) {
    val balance = snap.balance
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveDot(balance != null)
            Text(
                "ON YOUR ACCOUNT",
                style = LiraType.Caption.copy(letterSpacing = 0.12.em, color = LiraColors.TextTertiary)
            )
        }
        Spacer(Modifier.height(10.dp))
        if (balance == null) {
            Text(
                "What's on your account?",
                style = LiraType.TitleL.copy(fontFamily = NumberFont, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Type your bank balance once. Lira keeps it live as you log — even before card payments reach your bank.",
                style = LiraType.BodyM.copy(color = LiraColors.TextSecondary)
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Add my balance", onAdjust, Modifier.fillMaxWidth())
        } else {
            AnimatedMoneyText(
                balance, home,
                LiraType.AmountHero.copy(
                    fontSize = heroSize(Money.format(balance, home, trimZeros = true)),
                    color = if (balance < 0) LiraColors.OverLimit else LiraColors.TextPrimary
                ),
                trimZeros = true
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Live with every entry · set ${sinceLabel(snap.anchorAt)}",
                    style = LiraType.BodyS.copy(color = LiraColors.TextTertiary),
                    modifier = Modifier.weight(1f), maxLines = 2
                )
                Row(
                    Modifier.height(34.dp).clip(CircleShape).background(LiraColors.Surface2).liraClickable(onClick = onAdjust).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Edit, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(14.dp))
                    Text("Adjust", style = LiraType.Label.copy(fontSize = 13.sp))
                }
            }
        }
    }
}

private fun heroSize(text: String) = when {
    text.length <= 6 -> 72.sp
    text.length <= 8 -> 60.sp
    text.length <= 10 -> 50.sp
    else -> 40.sp
}

@Composable
private fun LiveDot(on: Boolean) {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(1100), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulse"
    )
    Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) Mint.copy(alpha = pulse) else LiraColors.TextDisabled))
}

private fun sinceLabel(at: Long?): String {
    if (at == null) return ""
    val mins = (System.currentTimeMillis() - at) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} h ago"
        mins < 60 * 24 * 2 -> "yesterday"
        else -> "${mins / (60 * 24)} days ago"
    }
}

// ───────────────────────────── Payday ─────────────────────────────

@Composable
private fun PaydayCard(snap: WalletSnapshot, home: String) {
    val next = snap.nextPayday ?: return
    val items = snap.nextPaydayItems
    val kind = IncomeKinds.of(items.firstOrNull()?.name ?: "")
    val amount = items.sumOf { it.amount }
    val days = snap.daysToPayday ?: 0
    val start = snap.cycleStart ?: next.minusMonths(1)
    val total = ChronoUnit.DAYS.between(start, next).coerceAtLeast(1)
    val elapsed = ChronoUnit.DAYS.between(start, LocalDate.now()).coerceIn(0, total)
    val progress by animateFloatAsState(elapsed.toFloat() / total, tween(700, easing = LiraMotion.Settle), label = "cycle")

    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(kind.color.copy(alpha = 0.14f), LiraColors.Surface1, LiraColors.Surface1), start = Offset.Zero, end = Offset(900f, 600f)))
            .border(1.dp, LiraColors.Line, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KindBadge(kind, 40.dp)
            Column(Modifier.weight(1f)) {
                Text("Next payday", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                Text(items.joinToString(" · ") { it.name }, style = LiraType.BodyL.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.clip(CircleShape).background(LiraColors.Surface3).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    when (days) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "in $days days" },
                    style = LiraType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "+" + Money.format(amount, home, trimZeros = true),
                style = LiraType.AmountXl.copy(color = Mint, fontSize = 40.sp),
                modifier = Modifier.weight(1f), maxLines = 1
            )
            Text(dateLabel(next), style = LiraType.BodyM.copy(color = LiraColors.TextSecondary))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val h = size.height
                drawLine(Color(0x1AFFFFFF), Offset(h / 2, h / 2), Offset(size.width - h / 2, h / 2), strokeWidth = h, cap = StrokeCap.Round)
                val end = h / 2 + (size.width - h) * progress
                drawLine(Brush.horizontalGradient(listOf(kind.color.copy(alpha = 0.5f), kind.color)), Offset(h / 2, h / 2), Offset(end, h / 2), strokeWidth = h, cap = StrokeCap.Round)
            }
            Row {
                Text(dateLabel(start, short = true), style = LiraType.BodyS.copy(fontSize = 11.sp, color = LiraColors.TextTertiary), modifier = Modifier.weight(1f))
                Text(dateLabel(next, short = true), style = LiraType.BodyS.copy(fontSize = 11.sp, color = LiraColors.TextTertiary))
            }
        }
        if (snap.billsBeforePayday > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.EventRepeat, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(16.dp))
                Text(
                    "${Money.format(snap.billsBeforePayday, home, trimZeros = true)} of bills still to go out before then",
                    style = LiraType.BodyS.copy(color = LiraColors.TextSecondary)
                )
            }
        }
    }
}

private fun dateLabel(d: LocalDate, short: Boolean = false): String {
    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    return if (short) "${d.dayOfMonth} $m" else "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} $m"
}

@Composable
private fun StatTile(label: String, value: String, caption: String, modifier: Modifier, valueColor: Color = LiraColors.TextPrimary) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(LiraColors.Surface1).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
        Text(value, style = LiraType.AmountM.copy(color = valueColor, fontSize = if (value.length > 8) 22.sp else 26.sp), maxLines = 1)
        Text(caption, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────────── Month ─────────────────────────────

@Composable
private fun MonthCard(snap: WalletSnapshot, home: String) {
    val month = YearMonth.now().month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val expected = snap.expectedThisMonth
    val received = snap.receivedThisMonth
    val p by animateFloatAsState(if (expected > 0) (received / expected).toFloat().coerceIn(0f, 1f) else 0f, tween(700, easing = LiraMotion.Settle), label = "recv")
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LiraColors.Surface1).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month, style = LiraType.TitleM, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.TrendingUp, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Came in", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                Text(Money.format(received, home, trimZeros = true), style = LiraType.AmountS.copy(color = Mint), maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Went out", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                Text(Money.format(snap.spentThisMonth, home, trimZeros = true), style = LiraType.AmountS, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val net = received - snap.spentThisMonth
                Text("Net", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                Text(
                    (if (net >= 0) "+" else "−") + Money.format(kotlin.math.abs(net), home, trimZeros = true),
                    style = LiraType.AmountS.copy(color = LiraColors.TextPrimary), maxLines = 1
                )
            }
        }
        if (expected > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(LiraColors.Track)) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(CircleShape).background(Mint))
                }
                Text(
                    "${Money.format(received.coerceAtMost(expected), home, trimZeros = true)} of ${Money.format(expected, home, trimZeros = true)} stable income received",
                    style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextSecondary)
                )
            }
        }
    }
}

// ───────────────────────────── Incomes ─────────────────────────────

@Composable
private fun IncomeList(snap: WalletSnapshot, home: String, onEdit: (Recurring) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LiraColors.Surface1)) {
        snap.incomes.forEach { r ->
            val kind = IncomeKinds.of(r.name)
            val d = IncomeDraft.from(r)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 68.dp).liraClickable { onEdit(r) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                KindBadge(kind, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.name, style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(scheduleLabel(d.interval, d.day), style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                }
                Text("+" + Money.format(r.amount, home, trimZeros = true), style = LiraType.AmountXs.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            }
            Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(1.dp).background(LiraColors.Line))
        }
        Row(
            Modifier.fillMaxWidth().height(56.dp).liraClickable(onClick = onAdd).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, LiraColors.Surface4, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
            Text("Add income", style = LiraType.BodyM.copy(color = LiraColors.TextSecondary), modifier = Modifier.weight(1f))
            if (snap.incomes.isNotEmpty()) {
                Text(
                    "${Money.format(snap.monthlyIncome, home, trimZeros = true)} / month",
                    style = LiraType.BodyM.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
private fun EmptyIncome(onAdd: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(LiraColors.Surface1).border(1.dp, LiraColors.Line, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Mint, modifier = Modifier.size(26.dp))
        Text("When does money come in?", style = LiraType.BodyL.copy(fontWeight = FontWeight.SemiBold))
        Text(
            "Add your salary, CAF, APL, bourse… Lira shows your next payday, what you can spend each day until then, and how the month ends.",
            style = LiraType.BodyM.copy(color = LiraColors.TextSecondary)
        )
        PrimaryButton("Add stable income", onAdd, Modifier.fillMaxWidth())
    }
}
