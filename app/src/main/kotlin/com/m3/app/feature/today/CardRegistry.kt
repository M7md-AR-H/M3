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
    CardType.CHECK_IN to placeholderCard("Morning check-in", "Coming with daily check-ins"),
    CardType.TODAY_WORKOUT to { _ -> TodayWorkoutCard() },
    CardType.FUEL_SUMMARY to placeholderCard("Fuel", "Coming with nutrition tracking"),
    CardType.COACH_NOTE to placeholderCard("Editor's note", "Ask your Coach anything on the COACH tab."),
    CardType.HABITS to placeholderCard("Habits", "Coming with daily check-ins"),
    CardType.WEIGHT_TREND to placeholderCard("Weight trend", "Coming with progress tracking"),
)

private fun placeholderCard(title: String, note: String): @Composable (CardPlacement) -> Unit = { _ ->
    PlaceholderCard(title, note)
}

@Composable
private fun PlaceholderCard(title: String, note: String) {
    M3Card {
        SectionHeader(title = title)
        Spacer(Modifier.height(M3.spacing.s))
        Text(text = note, style = M3.typography.body, color = M3.colors.muted)
    }
}
