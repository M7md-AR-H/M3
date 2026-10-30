package com.m3.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.displayName
import com.m3.app.feature.train.PlanText
import com.m3.app.feature.train.TrainState
import com.m3.app.feature.train.TrainViewModel
import com.m3.app.feature.train.rememberToday
import com.m3.app.feature.workout.StartWorkoutButton
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.Skeleton
import com.m3.core.designsystem.theme.M3

/** Today's planned session, or a rest day (ADR 0009). The one boxed item on the page, as in the prototype. */
@Composable
fun TodayWorkoutCard(viewModel: TrainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    M3Card {
        when (val s = state) {
            TrainState.Loading -> Skeleton()
            TrainState.Failed -> Text(text = "Couldn't load your plan.", style = M3.typography.body, color = M3.colors.accent)
            is TrainState.Ready -> {
                val today = rememberToday().value.dayOfWeek
                val session = s.plan.sessionOn(today)
                if (session == null) {
                    Kicker(text = "Train · Rest day")
                    Spacer(Modifier.height(M3.spacing.s))
                    Text(
                        text = "Rest is where the growth happens.",
                        style = M3.typography.serifBody.copy(fontStyle = FontStyle.Italic),
                        color = M3.colors.ink,
                    )
                    Spacer(Modifier.height(M3.spacing.xs))
                    Text(text = PlanText.nextSession(s.plan, today), style = M3.typography.bodySmall, color = M3.colors.muted)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Kicker(text = "Train · ${session.locationName}")
                        Kicker(text = PlanText.minutes(session))
                    }
                    Spacer(Modifier.height(M3.spacing.s))
                    Text(text = session.type.displayName, style = M3.typography.headline, color = M3.colors.ink)
                    Spacer(Modifier.height(M3.spacing.xs))
                    Text(text = PlanText.lineup(session, withMinutes = false), style = M3.typography.bodySmall, color = M3.colors.muted)
                    StartWorkoutButton(session, Modifier.padding(top = M3.spacing.l))
                }
            }
        }
    }
}
