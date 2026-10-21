package com.m3.coach

import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.UiChange
import com.m3.core.model.EquipmentKind
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A change the Coach proposed, after Kotlin parsed and validated it (ADR 0003, ADR 0010).
 * Nothing here has been applied yet: the owner taps Apply on the proposal card.
 */
sealed interface CoachAction {
    /** The owner lock that blocks this action for agents. */
    val lock: LockKey

    /** Equipment for one day this week only ("today I only have X and Y"). Empty = bodyweight. */
    data class SetDayEquipment(val date: LocalDate, val items: Set<EquipmentKind>) : CoachAction {
        val day: DayOfWeek get() = date.dayOfWeek
        override val lock get() = LockKey.TRAINING_PLAN
    }

    /**
     * Stop using an exercise until the owner allows it again. For pain, [similar] holds the rest of its movement
     * family (e.g. every lunge), so the planner doesn't swap in an exercise that loads the joint the same way.
     */
    data class AvoidExercise(
        val exerciseId: String,
        val exerciseName: String,
        val reason: String,
        val similar: List<String> = emptyList(),
        /** "lunges", "squats" …; set when [similar] is. */
        val familyName: String? = null,
    ) : CoachAction {
        override val lock get() = LockKey.TRAINING_PLAN
        val allIds: List<String> get() = listOf(exerciseId) + similar
    }

    data class AllowExercise(val exerciseId: String, val exerciseName: String) : CoachAction {
        override val lock get() = LockKey.TRAINING_PLAN
    }

    /** Where the owner is free to train on a weekday; null location = not free (ADR 0009). */
    data class SetAvailability(val day: DayOfWeek, val locationId: String?, val locationName: String?) : CoachAction {
        override val lock get() = LockKey.AVAILABILITY
    }

    /** A look/layout change, applied through the UiConfigStore with the Coach as actor (ADR 0007). */
    data class Ui(val change: UiChange) : CoachAction {
        override val lock: LockKey get() = change.lockKey ?: LockKey.THEME
    }
}

sealed interface ToolResult {
    data class Valid(val action: CoachAction) : ToolResult

    /** Not one of the app's tools at all (the model made one up): dropped without a card. */
    data object Ignored : ToolResult

    /** The model asked for something the app can't do; [message] is shown to the owner. */
    data class Invalid(val message: String) : ToolResult
}
