package com.m3.app.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.core.data.ReminderRepository
import com.m3.core.model.ReminderKind
import com.m3.core.model.ReminderMessage
import com.m3.core.model.ReminderSetting
import com.m3.core.model.ReminderSettings
import com.m3.core.model.ReminderTarget
import com.m3.core.model.Reminders
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminders: ReminderRepository,
    private val engine: ReminderEngine,
    private val notifier: ReminderNotifier,
) : ViewModel() {
    val settings: StateFlow<ReminderSettings> = reminders.observe()
        .catch { emit(ReminderSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReminderSettings())

    private val _canPost = MutableStateFlow(notifier.canPost())
    val canPost: StateFlow<Boolean> = _canPost.asStateFlow()

    /** Quick taps on a stepper run in order, each from the saved value. */
    private val writes = Mutex()

    /** After the permission prompt, and on coming back from Android settings. */
    fun refreshPermission() {
        _canPost.value = notifier.canPost()
        viewModelScope.launch { engine.reschedule() }
    }

    fun setOn(kind: ReminderKind, on: Boolean) = change(kind) { it.copy(on = on) }

    fun stepMinute(kind: ReminderKind, steps: Int) = change(kind) { it.copy(minute = Reminders.stepMinute(it.minute, steps)) }

    fun stepEveryHours(kind: ReminderKind, steps: Int) = change(kind) {
        it.copy(everyHours = (it.everyHours + steps).coerceIn(Reminders.WATER_HOURS))
    }

    fun setShowDetails(show: Boolean) = write { reminders.setShowDetails(show) }

    /** Proves the whole path to the owner: permission, channel, Do Not Disturb and all. */
    fun sendTest() = notifier.post(
        ReminderMessage(ReminderKind.MORNING, "Reminders work", "This is how M³ will reach you.", ReminderTarget.YOU),
    )

    private fun change(kind: ReminderKind, update: (ReminderSetting) -> ReminderSetting) = write {
        reminders.save(update(reminders.observe().first()[kind]))
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                try {
                    block()
                    engine.reschedule()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The rows show what's saved, so a failed write simply doesn't stick; tapping again retries.
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
