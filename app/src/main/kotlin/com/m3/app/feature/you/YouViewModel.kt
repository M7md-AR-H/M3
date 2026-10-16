package com.m3.app.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.core.designsystem.config.Actor
import com.m3.core.designsystem.config.ApplyResult
import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.RejectReason
import com.m3.core.designsystem.config.Specialist
import com.m3.core.designsystem.config.UiChange
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.config.UiConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** A one-off message for the snackbar; [undoable] adds an "Undo" action. */
data class YouMessage(val text: String, val undoable: Boolean = false)

@HiltViewModel
class YouViewModel @Inject constructor(
    private val store: UiConfigStore,
) : ViewModel() {
    val config: StateFlow<UiConfig> = store.config

    val canUndo: StateFlow<Boolean> = store.history
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    private val messages = Channel<YouMessage>(Channel.BUFFERED)
    val messageFlow: Flow<YouMessage> = messages.receiveAsFlow()

    fun apply(change: UiChange) {
        val result = store.apply(change, Actor.Owner)
        if (result is ApplyResult.Rejected) {
            reportRejection(result.reason)
        }
    }

    fun undo() {
        store.undo()
    }

    fun setAgentsMayChange(key: LockKey, allowed: Boolean) {
        store.setLock(key, locked = !allowed)
    }

    /** Dev demo: the Coach tries to move Fuel to the top of Today. */
    fun simulateCoachChange() {
        val change = UiChange.SetCardOrder(listOf(CardType.FUEL_SUMMARY))
        when (val result = store.apply(change, Actor.Agent(Specialist.COACH))) {
            is ApplyResult.Applied -> messages.trySend(YouMessage("Coach moved Fuel to the top", undoable = true))
            is ApplyResult.Rejected -> when (result.reason) {
                RejectReason.Denied -> messages.trySend(YouMessage("Coach can't change Today — you locked it"))
                is RejectReason.Invalid -> reportRejection(result.reason)
            }
        }
    }

    private fun reportRejection(reason: RejectReason) {
        val text = when (reason) {
            RejectReason.Denied -> "That change isn't allowed"
            is RejectReason.Invalid -> reason.errors.firstOrNull()?.message ?: "That change isn't valid"
        }
        messages.trySend(YouMessage(text))
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
