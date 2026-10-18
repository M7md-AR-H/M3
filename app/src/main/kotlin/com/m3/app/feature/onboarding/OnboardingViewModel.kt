package com.m3.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.core.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Year
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object About : OnboardingStep
    data object Goal : OnboardingStep
    data object Experience : OnboardingStep
    data object Locations : OnboardingStep
    data class Equipment(val locationId: String) : OnboardingStep
    data object Schedule : OnboardingStep
    data object Injuries : OnboardingStep
    data object Done : OnboardingStep
}

data class OnboardingState(
    val index: Int = 0,
    val profile: ProfileDraft = ProfileDraft(),
    val setup: SetupDraft = SetupDraft(),
    /** Set when "Next" was pressed on a step that isn't complete. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
) {
    /** One equipment step per location, so the list changes as locations are added or removed. */
    val steps: List<OnboardingStep>
        get() = buildList {
            add(OnboardingStep.Welcome)
            add(OnboardingStep.About)
            add(OnboardingStep.Goal)
            add(OnboardingStep.Experience)
            add(OnboardingStep.Locations)
            setup.locations.forEach { add(OnboardingStep.Equipment(it.id)) }
            add(OnboardingStep.Schedule)
            add(OnboardingStep.Injuries)
            add(OnboardingStep.Done)
        }

    val step: OnboardingStep get() = steps[index.coerceIn(steps.indices)]
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val currentYear get() = Year.now().value

    fun updateProfile(draft: ProfileDraft) = _state.update { it.copy(profile = draft) }

    fun togglePreset(name: String, on: Boolean) = _state.update { state ->
        val setup = state.setup
        val updated = if (on) {
            setup.addLocation(name, UUID.randomUUID().toString())
        } else {
            setup.locations.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.let { setup.removeLocation(it.id) } ?: setup
        }
        state.copy(setup = updated)
    }

    fun addLocation(name: String) =
        _state.update { it.copy(setup = it.setup.addLocation(name, UUID.randomUUID().toString())) }

    fun removeLocation(id: String) = _state.update { it.copy(setup = it.setup.removeLocation(id)) }

    fun updateLocation(location: LocationDraft) =
        _state.update { it.copy(setup = it.setup.updateLocation(location.id) { location }) }

    fun setDay(day: DayOfWeek, locationId: String?) =
        _state.update { it.copy(setup = it.setup.setDay(day, locationId)) }

    /** Moves on if the current step is complete; otherwise shows what's missing. */
    fun next() = _state.update { state ->
        if (stepError(state) == null) {
            state.copy(index = (state.index + 1).coerceAtMost(state.steps.lastIndex), showErrors = false)
        } else {
            state.copy(showErrors = true)
        }
    }

    fun back() = _state.update { it.copy(index = (it.index - 1).coerceAtLeast(0), showErrors = false, saveError = null) }

    /** Why "Next" can't continue from the current step, or null if it can. */
    fun stepError(state: OnboardingState = _state.value): String? = when (val step = state.step) {
        OnboardingStep.About -> if (state.profile.aboutErrors(currentYear).isEmpty) null else "Check the fields above"
        OnboardingStep.Goal -> if (state.profile.goal == null) "Choose a goal" else null
        OnboardingStep.Experience -> if (state.profile.experience == null) "Choose one" else null
        OnboardingStep.Locations -> if (state.setup.locations.isEmpty()) "Pick at least one place" else null
        is OnboardingStep.Equipment ->
            if (state.setup.locations.firstOrNull { it.id == step.locationId }?.weightErrors.isNullOrEmpty()) {
                null
            } else {
                "Fix the weight list above"
            }
        OnboardingStep.Injuries -> state.profile.injuriesError
        OnboardingStep.Welcome, OnboardingStep.Schedule, OnboardingStep.Done -> null
    }

    /** Saves everything in one transaction. The app then leaves onboarding by itself. */
    fun finish() {
        val state = _state.value
        if (state.saving) return
        val profile = state.profile.toProfile(currentYear)
        val setup = state.setup.toSetup()
        if (profile == null || setup == null) {
            _state.update { it.copy(saveError = "Something is missing. Go back and check each step.") }
            return
        }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                profiles.completeOnboarding(profile, setup)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, saveError = "Couldn't save. Please try again.") }
            }
        }
    }
}
