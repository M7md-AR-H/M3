package com.m3.app.feature.today

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.displayName
import com.m3.app.feature.train.PlanText
import com.m3.app.feature.train.TrainState
import com.m3.app.feature.train.TrainViewModel
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3
import com.m3.app.feature.train.rememberToday

/** Today's planned session, or a rest day (ADR 0009). */
@Composable
fun TodayWorkoutCard(viewModel: TrainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    M3Card {
        SectionHeader(title = "Today's session")
        Spacer(Modifier.height(M3.spacing.s))
        when (val s = state) {
            TrainState.Loading -> Text(text = "Planning…", style = M3.typography.body, color = M3.colors.muted)
            TrainState.Failed -> Text(text = "Couldn't load your plan.", style = M3.typography.body, color = M3.colors.accent)
            is TrainState.Ready -> {
                val today = rememberToday().value.dayOfWeek
                val session = s.plan.sessionOn(today)
                if (session == null) {
                    Text(text = "Rest day", style = M3.typography.title, color = M3.colors.ink)
                    val next = s.plan.sessions.firstOrNull { it.day > today } ?: s.plan.sessions.firstOrNull()
                    Text(
                        text = next?.let { "Next: ${it.type.displayName} on ${it.day.displayName} at ${it.locationName}." }
                            ?: "No sessions planned yet.",
                        style = M3.typography.bodySmall,
                        color = M3.colors.muted,
                    )
                } else {
                    Kicker(text = session.locationName, color = M3.colors.accent)
                    Text(text = session.type.displayName, style = M3.typography.title, color = M3.colors.ink)
                    session.exercises.forEach { planned ->
                        Text(
                            text = "${planned.exercise.name} · ${PlanText.prescription(planned).substringBefore(" · rest")}",
                            style = M3.typography.bodySmall,
                            color = M3.colors.ink,
                        )
                    }
                }
            }
        }
    }
}
