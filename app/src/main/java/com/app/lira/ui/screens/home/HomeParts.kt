package com.app.lira.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.CategoryStyle
import com.app.lira.data.RepeatSuggestion
import com.app.lira.data.WeeklyRecap
import com.app.lira.data.model.Category
import com.app.lira.ui.components.lira.CategoryIconBadge
import com.app.lira.ui.components.lira.liraClickable
import com.app.lira.ui.theme.CategoryPalette
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraMotion
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Travel colour (Stays lavender) — banner, currency chip ring, corner glow in travel mode. */
internal val TravelTint: Color = CategoryPalette.Stays

// ───────────────────────────── Headers ─────────────────────────────

/** Active-trip banner (Travel-Home): opens the trip view. */
@Composable
internal fun TripBanner(name: String, dayLabel: String?, currency: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(44.dp)
            .clip(CircleShape)
            .background(TravelTint.copy(alpha = 0.14f))
            .border(1.dp, TravelTint.copy(alpha = 0.35f), CircleShape)
            .liraClickable(onClick = onClick)
            .semantics { contentDescription = "Open trip $name" }
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val flag = remember(name) { com.app.lira.util.Flags.countriesIn(name).firstOrNull()?.let { com.app.lira.util.Flags.emoji(it) } }
        if (flag != null) Text(flag, style = LiraType.BodyL.copy(fontSize = 18.sp))
        else Icon(Icons.Outlined.Flight, null, tint = TravelTint, modifier = Modifier.size(20.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (dayLabel != null) Text(" · $dayLabel", style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = LiraColors.TextSecondary), maxLines = 1)
        }
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(TravelTint.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(currency, style = LiraType.AmountXs.copy(fontSize = 13.sp))
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

/** First launch header (Empty-First): replaces the strip until the first entry. */
@Composable
internal fun WelcomeHeader(onSetLimit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Welcome to Lira", style = LiraType.BodyL.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Text("Your first entry starts the streak", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
        }
        Row(
            Modifier.height(40.dp).clip(CircleShape).background(LiraColors.Surface2).liraClickable(onClick = onSetLimit).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Speed, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(16.dp))
            Text("Set a daily limit", style = LiraType.Label.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Scan review header: receipt thumbnail card + Retake. */
@Composable
internal fun ReceiptHeader(merchant: String?, onRetake: () -> Unit) {
    Column {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LiraColors.Surface1)
                .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                Modifier.size(40.dp, 52.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE9E6DD)).padding(horizontal = 6.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(0.7f, 0.9f, 0.6f, 0.8f).forEach { w ->
                    Box(Modifier.fillMaxWidth(w).height(3.dp).clip(CircleShape).background(Color(0xFF9A968C)))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Read from your receipt", style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                Text(
                    "Check it, then save." + (merchant?.let { " · $it" } ?: ""),
                    style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.height(40.dp).clip(CircleShape).liraClickable(onClick = onRetake).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("Retake", style = LiraType.Label.copy(fontWeight = FontWeight.SemiBold, color = LiraColors.TextSecondary))
            }
        }
    }
}

// ───────────────────────────── Chips ─────────────────────────────

/** "Lunch · €12.50 again?" — one tap saves it. */
@Composable
internal fun RepeatChip(suggestion: RepeatSuggestion, category: Category?, currency: String, onClick: () -> Unit) {
    val look = CategoryStyle.look(category?.id, category?.name)
    val label = (suggestion.description ?: category?.name ?: "Last entry") + " · " + Money.format(suggestion.amount, currency) + " again?"
    Row(
        Modifier.height(36.dp).clip(CircleShape).background(LiraColors.Surface1).liraClickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryIconBadge(look.icon, look.color, size = 24.dp)
        Text(label, style = LiraType.Label.copy(color = LiraColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
        Icon(Icons.Outlined.Replay, null, tint = LiraColors.TextTertiary, modifier = Modifier.size(16.dp))
    }
}

/** Initial (or wallet icon for salary-like payers) on surface/3. */
@Composable
internal fun PayerBadge(name: String, size: Dp = 32.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
        if (isSalary(name)) Icon(Icons.Outlined.Payments, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(size * 0.53f))
        else Text(name.trim().take(1).uppercase(), style = LiraType.Label.copy(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold))
    }
}

internal fun isSalary(name: String): Boolean {
    val n = name.lowercase(Locale.getDefault())
    return listOf("salary", "salaire", "wage", "payroll", "paycheck", "stipendio").any { n.contains(it) }
}

/** Payer chip for Income ("from who"). Same anatomy as the category chip. */
@Composable
internal fun PayerChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 1500f), label = "payer")
    Row(
        Modifier.scale(scale).height(44.dp).clip(CircleShape)
            .background(if (selected) LiraColors.Surface2 else LiraColors.Surface1)
            .then(if (selected) Modifier.border(1.5.dp, LiraColors.TextPrimary, CircleShape) else Modifier)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PayerBadge(name)
        Text(name, style = LiraType.Label.copy(fontSize = 14.sp), maxLines = 1)
    }
}

/** Trailing "More" / "New" chip. */
@Composable
internal fun MoreChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(LiraColors.Surface1).liraClickable(onClick = onClick)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(LiraColors.Surface3), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        Text(label, style = LiraType.Label.copy(fontSize = 14.sp, color = LiraColors.TextSecondary), maxLines = 1)
    }
}

internal val NewIcon: ImageVector get() = Icons.Outlined.Add

// ───────────────────────────── Feedback ─────────────────────────────

/** Save snackbar: check (or over-limit mark) + title/sub + Undo, 2dp line drains over 4s. */
@Composable
internal fun SaveSnackbar(snack: SaveSnack, onUndo: () -> Unit, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    var started by remember(snack) { mutableStateOf(false) }
    LaunchedEffect(snack) { started = true }
    val drain by animateFloatAsState(
        if (started) 0f else 1f,
        tween(if (reducedMotion) 0 else LiraMotion.UNDO_WINDOW_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing),
        label = "drain"
    )
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LiraColors.Surface3)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (snack.over) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(LiraColors.OverLimitTint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PriorityHigh, null, tint = LiraColors.OverLimit, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(Modifier.size(28.dp).clip(CircleShape).background(LiraColors.Lichen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, tint = LiraColors.Base, modifier = Modifier.size(16.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(snack.title, style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (snack.sub != null) Text(snack.sub, style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
            }
            Row(
                Modifier.height(40.dp).clip(CircleShape).liraClickable(onClick = onUndo).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = LiraColors.TextPrimary, modifier = Modifier.size(18.dp))
                Text("Undo", style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(LiraColors.Line)) {
            Box(Modifier.fillMaxWidth(drain).fillMaxHeight().background(LiraColors.TextSecondary))
        }
    }
}

/** "≈ €7.43   1 EUR = 172.30 JPY" under a foreign amount. */
@Composable
internal fun ConversionLine(amount: Double, from: String, home: String, rate: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(22.dp)) {
        if (rate == null) {
            Text("Getting the rate…", style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
        } else {
            Text("≈ " + Money.format(amount * rate, home), style = LiraType.AmountXs.copy(fontSize = 17.sp, color = LiraColors.TextSecondary))
            val inverse = if (rate != 0.0) 1.0 / rate else 0.0
            val rateText = if (inverse >= 100) "%,.2f".format(inverse) else if (inverse >= 1) "%.2f".format(inverse) else "%.4f".format(inverse)
            Text("1 $home = $rateText $from", style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary, fontFeatureSettings = "tnum"))
        }
    }
}

// ───────────────────────────── Weekly recap ─────────────────────────────

private val recapRange = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** Weekly recap card (Recap-Home): sits where the amount usually is, once per week. */
@Composable
internal fun RecapCard(recap: WeeklyRecap, home: String, category: (Int?) -> Category?, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LiraColors.Surface1).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("YOUR WEEK IN LIRA", style = LiraType.Caption)
                val range = if (recap.start.month == recap.end.month) "${recap.start.dayOfMonth}–${recap.end.format(recapRange)}"
                else "${recap.start.format(recapRange)} – ${recap.end.format(recapRange)}"
                Text(range, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
            }
            Box(
                Modifier.offset(x = 10.dp, y = (-10).dp).size(40.dp).clip(CircleShape).liraClickable(onClick = onDismiss)
                    .semantics { contentDescription = "Dismiss recap" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Close, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(20.dp)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Money.format(recap.spent, home), style = LiraType.AmountXl.copy(fontSize = 44.sp, lineHeight = 44.sp), maxLines = 1)
                val limit = recap.limit
                if (limit != null) {
                    val diff = limit - recap.spent
                    Row {
                        Text("spent · ", style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
                        if (diff >= 0) Text("${Money.format(diff, home, trimZeros = true)} under", style = LiraType.BodyS.copy(color = LiraColors.Lichen, fontWeight = FontWeight.SemiBold))
                        else Text("${Money.format(-diff, home, trimZeros = true)} over", style = LiraType.BodyS.copy(color = LiraColors.OverLimit, fontWeight = FontWeight.SemiBold))
                        Text(" your limit", style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
                    }
                } else Text("spent", style = LiraType.BodyS.copy(color = LiraColors.TextSecondary))
            }
            WeekBars(recap.perDay, Modifier.width(150.dp))
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(LiraColors.Surface2).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("TOP CATEGORY", style = LiraType.Caption)
                val cat = category(recap.topCategoryId)
                if (cat != null) {
                    val look = CategoryStyle.look(cat.id, cat.name)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryIconBadge(look.icon, look.color, size = 28.dp)
                        Column {
                            Text(cat.name, style = LiraType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Money.format(recap.topCategoryAmount, home), style = LiraType.AmountXs.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = LiraColors.TextSecondary))
                        }
                    }
                } else Text("—", style = LiraType.Label.copy(color = LiraColors.TextTertiary))
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(LiraColors.Surface2).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("LOGGING", style = LiraType.Caption)
                Text("${recap.loggedDays} of 7 days", style = LiraType.Label)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    recap.loggedPerDay.forEach { on ->
                        if (on) Box(Modifier.size(6.dp).clip(CircleShape).background(LiraColors.Lichen))
                        else Box(Modifier.size(6.dp).padding(1.dp).border(1.dp, LiraColors.TextDisabled, CircleShape))
                    }
                }
            }
        }
        val change = recap.change
        val changeCat = change?.let { category(it.first) }
        if (change != null && changeCat != null) {
            val pct = kotlin.math.abs(change.second * 100).toInt()
            val less = change.second < 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (less) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp, null,
                    tint = if (less) LiraColors.Lichen else LiraColors.TextSecondary, modifier = Modifier.size(20.dp)
                )
                Text(
                    "$pct% ${if (less) "less" else "more"} on ${changeCat.name.lowercase(Locale.getDefault())} than the week before.",
                    style = LiraType.BodyM.copy(fontSize = 14.sp)
                )
            }
        }
    }
}

@Composable
private fun WeekBars(perDay: List<Double>, modifier: Modifier = Modifier) {
    val max = (perDay.maxOrNull() ?: 0.0).coerceAtLeast(0.01)
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(modifier.semantics { contentDescription = "Spending per day this week" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        perDay.take(7).forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.height(40.dp), contentAlignment = Alignment.BottomCenter) {
                    val h = (40f * (v / max).toFloat()).coerceAtLeast(if (v > 0) 3f else 2f)
                    Box(
                        Modifier.width(14.dp).height(h.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(if (i >= 5) LiraColors.TextPrimary else Color(0xFF4A4A50))
                    )
                }
                Text(labels[i], style = LiraType.Caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp))
            }
        }
    }
}

/** Small spinner + text shown in place of the amount while a receipt is read. */
@Composable
internal fun ReadingReceipt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(LiraColors.Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Receipt, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(24.dp))
        }
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.width(120.dp).height(3.dp).clip(CircleShape),
            color = LiraColors.TextSecondary,
            trackColor = LiraColors.Track
        )
        Text("Reading your receipt…", style = LiraType.BodyM.copy(color = LiraColors.TextSecondary))
    }
}
