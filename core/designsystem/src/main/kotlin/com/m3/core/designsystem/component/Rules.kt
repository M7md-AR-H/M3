package com.m3.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.theme.M3

/** A 2dp ink line over a 1dp ink line, like the masthead of the prototype. */
@Composable
fun DoubleRule(modifier: Modifier = Modifier) {
    val ink = M3.colors.ink
    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(ink))
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink))
    }
}

/** A thin progress bar. [fraction] is clamped to 0..1, so going over a target shows as full. */
@Composable
fun M3Meter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(M3.colors.rule)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(color))
    }
}

/** The thin line between rows. */
@Composable
fun HairRule(modifier: Modifier = Modifier, color: Color = M3.colors.rule) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = title, style = M3.typography.title, color = M3.colors.ink)
            trailing?.invoke()
        }
        Spacer(Modifier.height(M3.spacing.xs))
        HairRule(color = M3.colors.ink)
    }
}
