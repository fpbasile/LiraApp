package com.app.lira.ui.components.lira

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraMotion
import com.app.lira.ui.theme.LiraType
import com.app.lira.util.Money

/*
 * Lira component library (design/reference/Components.dc.html).
 * Every screen builds from these so the app reads as one piece.
 */

// ───────────────────────────── Layout helpers ─────────────────────────────

/** Bare clickable without the Material ripple (Lira uses fills/scale for press feedback). */
@Composable
fun Modifier.liraClickable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/**
 * The faint Cedar corner glow, top-right. [intensity] is 0..1 of the 32% base alpha,
 * pulse it to ~1.9 on save and dim to ~0.4 over limit. [tint] shifts in travel mode.
 */
@Composable
fun CornerGlow(modifier: Modifier = Modifier, intensity: Float = 1f, tint: Color = LiraColors.Cedar) {
    val animated by animateFloatAsState(intensity, tween(600, easing = LiraMotion.Settle), label = "glow")
    Canvas(modifier.fillMaxSize()) {
        val r = 400.dp.toPx()
        val a = (0.32f * animated).coerceIn(0f, 0.7f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to tint.copy(alpha = a),
                0.22f to tint.copy(alpha = a * 0.62f),
                0.48f to tint.copy(alpha = a * 0.3f),
                0.74f to tint.copy(alpha = a * 0.1f),
                1f to Color.Transparent,
                center = Offset(size.width, 0f),
                radius = r
            ),
            radius = r,
            center = Offset(size.width, 0f)
        )
    }
}

/** Standard screen scaffold: base background, corner glow, status-bar inset. */
@Composable
fun LiraScreen(
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    glowIntensity: Float = 1f,
    glowTint: Color = LiraColors.Cedar,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.fillMaxSize().background(LiraColors.Base)) {
        if (glow) CornerGlow(intensity = glowIntensity, tint = glowTint)
        Box(Modifier.fillMaxSize().statusBarsPadding(), content = content)
    }
}

/** Back arrow + title header (56dp). */
@Composable
fun LiraTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = if (onBack != null) 8.dp else 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, background = Color.Transparent)
        Column(Modifier.weight(1f)) {
            Text(title, style = LiraType.TitleM, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = LiraType.BodyS.copy(color = LiraColors.TextTertiary))
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = LiraType.Caption, modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

// ───────────────────────────── Amount ─────────────────────────────

/**
 * Quick-add amount. [raw] is the keypad string ("34.8"). Implied decimals are ghosted
 * ("34.8" + ghost "0"). Steps 88 → 72 → 58 → 46 as it grows. Symbol at 42% size in tertiary.
 */
@Composable
fun AmountDisplay(
    raw: String,
    currencyCode: String,
    modifier: Modifier = Modifier,
    income: Boolean = false,
    maxSize: Int? = null
) {
    val digits = Money.minorDigits(currencyCode)
    val main = if (raw.isEmpty()) "0" else Money.groupRaw(raw)
    val ghost = run {
        if (raw.isEmpty() || digits == 0) ""
        else {
            val dot = raw.indexOf('.')
            if (dot < 0) "" else "0".repeat((digits - (raw.length - dot - 1)).coerceAtLeast(0))
        }
    }
    val chars = main.length + ghost.length
    val target = LiraType.heroSizeFor(chars).let { if (maxSize != null) minOf(it, maxSize) else it }
    val size by animateFloatAsState(target.toFloat(), tween(160, easing = LiraMotion.Standard), label = "amt")
    val style = LiraType.AmountHero.copy(fontSize = size.sp, lineHeight = size.sp)
    val color = if (raw.isEmpty()) LiraColors.TextTertiary else LiraColors.TextPrimary
    Row(modifier.semantics { contentDescription = Money.format(raw.toDoubleOrNull() ?: 0.0, currencyCode) }, verticalAlignment = Alignment.CenterVertically) {
        if (income) Text("+", style = style.copy(color = LiraColors.Lichen, fontSize = (size * 0.6f).sp))
        Text(Money.symbol(currencyCode), style = style.copy(color = LiraColors.TextTertiary, fontSize = (size * 0.42f).sp, lineHeight = (size * 0.42f).sp))
        Spacer(Modifier.width(4.dp))
        Text(main, style = style.copy(color = color), maxLines = 1)
        if (ghost.isNotEmpty()) Text(ghost, style = style.copy(color = LiraColors.TextDisabled), maxLines = 1)
    }
}

// ───────────────────────────── Keypad ─────────────────────────────

sealed interface KeypadKey {
    data class Digit(val d: Char) : KeypadKey
    data object Decimal : KeypadKey
    data object Delete : KeypadKey
}

/** Applies a key to a raw amount string, respecting minor units and the 9-integer-digit cap. */
fun applyKey(raw: String, key: KeypadKey, minorDigits: Int = 2): String = when (key) {
    KeypadKey.Delete -> raw.dropLast(1)
    KeypadKey.Decimal -> if (minorDigits == 0 || raw.contains('.')) raw else (raw.ifEmpty { "0" } + ".")
    is KeypadKey.Digit -> {
        val dot = raw.indexOf('.')
        when {
            dot >= 0 && raw.length - dot - 1 >= minorDigits -> raw
            dot < 0 && raw.replace("^0+".toRegex(), "").length >= 9 -> raw
            raw == "0" -> key.d.toString()
            else -> raw + key.d
        }
    }
}

/**
 * 4×4 keypad. Digits sit on the base (no boxes). Column 4 holds the two tall keys:
 * [topAction] (Scan) on rows 1–2 and [bottomAction] (Save) on rows 3–4.
 */
@Composable
fun Keypad(
    onKey: (KeypadKey) -> Unit,
    topAction: @Composable (Modifier) -> Unit,
    bottomAction: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    showDecimal: Boolean = true,
    /** When [showDecimal] is false (zero-decimal currencies like JPY), an optional "00" key takes the decimal slot. */
    onDoubleZero: (() -> Unit)? = null,
    /** Label of the zeros key: "00", or "000" for high-denomination currencies (LBP, VND…). */
    zerosLabel: String = "00"
) {
    val sep = Money.decimalSeparator().toString()
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "del"))
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k ->
                        val mod = Modifier.weight(1f).fillMaxHeight()
                        when (k) {
                            "." -> when {
                                showDecimal -> KeypadButton(mod, "Decimal point", { onKey(KeypadKey.Decimal) }) { Text(sep, style = LiraType.Keypad) }
                                onDoubleZero != null -> KeypadButton(mod, if (zerosLabel.length == 3) "Triple zero" else "Double zero", onDoubleZero) { Text(zerosLabel, style = LiraType.Keypad) }
                                else -> Spacer(mod)
                            }
                            "del" -> KeypadButton(mod, "Delete", { onKey(KeypadKey.Delete) }) {
                                Icon(Icons.Outlined.Backspace, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(26.dp))
                            }
                            else -> KeypadButton(mod, null, { onKey(KeypadKey.Digit(k[0])) }) { Text(k, style = LiraType.Keypad) }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            topAction(Modifier.fillMaxWidth().height(126.dp))
            bottomAction(Modifier.fillMaxWidth().height(126.dp))
        }
    }
}

@Composable
private fun KeypadButton(modifier: Modifier, label: String?, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberLiraHaptics()
    val bg by animateColorAsState(if (pressed) LiraColors.Surface2 else Color.Transparent, tween(if (pressed) 80 else 120), label = "key")
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(80), label = "keyScale")
    Box(
        modifier.scale(scale).clip(RoundedCornerShape(18.dp)).background(bg)
            .clickable(source, null, role = Role.Button) {
                haptics.key()
                onClick()
            }
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Tall keypad key (Scan / Save). [primary] = white fill (Save ready). */
@Composable
fun TallKey(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        when { primary && enabled -> LiraColors.TextPrimary; else -> LiraColors.Surface1 }, tween(LiraMotion.STANDARD), label = "tall"
    )
    val fg = when { primary && enabled -> LiraColors.Base; enabled -> LiraColors.TextPrimary; else -> LiraColors.TextDisabled }
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg).liraClickable(enabled, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
        Text(label, style = LiraType.Label.copy(fontSize = 12.sp, color = if (primary && enabled) LiraColors.Base else LiraColors.TextSecondary))
    }
}

@Composable
fun ScanKey(modifier: Modifier = Modifier, onClick: () -> Unit) = TallKey(Icons.Outlined.PhotoCamera, "Scan", modifier, onClick = onClick)

@Composable
fun SaveKey(modifier: Modifier = Modifier, ready: Boolean, onClick: () -> Unit) =
    TallKey(Icons.Outlined.Check, "Save", modifier, primary = true, enabled = ready, onClick = onClick)

// ───────────────────────────── Chips ─────────────────────────────

/** Category chip: 44dp tall, icon on an 18% tint of the category colour. */
@Composable
fun CategoryChip(
    label: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Likely pick for the typed amount: tinted outline in the category's colour. */
    hinted: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 1500f), label = "chip")
    val hint by animateFloatAsState(if (hinted && !selected) 1f else 0f, tween(180), label = "chipHint")
    val haptics = rememberLiraHaptics()
    Row(
        modifier.scale(scale).height(44.dp).clip(CircleShape)
            .background(if (selected) LiraColors.Surface3 else LiraColors.Surface1)
            .then(
                when {
                    selected -> Modifier.border(1.5.dp, LiraColors.TextPrimary, CircleShape)
                    hint > 0f -> Modifier.border(1.5.dp, color.copy(alpha = 0.85f * hint), CircleShape)
                    else -> Modifier
                }
            )
            .clickable(source, null, role = Role.Button) { haptics.pick(); onClick() }
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryIconBadge(icon, color, size = 32.dp)
        Text(label, style = LiraType.Label.copy(fontSize = 14.sp), maxLines = 1)
    }
}

/** Round icon on an 18% tint of [color]. */
@Composable
fun CategoryIconBadge(icon: ImageVector, color: Color, size: Dp = 34.dp, dashed: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(
                if (dashed) Modifier.border(1.dp, LiraColors.TextDisabled, CircleShape)
                else Modifier.background(color.copy(alpha = 0.18f))
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (dashed) LiraColors.TextTertiary else color, modifier = Modifier.size(size * 0.53f))
    }
}

/** Plain chip with leading icon/glyph. Used for "More", payers, repeat-last etc. */
@Composable
fun PlainChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    height: Dp = 44.dp
) {
    Row(
        modifier.height(height).clip(CircleShape).background(LiraColors.Surface1).liraClickable(onClick = onClick)
            .padding(start = if (leading != null) 8.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        leading?.invoke()
        Text(label, style = LiraType.Label.copy(fontSize = 14.sp), maxLines = 1)
    }
}

/** Option chip (Now · EUR · Note): quiet when default, filled + white once edited. 36dp. */
@Composable
fun OptionChip(label: String, icon: ImageVector?, edited: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(36.dp).clip(CircleShape)
            .background(if (edited) LiraColors.Surface2 else LiraColors.Surface1)
            .liraClickable(onClick = onClick)
            .padding(start = if (icon != null) 11.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val c = if (edited) LiraColors.TextPrimary else LiraColors.TextSecondary
        if (icon != null) Icon(icon, null, tint = c, modifier = Modifier.size(16.dp))
        Text(label, style = LiraType.Label.copy(color = c), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Lightweight segmented control — no borders, active segment on surface/3. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    background: Color = LiraColors.Surface1
) {
    val haptics = rememberLiraHaptics()
    Row(
        modifier.clip(RoundedCornerShape(height / 2 + 3.dp)).background(background).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            val bg by animateColorAsState(if (active) LiraColors.Surface3 else Color.Transparent, tween(LiraMotion.STANDARD), label = "seg")
            Box(
                Modifier.height(height - 6.dp).clip(CircleShape).background(bg)
                    .liraClickable(role = Role.Tab) { if (i != selected) haptics.tick(); onSelect(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = LiraType.Label.copy(fontSize = 12.sp, color = if (active) LiraColors.TextPrimary else LiraColors.TextTertiary)
                )
            }
        }
    }
}

// ───────────────────────────── Budget ─────────────────────────────

/**
 * Budget ring. Fills with spending in Lichen while on track. Past 100% it turns red and starts a
 * second, inner lap — so over-limit never depends on colour alone.
 */
@Composable
fun BudgetRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    stroke: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 2f), tween(540, easing = LiraMotion.Settle), label = "ring")
    val over = p > 1f
    val color by animateColorAsState(if (over) LiraColors.OverLimit else LiraColors.Lichen, tween(300), label = "ringColor")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            drawArc(LiraColors.Track, 0f, 360f, false, Offset(inset, inset), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw))
            drawArc(color, -90f, 360f * p.coerceAtMost(1f), false, Offset(inset, inset), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
            if (over) {
                val i2 = inset + sw * 1.6f
                drawArc(color, -90f, 360f * (p - 1f), false, Offset(i2, i2), Size(this.size.width - 2 * i2, this.size.height - 2 * i2), style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Thin progress bar (week / month cards). Grey on track, red when over. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, color: Color = LiraColors.TextSecondary) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(540, easing = LiraMotion.Settle), label = "bar")
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(LiraColors.Track)) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(CircleShape).background(if (progress > 1f) LiraColors.OverLimit else color))
    }
}

/** Streak: "12 days" + this week as seven dots (filled = logged). */
@Composable
fun StreakIndicator(days: Int, weekLogged: List<Boolean>, modifier: Modifier = Modifier) {
    Column(modifier.semantics { contentDescription = "$days days logging streak" }, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (days == 1) "1 day" else "$days days", style = LiraType.Label.copy(color = LiraColors.TextSecondary))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            weekLogged.take(7).forEach { on ->
                if (on) Box(Modifier.size(6.dp).clip(CircleShape).background(LiraColors.Lichen))
                else Box(Modifier.size(6.dp).padding(1.dp).border(1.dp, LiraColors.TextDisabled, CircleShape))
            }
        }
    }
}

/**
 * Top strip: ring + "€18 left today" + sub line, streak on the right. Positive framing;
 * when over, the number goes red and the word says "over".
 */
@Composable
fun TopStrip(
    leftAmount: Double,
    limit: Double,
    currencyCode: String,
    periodWord: String,
    subLine: String,
    streakDays: Int,
    weekLogged: List<Boolean>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val over = leftAmount < 0
    Row(
        modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).liraClickable(onClick = onClick).semantics { contentDescription = "Open Today" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val spent = limit - leftAmount
            BudgetRing(if (limit > 0) (spent / limit).toFloat() else 0f, size = 40.dp, stroke = 4.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedMoneyText(
                        kotlin.math.abs(leftAmount), currencyCode,
                        LiraType.AmountS.copy(color = if (over) LiraColors.OverLimit else LiraColors.Lichen),
                        trimZeros = true
                    )
                    Text(if (over) "over $periodWord" else "left $periodWord", style = LiraType.BodyM.copy(color = LiraColors.TextSecondary))
                }
                Text(subLine, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary, fontFeatureSettings = "tnum"))
            }
        }
        StreakIndicator(streakDays, weekLogged)
    }
}

/** Money text that counts to its new value (tween 540, EaseOutCubic). */
@Composable
fun AnimatedMoneyText(value: Double, currencyCode: String, style: TextStyle, modifier: Modifier = Modifier, trimZeros: Boolean = false) {
    val v by animateFloatAsState(value.toFloat(), tween(540, easing = LiraMotion.Settle), label = "count")
    Text(Money.format(v.toDouble(), currencyCode, trimZeros = trimZeros && kotlin.math.abs(v - value.toFloat()) < 0.005f), style = style, maxLines = 1, softWrap = false, modifier = modifier)
}

// ───────────────────────────── Rows & cards ─────────────────────────────

/**
 * Timeline row: time · category icon · description · amount. Income gets a green "+".
 * Scheduled entries are dashed and grey.
 */
@Composable
fun TimelineRow(
    time: String,
    title: String,
    subtitle: String,
    amount: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    income: Boolean = false,
    scheduled: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().height(64.dp)
            .then(if (onClick != null) Modifier.liraClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(time, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary, fontFeatureSettings = "tnum"), modifier = Modifier.width(40.dp))
        CategoryIconBadge(icon, color, 34.dp, dashed = scheduled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LiraType.BodyM.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = if (scheduled) LiraColors.TextTertiary else LiraColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            (if (income) "+" else "") + amount,
            style = LiraType.AmountXs.copy(color = when { scheduled -> LiraColors.TextTertiary; income -> LiraColors.Lichen; else -> LiraColors.TextPrimary })
        )
    }
}

/** Card: surface/1 (or 2 on sheets), radius 20, padding 16. One idea per card. */
@Composable
fun LiraCard(
    modifier: Modifier = Modifier,
    color: Color = LiraColors.Surface1,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color)
            .then(if (onClick != null) Modifier.liraClickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Insight card: icon marker + headline + supporting line. */
@Composable
fun InsightCard(icon: ImageVector, headline: String, body: String?, modifier: Modifier = Modifier, iconTint: Color = LiraColors.TextSecondary) {
    LiraCard(modifier) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Text(headline, style = LiraType.BodyL.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        if (body != null) Text(body, style = LiraType.BodyS)
    }
}

/** List row for settings-style screens. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = LiraColors.TextPrimary,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.liraClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LiraType.BodyM.copy(color = titleColor))
            if (subtitle != null) Text(subtitle, style = LiraType.BodyS.copy(fontSize = 12.sp, color = LiraColors.TextTertiary))
        }
        trailing?.invoke()
    }
}

// ───────────────────────────── Feedback ─────────────────────────────

/** Undo snackbar: never needs dismissing; a 2dp line drains over the undo window. */
@Composable
fun UndoSnackbar(
    title: String,
    subtitle: String?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = LiraMotion.UNDO_WINDOW_MS
) {
    var started by remember(title, subtitle) { mutableStateOf(false) }
    LaunchedEffect(title, subtitle) { started = true }
    val drain by animateFloatAsState(if (started) 0f else 1f, tween(durationMs.toInt(), easing = androidx.compose.animation.core.LinearEasing), label = "drain")
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LiraColors.Surface3)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = LiraType.Label.copy(fontSize = 14.sp))
                if (subtitle != null) Text(subtitle, style = LiraType.BodyS.copy(fontSize = 12.sp))
            }
            Box(
                Modifier.height(40.dp).clip(CircleShape).liraClickable(onClick = onUndo).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Undo", style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) }
        }
        Box(Modifier.fillMaxWidth(drain).height(2.dp).background(LiraColors.TextSecondary))
    }
}

/** Centered empty state: icon in a circle, one line, one action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(LiraColors.Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(24.dp))
        }
        Text(title, style = LiraType.BodyL.copy(fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), textAlign = TextAlign.Center)
        if (body != null) Text(body, style = LiraType.BodyM.copy(color = LiraColors.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        if (actionLabel != null && onAction != null) SecondaryButton(actionLabel, onAction)
    }
}

// ───────────────────────────── Buttons & fields ─────────────────────────────

/** Primary: white pill, near-black text. 52dp. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Box(
        modifier.height(52.dp).clip(CircleShape)
            .background(if (enabled) LiraColors.TextPrimary else LiraColors.Surface2)
            .liraClickable(enabled && !loading, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = LiraColors.Base, strokeWidth = 2.dp)
        else Text(text, style = LiraType.BodyM.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = if (enabled) LiraColors.Base else LiraColors.TextDisabled))
    }
}

/** Secondary: surface/2 pill, white text. 44dp. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, textColor: Color = LiraColors.TextPrimary) {
    Row(
        modifier.height(44.dp).clip(CircleShape).background(LiraColors.Surface2).liraClickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) Icon(icon, null, tint = textColor, modifier = Modifier.size(18.dp))
        Text(text, style = LiraType.BodyM.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = textColor))
    }
}

/** Text-only button. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LiraColors.TextSecondary) {
    Box(modifier.heightIn(min = 44.dp).clip(CircleShape).liraClickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = LiraType.BodyM.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = color))
    }
}

/** Round icon-only button, 48dp. */
@Composable
fun IconCircleButton(icon: ImageVector, label: String, onClick: () -> Unit, background: Color = LiraColors.Surface1, tint: Color = LiraColors.TextPrimary, size: Dp = 48.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).liraClickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Text field: surface/2 pill (radius 16), focused = 1dp white ring. */
@Composable
fun LiraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingIcon: ImageVector? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = LiraType.Label.copy(color = LiraColors.TextSecondary))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = LiraType.BodyM,
            cursorBrush = SolidColor(LiraColors.TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(LiraColors.Surface2)
                        .then(if (focused) Modifier.border(1.dp, LiraColors.TextPrimary, RoundedCornerShape(16.dp)) else Modifier)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (leadingIcon != null) Icon(leadingIcon, null, tint = LiraColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = LiraType.BodyM.copy(color = LiraColors.TextTertiary))
                        inner()
                    }
                    trailing?.invoke()
                }
            }
        )
    }
}

// ───────────────────────────── Sheets ─────────────────────────────

/** Bottom sheet: grabber, 28dp top corners, surface/1, 62% scrim, drag to dismiss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiraSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LiraColors.Surface1,
        scrimColor = LiraColors.Scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(CircleShape).background(LiraColors.TextDisabled)) }
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = LiraType.TitleM, modifier = Modifier.weight(1f))
                IconCircleButton(Icons.Outlined.Close, "Close", onDismiss, background = Color.Transparent, tint = LiraColors.TextSecondary)
            }
        }
        content()
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

// ───────────────────────────── Navigation ─────────────────────────────

data class NavTab(val route: String, val label: String, val icon: ImageVector)

/**
 * Floating nav bar: 56dp, 20dp from sides and bottom, surface/2. Inactive tabs are icons;
 * the active one widens into a filled pill with its label.
 */
@Composable
fun FloatingNavBar(tabs: List<NavTab>, current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)).background(LiraColors.Surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val active = tab.route == current
            val weight by animateFloatAsState(if (active) 1.4f else 1f, tween(LiraMotion.STANDARD, easing = LiraMotion.Standard), label = "navW")
            val bg by animateColorAsState(if (active) LiraColors.Surface4 else Color.Transparent, tween(LiraMotion.STANDARD), label = "navBg")
            Row(
                Modifier.weight(weight).height(48.dp).clip(CircleShape).background(bg)
                    .liraClickable(role = Role.Tab) { onSelect(tab.route) }
                    .semantics { contentDescription = tab.label },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(tab.icon, null, tint = if (active) LiraColors.TextPrimary else LiraColors.TextSecondary, modifier = Modifier.size(22.dp))
                if (active) Text(tab.label, style = LiraType.Label.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            }
        }
    }
}

/** Height the floating nav occupies above the gesture inset — pad scrolling content by this. */
val FloatingNavHeight: Dp = 96.dp

/** "Lira" set in plain type — used on the splash and the welcome screen instead of a drawn logo. */
@Composable
fun LiraWordmark(modifier: Modifier = Modifier, size: Int = 40) {
    Text(
        "Lira",
        modifier = modifier,
        style = LiraType.TitleL.copy(
            fontFamily = com.app.lira.ui.theme.NumberFont,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontSize = size.sp,
            lineHeight = size.sp,
            letterSpacing = (-0.03).em
        )
    )
}

/**
 * Lira's haptic vocabulary, through the view so the system "touch feedback" setting is respected:
 * a crisp tap per key, a light click for picks, a firm confirm on save, a reject buzz when a save
 * goes over the limit, a tick for toggles.
 */
class LiraHaptics(private val view: android.view.View) {
    private fun fire(constant: Int) { view.performHapticFeedback(constant) }
    fun key() = fire(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    fun tick() = fire(android.view.HapticFeedbackConstants.CLOCK_TICK)
    fun pick() = fire(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
    fun undo() = fire(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    fun confirm() = fire(
        if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
        else android.view.HapticFeedbackConstants.LONG_PRESS
    )
    fun reject() = fire(
        if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT
        else android.view.HapticFeedbackConstants.LONG_PRESS
    )
}

@Composable
fun rememberLiraHaptics(): LiraHaptics {
    val view = androidx.compose.ui.platform.LocalView.current
    return remember(view) { LiraHaptics(view) }
}
