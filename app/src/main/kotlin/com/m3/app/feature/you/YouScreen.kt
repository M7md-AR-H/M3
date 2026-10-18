package com.m3.app.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
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
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3

@Composable
fun YouScreen(onOpenAiModel: () -> Unit, viewModel: YouViewModel = hiltViewModel()) {
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
                .padding(M3.spacing.l),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
        ) {
            Text(text = "You", style = M3.typography.display, color = M3.colors.ink)
            DoubleRule()
            SectionHeader(title = "Customize")
            CustomizeSection(
                config = config,
                canUndo = canUndo,
                onChange = viewModel::apply,
                onAgentsMayChange = viewModel::setAgentsMayChange,
                onUndo = viewModel::undo,
                onSimulateCoachChange = viewModel::simulateCoachChange,
            )
            SectionHeader(title = "On-device AI")
            M3OutlineButton(
                text = "AI model & benchmark",
                onClick = onOpenAiModel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = M3.shapes.shape,
                containerColor = M3.colors.ink,
                contentColor = M3.colors.ground,
                actionContentColor = M3.colors.ground,
            )
        }
    }
}
