package com.m3.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.theme.M3

private val MinTouch = 48.dp

/** A word or two you can tap, without a box: "← You", "Clear". The touch target is taller than the text. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = M3.colors.ink,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .heightIn(min = MinTouch)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) color else M3.colors.muted,
        )
    }
}

/** A square button holding a symbol or icon; [description] is what a screen reader says. */
@Composable
fun M3SquareButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = MinTouch,
    enabled: Boolean = true,
    filled: Boolean = false,
    borderStroke: BorderStroke = BorderStroke(1.dp, M3.colors.rule),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = M3.shapes.shape
    val fill = when {
        !filled -> Color.Transparent
        enabled -> M3.colors.accent
        else -> M3.colors.rule
    }
    Box(
        modifier
            .size(size)
            .background(fill, shape)
            .then(if (filled) Modifier else Modifier.border(borderStroke, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A full-width row that opens something: title, optional line below, an arrow on the right, a thin rule under it. */
@Composable
fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouch)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(vertical = M3.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = M3.typography.body.copy(fontWeight = FontWeight.Medium),
                    color = if (enabled) M3.colors.ink else M3.colors.muted,
                )
                if (supportingText != null) {
                    Text(text = supportingText, style = M3.typography.bodySmall, color = M3.colors.muted)
                }
            }
            Text(text = "→", style = M3.typography.body, color = M3.colors.muted, modifier = Modifier.padding(start = M3.spacing.m))
        }
        HairRule()
    }
}

/** The inverted panel of the Today page. Text inside uses ground on ink, and accentOnInk for its kicker. */
@Composable
fun InkPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(M3.colors.ink, M3.shapes.shape)
            .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.xl),
        content = content,
    )
}

/** Undo toast: the same look wherever one shows. */
@Composable
fun M3Snackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        shape = M3.shapes.shape,
        containerColor = M3.colors.ink,
        contentColor = M3.colors.ground,
        actionContentColor = M3.colors.accentOnInk,
    )
}
