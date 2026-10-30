package com.m3.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.m3.core.designsystem.config.ColorTokens
import com.m3.core.designsystem.config.Contrast

@Immutable
data class M3Colors(
    val ground: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val accent: Color,
    val onAccent: Color,
    /** The accent on an ink-filled panel: the other palette's, since ink is that palette's ground. */
    val accentOnInk: Color,
)

/** Resolves "#RRGGBB" tokens. A config that passed validation always parses; invalid values become Unspecified. */
fun ColorTokens.toM3Colors(opposite: ColorTokens): M3Colors = M3Colors(
    ground = ground.toComposeColor(),
    surface = surface.toComposeColor(),
    ink = ink.toComposeColor(),
    muted = muted.toComposeColor(),
    rule = rule.toComposeColor(),
    accent = accent.toComposeColor(),
    onAccent = onAccent.toComposeColor(),
    accentOnInk = opposite.accent.toComposeColor(),
)

/** Parses a "#RRGGBB" string; Unspecified if invalid. */
fun String.toComposeColor(): Color =
    Contrast.parseHex(this)?.let { Color(it.red, it.green, it.blue) } ?: Color.Unspecified
