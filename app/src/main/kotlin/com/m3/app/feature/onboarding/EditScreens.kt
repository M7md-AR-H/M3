package com.m3.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.component.Skeleton
import com.m3.core.designsystem.component.TextAction
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
            SectionHeader(title = "Equipment at ${location.name}", modifier = Modifier.padding(top = M3.spacing.m))
            EquipmentSection(location, viewModel::updateLocation)
        }
        SectionHeader(title = "When you can train", modifier = Modifier.padding(top = M3.spacing.m))
        AvailabilitySection(draft, viewModel::setDay)
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
                .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
                TextAction(text = "← You", onClick = onBack)
                Text(text = title, style = M3.typography.display, color = M3.colors.ink)
                Spacer(Modifier.size(M3.spacing.xs))
                DoubleRule()
            }
            val draft = state.draft
            if (draft == null) {
                if (state.message == null) Skeleton()
            } else {
                content(draft)
            }
        }
        HairRule()
        Column(Modifier.padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m)) {
            state.message?.let { message ->
                if (state.saved) {
                    Text(text = message, style = M3.typography.bodySmall, color = M3.colors.ink)
                } else {
                    ErrorText(message)
                }
            }
            M3Button(
                text = if (state.saving) "Saving…" else "Save",
                onClick = onSave,
                enabled = state.draft != null && !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = M3.spacing.s),
            )
        }
    }
}
