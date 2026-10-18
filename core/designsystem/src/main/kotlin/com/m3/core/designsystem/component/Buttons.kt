package com.m3.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.theme.M3

private val MinTouchHeight = 48.dp

@Composable
fun M3Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchHeight),
        enabled = enabled,
        shape = M3.shapes.shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = M3.colors.accent,
            contentColor = M3.colors.onAccent,
            disabledContainerColor = M3.colors.rule,
            disabledContentColor = M3.colors.muted,
        ),
    ) {
        Text(text = text, style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun M3OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchHeight),
        enabled = enabled,
        contentPadding = contentPadding,
        shape = M3.shapes.shape,
        border = BorderStroke(1.dp, if (enabled) M3.colors.ink else M3.colors.rule),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = M3.colors.ink,
            disabledContentColor = M3.colors.muted,
        ),
    ) {
        Text(text = text, style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold))
    }
}
