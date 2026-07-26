package com.app.lira.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A ring/donut chart: each (color, value) slice is drawn as a proportional arc. */
@Composable
fun DonutChart(
    slices: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp
) {
    val total = slices.sumOf { it.second.toDouble() }.toFloat()

    Canvas(modifier = modifier) {
        if (total <= 0f) return@Canvas

        val strokePx = strokeWidth.toPx()
        val diameter = minOf(size.width, size.height) - strokePx
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        var startAngle = -90f
        slices.forEach { (color, value) ->
            if (value <= 0f) return@forEach
            val sweep = (value / total) * 360f
            // A small gap between slices, capped so it doesn't eat a very thin slice entirely.
            val gap = (sweep * 0.06f).coerceAtMost(4f)
            drawArc(
                color = color,
                startAngle = startAngle + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(0f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            startAngle += sweep
        }
    }
}
