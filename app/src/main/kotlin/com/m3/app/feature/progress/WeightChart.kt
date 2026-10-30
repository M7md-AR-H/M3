package com.m3.app.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.TrendPoint
import com.m3.core.model.Units
import java.time.temporal.ChronoUnit

/** Weigh-ins as a thin muted line, the trend as a thick accent line, with the range above and below it. */
@Composable
fun WeightChart(points: List<TrendPoint>, modifier: Modifier = Modifier) {
    val values = points.flatMap { listOf(it.kg, it.trendKg) }
    val high = values.max()
    val low = values.min()
    val span = (high - low).coerceAtLeast(MIN_SPAN_KG)
    val days = ChronoUnit.DAYS.between(points.first().date, points.last().date).coerceAtLeast(1L).toFloat()
    val rule = M3.colors.rule
    val muted = M3.colors.muted
    val accent = M3.colors.accent
    val description = "Weight chart: trend from ${Units.oneDecimal(points.first().trendKg)} to " +
        "${Units.oneDecimal(points.last().trendKg)} kg"

    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(text = "${Units.oneDecimal(high)} ${Units.KG}", style = M3.typography.label, color = M3.colors.muted)
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            fun place(point: TrendPoint, kg: Double) = Offset(
                x = ChronoUnit.DAYS.between(points.first().date, point.date) / days * size.width,
                y = ((high - kg) / span).toFloat() * size.height,
            )
            listOf(0f, 0.5f, 1f).forEach { share ->
                val y = share * size.height
                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (points.size == 1) {
                drawCircle(accent, radius = 4.dp.toPx(), center = place(points.first(), points.first().kg))
            } else {
                drawSeries(points.map { place(it, it.kg) }, muted, 1.dp.toPx())
                drawSeries(points.map { place(it, it.trendKg) }, accent, 2.5.dp.toPx())
            }
        }
        Text(text = "${Units.oneDecimal(low)} ${Units.KG}", style = M3.typography.label, color = M3.colors.muted)
    }
}

private fun DrawScope.drawSeries(offsets: List<Offset>, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(offsets.first().x, offsets.first().y)
        offsets.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = width, join = StrokeJoin.Round))
}

/** Keeps a flat week from looking like wild swings. */
private const val MIN_SPAN_KG = 1.0
