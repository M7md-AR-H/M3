package com.m3.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.config.UiDensity

@Immutable
data class M3Spacing(
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
)

fun UiDensity.toM3Spacing(): M3Spacing {
    val factor = when (this) {
        UiDensity.COMPACT -> 0.85f
        UiDensity.COMFORTABLE -> 1f
        UiDensity.SPACIOUS -> 1.2f
    }
    return M3Spacing(
        xs = (4 * factor).dp,
        s = (8 * factor).dp,
        m = (12 * factor).dp,
        l = (20 * factor).dp,
        xl = (28 * factor).dp,
    )
}
