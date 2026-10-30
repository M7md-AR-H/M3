package com.m3.app.feature.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.train.rememberToday
import com.m3.app.ui.LocalOpenFuel
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.component.Skeleton
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.Units

/** Today's calories and macros against the targets. */
@Composable
fun FuelSummaryCard(viewModel: FuelViewModel = hiltViewModel()) {
    val today by rememberToday()
    val flow = remember(today) { viewModel.day(today) }
    val state by flow.collectAsStateWithLifecycle(initialValue = FuelState.Loading)
    val openFuel = LocalOpenFuel.current
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        SectionHeader(title = "Fuel") { TextAction(text = "Log a meal →", onClick = openFuel) }
        when (val s = state) {
            FuelState.Loading -> Skeleton()
            FuelState.Failed -> Text(text = "Couldn't load today's food.", style = M3.typography.body, color = M3.colors.accent)
            is FuelState.Ready -> s.targets?.let { targets ->
                val left = targets.kcal - s.eaten.kcal
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                    Text(text = Units.count(s.eaten.kcal), style = M3.typography.figure, color = M3.colors.ink)
                    Text(
                        text = "of ${Units.kcal(targets.kcal)} · ${kcalLeftText(left)}",
                        style = M3.typography.bodySmall,
                        color = M3.colors.muted,
                        modifier = Modifier.padding(bottom = M3.spacing.xs),
                    )
                }
                MacroMeters(s.eaten, targets)
            }
        }
    }
}

fun kcalLeftText(left: Int): String =
    if (left >= 0) "${Units.count(left)} left" else "${Units.count(-left)} over"
