package com.app.lira.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.AppPrefs
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.HomeDataCache
import com.app.lira.data.repository.SpendingLimitRepository
import com.app.lira.data.repository.TransactionRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.lira.*
import com.app.lira.ui.screens.more.MessageLine
import com.app.lira.ui.screens.travel.CurrencyPickerSheet
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

/**
 * No feature tour: three questions (home currency, daily limit, stable income + bank balance),
 * then the real Home with a first-log coach card (step 4 of 4, drawn by Home from AppPrefs.showFirstLogCoach).
 */
@Composable
fun OnboardingScreen(navController: NavHostController) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var currency by rememberSaveable { mutableStateOf(suggestedCurrency()) }

    // Existing users who update the app land here once (no onboarding flag yet) — if the account
    // already has entries, skip straight to Home without the coach card.
    var checking by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (!checking) return@LaunchedEffect
        val existing = withTimeoutOrNull(4000) {
            TransactionRepository.getAllTransactions(1).getOrNull()?.isNotEmpty() == true
        } ?: false
        if (existing) {
            AppPrefs.onboardingDone = true
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
                launchSingleTop = true
            }
        } else checking = false
    }
    if (checking) {
        LiraScreen { }
        return
    }

    BackHandler(enabled = step > 0) { step -= 1 }

    fun finish() {
        AppPrefs.onboardingDone = true
        AppPrefs.showFirstLogCoach = true
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.ONBOARDING) { inclusive = true }
            launchSingleTop = true
        }
    }

    LiraScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            // Step header: back · progress (3 steps; the third is the first log on Home) · skip
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(72.dp)) {
                    if (step > 0) IconCircleButton(
                        Icons.AutoMirrored.Outlined.ArrowBack, "Back", { step -= 1 }, background = Color.Transparent
                    )
                }
                Row(
                    Modifier.weight(1f).semantics { contentDescription = "Step ${step + 1} of 4" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    repeat(4) { i ->
                        val c = when {
                            i == step -> LiraColors.TextPrimary
                            i < step -> LiraColors.TextSecondary
                            else -> LiraColors.TextDisabled
                        }
                        Box(Modifier.size(28.dp, 4.dp).clip(CircleShape).background(c))
                    }
                }
                Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
                    when (step) {
                        1 -> GhostButton("Skip", { step = 2 })
                        2 -> GhostButton("Skip", { finish() })
                    }
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160)))
                },
                modifier = Modifier.weight(1f),
                label = "onboarding"
            ) { s ->
                if (s == 0) {
                    CurrencyStep(
                        selected = currency,
                        onSelect = { currency = it },
                        onContinue = { CurrencyPreference.select(currency); step = 1 }
                    )
                } else if (s == 1) {
                    LimitStep(code = currency, onDone = { step = 2 })
                } else {
                    com.app.lira.ui.screens.wallet.IncomeStep(home = currency, onDone = { finish() })
                }
            }
        }
    }
}

private fun suggestedCurrency(): String =
    runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: CurrencyPreference.code

private fun currencyName(code: String): String =
    runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)
        .replaceFirstChar { it.uppercase() }

@Composable
private fun StepTitle(title: String, body: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = LiraType.TitleL.copy(fontFamily = NumberFont, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-0.02).em))
        Text(body, style = LiraType.BodyM.copy(color = LiraColors.TextSecondary))
    }
}

@Composable
private fun CurrencyStep(selected: String, onSelect: (String) -> Unit, onContinue: () -> Unit) {
    val suggested = remember { suggestedCurrency() }
    var showPicker by remember { mutableStateOf(false) }
    val options = remember(selected) {
        (listOf(selected, suggested) + listOf("EUR", "USD", "GBP", "CHF", "JPY")).distinct().take(5)
            .let { list -> listOf(suggested) + list.filter { it != suggested } }.distinct().take(5)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle("What do you spend in?", "Your home currency. Limits and totals use it — trips can switch to others later.")
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { code ->
                    val isSel = code == selected
                    val symbol = Money.symbol(code)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) LiraColors.Surface2 else LiraColors.Surface1)
                            .then(if (isSel) Modifier.border(1.5.dp, LiraColors.TextPrimary, RoundedCornerShape(20.dp)) else Modifier)
                            .liraClickable { onSelect(code) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
                            Text(
                                symbol,
                                style = LiraType.AmountXs.copy(fontSize = if (symbol.length > 2) 12.sp else 17.sp),
                                maxLines = 1
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(currencyName(code), style = LiraType.BodyM.copy(fontWeight = FontWeight.Medium))
                                Text(code, style = LiraType.BodyM.copy(color = LiraColors.TextTertiary))
                            }
                            if (code == suggested) Text("Suggested from your phone’s region", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
                        }
                        if (isSel) Icon(Icons.Outlined.Check, "Selected", tint = LiraColors.TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                Row(
                    Modifier.padding(top = 4.dp).fillMaxWidth().height(52.dp).clip(CircleShape)
                        .border(1.dp, Color(0x24FFFFFF), CircleShape)
                        .liraClickable { showPicker = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Search, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(20.dp))
                    Text("Search all currencies", style = LiraType.BodyM.copy(color = LiraColors.TextTertiary))
                }
            }
        }
        PrimaryButton("Continue", onContinue, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp))
    }

    if (showPicker) {
        CurrencyPickerSheet(
            selected = selected,
            onSelect = { onSelect(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/** Rough "normal day" presets in the home currency (the board's 30/45/60/80 are euro values). */
private fun presetsFor(code: String): List<Double> {
    val factor = when (code.uppercase()) {
        "JPY" -> 150.0; "KRW" -> 1400.0; "INR" -> 80.0; "CNY" -> 7.0; "HKD" -> 8.0
        "SEK", "NOK", "DKK" -> 10.0; "PLN" -> 4.0; "CZK" -> 25.0; "HUF" -> 400.0; "TRY" -> 35.0
        "BRL" -> 5.0; "MXN" -> 20.0; "ZAR" -> 20.0; "THB" -> 35.0; "IDR" -> 16000.0; "VND" -> 25000.0
        "PHP" -> 60.0; "RUB" -> 100.0; "ILS" -> 4.0; "AED", "SAR" -> 4.0; "CAD", "AUD", "NZD", "SGD" -> 1.5
        else -> 1.0
    }
    return listOf(30.0, 45.0, 60.0, 80.0).map { niceRound(it * factor) }
}

private fun niceRound(v: Double): Double {
    if (v < 100) return (v / 5).roundToInt() * 5.0
    val mag = Math.pow(10.0, Math.floor(Math.log10(v)) - 1)
    return (v / mag).roundToInt() * mag
}

@Composable
private fun LimitStep(code: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val digits = Money.minorDigits(code)
    val presets = remember(code) { presetsFor(code) }
    var raw by remember { mutableStateOf(rawOf(presets[2])) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val value = raw.toDoubleOrNull() ?: 0.0

    Column(Modifier.fillMaxSize()) {
        StepTitle("How much for a normal day?", "Every time you open Lira, you’ll see what’s left of it.")
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            AmountDisplay(raw, code, maxSize = 80)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    val sel = value == p
                    Box(
                        Modifier.height(40.dp).clip(CircleShape)
                            .background(if (sel) LiraColors.Surface2 else LiraColors.Surface1)
                            .then(if (sel) Modifier.border(1.5.dp, LiraColors.TextPrimary, CircleShape) else Modifier)
                            .liraClickable { raw = rawOf(p); error = null }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            Money.format(p, code, trimZeros = true),
                            style = LiraType.AmountXs.copy(fontSize = 15.sp, color = if (sel) LiraColors.TextPrimary else LiraColors.TextSecondary)
                        )
                    }
                }
            }
            Text(
                (if (value > 0) "≈ ${Money.format(value * 7, code, trimZeros = true)} a week · ${Money.format(value * 30, code, trimZeros = true)} a month\n" else "") +
                    "You can set each one separately in More.",
                style = LiraType.BodyS.copy(color = LiraColors.TextTertiary, lineHeight = 19.sp, fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center
            )
            if (error != null) MessageLine(error!!)
        }
        Keypad(
            onKey = { key -> error = null; raw = applyKey(raw, key, digits) },
            topAction = { m -> TallKey(Icons.Outlined.DoNotDisturbOn, "No limit", m) { onDone() } },
            bottomAction = { m ->
                TallKey(Icons.Outlined.Check, "Set", m, primary = true, enabled = value > 0 && !saving) {
                    scope.launch {
                        saving = true
                        // Daily drives Home; week and month start from the same pace and can be tuned in More.
                        val daily = async { SpendingLimitRepository.setLimit(SpendingLimitRepository.PERIOD_DAILY, value) }
                        val weekly = async { SpendingLimitRepository.setLimit(SpendingLimitRepository.PERIOD_WEEKLY, value * 7) }
                        val monthly = async { SpendingLimitRepository.setLimit(SpendingLimitRepository.PERIOD_MONTHLY, value * 30) }
                        val results = listOf(daily.await(), weekly.await(), monthly.await())
                        saving = false
                        if (results[0].isSuccess) {
                            HomeDataCache.dailyLimit = value
                            if (results[1].isSuccess) HomeDataCache.weekLimit = value * 7
                            if (results[2].isSuccess) HomeDataCache.monthLimit = value * 30
                            onDone()
                        } else {
                            error = results[0].exceptionOrNull()?.message ?: "Couldn't save your limit."
                        }
                    }
                }
            },
            modifier = Modifier.padding(bottom = 8.dp),
            showDecimal = digits > 0
        )
    }
}

private fun rawOf(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
