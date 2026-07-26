package com.app.lira.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A smooth line chart with a soft gradient fill beneath it — no charting library needed, just
 * Canvas + a cubic-Bezier midpoint smoothing between points.
 */
@Composable
fun TrendChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val maxVal = values.max()
        val minVal = values.min()
        val range = (maxVal - minVal).let { if (it <= 0f) 1f else it }

        val verticalInset = size.height * 0.12f
        val usableHeight = size.height - verticalInset * 2
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f

        fun yFor(v: Float) = verticalInset + usableHeight - ((v - minVal) / range) * usableHeight

        val coords = values.mapIndexed { i, v -> Offset(i * stepX, yFor(v)) }

        val linePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 0 until coords.size - 1) {
                val p0 = coords[i]
                val p1 = coords[i + 1]
                val midX = (p0.x + p1.x) / 2
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(coords.last().x, size.height)
            lineTo(coords.first().x, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = size.height
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
