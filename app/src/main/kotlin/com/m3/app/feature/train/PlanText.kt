package com.m3.app.feature.train

import com.m3.app.feature.onboarding.displayName
import com.m3.core.model.Durations
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.training.ExerciseUnit
import com.m3.core.training.MovementPattern
import com.m3.core.training.PlannedExercise
import com.m3.core.training.Slot
import com.m3.core.training.WeekPlan

/** Plain-language text for plans; the numbers all come from the plan generator. */
object PlanText {
    /** "4 × 6–10", "3 × 30–45 s", "10–15 min"; unilateral work adds "each side". */
    fun prescription(planned: PlannedExercise): String {
        val p = planned.prescription
        val range = if (p.min == p.max) "${p.min}" else "${p.min}–${p.max}"
        val amount = when (p.unit) {
            ExerciseUnit.REPS -> "${p.sets} × $range"
            ExerciseUnit.SECONDS -> "${p.sets} × $range s"
            ExerciseUnit.MINUTES -> if (p.sets == 1) "$range min" else "${p.sets} × $range min"
        }
        val side = if (planned.exercise.unilateral && p.unit != ExerciseUnit.MINUTES) " each side" else ""
        val rest = if (p.restSeconds > 0) " · rest ${Durations.rest(p.restSeconds)}" else ""
        return amount + side + rest
    }

    fun equipment(planned: PlannedExercise): String =
        if (planned.equipment.isEmpty()) "Bodyweight" else planned.equipment.joinToString(" + ") { it.displayName }

    fun unfilled(slot: Slot, byAvoided: Boolean = false): String {
        val what = slot.muscle?.displayName?.lowercase() ?: when (slot.pattern) {
            MovementPattern.SQUAT -> "squat"
            MovementPattern.HINGE -> "hip hinge"
            MovementPattern.LUNGE -> "lunge"
            MovementPattern.HORIZONTAL_PUSH -> "pressing"
            MovementPattern.VERTICAL_PUSH -> "overhead pressing"
            MovementPattern.HORIZONTAL_PULL -> "rowing"
            MovementPattern.VERTICAL_PULL -> "pulling from above"
            MovementPattern.CARRY -> "carry"
            MovementPattern.CORE -> "core"
            MovementPattern.ISOLATION -> "isolation"
            MovementPattern.CONDITIONING -> "conditioning"
        }
        if (byAvoided) return "No $what exercise: you're avoiding the ones possible here. Ask the Coach to use one again."
        return "No $what exercise possible with the equipment here."
    }

    private fun goalPhrase(goal: Goal) = when (goal) {
        Goal.LOSE_FAT -> "losing fat"
        Goal.BUILD_MUSCLE -> "building muscle"
        Goal.GET_STRONGER -> "getting stronger"
        Goal.GENERAL_FITNESS -> "general fitness"
    }

    private fun experiencePhrase(experience: Experience) = when (experience) {
        Experience.NEW -> "as a beginner"
        Experience.SOME -> "with some experience"
        Experience.EXPERIENCED -> "with years of experience"
    }

    /** Why the week looks the way it does, from the generator's facts. */
    fun summary(plan: WeekPlan, goal: Goal?, experience: Experience?): String {
        val free = plan.availableDays.size
        if (free == 0) return "Tell your coach when you're free (You → Training setup) to get a plan."
        val sessions = plan.sessions.size
        val who = if (goal != null && experience != null) "For ${goalPhrase(goal)} ${experiencePhrase(experience)}, " else ""
        val dayWord = if (sessions == 1) "session" else "sessions"
        return if (plan.limitedByAvailability) {
            "${who}your coach would aim for ${plan.targetSessions} sessions a week. You're free $free " +
                "${if (free == 1) "day" else "days"}, so each of them is a training day."
        } else {
            "You're free $free days. ${who.replaceFirstChar { it.uppercase() }}your coach plans $sessions $dayWord " +
                "a week, with rest days in between: " +
                plan.sessions.joinToString(", ") { it.day.displayName } + "."
        }
    }
}
