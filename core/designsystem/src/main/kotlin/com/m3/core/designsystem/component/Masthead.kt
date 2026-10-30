package com.m3.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.m3.core.designsystem.theme.M3

/** "M" with a superscript accent "3" on the left, one or two issue lines on the right, a double rule below. */
@Composable
fun Masthead(issueLine: String, modifier: Modifier = Modifier, secondLine: String? = null) {
    val display = M3.typography.display
    val accent = M3.colors.accent
    val logo = buildAnnotatedString {
        append("M")
        withStyle(
            SpanStyle(
                color = accent,
                fontSize = display.fontSize * 0.5f,
                baselineShift = BaselineShift.Superscript,
            ),
        ) {
            append("3")
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = logo, style = display, color = M3.colors.ink)
            Column(horizontalAlignment = Alignment.End) {
                Kicker(text = issueLine, textAlign = TextAlign.End)
                if (secondLine != null) Kicker(text = secondLine, textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(M3.spacing.s))
        DoubleRule()
    }
}
