package com.m3.core.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.ThemeTokens

val LocalM3Colors = staticCompositionLocalOf<M3Colors> { error("M3Theme not provided") }
val LocalM3Typography = staticCompositionLocalOf<M3Typography> { error("M3Theme not provided") }
val LocalM3Spacing = staticCompositionLocalOf<M3Spacing> { error("M3Theme not provided") }
val LocalM3Shapes = staticCompositionLocalOf<M3Shapes> { error("M3Theme not provided") }

/** Accessor for the current design tokens: `M3.colors.ink`, `M3.typography.body`, ... */
object M3 {
    val colors: M3Colors
        @Composable @ReadOnlyComposable get() = LocalM3Colors.current
    val typography: M3Typography
        @Composable @ReadOnlyComposable get() = LocalM3Typography.current
    val spacing: M3Spacing
        @Composable @ReadOnlyComposable get() = LocalM3Spacing.current
    val shapes: M3Shapes
        @Composable @ReadOnlyComposable get() = LocalM3Shapes.current
}

@Composable
fun M3Theme(tokens: ThemeTokens, content: @Composable () -> Unit) {
    val dark = when (tokens.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val targetColors = remember(tokens.light, tokens.dark, dark) {
        (if (dark) tokens.dark else tokens.light).toM3Colors()
    }
    // Cross-fade between palettes (light/dark switch, agent colour changes) instead of snapping.
    val colors = M3Colors(
        ground = animateToken(targetColors.ground),
        surface = animateToken(targetColors.surface),
        ink = animateToken(targetColors.ink),
        muted = animateToken(targetColors.muted),
        rule = animateToken(targetColors.rule),
        accent = animateToken(targetColors.accent),
        onAccent = animateToken(targetColors.onAccent),
    )
    val typography = remember(tokens.fontPairing, tokens.typeScale) {
        m3Typography(tokens.fontPairing, tokens.typeScale)
    }
    val spacing = remember(tokens.density) { tokens.density.toM3Spacing() }
    val shapes = remember(tokens.cornerRadiusDp) { cornerRadiusToM3Shapes(tokens.cornerRadiusDp) }

    CompositionLocalProvider(
        LocalM3Colors provides colors,
        LocalM3Typography provides typography,
        LocalM3Spacing provides spacing,
        LocalM3Shapes provides shapes,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme(colors, dark),
            typography = materialTypography(typography),
            shapes = materialShapes(shapes.shape),
        ) {
            Surface(color = colors.ground, contentColor = colors.ink, content = content)
        }
    }
}

@Composable
private fun animateToken(target: Color): Color =
    animateColorAsState(target, animationSpec = Motion.medium(), label = "m3Color").value

private fun materialColorScheme(colors: M3Colors, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.ground,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            outline = colors.rule,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.ground,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            outline = colors.rule,
        )
    }

private fun materialTypography(typography: M3Typography) = Typography(
    displayLarge = typography.display,
    headlineLarge = typography.headline,
    titleLarge = typography.title,
    bodyLarge = typography.body,
    bodyMedium = typography.body,
    bodySmall = typography.bodySmall,
    labelLarge = typography.label,
    labelMedium = typography.label,
    labelSmall = typography.label,
)

private fun materialShapes(shape: RoundedCornerShape) = Shapes(
    extraSmall = shape,
    small = shape,
    medium = shape,
    large = shape,
    extraLarge = shape,
)
