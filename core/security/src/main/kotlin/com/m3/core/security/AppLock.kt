package com.m3.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When the app must be unlocked: on cold start, and after [timeoutMillis] in the background.
 * [clock] should be monotonic (e.g. `SystemClock.elapsedRealtime`).
 *
 * This is a UI gate. The data itself is protected at rest by SQLCipher + Keystore; the Keystore key
 * is not bound to user authentication (see README known limitations).
 */
class AppLock(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val clock: () -> Long,
) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** Goes up each time the app locks, so the prompt can open by itself once per lock, not on every resume. */
    @Volatile
    var lockGeneration: Int = 1
        private set

    private var backgroundedAt: Long? = null

    @Synchronized
    fun onBackground() {
        if (!_locked.value) backgroundedAt = clock()
    }

    @Synchronized
    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        // A clock that went backwards counts as expired.
        val elapsed = clock() - since
        if (elapsed < 0 || elapsed >= timeoutMillis) {
            lockGeneration++
            _locked.value = true
        }
    }

    @Synchronized
    fun unlock() {
        backgroundedAt = null
        _locked.value = false
    }

    companion object {
        /** Becomes an owner setting later. */
        const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}
