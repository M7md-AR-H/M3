package com.m3.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.theme.M3
import kotlinx.coroutines.delay

private const val SHOW_AFTER_MILLIS = 200L
private const val PULSE_MILLIS = 900

/**
 * Placeholder bars for content that's loading. Fast loads never show it: it appears only after a short wait, so
 * there's no flicker. [widths] are fractions of the width, one bar each.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, widths: List<Float> = listOf(0.7f, 1f, 0.9f), barHeight: Dp = 14.dp) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SHOW_AFTER_MILLIS)
        show = true
    }
    if (!show) return
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    Column(modifier.fillMaxWidth().alpha(pulse), verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        widths.forEach { width ->
            Box(Modifier.fillMaxWidth(width).height(barHeight).background(M3.colors.rule, M3.shapes.shape))
        }
    }
}
