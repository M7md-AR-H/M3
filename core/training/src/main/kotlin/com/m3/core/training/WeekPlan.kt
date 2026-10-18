package com.m3.core.training

import com.m3.core.model.EquipmentKind
import java.time.DayOfWeek

enum class SessionType(val displayName: String) {
    FULL_BODY_A("Full body A"),
    FULL_BODY_B("Full body B"),
    FULL_BODY_C("Full body C"),
    UPPER_A("Upper body A"),
    LOWER_A("Lower body A"),
    UPPER_B("Upper body B"),
    LOWER_B("Lower body B"),
    PUSH("Push"),
    PULL("Pull"),
    LEGS("Legs"),
}

/** What a slot is for; it decides sets, reps and rest. */
enum class SlotRole { MAIN, ACCESSORY, ISOLATION, CORE, CONDITIONING }

/** One place in a session: a movement pattern, optionally narrowed to a target muscle. */
data class Slot(val pattern: MovementPattern, val role: SlotRole, val muscle: Muscle? = null) {
    fun matches(exercise: Exercise): Boolean =
        exercise.pattern == pattern && (muscle == null || muscle in exercise.primary)
}

/** Sets × [min]–[max] of [unit], with [restSeconds] between sets. Loads come with logging (Phase 1c-3). */
data class Prescription(
    val sets: Int,
    val min: Int,
    val max: Int,
    val unit: ExerciseUnit,
    val restSeconds: Int,
)

data class PlannedExercise(
    val exercise: Exercise,
    /** The equipment alternative used at this location; empty = bodyweight. */
    val equipment: Set<EquipmentKind>,
    val role: SlotRole,
    val prescription: Prescription,
)

data class PlannedSession(
    val day: DayOfWeek,
    val locationId: String,
    val locationName: String,
    val type: SessionType,
    val exercises: List<PlannedExercise>,
    /** Slots nothing at this location could fill. The plan says so rather than inventing something. */
    val unfilled: List<Slot>,
)

/** One week of training. Days without a session are rest days. */
data class WeekPlan(
    val sessions: List<PlannedSession>,
    /** Days the owner said they're free (ADR 0009). */
    val availableDays: List<DayOfWeek>,
    /** Sessions the owner's goal and experience call for, before capping at [availableDays]. */
    val targetSessions: Int,
) {
    fun sessionOn(day: DayOfWeek): PlannedSession? = sessions.firstOrNull { it.day == day }

    val restDays: List<DayOfWeek> get() = DayOfWeek.entries.filter { day -> sessions.none { it.day == day } }

    /** True when there were fewer free days than the target, so every free day is a training day. */
    val limitedByAvailability: Boolean get() = availableDays.size < targetSessions
}
