package com.app.lira.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.lira.data.AppPrefs
import com.app.lira.data.CurrencyPreference
import com.app.lira.data.model.Trip
import com.app.lira.data.repository.TripRepository
import com.app.lira.navigation.Routes
import com.app.lira.ui.components.lira.Keypad
import com.app.lira.ui.components.lira.KeypadKey
import com.app.lira.ui.components.lira.LiraScreen
import com.app.lira.ui.components.lira.LiraTopBar
import com.app.lira.ui.components.lira.TallKey
import com.app.lira.ui.components.lira.applyKey
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import com.app.lira.util.Money
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Keypad converter (Converter board). Type in the top currency; the bottom one follows live.
 * "Log it" hands the typed amount + currency to Home via [TravelHandoff] and opens Home.
 */
@Composable
fun ConverterScreen(navController: NavHostController) {
    val home = CurrencyPreference.code
    var activeTrip by remember { mutableStateOf<Trip?>(null) }
    var from by remember { mutableStateOf(home) }
    var to by remember { mutableStateOf(defaultTarget(home, null)) }
    var raw by remember { mutableStateOf("100") }
    var fresh by remember { mutableStateOf(true) } // first key replaces the sample amount
    var table by remember { mutableStateOf(TravelRates.cached(home)) }
    var ratesError by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = from, false = to

    LaunchedEffect(Unit) {
        TripRepository.getTrips().onSuccess { trips ->
            val trip = trips.firstOrNull { it.is_active }
            activeTrip = trip
            val local = trip?.currencies?.firstOrNull { it != home }
            if (local != null) { from = local; to = home }
        }
    }
    LaunchedEffect(home) {
        TravelRates.latest(home).onSuccess { table = it; ratesError = false }.onFailure { ratesError = table == null }
    }

    val amount = raw.toDoubleOrNull() ?: 0.0
    val converted = table?.convert(amount, from, to)
    val chips = remember(from, to, activeTrip) {
        (activeTrip?.currencies.orEmpty() + AppPrefs.pinnedCurrencies + listOf(home, "USD", "EUR", "GBP"))
            .map { it.uppercase() }.distinct().filter { it != from && it != to }.take(4)
    }

    fun onKey(key: KeypadKey) {
        val base = if (fresh) "" else raw
        fresh = false
        raw = applyKey(base, key, Money.minorDigits(from))
    }

    LiraScreen(glow = false) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            LiraTopBar("Converter", onBack = { navController.popBackStack() })
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountCard(
                    code = from,
                    text = if (raw.isEmpty()) "0" else Money.groupRaw(raw),
                    primary = true,
                    onPick = { picking = true }
                )
                AmountCard(
                    code = to,
                    text = converted?.let { plainAmount(it, to) } ?: if (ratesError) "—" else "…",
                    primary = false,
                    onPick = { picking = false }
                )
            }
            val t = table
            Text(
                when {
                    t != null -> rateLine(t, from, to)
                    ratesError -> "Rates unavailable — check your connection."
                    else -> "Loading rates…"
                },
                style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary, fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
            )
            if (t != null && amount > 0) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { code ->
                        val v = t.convert(amount, from, code) ?: return@forEach
                        Box(
                            Modifier.height(36.dp).clip(CircleShape).background(LiraColors.Surface1)
                                .liraClickable { to = code }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${Money.format(amount, from, trimZeros = true)} → ${Money.format(v, code)}",
                                style = LiraType.Label.copy(color = LiraColors.TextSecondary, fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Keypad(
                onKey = ::onKey,
                showDecimal = Money.minorDigits(from) > 0,
                topAction = { m ->
                    TallKey(Icons.Outlined.SwapVert, "Swap", m) { val f = from; from = to; to = f }
                },
                bottomAction = { m ->
                    TallKey(Icons.Outlined.AddCircleOutline, "Log it", m, enabled = amount > 0) {
                        TravelHandoff.pendingLog = PendingLog(raw, from)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }

    picking?.let { isFrom ->
        CurrencyPickerSheet(
            selected = if (isFrom) from else to,
            onSelect = { code ->
                if (isFrom) {
                    if (code == to) to = from
                    from = code
                    if (Money.minorDigits(code) == 0) raw = raw.substringBefore('.')
                } else {
                    if (code == from) from = to
                    to = code
                }
            },
            onDismiss = { picking = null }
        )
    }
}

private fun defaultTarget(home: String, trip: Trip?): String =
    trip?.currencies?.firstOrNull { it != home }
        ?: AppPrefs.pinnedCurrencies.firstOrNull { it != home }
        ?: if (home == "USD") "EUR" else "USD"

@Composable
private fun AmountCard(code: String, text: String, primary: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .then(if (primary) Modifier.background(LiraColors.Surface1) else Modifier.border(1.dp, Color(0x24FFFFFF), shape))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.height(32.dp).clip(CircleShape).liraClickable(onClick = onPick)
                .semantics { contentDescription = "Change currency, now $code" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
                val g = CurrencyCatalog.glyph(code)
                Text(g, style = LiraType.Label.copy(fontFamily = NumberFont, fontWeight = FontWeight.SemiBold, fontSize = if (g.length > 2) 9.sp else 14.sp), maxLines = 1)
            }
            Text(code, style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            Text(
                CurrencyCatalog.name(code), style = LiraType.Label.copy(color = LiraColors.TextTertiary, fontWeight = FontWeight.Normal),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Outlined.ExpandMore, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
        val max = if (primary) 52 else 44
        val size = when {
            text.length <= 7 -> max
            text.length <= 10 -> max - 12
            else -> max - 20
        }
        Text(
            text,
            style = LiraType.AmountXl.copy(
                fontSize = size.sp, lineHeight = (size * 1.05).sp,
                color = if (primary) LiraColors.TextPrimary else LiraColors.TextSecondary
            ),
            maxLines = 1
        )
    }
}

/** Number without symbol, minor units of [code]; tiny values keep 4 decimals. */
private fun plainAmount(v: Double, code: String): String {
    val fmt = NumberFormat.getNumberInstance()
    val digits = if (abs(v) in 0.0000001..0.99) 4 else Money.minorDigits(code)
    fmt.minimumFractionDigits = digits
    fmt.maximumFractionDigits = digits
    return fmt.format(v)
}

private fun rateNumber(r: Double): String {
    if (r <= 0.0 || r.isNaN() || r.isInfinite()) return "0"
    val fmt = NumberFormat.getNumberInstance()
    val digits = when {
        r >= 100 -> 2
        r >= 1 -> 4
        else -> (-kotlin.math.floor(kotlin.math.log10(r)).toInt() + 2).coerceIn(4, 10)
    }
    fmt.minimumFractionDigits = digits
    fmt.maximumFractionDigits = digits
    return fmt.format(r)
}

private fun rateLine(t: RateTable, from: String, to: String): String {
    val time = Instant.ofEpochMilli(t.fetchedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    val ab = t.convert(1.0, from, to) ?: return "No rate for $from → $to"
    val ba = t.convert(1.0, to, from)
    return buildString {
        append("1 $from = ${rateNumber(ab)} $to")
        if (ba != null) append(" · 1 $to = ${rateNumber(ba)} $from")
        append(" · updated $time")
    }
}
