package com.m3.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.m3.core.designsystem.theme.M3

@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = M3.colors.muted,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = M3.typography.kicker,
        color = color,
        textAlign = textAlign,
    )
}
