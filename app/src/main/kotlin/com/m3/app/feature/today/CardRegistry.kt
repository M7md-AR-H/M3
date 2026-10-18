package com.m3.app.feature.today

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.config.CardPlacement
import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.theme.M3

/** Maps each card type to its renderer. Types without an entry are skipped by [TodayScreen]. */
val CardRegistry: Map<CardType, @Composable (CardPlacement) -> Unit> = mapOf(
    CardType.CHECK_IN to placeholderCard("Morning check-in"),
    CardType.TODAY_WORKOUT to { _ -> TodayWorkoutCard() },
    CardType.FUEL_SUMMARY to placeholderCard("Fuel"),
    CardType.COACH_NOTE to placeholderCard("Editor's note"),
    CardType.HABITS to placeholderCard("Habits"),
    CardType.WEIGHT_TREND to placeholderCard("Weight trend"),
)

private fun placeholderCard(title: String): @Composable (CardPlacement) -> Unit = { _ ->
    PlaceholderCard(title)
}

@Composable
private fun PlaceholderCard(title: String) {
    M3Card {
        SectionHeader(title = title)
        Spacer(Modifier.height(M3.spacing.s))
        Text(text = "Arrives in Phase 1", style = M3.typography.body, color = M3.colors.muted)
    }
}
