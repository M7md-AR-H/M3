package com.m3.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The tick used by check boxes and the workout's Done box. Decorative: its parent carries the meaning. */
@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(modifier.size(size)) {
        val unit = this.size.width / 24f
        val path = Path().apply {
            moveTo(4f * unit, 12f * unit)
            lineTo(9f * unit, 17f * unit)
            lineTo(20f * unit, 6f * unit)
        }
        drawPath(path, color, style = Stroke(width = 3f * unit, cap = StrokeCap.Square, join = StrokeJoin.Miter))
    }
}

/** The small dot that marks a day with a session. */
@Composable
fun DotGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Canvas(modifier.size(size)) { drawCircle(color) }
}
