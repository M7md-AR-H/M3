package com.m3.app.feature.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.m3.app.feature.onboarding.displayName
import com.m3.app.feature.train.rememberToday
import com.m3.core.data.StoredWorkout
import com.m3.core.data.WorkoutRepository
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.theme.M3
import com.m3.core.training.PlannedSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Opens the live workout screen; provided by the navigation host so cards don't need to know about it. */
val LocalOpenWorkout = compositionLocalOf<() -> Unit> { {} }

/** Whether a workout is open. [workout] is null when none is. The state itself is null until known. */
data class OpenWorkout(val workout: StoredWorkout?)

@HiltViewModel
class WorkoutStartViewModel @Inject constructor(private val workouts: WorkoutRepository) : ViewModel() {
    val open: StateFlow<OpenWorkout?> = workouts.observeOpen()
        .map { OpenWorkout(it) }
        .catch { emit(OpenWorkout(null)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Starts today's session (or resumes the open one), then calls [onStarted]. */
    fun start(session: PlannedSession, date: LocalDate, onStarted: () -> Unit) {
        viewModelScope.launch {
            try {
                workouts.startOrResume(date, session.type.name, session.locationId, session.locationName)
                onStarted()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Storage failed: stay where we are; the button is still there to try again.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** "Start workout" / "Resume workout" for today's [session]. Resume shows only for the workout that is open for it. */
@Composable
fun StartWorkoutButton(session: PlannedSession, modifier: Modifier = Modifier, viewModel: WorkoutStartViewModel = hiltViewModel()) {
    val open by viewModel.open.collectAsStateWithLifecycle()
    val known = open ?: return
    val today = rememberToday().value
    val openWorkout = known.workout
    val resuming = openWorkout != null && openWorkout.date == today && openWorkout.sessionType == session.type.name
    val openScreen = LocalOpenWorkout.current
    M3Button(
        text = if (resuming) "Resume workout →" else "Start workout →",
        onClick = { viewModel.start(session, today, openScreen) },
        modifier = modifier.fillMaxWidth(),
    )
}

/** For a session on another day: it can't be started early. */
@Composable
fun StartLaterNote(session: PlannedSession, modifier: Modifier = Modifier) {
    Text(
        text = "You can start this on ${session.day.displayName}.",
        style = M3.typography.bodySmall,
        color = M3.colors.muted,
        modifier = modifier,
    )
}
