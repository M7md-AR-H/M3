package com.m3.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3
import java.time.Year

@Composable
fun ProfileEditScreen(onBack: () -> Unit, viewModel: ProfileEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditScaffold(
        title = "Profile",
        state = state,
        onSave = viewModel::save,
        onBack = onBack,
    ) { draft ->
        SectionHeader(title = "About you")
        AboutYouSection(draft, draft.aboutErrors(Year.now().value), state.showErrors, viewModel::update)
        SectionHeader(title = "Goal")
        GoalSection(draft.goal) { viewModel.update(draft.copy(goal = it)) }
        SectionHeader(title = "Experience")
        ExperienceSection(draft.experience) { viewModel.update(draft.copy(experience = it)) }
        SectionHeader(title = "Injuries & limitations")
        InjuriesSection(draft, viewModel::update)
    }
}

@Composable
fun TrainingSetupScreen(onBack: () -> Unit, viewModel: TrainingSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditScaffold(
        title = "Training setup",
        state = state,
        onSave = viewModel::save,
        onBack = onBack,
    ) { draft ->
        SectionHeader(title = "Locations")
        LocationsSection(draft, viewModel::togglePreset, viewModel::addLocation, viewModel::removeLocation)
        draft.locations.forEach { location ->
            SectionHeader(title = "Equipment at ${location.name}", modifier = Modifier.padding(top = M3.spacing.l))
            EquipmentSection(location, viewModel::updateLocation)
        }
        SectionHeader(title = "Weekly schedule", modifier = Modifier.padding(top = M3.spacing.l))
        ScheduleSection(draft, viewModel::setDay)
    }
}

@Composable
private fun <T> EditScaffold(
    title: String,
    state: EditState<T>,
    onSave: () -> Unit,
    onBack: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(M3.spacing.l),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
        ) {
            Kicker(text = "You")
            Text(text = title, style = M3.typography.display, color = M3.colors.ink)
            DoubleRule()
            val draft = state.draft
            if (draft == null) {
                Text(text = "Loading…", style = M3.typography.body, color = M3.colors.muted)
            } else {
                content(draft)
            }
        }
        Column(Modifier.padding(horizontal = M3.spacing.l, vertical = M3.spacing.m)) {
            state.message?.let { message ->
                if (state.saved) {
                    Text(text = message, style = M3.typography.bodySmall, color = M3.colors.ink)
                } else {
                    ErrorText(message)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = M3.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
            ) {
                M3OutlineButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
                M3Button(
                    text = if (state.saving) "Saving…" else "Save",
                    onClick = onSave,
                    enabled = state.draft != null && !state.saving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
