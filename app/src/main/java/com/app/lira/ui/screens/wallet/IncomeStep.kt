package com.app.lira.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.app.lira.data.BalanceRepository
import com.app.lira.data.IncomeKind
import com.app.lira.data.IncomeKinds
import com.app.lira.data.repository.RecurringRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.screens.more.MessageLine
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import kotlinx.coroutines.launch

private val Mint = Color(0xFF8FC79C)

/**
 * Onboarding step 3: the steady money coming in (salary, CAF, APL, bourse…) and, optionally,
 * today's bank balance. Nothing is written until "Continue".
 */
@Composable
fun IncomeStep(home: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val drafts = remember { mutableStateListOf<IncomeDraft>() }
    var sheetFor by remember { mutableStateOf<IncomeKind?>(null) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var balanceText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val monthly = drafts.sumOf { if (it.interval == "weekly") it.amount * 52 / 12 else it.amount }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "What comes in?",
                    style = LiraType.TitleL.copy(fontFamily = NumberFont, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-0.02).em)
                )
                Text(
                    "Your steady money — salary, CAF, APL, bourse… Lira logs each one on its day and keeps your bank balance live.",
                    style = LiraType.BodyM.copy(color = LiraColors.TextSecondary)
                )
            }

            // Kind tiles, two per row.
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IncomeKinds.picks.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { k ->
                            val count = drafts.count { IncomeKinds.of(it.name) == k }
                            Row(
                                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (count > 0) k.color.copy(alpha = 0.12f) else LiraColors.Surface1)
                                    .then(if (count > 0) Modifier.border(1.dp, k.color.copy(alpha = 0.5f), RoundedCornerShape(18.dp)) else Modifier)
                                    .liraClickable { error = null; editIndex = null; sheetFor = k }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                KindBadge(k, 32.dp)
                                Text(k.label, style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 1)
                                if (count > 0) Text("✓", style = LiraType.BodyM.copy(color = k.color, fontWeight = FontWeight.SemiBold))
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (drafts.isNotEmpty()) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LiraColors.Surface1)
                ) {
                    drafts.forEachIndexed { i, d ->
                        val k = IncomeKinds.of(d.name)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).liraClickable { error = null; editIndex = i; sheetFor = k }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KindBadge(k, 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(scheduleLabel(d.interval, d.day), style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                            }
                            Text("+" + Money.format(d.amount, home, trimZeros = true), style = LiraType.AmountXs.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LiraColors.Line))
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Every month", style = LiraType.BodyM.copy(color = LiraColors.TextSecondary), modifier = Modifier.weight(1f))
                        Text(Money.format(monthly, home, trimZeros = true), style = LiraType.AmountM.copy(color = Mint))
                    }
                }
            }

            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LiraTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == ' ' || c == '-' }.take(14); error = null },
                    placeholder = "Optional",
                    label = "On your bank account today (${Money.symbol(home)})",
                    keyboardType = KeyboardType.Decimal
                )
                Text(
                    "Lira moves it with every entry, so you always know what's left — even before payments reach your bank.",
                    style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                error?.let { MessageLine(it) }
            }
        }

        PrimaryButton(
            if (drafts.isEmpty() && balanceText.isBlank()) "Not now" else "Continue",
            {
                val negative = balanceText.trim().startsWith("-")
                val balance = balanceText.takeIf { it.isNotBlank() }?.let { parseAmount(it.replace("-", "")) }
                if (balanceText.isNotBlank() && balance == null) { error = "That balance doesn't look like an amount."; return@PrimaryButton }
                scope.launch {
                    saving = true
                    val failed = drafts.mapNotNull { d ->
                        RecurringRepository.addRecurring(d.name, d.amount, d.startDate().toString(), d.interval, null, TransactionRepository.TYPE_RECEIVED, null)
                            .exceptionOrNull()?.message
                    }
                    if (balance != null) BalanceRepository.set(if (negative) -balance else balance)
                    saving = false
                    if (failed.isEmpty()) onDone() else error = failed.first()
                }
            },
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
            loading = saving
        )
    }

    sheetFor?.let { k ->
        val idx = editIndex
        IncomeSheet(
            home = home,
            initial = idx?.let { drafts.getOrNull(it) },
            presetKind = k,
            onSave = { d -> if (idx != null) drafts[idx] = d else drafts.add(d); sheetFor = null; editIndex = null },
            onDelete = idx?.let { { drafts.removeAt(it); sheetFor = null; editIndex = null } },
            onDismiss = { sheetFor = null; editIndex = null }
        )
    }
}
