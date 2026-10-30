package com.m3.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.m3.core.designsystem.config.TabId
import com.m3.core.designsystem.theme.M3

@Composable
fun M3BottomBar(
    tabs: List<TabId>,
    isSelected: (TabId) -> Boolean,
    onSelect: (TabId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(M3.colors.ground).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(M3.colors.ink))
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                TabItem(tab = tab, selected = isSelected(tab), onClick = { onSelect(tab) })
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: TabId, selected: Boolean, onClick: () -> Unit) {
    val tint = when {
        tab == TabId.COACH -> M3.colors.accent
        selected -> M3.colors.ink
        else -> M3.colors.muted
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) M3.colors.ink else Color.Transparent),
        )
        Spacer(Modifier.height(M3.spacing.s))
        Icon(imageVector = tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = tab.label,
            // Tighter than a kicker: five labels share one row, down to 360 dp wide.
            style = M3.typography.kicker.copy(letterSpacing = 0.08.em),
            color = tint,
            modifier = Modifier.padding(top = M3.spacing.xs, bottom = M3.spacing.s),
        )
    }
}
