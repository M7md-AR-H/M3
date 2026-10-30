package com.m3.app.feature.today

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.m3.app.feature.steps.StepsCard
import com.m3.app.ui.LocalOpenCoach
import com.m3.core.designsystem.component.InkPanel
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.config.CardPlacement
import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.theme.M3

/** Maps each card type to its renderer. Types without an entry are skipped by [TodayScreen]. */
val CardRegistry: Map<CardType, @Composable (CardPlacement) -> Unit> = mapOf(
    CardType.TODAY_WORKOUT to { _ -> TodayWorkoutCard() },
    CardType.STEPS to { _ -> StepsCard() },
    CardType.COACH_NOTE to { _ -> CoachNoteCard() },
)

/** Cards that aren't built yet. [TodayScreen] names them once, at the end, instead of showing empty boxes. */
val ComingCards: Map<CardType, String> = mapOf(
    CardType.CHECK_IN to "Morning check-in",
    CardType.FUEL_SUMMARY to "Fuel",
    CardType.HABITS to "Habits",
    CardType.WEIGHT_TREND to "Weight trend",
)

@Composable
private fun CoachNoteCard() {
    val openCoach = LocalOpenCoach.current
    InkPanel {
        Kicker(text = "Editor's note — your coach", color = M3.colors.accentOnInk)
        Spacer(Modifier.height(M3.spacing.s))
        Text(
            text = "“Ask why your week looks like this, or tell me what you have today. I'll suggest changes; you decide.”",
            style = M3.typography.serifBody.copy(fontStyle = FontStyle.Italic),
            color = M3.colors.ground,
        )
        TextAction(text = "Talk to your coach →", onClick = openCoach, color = M3.colors.ground)
    }
}
