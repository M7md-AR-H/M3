package com.m3.app.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.theme.M3
import com.m3.core.designsystem.theme.toComposeColor

private val MinTouch = 48.dp

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = M3.shapes.shape
    Row(modifier = modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MinTouch)
                    .background(if (isSelected) M3.colors.ink else Color.Transparent, shape)
                    .border(1.dp, M3.colors.ink, shape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) M3.colors.ground else M3.colors.ink,
                )
            }
        }
    }
}

/** Square color buttons; [selectedHex] is outlined. */
@Composable
fun SwatchRow(
    swatches: List<String>,
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = M3.shapes.shape
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(M3.spacing.m),
    ) {
        swatches.forEach { hex ->
            val isSelected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(MinTouch)
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) M3.colors.ink else M3.colors.rule, shape)
                    .padding(5.dp)
                    .background(hex.toComposeColor(), shape)
                    .semantics { contentDescription = "Accent $hex" }
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(hex) }),
            )
        }
    }
}

@Composable
fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = M3.typography.body, color = M3.colors.ink, modifier = Modifier.weight(1f))
        M3Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun M3Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = M3.colors.onAccent,
            checkedTrackColor = M3.colors.accent,
            checkedBorderColor = M3.colors.accent,
            uncheckedThumbColor = M3.colors.muted,
            uncheckedTrackColor = M3.colors.ground,
            uncheckedBorderColor = M3.colors.muted,
        ),
    )
}

/**
 * A slider that commits once, when the drag ends, so one gesture is one undo step.
 * [steps] is the number of stops between the ends (as in [Slider]).
 */
@Composable
fun CommitSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = M3.typography.body, color = M3.colors.ink)
            Text(text = valueText, style = M3.typography.body, color = M3.colors.muted)
        }
        Slider(
            value = pending,
            onValueChange = { pending = it },
            onValueChangeFinished = { if (pending != value) onCommit(pending) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.heightIn(min = MinTouch),
            colors = SliderDefaults.colors(
                thumbColor = M3.colors.accent,
                activeTrackColor = M3.colors.ink,
                inactiveTrackColor = M3.colors.rule,
            ),
        )
    }
}
