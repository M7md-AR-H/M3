package com.m3.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.theme.M3

@Composable
fun M3Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = M3.shapes.shape
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(M3.colors.surface, shape)
            .border(1.dp, M3.colors.ink, shape)
            .padding(M3.spacing.m),
        content = content,
    )
}
