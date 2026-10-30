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

/** The config and its history at one moment; what gets persisted. */
data class UiConfigSnapshot(
    val config: UiConfig,
    val history: List<UiHistoryEntry>,
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
 * The app restores it from the encrypted DB at startup and saves every [snapshot] (ADR 0004, ADR 0007).
 */
class UiConfigStore(
    initial: UiConfig = UiConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _config = MutableStateFlow(initial)
    val config: StateFlow<UiConfig> = _config.asStateFlow()

    private val _history = MutableStateFlow<List<UiHistoryEntry>>(emptyList())
    val history: StateFlow<List<UiHistoryEntry>> = _history.asStateFlow()

    private val _snapshot = MutableStateFlow(UiConfigSnapshot(initial, emptyList()))

    /** Config and history together, updated atomically. Collect this to persist. */
    val snapshot: StateFlow<UiConfigSnapshot> = _snapshot.asStateFlow()

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
        set(updated, (_history.value + entry).takeLast(MAX_HISTORY))
        return ApplyResult.Applied(entry)
    }

    /** Restores the config from before the latest change. Current locks are kept. Returns false if there is nothing to undo. */
    @Synchronized
    fun undo(): Boolean {
        val entry = _history.value.lastOrNull() ?: return false
        set(entry.previous.copy(locks = _config.value.locks), _history.value.dropLast(1))
        return true
    }

    /** Owner-only action: locking a key stops agents from changing that area. */
    @Synchronized
    fun setLock(key: LockKey, locked: Boolean) {
        val current = _config.value
        set(current.copy(locks = if (locked) current.locks + key else current.locks - key), _history.value)
    }

    /**
     * Replaces everything with a saved snapshot (app start). An invalid saved config falls back to the
     * default, keeping the saved locks; invalid history entries are dropped.
     */
    @Synchronized
    fun restore(saved: UiConfigSnapshot) {
        val config = if (UiConfigValidator.validate(saved.config).isEmpty()) {
            saved.config.withNewDefaultCards()
        } else {
            UiConfig(locks = saved.config.locks)
        }
        val history = saved.history
            .filter { UiConfigValidator.validate(it.previous).isEmpty() }
            .takeLast(MAX_HISTORY)
        set(config, history)
    }

    private fun set(config: UiConfig, history: List<UiHistoryEntry>) {
        _config.value = config
        _history.value = history
        _snapshot.value = UiConfigSnapshot(config, history)
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
