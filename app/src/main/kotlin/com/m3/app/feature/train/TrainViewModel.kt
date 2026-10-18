package com.m3.app.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.app.data.PlanRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.model.Profile
import com.m3.core.training.WeekPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface TrainState {
    data object Loading : TrainState
    data class Ready(val plan: WeekPlan, val profile: Profile?) : TrainState
    data object Failed : TrainState
}

@HiltViewModel
class TrainViewModel @Inject constructor(
    plans: PlanRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    val state: StateFlow<TrainState> =
        combine(plans.observeWeek(), profiles.observe()) { plan, profile -> TrainState.Ready(plan, profile) as TrainState }
            .catch { emit(TrainState.Failed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TrainState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
