package com.m3.app

import androidx.lifecycle.ViewModel
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.config.UiConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class MainViewModel @Inject constructor(store: UiConfigStore) : ViewModel() {
    val config: StateFlow<UiConfig> = store.config
}
