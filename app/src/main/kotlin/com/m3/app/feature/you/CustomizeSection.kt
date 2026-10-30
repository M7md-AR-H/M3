package com.m3.app.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.M3SquareButton
import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.config.ColorTokenName
import com.m3.core.designsystem.config.FontPairing
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.ThemeScheme
import com.m3.core.designsystem.config.ThemeTokens
import com.m3.core.designsystem.config.UiChange
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.config.displayName
import com.m3.core.designsystem.theme.M3
import kotlin.math.roundToInt

/** Light-theme accents that all keep at least 4.5:1 contrast with white text. */
private val AccentSwatches = listOf("#A3122A", "#1F4E79", "#8A5A00", "#121212")

private val LockLabels = listOf(
    LockKey.THEME to "Theme",
    LockKey.TABS to "Tabs",
    LockKey.TODAY_LAYOUT to "Today layout",
    LockKey.TRAINING_PLAN to "Your plan",
    LockKey.AVAILABILITY to "Your free days",
)

@Composable
fun CustomizeSection(
    config: UiConfig,
    canUndo: Boolean,
    onChange: (UiChange) -> Unit,
    onAgentsMayChange: (LockKey, Boolean) -> Unit,
    onUndo: () -> Unit,
    onSimulateCoachChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = config.theme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(M3.spacing.xl)) {
        Group("Theme") {
            SegmentedChoice(
                options = ThemeMode.entries,
                selected = theme.mode,
                label = { it.name.titleCase() },
                onSelect = { onChange(UiChange.SetThemeMode(it)) },
            )
        }

        Group("Accent") {
            SwatchRow(
                swatches = AccentSwatches,
                selectedHex = theme.light.accent,
                onSelect = { onChange(UiChange.SetColorToken(ThemeScheme.LIGHT, ColorTokenName.ACCENT, it)) },
            )
        }

        Group("Fonts") {
            SegmentedChoice(
                options = FontPairing.entries,
                selected = theme.fontPairing,
                label = { it.name.titleCase() },
                onSelect = { onChange(UiChange.SetFontPairing(it)) },
            )
        }

        CommitSlider(
            label = "Text size",
            valueText = "${(theme.typeScale * 100).roundToInt()}%",
            value = theme.typeScale,
            valueRange = ThemeTokens.MIN_TYPE_SCALE..ThemeTokens.MAX_TYPE_SCALE,
            steps = 0,
            onCommit = {
                onChange(UiChange.SetTypeScale(it.coerceIn(ThemeTokens.MIN_TYPE_SCALE, ThemeTokens.MAX_TYPE_SCALE)))
            },
        )

        CommitSlider(
            label = "Corner radius",
            valueText = "${theme.cornerRadiusDp} dp",
            value = theme.cornerRadiusDp.toFloat(),
            valueRange = ThemeTokens.MIN_CORNER_RADIUS_DP.toFloat()..ThemeTokens.MAX_CORNER_RADIUS_DP.toFloat(),
            steps = ThemeTokens.MAX_CORNER_RADIUS_DP - ThemeTokens.MIN_CORNER_RADIUS_DP - 1,
            onCommit = { onChange(UiChange.SetCornerRadius(it.roundToInt())) },
        )

        Group("Today cards") { CardRows(config = config, onChange = onChange) }

        Group("Agents may change") {
            LockLabels.forEach { (key, name) ->
                LabeledSwitch(
                    label = name,
                    checked = key !in config.locks,
                    onCheckedChange = { onAgentsMayChange(key, it) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
            M3OutlineButton(text = "Undo", onClick = onUndo, enabled = canUndo)
            M3OutlineButton(text = "Reset to default", onClick = { onChange(UiChange.ResetToDefault) })
        }

        Group("Developer") {
            M3OutlineButton(text = "Simulate a Coach change", onClick = onSimulateCoachChange)
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        Kicker(title)
        content()
    }
}

@Composable
private fun CardRows(config: UiConfig, onChange: (UiChange) -> Unit) {
    val order = config.todayCards.map { it.type }
    config.todayCards.forEachIndexed { index, placement ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
            Text(
                text = placement.type.displayName,
                style = M3.typography.body,
                color = M3.colors.ink,
                modifier = Modifier.weight(1f),
            )
            M3Switch(
                checked = placement.visible,
                onCheckedChange = { onChange(UiChange.SetCardVisible(placement.type, it)) },
            )
            MoveButton(
                symbol = "↑",
                description = "Move ${placement.type.displayName} up",
                enabled = index > 0,
                onClick = { onChange(UiChange.SetCardOrder(order.swapped(index, index - 1))) },
            )
            MoveButton(
                symbol = "↓",
                description = "Move ${placement.type.displayName} down",
                enabled = index < order.lastIndex,
                onClick = { onChange(UiChange.SetCardOrder(order.swapped(index, index + 1))) },
            )
        }
    }
}

@Composable
private fun MoveButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    M3SquareButton(description = description, onClick = onClick, enabled = enabled, size = 44.dp) {
        Text(text = symbol, style = M3.typography.body, color = if (enabled) M3.colors.ink else M3.colors.rule)
    }
}

private fun List<CardType>.swapped(first: Int, second: Int): List<CardType> =
    toMutableList().also { it[first] = this[second]; it[second] = this[first] }

private fun String.titleCase(): String = lowercase().replaceFirstChar { it.uppercase() }
