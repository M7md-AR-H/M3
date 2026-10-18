package com.m3.app

import androidx.lifecycle.ViewModel
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.config.UiConfigStore
import com.m3.core.security.AppLock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class MainViewModel @Inject constructor(
    store: UiConfigStore,
    appLock: AppLock,
    private val startup: AppStartup,
) : ViewModel() {
    val config: StateFlow<UiConfig> = store.config
    val locked: StateFlow<Boolean> = appLock.locked
    val startupState: StateFlow<StartupState> = startup.state

    fun retryStartup() = startup.retry()
}
