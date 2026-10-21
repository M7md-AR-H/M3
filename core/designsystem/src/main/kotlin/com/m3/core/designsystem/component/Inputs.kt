package com.m3.core.designsystem.component

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.theme.M3

private val MinTouch = 48.dp

/** Square, ink-outlined text field. [suffix] is for units, e.g. "kg"; [error] shows below in the accent color. */
@Composable
fun M3TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    placeholder: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label, style = M3.typography.bodySmall) },
            placeholder = placeholder?.let { { Text(text = it, style = M3.typography.body, color = M3.colors.muted) } },
            suffix = suffix?.let { { Text(text = it, style = M3.typography.body, color = M3.colors.muted) } },
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = M3.typography.body,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction ?: if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
            keyboardActions = keyboardActions,
            shape = M3.shapes.shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = M3.colors.ink,
                unfocusedTextColor = M3.colors.ink,
                focusedBorderColor = M3.colors.ink,
                unfocusedBorderColor = M3.colors.muted,
                errorBorderColor = M3.colors.accent,
                focusedLabelColor = M3.colors.ink,
                unfocusedLabelColor = M3.colors.muted,
                errorLabelColor = M3.colors.accent,
                cursorColor = M3.colors.ink,
                focusedContainerColor = M3.colors.surface,
                unfocusedContainerColor = M3.colors.surface,
                errorContainerColor = M3.colors.surface,
            ),
        )
        if (error != null) {
            Text(
                text = error,
                style = M3.typography.bodySmall,
                color = M3.colors.accent,
                modifier = Modifier.padding(top = M3.spacing.xs),
            )
        }
    }
}

/** A full-width row with a square check box; the whole row is the touch target. */
@Composable
fun M3CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = M3.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(M3.spacing.m),
    ) {
        val shape = M3.shapes.shape
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(1.5.dp, M3.colors.ink, shape)
                .padding(4.dp)
                .background(if (checked) M3.colors.accent else Color.Transparent, shape),
        )
        Column(Modifier.weight(1f)) {
            Text(text = label, style = M3.typography.body, color = M3.colors.ink)
            if (supportingText != null) {
                Text(text = supportingText, style = M3.typography.bodySmall, color = M3.colors.muted)
            }
        }
    }
}

/** One option in a single-choice group: ink fill when selected. Put several in a selectableGroup. */
@Composable
fun M3Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val shape = M3.shapes.shape
    Column(
        modifier = modifier
            .heightIn(min = MinTouch)
            .background(if (selected) M3.colors.ink else Color.Transparent, shape)
            .border(1.dp, M3.colors.ink, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = M3.spacing.m, vertical = M3.spacing.s),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) M3.colors.ground else M3.colors.ink,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = M3.typography.bodySmall,
                color = if (selected) M3.colors.ground else M3.colors.muted,
            )
        }
    }
}
