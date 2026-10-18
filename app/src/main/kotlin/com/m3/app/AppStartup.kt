package com.m3.app

import com.m3.app.di.ApplicationScope
import com.m3.core.data.ProfileRepository
import com.m3.core.data.UiConfigRepository
import com.m3.core.designsystem.config.UiConfigStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StartupState {
    data object Loading : StartupState
    data class Ready(val onboardingCompleted: Boolean) : StartupState

    /** The encrypted database couldn't be opened. Nothing was deleted. */
    data object Failed : StartupState
}

/**
 * Opens the encrypted database, restores the saved UI config, then keeps saving every config change.
 * Started once from [M3Application]; [retry] after [StartupState.Failed].
 */
@Singleton
class AppStartup @Inject constructor(
    private val store: UiConfigStore,
    private val uiConfigs: UiConfigRepository,
    private val profiles: ProfileRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true || _state.value is StartupState.Ready) return
        _state.value = StartupState.Loading
        job = scope.launch {
            try {
                uiConfigs.load()?.let(store::restore)
                launch { persistUiConfig() }
                profiles.observeOnboardingCompleted().collect { done ->
                    _state.value = StartupState.Ready(onboardingCompleted = done)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No logging: the exception text could include file paths or data.
                coroutineContext.cancelChildren()
                _state.value = StartupState.Failed
            }
        }
    }

    fun retry() = start()

    /** Saves snapshots one at a time; if changes come faster than writes, only the latest is written. */
    private suspend fun persistUiConfig() {
        // StateFlow is conflated: a slow write is followed by the latest snapshot, not every one in between.
        store.snapshot.collect { snapshot ->
            try {
                uiConfigs.save(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the app usable; the next change saves the whole snapshot again.
            }
        }
    }
}
