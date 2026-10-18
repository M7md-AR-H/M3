package com.m3.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.core.data.ProfileRepository
import com.m3.core.data.TrainingSetupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Year
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditState<T>(
    /** Null while loading from the database. */
    val draft: T? = null,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EditState<ProfileDraft>())
    val state: StateFlow<EditState<ProfileDraft>> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profiles.observe().first()
            _state.update { it.copy(draft = profile?.let(ProfileDraft::from) ?: ProfileDraft()) }
        }
    }

    fun update(draft: ProfileDraft) = _state.update { it.copy(draft = draft, message = null, saved = false) }

    fun save() {
        val state = _state.value
        val profile = state.draft?.toProfile(Year.now().value)
        if (state.saving) return
        if (profile == null) {
            _state.update { it.copy(showErrors = true, message = "Check the fields above") }
            return
        }
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result = try {
                profiles.save(profile)
                "Saved"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "Couldn't save. Please try again."
            }
            _state.update { it.copy(saving = false, message = result, saved = result == "Saved", showErrors = false) }
        }
    }
}

@HiltViewModel
class TrainingSetupViewModel @Inject constructor(
    private val setups: TrainingSetupRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EditState<SetupDraft>())
    val state: StateFlow<EditState<SetupDraft>> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val setup = setups.observe().first()
            _state.update { it.copy(draft = SetupDraft.from(setup)) }
        }
    }

    private fun edit(transform: (SetupDraft) -> SetupDraft) = _state.update { state ->
        state.draft?.let { state.copy(draft = transform(it), message = null, saved = false) } ?: state
    }

    fun togglePreset(name: String, on: Boolean) = edit { setup ->
        if (on) {
            setup.addLocation(name, UUID.randomUUID().toString())
        } else {
            setup.locations.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.let { setup.removeLocation(it.id) } ?: setup
        }
    }

    fun addLocation(name: String) = edit { it.addLocation(name, UUID.randomUUID().toString()) }

    fun removeLocation(id: String) = edit { it.removeLocation(id) }

    fun updateLocation(location: LocationDraft) = edit { it.updateLocation(location.id) { location } }

    fun setDay(day: DayOfWeek, locationId: String?) = edit { it.setDay(day, locationId) }

    fun save() {
        val state = _state.value
        if (state.saving) return
        val setup = state.draft?.toSetup()
        if (setup == null) {
            _state.update { it.copy(showErrors = true, message = "Fix the weight lists marked above") }
            return
        }
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result = try {
                setups.replace(setup)
                "Saved"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "Couldn't save. Please try again."
            }
            _state.update { it.copy(saving = false, message = result, saved = result == "Saved", showErrors = false) }
        }
    }
}
