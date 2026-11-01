package com.m3.app.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.app.data.PlanInputs
import com.m3.app.data.PlanRepository
import com.m3.core.data.DailyRepository
import com.m3.core.data.DeloadState
import com.m3.core.data.PlanOverridesRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.data.WorkoutRepository
import com.m3.core.model.Experience
import com.m3.core.model.Profile
import com.m3.core.training.Deload
import com.m3.core.training.DeloadSuggestion
import com.m3.core.training.WeekPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Train tab shows about deloading this week. [active]: the week is an accepted deload. [suggestion]: the
 * rule found a reason and the owner hasn't answered yet. [canTake]: nothing is on and a session is planned, so the
 * owner may choose a deload without a suggestion.
 */
data class DeloadUi(val active: Boolean = false, val suggestion: DeloadSuggestion? = null, val canTake: Boolean = false)

sealed interface TrainState {
    data object Loading : TrainState
    data class Ready(val plan: WeekPlan, val profile: Profile?, val deload: DeloadUi = DeloadUi()) : TrainState
    data object Failed : TrainState
}

@HiltViewModel
class TrainViewModel @Inject constructor(
    plans: PlanRepository,
    profiles: ProfileRepository,
    private val overrides: PlanOverridesRepository,
    private val workouts: WorkoutRepository,
    private val daily: DailyRepository,
) : ViewModel() {
    val state: StateFlow<TrainState> =
        combine(plans.observeInputs(), profiles.observe()) { inputs, profile -> inputs to profile }
            .map { (inputs, profile) -> TrainState.Ready(inputs.plan, profile, deloadFor(inputs, profile)) as TrainState }
            .catch { emit(TrainState.Failed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TrainState.Loading)

    /** The rule is Kotlin ([Deload.suggest]); a failed read just means no suggestion. */
    private suspend fun deloadFor(inputs: PlanInputs, profile: Profile?): DeloadUi {
        val today = LocalDate.now()
        val monday = Deload.mondayOf(today)
        val canTake = !inputs.deloadThisWeek && inputs.plan.sessions.isNotEmpty()
        val suggestion = if (inputs.deloadThisWeek || monday in inputs.stored.deloadDeclined) {
            null
        } else {
            try {
                Deload.suggest(
                    thisMonday = monday,
                    workoutDates = workouts.datesBetween(monday.minusWeeks(LOOKBACK_WEEKS), monday.minusDays(1)),
                    checkIns = daily.checkInsBetween(today.minusDays(CHECK_IN_DAYS), today),
                    deloadWeeks = inputs.deloadWeeks,
                    experience = profile?.experience ?: Experience.NEW,
                    plannedSessions = inputs.plan.sessions.size,
                    today = today,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        return DeloadUi(active = inputs.deloadThisWeek, suggestion = suggestion, canTake = canTake)
    }

    /** Writes this week's answer; the plan, the workout screen and the Coach follow from the stored row. */
    fun acceptDeload() = answer(DeloadState.ACCEPTED)

    fun declineDeload() = answer(DeloadState.DECLINED)

    /** Undo and "Back to normal": no answer for this week any more. */
    fun clearDeload() = answer(null)

    private fun answer(state: DeloadState?) {
        viewModelScope.launch {
            try {
                overrides.setDeload(Deload.mondayOf(LocalDate.now()), state)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The screen is derived from what is saved, so a failed write leaves it as it was.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val LOOKBACK_WEEKS = 53L
        const val CHECK_IN_DAYS = 13L
    }
}
