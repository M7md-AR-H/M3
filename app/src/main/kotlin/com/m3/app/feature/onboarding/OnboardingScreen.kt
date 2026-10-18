package com.m3.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.Masthead
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.Units
import java.time.DayOfWeek
import java.time.Year

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    LaunchedEffect(state.index) { scroll.scrollTo(0) }
    BackHandler(enabled = state.index > 0 && !state.saving) { viewModel.back() }

    val step = state.step
    val stepNumber = state.index + 1
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(M3.spacing.l),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
        ) {
            if (step == OnboardingStep.Welcome) {
                Masthead(issueLine = "Setup")
            } else {
                Kicker(text = "Step $stepNumber of ${state.steps.size}")
            }
            Text(text = title(step, state), style = M3.typography.display, color = M3.colors.ink)
            if (step != OnboardingStep.Welcome) DoubleRule()
            intro(step)?.let { Text(text = it, style = M3.typography.body, color = M3.colors.muted) }
            StepContent(step, state, viewModel)
        }
        Column(Modifier.padding(horizontal = M3.spacing.l, vertical = M3.spacing.m)) {
            val error = if (state.showErrors) viewModel.stepError(state) else null
            (error ?: state.saveError)?.let { ErrorText(it) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = M3.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
            ) {
                if (state.index > 0) {
                    M3OutlineButton(
                        text = "Back",
                        onClick = viewModel::back,
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (step == OnboardingStep.Done) {
                    M3Button(
                        text = if (state.saving) "Saving…" else "Start",
                        onClick = viewModel::finish,
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    M3Button(
                        text = if (step == OnboardingStep.Welcome) "Begin" else "Next",
                        onClick = viewModel::next,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepContent(step: OnboardingStep, state: OnboardingState, viewModel: OnboardingViewModel) {
    when (step) {
        OnboardingStep.Welcome -> Unit
        OnboardingStep.About -> AboutYouSection(
            draft = state.profile,
            errors = state.profile.aboutErrors(Year.now().value),
            showErrors = state.showErrors,
            onChange = viewModel::updateProfile,
        )
        OnboardingStep.Goal ->
            GoalSection(state.profile.goal) { viewModel.updateProfile(state.profile.copy(goal = it)) }
        OnboardingStep.Experience ->
            ExperienceSection(state.profile.experience) { viewModel.updateProfile(state.profile.copy(experience = it)) }
        OnboardingStep.Locations -> LocationsSection(
            draft = state.setup,
            onTogglePreset = viewModel::togglePreset,
            onAdd = viewModel::addLocation,
            onRemove = viewModel::removeLocation,
        )
        is OnboardingStep.Equipment -> state.setup.locations.firstOrNull { it.id == step.locationId }
            ?.let { EquipmentSection(it, viewModel::updateLocation) }
        OnboardingStep.Schedule -> ScheduleSection(state.setup, viewModel::setDay)
        OnboardingStep.Injuries -> InjuriesSection(state.profile, viewModel::updateProfile)
        OnboardingStep.Done -> Summary(state)
    }
}

@Composable
private fun Summary(state: OnboardingState) {
    val profile = state.profile
    M3Card {
        Kicker(text = "You")
        Text(
            text = listOfNotNull(
                Units.parseWhole(profile.heightCm)?.let(Units::cm),
                Units.parseDecimal(profile.weightKg)?.let(Units::kg),
                "born ${profile.birthYear}",
            ).joinToString(" · "),
            style = M3.typography.body,
            color = M3.colors.ink,
        )
        Text(
            text = listOfNotNull(profile.goal?.displayName, profile.experience?.displayName?.let { "$it experience" })
                .joinToString(" · "),
            style = M3.typography.body,
            color = M3.colors.ink,
        )
    }
    M3Card {
        Kicker(text = "Training week")
        DayOfWeek.entries.forEach { day ->
            val location = state.setup.schedule[day]?.let { id -> state.setup.locations.firstOrNull { it.id == id } }
            Text(
                text = "${day.displayName}: ${location?.name ?: "Rest"}",
                style = M3.typography.body,
                color = M3.colors.ink,
            )
        }
    }
    Text(
        text = "You can change all of this later under You.",
        style = M3.typography.bodySmall,
        color = M3.colors.muted,
    )
}

private fun title(step: OnboardingStep, state: OnboardingState): String = when (step) {
    OnboardingStep.Welcome -> "Your coach, on this phone."
    OnboardingStep.About -> "About you"
    OnboardingStep.Goal -> "Your goal"
    OnboardingStep.Experience -> "Experience"
    OnboardingStep.Locations -> "Where you train"
    is OnboardingStep.Equipment ->
        "At ${state.setup.locations.firstOrNull { it.id == step.locationId }?.name ?: "this place"}"
    OnboardingStep.Schedule -> "Your week"
    OnboardingStep.Injuries -> "Anything to work around?"
    OnboardingStep.Done -> "All set"
}

private fun intro(step: OnboardingStep): String? = when (step) {
    OnboardingStep.Welcome ->
        "A few questions so M³ can plan your training. Everything you enter is encrypted and stays on " +
            "this phone; nothing is sent anywhere."
    OnboardingStep.About -> "Metric units. Used for training loads and, later, energy needs."
    OnboardingStep.Goal -> "What matters most right now? You can change it any time."
    OnboardingStep.Experience -> "So the plan starts at the right level."
    OnboardingStep.Locations -> "Pick the places you train. Each gets its own equipment list."
    is OnboardingStep.Equipment -> "Tick what's available. The Coach only plans exercises you can actually do here."
    OnboardingStep.Schedule -> "Where do you train on each day? Rest days are fine."
    OnboardingStep.Injuries, OnboardingStep.Done -> null
}
