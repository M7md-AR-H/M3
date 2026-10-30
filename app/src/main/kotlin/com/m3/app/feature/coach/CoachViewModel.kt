package com.m3.app.feature.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.core.data.ChatRepository
import com.m3.core.data.StoredChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val controller: CoachController,
    private val runtime: CoachRuntime,
    private val chat: ChatRepository,
) : ViewModel() {
    /** True while the chat looks cleared but can still be restored with Undo. */
    private val _clearPending = MutableStateFlow(false)
    val clearPending: StateFlow<Boolean> = _clearPending.asStateFlow()

    private var clearJob: Job? = null

    val messages: StateFlow<List<StoredChatMessage>> =
        combine(chat.observe().catch { emit(emptyList()) }, _clearPending) { saved, clearing ->
            if (clearing) emptyList() else saved
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val modelState: StateFlow<ModelState> = runtime.state

    /** The Coach's answer while it's being written; null when idle. */
    private val _streaming = MutableStateFlow<String?>(null)
    val streaming: StateFlow<String?> = _streaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    /** Identifies the answer the screen is showing; cleared by [stop]. */
    private var currentTurn: Any? = null

    init {
        runtime.refresh()
    }

    /** The owner's message while its answer is being written (it's saved together with the answer). */
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Text to put back in the input box after an answer failed or was stopped. */
    private val _restoredInput = MutableStateFlow<String?>(null)
    val restoredInput: StateFlow<String?> = _restoredInput.asStateFlow()

    fun send(text: String) {
        if (_pending.value != null || text.isBlank()) return
        _error.value = null
        _restoredInput.value = null
        _pending.value = text.trim()
        _streaming.value = ""
        job = viewModelScope.launch {
            var answered = false
            val turn = Any()
            currentTurn = turn
            try {
                // A new message ends the Undo window, so the Coach doesn't read a chat the owner just cleared.
                if (_clearPending.value) {
                    clearJob?.cancel()
                    commitClear()
                }
                controller.send(text) { visible -> _streaming.value = visible }
                answered = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No details: they could contain prompt text. ModelState tells the screen what went wrong.
                _error.value = when (runtime.state.value) {
                    ModelState.NoModel -> "The Coach needs its AI model first: You → AI model & benchmark."
                    else -> "The Coach couldn't answer. Please try again."
                }
            } finally {
                // After Stop the screen was already reset; a late finish must not touch it again.
                if (currentTurn === turn) {
                    if (!answered) _restoredInput.value = text
                    _pending.value = null
                    _streaming.value = null
                    currentTurn = null
                }
            }
        }
    }

    fun restoredInputUsed() {
        _restoredInput.value = null
    }

    /**
     * Stops the answer. The screen resets at once; the model may need a moment to actually stop (it can't be
     * interrupted while reading the prompt), and the next question waits for that.
     */
    fun stop() {
        val text = _pending.value
        currentTurn = null
        job?.cancel()
        _pending.value = null
        _streaming.value = null
        if (text != null) _restoredInput.value = text
    }

    fun apply(messageId: Long) = launchAction { controller.apply(messageId) }

    fun dismiss(messageId: Long) = launchAction { controller.dismiss(messageId); null }

    fun undo(messageId: Long) = launchAction { controller.undo(messageId) }

    /** Hides the chat at once and deletes it after the Undo window. Nothing is lost if the app closes meanwhile. */
    fun clear() {
        if (_clearPending.value) return
        _clearPending.value = true
        clearJob = viewModelScope.launch {
            delay(CLEAR_UNDO_MILLIS)
            commitClear()
        }
    }

    fun undoClear() {
        clearJob?.cancel()
        _clearPending.value = false
    }

    private suspend fun commitClear() = withContext(NonCancellable) {
        try {
            controller.clear()
            // Wait for the empty list, so the old chat doesn't flash back when the flag drops.
            chat.observe().first { it.isEmpty() }
        } catch (_: Exception) {
            _error.value = "That didn't work. Please try again."
        } finally {
            _clearPending.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun launchAction(block: suspend () -> String?) {
        viewModelScope.launch {
            _error.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "That didn't work. Please try again."
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Longer than the Undo toast stays on screen. */
        const val CLEAR_UNDO_MILLIS = 6_000L
    }
}
