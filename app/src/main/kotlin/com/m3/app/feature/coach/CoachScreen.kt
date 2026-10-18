package com.m3.app.feature.coach

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.ai.engine.LlmBackend
import com.m3.app.feature.onboarding.ErrorText
import com.m3.core.data.ChatAuthor
import com.m3.core.data.ProposalStatus
import com.m3.core.data.StoredChatMessage
import com.m3.core.data.StoredProposal
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.M3TextField
import com.m3.core.designsystem.theme.M3

private val Suggestions = listOf(
    "Today I only have dumbbells",
    "Why does my week look like this?",
    "My knee hurts on lunges",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(viewModel: CoachViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val busy = streaming != null

    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val restored by viewModel.restoredInput.collectAsStateWithLifecycle()
    LaunchedEffect(restored) {
        restored?.let {
            input = it
            viewModel.restoredInputUsed()
        }
    }
    // Items: saved messages, then the pending message and the answer being written.
    val itemCount = messages.size + (if (pending != null) 2 else 0)
    LaunchedEffect(messages.size, pending != null) {
        if (itemCount > 0) list.animateScrollToItem(itemCount - 1)
    }
    // While the answer streams, keep its end in view without restarting an animation for every word.
    LaunchedEffect((streaming?.length ?: -1) / SCROLL_STEP_CHARS) {
        if (streaming != null && itemCount > 0) list.scrollToItem(itemCount - 1, Int.MAX_VALUE / 2)
    }
    val latestApplied = messages.lastOrNull { it.proposal?.status == ProposalStatus.APPLIED }?.id

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal = M3.spacing.l, vertical = M3.spacing.m)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Kicker(text = "On this phone")
                    Text(text = "Coach", style = M3.typography.display, color = M3.colors.ink)
                }
                if (messages.isNotEmpty() && !busy) M3OutlineButton(text = "Clear", onClick = viewModel::clear)
            }
            DoubleRule()
            Text(
                text = modelLine(modelState),
                style = M3.typography.bodySmall,
                color = if (modelState == ModelState.Failed || modelState == ModelState.NoModel) M3.colors.accent else M3.colors.muted,
                modifier = Modifier.padding(top = M3.spacing.xs),
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = M3.spacing.l, vertical = M3.spacing.s),
        ) {
            if (messages.isEmpty() && !busy) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                        Text(
                            text = "Ask about your plan, or tell me what you have today. I'll suggest changes; you decide.",
                            style = M3.typography.body,
                            color = M3.colors.muted,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(M3.spacing.s), verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                            Suggestions.forEach { suggestion ->
                                M3OutlineButton(
                                    text = suggestion,
                                    onClick = { viewModel.send(suggestion) },
                                    enabled = modelState != ModelState.NoModel,
                                )
                            }
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBlock(
                    message = message,
                    enabled = !busy,
                    onApply = { viewModel.apply(message.id) },
                    onDismiss = { viewModel.dismiss(message.id) },
                    onUndo = { viewModel.undo(message.id) },
                    canUndo = message.id == latestApplied,
                )
            }
            pending?.let { text -> item(key = "pending") { OwnerBubble(text) } }
            streaming?.let { text ->
                item(key = "streaming") {
                    CoachText(text.ifEmpty { "Thinking…" }, muted = text.isEmpty())
                }
            }
        }
        Column(Modifier.padding(horizontal = M3.spacing.l, vertical = M3.spacing.s)) {
            error?.let { ErrorText(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
            ) {
                M3TextField(
                    value = input,
                    onValueChange = { input = it.take(CoachController.MAX_MESSAGE_CHARS) },
                    label = "Message the Coach",
                    singleLine = false,
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    M3OutlineButton(text = "Stop", onClick = viewModel::stop)
                } else {
                    M3Button(
                        text = "Send",
                        onClick = {
                            viewModel.send(input)
                            input = ""
                        },
                        enabled = input.isNotBlank() && modelState != ModelState.NoModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBlock(
    message: StoredChatMessage,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
) {
    if (message.author == ChatAuthor.OWNER) {
        OwnerBubble(message.text)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
            CoachText(message.text, muted = false)
            message.proposal?.let { ProposalCard(it, enabled, canUndo = canUndo, onApply = onApply, onDismiss = onDismiss, onUndo = onUndo) }
        }
    }
}

@Composable
private fun OwnerBubble(text: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .border(1.dp, M3.colors.ink, M3.shapes.shape)
                .padding(M3.spacing.m),
        ) {
            Kicker(text = "You")
            Text(text = text, style = M3.typography.body, color = M3.colors.ink)
        }
    }
}

@Composable
private fun CoachText(text: String, muted: Boolean) {
    Column {
        Kicker(text = "Coach", color = M3.colors.accent)
        Text(text = text, style = M3.typography.body, color = if (muted) M3.colors.muted else M3.colors.ink)
    }
}

@Composable
private fun ProposalCard(
    proposal: StoredProposal,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
) {
    M3Card {
        Kicker(
            text = when (proposal.status) {
                ProposalStatus.PENDING -> "Suggested change"
                ProposalStatus.APPLIED -> "Applied"
                ProposalStatus.DISMISSED -> "Not applied"
                ProposalStatus.UNDONE -> "Undone"
                ProposalStatus.REJECTED -> "Couldn't use this suggestion"
                ProposalStatus.BLOCKED -> "Locked by you"
            },
            color = if (proposal.status == ProposalStatus.PENDING) M3.colors.accent else M3.colors.muted,
        )
        Text(
            text = proposal.summary,
            style = M3.typography.title,
            color = if (proposal.status == ProposalStatus.REJECTED) M3.colors.muted else M3.colors.ink,
        )
        proposal.details.forEach { line ->
            Text(text = line, style = M3.typography.bodySmall, color = M3.colors.muted)
        }
        if (proposal.status == ProposalStatus.BLOCKED) {
            Text(
                text = "You've locked this for agents (You → Customize). Unlock it there, then tap Apply.",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
            )
        }
        when (proposal.status) {
            ProposalStatus.PENDING, ProposalStatus.BLOCKED -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = M3.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
            ) {
                M3Button(text = "Apply", onClick = onApply, enabled = enabled, modifier = Modifier.weight(1f))
                M3OutlineButton(text = "Not now", onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f))
            }
            ProposalStatus.APPLIED ->
                if (canUndo) M3OutlineButton(text = "Undo", onClick = onUndo, enabled = enabled, modifier = Modifier.padding(top = M3.spacing.s))
            else -> Unit
        }
    }
}

private const val SCROLL_STEP_CHARS = 60

private fun modelLine(state: ModelState): String = when (state) {
    ModelState.NoModel -> "Add the AI model first: You → AI model & benchmark."
    ModelState.NotLoaded -> "Runs entirely on this phone. The first answer takes longer while the model starts."
    ModelState.Loading -> "Starting the Coach's model…"
    is ModelState.Ready -> "Running on this phone's ${if (state.backend == LlmBackend.GPU) "GPU" else "CPU"}."
    ModelState.Failed -> "The model couldn't start. Try again, or check You → AI model & benchmark."
}
