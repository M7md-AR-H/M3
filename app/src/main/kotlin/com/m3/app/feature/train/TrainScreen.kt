package com.m3.app.feature.train

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.displayName
import com.m3.app.feature.workout.StartLaterNote
import com.m3.app.feature.workout.StartWorkoutButton
import com.m3.core.designsystem.component.DotGlyph
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.M3Snackbar
import com.m3.core.designsystem.component.Skeleton
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3
import com.m3.core.designsystem.theme.Motion
import com.m3.core.training.PlannedSession
import com.m3.core.training.WeekPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch

@Composable
fun TrainScreen(viewModel: TrainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val applyDeload = {
        viewModel.acceptDeload()
        scope.launch {
            val result = snackbar.showSnackbar(message = "Deload week on", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.clearDeload()
        }
        Unit
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
                Kicker(text = "This week", color = M3.colors.accent)
                Text(text = "Train", style = M3.typography.display, color = M3.colors.ink)
                Spacer(Modifier.size(M3.spacing.xs))
                DoubleRule()
            }
            when (val s = state) {
                TrainState.Loading -> Skeleton(widths = listOf(1f, 0.5f, 0.8f, 0.9f, 0.7f))
                TrainState.Failed ->
                    Text(text = "Couldn't load your plan.", style = M3.typography.body, color = M3.colors.accent)
                is TrainState.Ready -> {
                    val date = rememberToday().value
                    var selected by rememberSaveable { mutableStateOf(date.dayOfWeek) }
                    DeloadSection(
                        deload = s.deload,
                        onApply = applyDeload,
                        onNotNow = viewModel::declineDeload,
                        onBackToNormal = viewModel::clearDeload,
                    )
                    DayStrip(plan = s.plan, today = date, selected = selected, onSelect = { selected = it })
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = { fadeIn(Motion.medium()) togetherWith fadeOut(Motion.short()) },
                        label = "day",
                    ) { day ->
                        DayDetail(plan = s.plan, day = day, today = date)
                    }
                    Note(
                        title = "Why this week",
                        text = PlanText.summary(s.plan, s.profile?.goal, s.profile?.experience),
                    )
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { data -> M3Snackbar(data) }
    }
}

/** The deload offer, the note while one is on, or a quiet way to choose one. Hidden when none of these apply. */
@Composable
private fun DeloadSection(deload: DeloadUi, onApply: () -> Unit, onNotNow: () -> Unit, onBackToNormal: () -> Unit) {
    when {
        deload.active -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Deload week",
                style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = M3.colors.accent,
                modifier = Modifier.weight(1f),
            )
            TextAction(text = "Back to normal", onClick = onBackToNormal)
        }
        deload.suggestion != null -> M3Card {
            Kicker(text = "Suggestion", color = M3.colors.accent)
            Text(text = "Time for a lighter week", style = M3.typography.title, color = M3.colors.ink)
            Spacer(Modifier.size(M3.spacing.xs))
            Text(text = deload.suggestion.sentence, style = M3.typography.bodySmall, color = M3.colors.ink)
            Text(
                text = "Fewer sets, about 10% lighter, for this week only.",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
            )
            Spacer(Modifier.size(M3.spacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                M3Button(text = "Apply", onClick = onApply, modifier = Modifier.weight(1f))
                M3OutlineButton(text = "Not now", onClick = onNotNow, modifier = Modifier.weight(1f))
            }
        }
        deload.canTake -> TextAction(text = "Take a deload week", onClick = onApply, color = M3.colors.muted)
    }
}

/** Mon–Sun as seven squares: dark when selected, a dot on days with a session. */
@Composable
private fun DayStrip(plan: WeekPlan, today: LocalDate, selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(M3.spacing.xs),
    ) {
        DayOfWeek.entries.forEach { day ->
            val date = monday.plusDays(day.ordinal.toLong())
            val session = plan.sessionOn(day)
            val isSelected = day == selected
            val isToday = day == today.dayOfWeek
            val shape = M3.shapes.shape
            val text = if (isSelected) M3.colors.ground else M3.colors.ink
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp)
                    .background(if (isSelected) M3.colors.ink else Color.Transparent, shape)
                    .border(1.dp, if (isSelected || isToday) M3.colors.ink else M3.colors.rule, shape)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(day) })
                    .semantics {
                        contentDescription = day.displayName + " " + date.dayOfMonth +
                            (if (isToday) ", today" else "") + ", " + (session?.type?.displayName ?: "rest")
                    }
                    .padding(vertical = M3.spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(M3.spacing.xs),
            ) {
                Text(text = day.displayName.take(3).uppercase(), style = M3.typography.label, color = text)
                Text(text = date.dayOfMonth.toString(), style = M3.typography.title, color = text)
                if (session != null) {
                    DotGlyph(color = if (isSelected) M3.colors.accentOnInk else M3.colors.accent)
                } else {
                    Spacer(Modifier.size(6.dp))
                }
            }
        }
    }
}

@Composable
private fun DayDetail(plan: WeekPlan, day: DayOfWeek, today: LocalDate) {
    val session = plan.sessionOn(day)
    val isToday = day == today.dayOfWeek
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(M3.spacing.l)) {
        Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
            Kicker(
                text = listOfNotNull(if (isToday) "Today" else null, day.displayName, session?.locationName).joinToString(" · "),
                color = if (isToday) M3.colors.accent else M3.colors.muted,
            )
            Text(text = session?.type?.displayName ?: "Rest", style = M3.typography.headline, color = M3.colors.ink)
            if (session != null) {
                Text(text = PlanText.lineup(session), style = M3.typography.bodySmall, color = M3.colors.muted)
            }
        }
        if (session == null) {
            M3Card {
                Text(
                    text = "Rest is where the growth happens.",
                    style = M3.typography.serifBody.copy(fontStyle = FontStyle.Italic),
                    color = M3.colors.ink,
                )
                Spacer(Modifier.size(M3.spacing.xs))
                Text(text = PlanText.nextSession(plan, day), style = M3.typography.bodySmall, color = M3.colors.muted)
            }
        } else {
            SessionDetails(session)
            if (isToday) StartWorkoutButton(session) else StartLaterNote(session)
        }
    }
}

/** The exercises as a numbered list, then how to pick weights. */
@Composable
private fun SessionDetails(session: PlannedSession) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.l)) {
        Column {
            HairRule()
            session.exercises.forEachIndexed { index, planned ->
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
                            text = planned.exercise.name,
                            style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = M3.colors.ink,
                        )
                        Text(
                            text = PlanText.prescription(planned) + " · " + PlanText.equipment(planned),
                            style = M3.typography.bodySmall,
                            color = M3.colors.muted,
                        )
                    }
                }
                HairRule()
            }
        }
        session.unfilled.forEach { slot ->
            Text(
                text = PlanText.unfilled(slot, byAvoided = slot in session.unfilledByAvoided),
                style = M3.typography.bodySmall,
                color = M3.colors.accent,
            )
        }
        if (session.exercises.any { it.equipment.isNotEmpty() }) {
            Note(
                title = "Loads",
                text = "Pick a weight you could lift 2–3 more times at the end of each set. Once you log a workout, " +
                    "the app suggests the next weight for each exercise.",
            )
        } else if (session.exercises.isNotEmpty()) {
            Note(title = "Effort", text = "Bodyweight session: stop each set 2–3 reps before you'd fail.")
        }
    }
}

@Composable
private fun Note(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
        Kicker(text = title, color = M3.colors.ink)
        Text(text = text, style = M3.typography.bodySmall, color = M3.colors.muted)
    }
}
