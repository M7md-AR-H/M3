package com.m3.app.feature.coach

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.ai.engine.LlmBackend
import com.m3.app.feature.onboarding.ErrorText
import com.m3.app.ui.icons.M3Icons
import com.m3.core.data.ChatAuthor
import com.m3.core.data.ProposalStatus
import com.m3.core.data.StoredChatMessage
import com.m3.core.data.StoredProposal
import com.m3.core.designsystem.component.DotGlyph
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.LinkRow
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.M3Snackbar
import com.m3.core.designsystem.component.M3SquareButton
import com.m3.core.designsystem.component.M3TextField
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3
import com.m3.core.designsystem.theme.Motion

private val Suggestions = listOf(
    "Today I only have dumbbells",
    "Why does my week look like this?",
    "My knee hurts on lunges",
)

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
    // While the answer streams, keep its end in view with a short smooth scroll, but only while the
    // answer is on screen and the user isn't dragging, so reading earlier messages is never interrupted.
    LaunchedEffect((streaming?.length ?: -1) / SCROLL_STEP_CHARS) {
        if (streaming == null || itemCount == 0 || list.isScrollInProgress) return@LaunchedEffect
        val info = list.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return@LaunchedEffect
        if (last.index != itemCount - 1) return@LaunchedEffect
        val overflow = last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding)
        if (overflow > 0) list.animateScrollBy(overflow.toFloat(), Motion.short())
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val submit = { text: String ->
        viewModel.send(text)
        keyboard?.hide()
        focus.clearFocus()
    }
    val latestApplied = messages.lastOrNull { it.proposal?.status == ProposalStatus.APPLIED }?.id
    val clearPending by viewModel.clearPending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(clearPending) {
        if (clearPending) {
            val result = snackbar.showSnackbar(message = "Chat cleared", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoClear()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal = M3.spacing.gutter).padding(top = M3.spacing.m)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Coach", style = M3.typography.headline, color = M3.colors.ink, modifier = Modifier.weight(1f))
                if (messages.isNotEmpty() && !busy) TextAction(text = "Clear", onClick = viewModel::clear)
            }
            Row(
                Modifier.padding(top = M3.spacing.xs, bottom = M3.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
            ) {
                val problem = modelState == ModelState.Failed || modelState == ModelState.NoModel
                if (modelState is ModelState.Ready) DotGlyph(color = M3.colors.accent)
                Text(
                    text = modelLine(modelState),
                    style = M3.typography.bodySmall,
                    color = if (problem) M3.colors.accent else M3.colors.muted,
                )
            }
            HairRule(color = M3.colors.ink)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
                contentPadding = PaddingValues(horizontal = M3.spacing.gutter, vertical = M3.spacing.l),
            ) {
                if (messages.isEmpty() && !busy) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.l)) {
                            Text(
                                text = "Ask about your plan, or tell me what you have today. I'll suggest changes; you decide.",
                                style = M3.typography.serifBody,
                                color = M3.colors.ink,
                            )
                            Column {
                                Kicker(text = "Try asking")
                                Spacer(Modifier.height(M3.spacing.xs))
                                HairRule()
                                Suggestions.forEach { suggestion ->
                                    LinkRow(
                                        title = suggestion,
                                        onClick = { submit(suggestion) },
                                        enabled = modelState != ModelState.NoModel,
                                    )
                                }
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    Column(Modifier.animateItem()) {
                        MessageBlock(
                            message = message,
                            enabled = !busy,
                            onApply = { viewModel.apply(message.id) },
                            onDismiss = { viewModel.dismiss(message.id) },
                            onUndo = { viewModel.undo(message.id) },
                            canUndo = message.id == latestApplied,
                        )
                    }
                }
                // No item animation here: these two are swapped for the stored messages when the turn ends, and a
                // fade-out would briefly show the answer twice.
                pending?.let { text -> item(key = "pending") { OwnerBubble(text) } }
                streaming?.let { text ->
                    item(key = "streaming") {
                        CoachText(text.ifEmpty { "Thinking on this phone…" }, muted = text.isEmpty())
                    }
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { data -> M3Snackbar(data) }
        }
        HairRule(color = M3.colors.ink)
        Column(Modifier.padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.s)) {
            error?.let { ErrorText(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
            ) {
                M3TextField(
                    value = input,
                    onValueChange = { input = it.take(CoachController.MAX_MESSAGE_CHARS) },
                    label = null,
                    placeholder = "Ask anything…",
                    singleLine = false,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Message the Coach" },
                    imeAction = ImeAction.Send,
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (input.isNotBlank() && modelState != ModelState.NoModel && !busy) {
                                submit(input)
                                input = ""
                            }
                        },
                    ),
                )
                if (busy) {
                    M3SquareButton(
                        description = "Stop",
                        onClick = viewModel::stop,
                        size = ComposerButton,
                        borderStroke = BorderStroke(1.dp, M3.colors.ink),
                    ) {
                        Icon(imageVector = M3Icons.Stop, contentDescription = null, tint = M3.colors.ink)
                    }
                } else {
                    val canSend = input.isNotBlank() && modelState != ModelState.NoModel
                    M3SquareButton(
                        description = "Send",
                        onClick = {
                            submit(input)
                            input = ""
                        },
                        size = ComposerButton,
                        enabled = canSend,
                        filled = true,
                    ) {
                        Icon(
                            imageVector = M3Icons.Send,
                            contentDescription = null,
                            tint = if (canSend) M3.colors.onAccent else M3.colors.muted,
                        )
                    }
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
                .widthIn(max = 300.dp)
                .background(M3.colors.ink, M3.shapes.shape)
                .padding(horizontal = M3.spacing.l, vertical = M3.spacing.m),
        ) {
            Text(text = text, style = M3.typography.body, color = M3.colors.ground)
        }
    }
}

@Composable
private fun CoachText(text: String, muted: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
        Kicker(text = "Coach", color = M3.colors.accent)
        Text(
            text = text,
            style = if (muted) M3.typography.serifBody.copy(fontStyle = FontStyle.Italic) else M3.typography.serifBody,
            color = if (muted) M3.colors.muted else M3.colors.ink,
        )
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
    M3Card(Modifier.animateContentSize(Motion.medium())) {
        Kicker(
            text = when (proposal.status) {
                ProposalStatus.PENDING -> "Suggested change"
                ProposalStatus.APPLIED -> "Applied"
                ProposalStatus.DISMISSED -> "Not applied"
                ProposalStatus.UNDONE -> "Undone"
                ProposalStatus.REJECTED -> "Couldn't use this suggestion"
                ProposalStatus.BLOCKED -> "Locked by you"
                ProposalStatus.NO_CHANGE -> "Nothing to change"
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
private val ComposerButton = 56.dp

private fun modelLine(state: ModelState): String = when (state) {
    ModelState.NoModel -> "Add the AI model first: You → AI model & benchmark."
    ModelState.NotLoaded -> "On this phone. The first answer takes longer."
    ModelState.Loading -> "Starting the Coach's model…"
    is ModelState.Ready -> "On this phone · ${if (state.backend == LlmBackend.GPU) "GPU" else "CPU"}"
    ModelState.Failed -> "The model couldn't start. Try again, or check You → AI model & benchmark."
}
