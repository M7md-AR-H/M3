package com.m3.app.feature.train

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.displayName
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3
import com.m3.core.designsystem.theme.Motion
import com.m3.core.training.PlannedSession
import java.time.DayOfWeek


@Composable
fun TrainScreen(viewModel: TrainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(M3.spacing.l),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
    ) {
        Kicker(text = "This week")
        Text(text = "Train", style = M3.typography.display, color = M3.colors.ink)
        DoubleRule()
        when (val s = state) {
            TrainState.Loading -> Text(text = "Planning…", style = M3.typography.body, color = M3.colors.muted)
            TrainState.Failed ->
                Text(text = "Couldn't load your plan.", style = M3.typography.body, color = M3.colors.accent)
            is TrainState.Ready -> {
                Text(
                    text = PlanText.summary(s.plan, s.profile?.goal, s.profile?.experience),
                    style = M3.typography.body,
                    color = M3.colors.muted,
                )
                val today = rememberToday().value.dayOfWeek
                var expanded by rememberSaveable { mutableStateOf<DayOfWeek?>(today) }
                DayOfWeek.entries.forEach { day ->
                    val session = s.plan.sessionOn(day)
                    if (session == null) {
                        RestDayRow(day, isToday = day == today)
                    } else {
                        SessionCard(
                            session = session,
                            isToday = day == today,
                            expanded = expanded == day,
                            onToggle = { expanded = if (expanded == day) null else day },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestDayRow(day: DayOfWeek, isToday: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.displayName + if (isToday) " · today" else "",
            style = M3.typography.body,
            color = M3.colors.muted,
            modifier = Modifier.weight(1f),
        )
        Text(text = "Rest", style = M3.typography.label, color = M3.colors.muted)
    }
}

@Composable
fun SessionCard(session: PlannedSession, isToday: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    M3Card(
        Modifier
            .animateContentSize(Motion.medium())
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide exercises" else "Show exercises", onClick = onToggle),
    ) {
        Kicker(
            text = session.day.displayName + (if (isToday) " · today" else "") + " · " + session.locationName,
            color = if (isToday) M3.colors.accent else M3.colors.muted,
        )
        Text(text = session.type.displayName, style = M3.typography.title, color = M3.colors.ink)
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(Motion.medium()) + expandVertically(Motion.medium()),
            exit = fadeOut(Motion.short()) + shrinkVertically(Motion.short()),
        ) {
            Text(
                text = "${session.exercises.size} exercises" +
                    (if (session.unfilled.isNotEmpty()) " · ${session.unfilled.size} not possible here" else "") +
                    " · tap to see them",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.medium()) + expandVertically(Motion.medium()),
            exit = fadeOut(Motion.short()) + shrinkVertically(Motion.short()),
        ) {
            SessionDetails(session)
        }
    }
}

@Composable
fun SessionDetails(session: PlannedSession) {
    Column(
        modifier = Modifier.padding(top = M3.spacing.s),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.s),
    ) {
        session.exercises.forEach { planned ->
            Column {
                Text(text = planned.exercise.name, style = M3.typography.body, color = M3.colors.ink)
                Text(
                    text = PlanText.prescription(planned) + " · " + PlanText.equipment(planned),
                    style = M3.typography.bodySmall,
                    color = M3.colors.muted,
                )
            }
        }
        session.unfilled.forEach { slot ->
            Text(text = PlanText.unfilled(slot, byAvoided = slot in session.unfilledByAvoided), style = M3.typography.bodySmall, color = M3.colors.accent)
        }
        if (session.exercises.any { it.equipment.isNotEmpty() }) {
            SectionHeader(title = "Loads", modifier = Modifier.padding(top = M3.spacing.s))
            Text(
                text = "Pick a weight you could lift 2–3 more times at the end of each set. Logging and automatic " +
                    "progression arrive next.",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
            )
        } else if (session.exercises.isNotEmpty()) {
            Text(
                text = "Bodyweight session: stop each set 2–3 reps before you'd fail.",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
                modifier = Modifier.padding(top = M3.spacing.s),
            )
        }
    }
}
