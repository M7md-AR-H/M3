package com.m3.app.feature.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.core.data.ChatRepository
import com.m3.core.data.StoredChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val controller: CoachController,
    private val runtime: CoachRuntime,
    chat: ChatRepository,
) : ViewModel() {
    val messages: StateFlow<List<StoredChatMessage>> = chat.observe()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val modelState: StateFlow<ModelState> = runtime.state

    /** The Coach's answer while it's being written; null when idle. */
    private val _streaming = MutableStateFlow<String?>(null)
    val streaming: StateFlow<String?> = _streaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    init {
        runtime.refresh()
    }

    fun send(text: String) {
        if (job?.isActive == true || text.isBlank()) return
        _error.value = null
        _streaming.value = ""
        job = viewModelScope.launch {
            try {
                controller.send(text) { visible -> _streaming.value = visible }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No details: they could contain prompt text. ModelState tells the screen what went wrong.
                _error.value = when (runtime.state.value) {
                    ModelState.NoModel -> "The Coach needs its AI model first: You → AI model & benchmark."
                    else -> "The Coach couldn't answer. Please try again."
                }
            } finally {
                _streaming.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun apply(messageId: Long) = launchAction { controller.apply(messageId) }

    fun dismiss(messageId: Long) = launchAction { controller.dismiss(messageId); null }

    fun undo(messageId: Long) = launchAction { controller.undo(messageId); null }

    fun clear() = launchAction { controller.clear(); null }

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
    }
}
