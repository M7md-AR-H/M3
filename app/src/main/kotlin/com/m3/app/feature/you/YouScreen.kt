package com.m3.app.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.progress.ProgressSection
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.LinkRow
import com.m3.core.designsystem.component.M3Snackbar
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3

@Composable
fun YouScreen(
    onOpenProfile: () -> Unit,
    onOpenTrainingSetup: () -> Unit,
    onOpenAiModel: () -> Unit,
    onOpenMeasurements: () -> Unit,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messageFlow.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.undoable) "Undo" else null,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo()
        }
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
                Kicker(text = "Yours alone", color = M3.colors.accent)
                Text(text = "You", style = M3.typography.display, color = M3.colors.ink)
                Spacer(Modifier.size(M3.spacing.xs))
                DoubleRule()
            }
            ProgressSection(onOpenMeasurements = onOpenMeasurements)
            Column {
                SectionHeader(title = "Your setup")
                LinkRow(title = "Profile", supportingText = "Body, goal, experience, injuries", onClick = onOpenProfile)
                LinkRow(title = "Training setup", supportingText = "Places, equipment, free days", onClick = onOpenTrainingSetup)
                LinkRow(title = "AI model & benchmark", supportingText = "The Coach's on-device model", onClick = onOpenAiModel)
            }
            SectionHeader(title = "Customize")
            CustomizeSection(
                config = config,
                canUndo = canUndo,
                onChange = viewModel::apply,
                onAgentsMayChange = viewModel::setAgentsMayChange,
                onUndo = viewModel::undo,
                onSimulateCoachChange = viewModel::simulateCoachChange,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data -> M3Snackbar(data) }
    }
}
