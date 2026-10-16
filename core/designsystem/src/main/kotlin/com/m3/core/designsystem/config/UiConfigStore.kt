package com.m3.core.designsystem.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiHistoryEntry(
    val actor: Actor,
    val description: String,
    val previous: UiConfig,
    val timestampMillis: Long,
)

sealed interface RejectReason {
    data object Denied : RejectReason
    data class Invalid(val errors: List<UiConfigError>) : RejectReason
}

sealed interface ApplyResult {
    data class Applied(val entry: UiHistoryEntry) : ApplyResult
    data class Rejected(val reason: RejectReason) : ApplyResult
}

/**
 * Holds the current [UiConfig] and its undo history.
 * Phase 1: persisted in the encrypted DB.
 */
class UiConfigStore(
    initial: UiConfig = UiConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _config = MutableStateFlow(initial)
    val config: StateFlow<UiConfig> = _config.asStateFlow()

    private val _history = MutableStateFlow<List<UiHistoryEntry>>(emptyList())
    val history: StateFlow<List<UiHistoryEntry>> = _history.asStateFlow()

    @Synchronized
    fun apply(change: UiChange, actor: Actor): ApplyResult {
        val current = _config.value
        if (!Permissions.canApply(actor, change, current)) {
            return ApplyResult.Rejected(RejectReason.Denied)
        }
        val updated = change.applyTo(current)
        val errors = UiConfigValidator.validate(updated)
        if (errors.isNotEmpty()) {
            return ApplyResult.Rejected(RejectReason.Invalid(errors))
        }
        val entry = UiHistoryEntry(actor, change.describe(), current, clock())
        _history.value = (_history.value + entry).takeLast(MAX_HISTORY)
        _config.value = updated
        return ApplyResult.Applied(entry)
    }

    /** Restores the config from before the latest change. Current locks are kept. Returns false if there is nothing to undo. */
    @Synchronized
    fun undo(): Boolean {
        val entry = _history.value.lastOrNull() ?: return false
        _history.value = _history.value.dropLast(1)
        _config.value = entry.previous.copy(locks = _config.value.locks)
        return true
    }

    /** Owner-only action: locking a key stops agents from changing that area. */
    @Synchronized
    fun setLock(key: LockKey, locked: Boolean) {
        val current = _config.value
        _config.value = current.copy(locks = if (locked) current.locks + key else current.locks - key)
    }

    private companion object {
        const val MAX_HISTORY = 100
    }
}
