package com.m3.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.theme.M3

/** A tab that isn't built yet: says plainly what will be here. [items] are title to description. */
@Composable
fun ComingSoonScreen(
    title: String,
    lede: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
            Kicker(text = "Not built yet", color = M3.colors.accent)
            Text(text = title, style = M3.typography.display, color = M3.colors.ink)
            Spacer(Modifier.size(M3.spacing.xs))
            DoubleRule()
        }
        Text(
            text = lede,
            style = M3.typography.headline.copy(fontStyle = FontStyle.Italic),
            color = M3.colors.ink,
        )
        Column {
            HairRule()
            items.forEachIndexed { index, (name, description) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = M3.spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(M3.spacing.m),
                ) {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        style = M3.typography.title.copy(fontWeight = FontWeight.Medium),
                        color = M3.colors.muted,
                        modifier = Modifier.widthIn(min = 32.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = M3.colors.ink,
                        )
                        Text(text = description, style = M3.typography.bodySmall, color = M3.colors.muted)
                    }
                }
                HairRule()
            }
        }
    }
}
