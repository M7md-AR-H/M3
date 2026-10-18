package com.m3.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.theme.M3

@Composable
fun ComingSoonScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(M3.spacing.l),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
    ) {
        Text(text = title, style = M3.typography.display, color = M3.colors.ink)
        DoubleRule()
        Kicker(text = "Coming in Phase 1")
        Text(text = description, style = M3.typography.body, color = M3.colors.ink)
    }
}
