package com.app.lira.ui.screens.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.lira.data.repository.MonthlyTotal
import com.app.lira.ui.theme.InterFont
import com.app.lira.ui.theme.LiraColors
import com.app.lira.ui.theme.LiraMotion
import com.app.lira.ui.theme.LiraType
import com.app.lira.ui.theme.NumberFont
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Bars that aren't the point of the chart. */
internal val BarIdle = Color(0xFF4A4A50)
/** Last month's line on the trend chart. */
internal val PreviousLine = Color(0xFF5E5E66)
/** Category bars stay neutral; the icon badge carries the colour. */
internal val CategoryBar = Color(0xFFD4D4D8)
private val GridLine = Color(0x0FFFFFFF)
private val CursorLine = Color(0x1FFFFFFF)

private val chartLabel = TextStyle(fontFamily = InterFont, fontSize = 11.sp, color = LiraColors.TextTertiary)
private val chartNumber = TextStyle(fontFamily = NumberFont, fontSize = 11.sp, color = LiraColors.TextTertiary, fontFeatureSettings = "tnum")

/** Line-draw progress 0→1 over 600ms when [animate], else already drawn. */
@Composable
private fun rememberDrawProgress(animate: Boolean): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) progress.animateTo(1f, tween(600, easing = LiraMotion.Standard)) }
    return progress
}

/**
 * Running total this month (white) against last month (grey), with the monthly limit as a dashed
 * line when one is set. [current] holds cumulative spend for each day so far.
 */
@Composable
fun SpendingTrendChart(
    current: List<Double>,
    daysInMonth: Int,
    previous: List<Double>?,
    previousLabel: String?,
    limit: Double?,
    code: String,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val progress = rememberDrawProgress(animate)
    val description = buildString {
        append("Cumulative spending: ")
        append(if (current.isNotEmpty()) money(current.last(), code) else "nothing yet")
        val p = previous?.getOrNull(current.size - 1)
        if (p != null && previousLabel != null) append(" vs $previousLabel ${money(p, code)} at the same point")
    }
    Canvas(modifier.fillMaxWidth().height(176.dp).semantics { contentDescription = description }) {
        val labelW = 46.dp.toPx()
        val plotW = size.width - labelW
        val top = 14.dp.toPx()
        val bottom = 152.dp.toPx()
        val maxV = listOfNotNull(current.maxOrNull(), previous?.maxOrNull(), limit).maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val yMax = if (limit != null && limit >= maxV) maxV else maxV * 1.08
        fun x(i: Int) = if (daysInMonth <= 1) 0f else i.toFloat() / (daysInMonth - 1) * plotW
        fun y(v: Double) = bottom - (v / yMax).toFloat() * (bottom - top)
        val stroke = 2.dp.toPx()

        // Grid.
        listOf(1f / 3f, 2f / 3f).forEach { f ->
            val gy = bottom - f * (bottom - top)
            drawLine(GridLine, Offset(0f, gy), Offset(plotW, gy), 1.dp.toPx())
        }

        // Limit.
        var limitLabelY: Float? = null
        if (limit != null && limit > 0) {
            val ly = y(limit)
            drawLine(
                LiraColors.TextDisabled, Offset(0f, ly), Offset(plotW, ly), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()), 0f)
            )
            val t = measurer.measure("Limit", chartLabel)
            limitLabelY = ly - t.size.height / 2f
            drawText(t, topLeft = Offset(plotW + 6.dp.toPx(), limitLabelY))
        }

        fun pathOf(values: List<Double>): Path = Path().apply {
            values.take(daysInMonth).forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }

        clipRect(right = plotW * progress.value + stroke) {
            if (previous != null && previous.size > 1) {
                drawPath(pathOf(previous), PreviousLine, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (current.size > 1) {
                drawPath(pathOf(current), LiraColors.TextPrimary, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Last month's name at the end of its line, nudged clear of the limit label.
        if (previous != null && previous.isNotEmpty() && previousLabel != null && progress.value >= 1f) {
            val t = measurer.measure(previousLabel, chartLabel)
            var py = y(previous.take(daysInMonth).last()) - t.size.height / 2f
            limitLabelY?.let { if (kotlin.math.abs(py - it) < 13.dp.toPx()) py = it + 13.dp.toPx() }
            drawText(t, topLeft = Offset(plotW + 6.dp.toPx(), py))
        }

        // Today marker.
        if (current.isNotEmpty() && progress.value >= 1f) {
            val i = current.size - 1
            val cx = x(i)
            val cy = y(current.last())
            drawLine(CursorLine, Offset(cx, top), Offset(cx, bottom), 1.dp.toPx())
            drawCircle(LiraColors.Surface1, 7.dp.toPx(), Offset(cx, cy))
            drawCircle(LiraColors.TextPrimary, 5.dp.toPx(), Offset(cx, cy))
            val label = measurer.measure(
                moneyWhole(current.last(), code),
                chartNumber.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LiraColors.TextPrimary)
            )
            val lx = if (cx + 10.dp.toPx() + label.size.width <= size.width) cx + 10.dp.toPx() else cx - 10.dp.toPx() - label.size.width
            drawText(label, topLeft = Offset(lx, cy - label.size.height / 2f))

            val pv = previous?.getOrNull(i)
            if (pv != null) {
                val pl = measurer.measure(moneyWhole(pv, code), chartNumber)
                val py = y(pv)
                // Above the grey line, unless that would sit on the white dot's label.
                var ty = py - 8.dp.toPx() - pl.size.height
                if (kotlin.math.abs((ty + pl.size.height / 2f) - cy) < 14.dp.toPx()) ty = py + 6.dp.toPx()
                val tx = (cx - pl.size.width / 2f).coerceIn(0f, size.width - pl.size.width)
                drawText(pl, topLeft = Offset(tx, ty.coerceAtLeast(0f)))
            }
        }

        // Day axis.
        listOf(1, 8, 15, 22, 29).filter { it <= daysInMonth }.forEach { d ->
            val t = measurer.measure(d.toString(), chartLabel)
            val cx = x(d - 1)
            val lx = if (d == 1) cx else (cx - t.size.width / 2f)
            drawText(t, topLeft = Offset(lx.coerceIn(0f, size.width - t.size.width), bottom + 8.dp.toPx()))
        }
    }
}

/** Donut: top three categories in colour, the rest one grey segment. 3dp gaps between segments. */
@Composable
fun CategoryDonut(
    slices: List<Pair<Color, Double>>,
    centerValue: String,
    centerCaption: String,
    description: String,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = rememberDrawProgress(animate)
    Box(modifier.size(136.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 18.dp.toPx()
            val r = size.minDimension / 2f - sw / 2f
            val total = slices.sumOf { it.second }.takeIf { it > 0 } ?: return@Canvas
            val circumference = (2 * Math.PI * r).toFloat()
            val gapDeg = if (slices.size > 1) 3.dp.toPx() / circumference * 360f else 0f
            var start = -90f
            val tl = Offset(center.x - r, center.y - r)
            val arcSize = Size(2 * r, 2 * r)
            val budget = 360f * progress.value
            slices.forEach { (color, v) ->
                val full = (v / total * 360f).toFloat()
                val sweep = (full - gapDeg).coerceAtLeast(0.5f)
                val visible = (budget - (start + 90f)).coerceIn(0f, sweep)
                if (visible > 0f) drawArc(color, start, visible, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Butt))
                start += full
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerValue, style = LiraType.AmountXs.copy(fontSize = 20.sp, lineHeight = 24.sp))
            Text(centerCaption, style = LiraType.BodyS.copy(fontSize = 11.sp, lineHeight = 14.sp, color = LiraColors.TextTertiary))
        }
    }
}

/** A horizontal dashed rule. */
@Composable
private fun DashedRule(modifier: Modifier = Modifier, color: Color = LiraColors.TextDisabled) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), 0f)
        )
    }
}

/**
 * Average spend per weekday. The biggest days are white with their value on top; the dashed rule
 * marks the weekday (Mon–Fri) average.
 */
@Composable
fun WeekdayChart(averages: List<Double?>, code: String, modifier: Modifier = Modifier) {
    val values = averages.map { it ?: 0.0 }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val weekdayAvg = averages.take(5).filterNotNull().takeIf { it.isNotEmpty() }?.average()
    val barArea = 120.dp
    val barMax = 111.dp
    val names = DayOfWeek.entries.map { it.getDisplayName(JTextStyle.SHORT, Locale.getDefault()) }
    val description = DayOfWeek.entries.mapIndexedNotNull { i, d ->
        averages[i]?.let { "${d.getDisplayName(JTextStyle.FULL, Locale.getDefault())} ${moneyWhole(it, code)}" }
    }.joinToString(", ", prefix = "Average spend per day of week: ")
    Box(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEachIndexed { i, v ->
                val highlight = v > 0 && v >= max * 0.9
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(16.dp), contentAlignment = Alignment.BottomCenter) {
                        if (highlight) Text(
                            moneyWhole(v, code),
                            style = LiraType.AmountXs.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            maxLines = 1, softWrap = false
                        )
                    }
                    Box(Modifier.height(barArea), contentAlignment = Alignment.BottomCenter) {
                        val h = barMax * (v / max).toFloat()
                        if (averages[i] != null) Box(
                            Modifier.width(28.dp).height(if (v > 0) h.coerceAtLeast(2.dp) else 2.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (highlight) LiraColors.TextPrimary else BarIdle)
                        )
                    }
                    Text(
                        names[i],
                        style = LiraType.Label.copy(
                            fontSize = 11.sp, lineHeight = 14.sp,
                            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (highlight) LiraColors.TextPrimary else LiraColors.TextTertiary
                        ),
                        maxLines = 1
                    )
                }
            }
        }
        if (weekdayAvg != null && weekdayAvg > 0) {
            val lineTop = 16.dp + 6.dp + barArea - barMax * (weekdayAvg / max).toFloat()
            DashedRule(Modifier.offset(y = lineTop))
            Text(
                "weekday avg ${moneyWhole(weekdayAvg, code)}",
                style = LiraType.BodyS.copy(fontSize = 11.sp, lineHeight = 14.sp, color = LiraColors.TextTertiary),
                modifier = Modifier.offset(y = lineTop - 18.dp).background(LiraColors.Surface1).padding(end = 6.dp)
            )
        }
    }
}

/** Spend per month for the last months; [highlight] (the month on screen) is white. */
@Composable
fun SixMonthBars(totals: List<MonthlyTotal>, highlight: YearMonth, code: String, modifier: Modifier = Modifier) {
    val max = totals.maxOfOrNull { it.spent }?.takeIf { it > 0 } ?: 1.0
    val highlighted = if (totals.any { it.yearMonth == highlight }) highlight else totals.lastOrNull()?.yearMonth
    val description = totals.joinToString(", ", prefix = "Spending per month: ") {
        "${it.yearMonth.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault())} ${moneyWhole(it.spent, code)}"
    }
    Row(modifier.fillMaxWidth().semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        totals.forEach { t ->
            val on = t.yearMonth == highlighted
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (t.spent > 0) moneyWhole(t.spent, code) else "–",
                    style = LiraType.AmountXs.copy(
                        fontSize = 11.sp, lineHeight = 16.sp,
                        color = if (on) LiraColors.TextPrimary else LiraColors.TextTertiary
                    ),
                    maxLines = 1, softWrap = false, textAlign = TextAlign.Center
                )
                Box(Modifier.height(104.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.width(28.dp).height((100.dp * (t.spent / max).toFloat()).coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(if (on) LiraColors.TextPrimary else BarIdle)
                    )
                }
                Text(
                    t.yearMonth.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                    style = LiraType.Label.copy(
                        fontSize = 11.sp, lineHeight = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) LiraColors.TextPrimary else LiraColors.TextTertiary
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** Seven small bars (Mon..Sun) for the weekly recap; the biggest days are white. */
@Composable
fun MiniWeekBars(perDay: List<Double>, modifier: Modifier = Modifier) {
    val max = perDay.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val letters = DayOfWeek.entries.map { it.getDisplayName(JTextStyle.NARROW, Locale.getDefault()) }
    Row(modifier.width(150.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        perDay.forEachIndexed { i, v ->
            val highlight = v > 0 && v >= max * 0.9
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.height(40.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.width(14.dp).height((40.dp * (v / max).toFloat()).coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(if (highlight) LiraColors.TextPrimary else BarIdle)
                    )
                }
                Text(letters[i], style = LiraType.Label.copy(fontSize = 10.sp, lineHeight = 12.sp, color = LiraColors.TextTertiary))
            }
        }
    }
}

/** Small legend swatch + label for the calendar key. */
@Composable
internal fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(label, style = LiraType.BodyS.copy(fontSize = 11.sp, lineHeight = 14.sp, color = LiraColors.TextTertiary))
    }
}
