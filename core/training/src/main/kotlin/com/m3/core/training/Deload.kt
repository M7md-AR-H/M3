package com.m3.core.training

import com.m3.core.model.CheckIn
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import java.time.LocalDate
import kotlin.math.floor

enum class DeloadReason {
    /** Weeks of training in a row without a rest week. */
    LONG_BLOCK,

    /** A few hard weeks, and the morning check-ins say the body is tired. */
    FATIGUE,
}

/** [hardWeeks] = the unbroken run of training weeks before this one. */
data class DeloadSuggestion(val reason: DeloadReason, val hardWeeks: Int) {
    val sentence: String
        get() = when (reason) {
            DeloadReason.LONG_BLOCK -> "You've trained hard for $hardWeeks weeks in a row. A lighter week lets your body catch up."
            DeloadReason.FATIGUE -> "You've trained hard for $hardWeeks weeks and your check-ins show tired muscles or low energy."
        }
}

/**
 * Deload weeks (ADR 0003: code decides, the model never does). A deload week keeps the same sessions with
 * fewer sets and about 10% lighter loads, for one Monday–Sunday week, and only when the owner accepts it.
 */
object Deload {
    const val SET_FACTOR_PERCENT = 60
    const val LOAD_FACTOR = 0.9
    const val MIN_WEEKS_BETWEEN = 4
    private const val FATIGUE_MIN_RUN = 3
    private const val FATIGUE_DAYS = 14L
    private const val FATIGUE_MIN_CHECK_INS = 4
    private const val FATIGUE_SORENESS = 4.0
    private const val FATIGUE_ENERGY = 2.0
    private const val MAX_LOOKBACK_WEEKS = 52

    const val REASON_LOAD = "Deload week: lighter on purpose. Stop 3–4 reps short of failure."
    const val REASON_NO_LOAD = "Deload week: easy reps only, stop well short of failure."

    fun blockWeeks(experience: Experience): Int = when (experience) {
        Experience.NEW -> 8
        Experience.SOME -> 6
        Experience.EXPERIENCED -> 5
    }

    /**
     * A suggestion for the week starting [thisMonday], or null. A hard week is a finished week with at least one
     * workout; a week without any, or an accepted deload week, resets the count. [today] ends the 14-day window of
     * check-ins (default: the end of this week).
     */
    fun suggest(
        thisMonday: LocalDate,
        workoutDates: Collection<LocalDate>,
        checkIns: List<CheckIn>,
        deloadWeeks: Set<LocalDate>,
        experience: Experience,
        plannedSessions: Int,
        today: LocalDate = thisMonday.plusDays(6),
    ): DeloadSuggestion? {
        if (plannedSessions <= 0 || thisMonday in deloadWeeks) return null
        if (deloadWeeks.any { it.isAfter(thisMonday.minusWeeks(MIN_WEEKS_BETWEEN.toLong())) && it.isBefore(thisMonday) }) return null
        val run = hardWeeks(thisMonday, workoutDates, deloadWeeks)
        if (run >= blockWeeks(experience)) return DeloadSuggestion(DeloadReason.LONG_BLOCK, run)
        if (run >= FATIGUE_MIN_RUN) {
            val recent = checkIns.filter { !it.date.isBefore(today.minusDays(FATIGUE_DAYS - 1)) && !it.date.isAfter(today) }
            if (recent.size >= FATIGUE_MIN_CHECK_INS) {
                val soreness = recent.map { it.soreness }.average()
                val energy = recent.map { it.energy }.average()
                if (soreness >= FATIGUE_SORENESS || energy <= FATIGUE_ENERGY) return DeloadSuggestion(DeloadReason.FATIGUE, run)
            }
        }
        return null
    }

    /** The unbroken run of weeks, counting back from last week, that had a workout and weren't deload weeks. */
    fun hardWeeks(thisMonday: LocalDate, workoutDates: Collection<LocalDate>, deloadWeeks: Set<LocalDate>): Int {
        var run = 0
        for (back in 1..MAX_LOOKBACK_WEEKS) {
            val monday = thisMonday.minusWeeks(back.toLong())
            val sunday = monday.plusDays(6)
            if (monday in deloadWeeks || workoutDates.none { !it.isBefore(monday) && !it.isAfter(sunday) }) break
            run++
        }
        return run
    }

    /** Fewer sets everywhere except conditioning; rep ranges and rest stay. */
    fun apply(plan: WeekPlan): WeekPlan = plan.copy(
        sessions = plan.sessions.map { session ->
            session.copy(
                exercises = session.exercises.map { planned ->
                    if (planned.role == SlotRole.CONDITIONING) planned else planned.copy(prescription = planned.prescription.copy(sets = sets(planned.prescription.sets)))
                },
            )
        },
    )

    /** ceil(60%), but never below 2 for an exercise that had 2 or more. */
    fun sets(sets: Int): Int {
        if (sets < 2) return sets
        return maxOf(2, (sets * SET_FACTOR_PERCENT + 99) / 100)
    }

    /** The Monday of the week [date] is in. */
    fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /**
     * The target for a deload week: about 10% lighter than [target], rounded down to something the owner can load
     * ([listedKg] ascending; empty = not listed). First-time targets stay as they are.
     */
    fun target(target: Target, equipment: Set<EquipmentKind>, listedKg: List<Double>): Target {
        if (target.kind == TargetKind.START) return target
        val kg = target.kg ?: return Target(TargetKind.DELOAD, null, REASON_NO_LOAD)
        val lighter = when (Progression.loadKind(equipment)) {
            LoadKind.PLATES -> roundedDown(kg, Progression.PLATE_STEP_KG)
            LoadKind.STACK -> roundedDown(kg, Progression.STACK_STEP_KG)
            LoadKind.LISTED ->
                if (listedKg.isNotEmpty()) {
                    listedKg.sorted().lastOrNull { it <= kg * LOAD_FACTOR + Progression.EPSILON } ?: listedKg.min()
                } else {
                    kg - Progression.bellStep(equipment)
                }
            // A fixed load can't be made lighter.
            LoadKind.NONE -> return Target(TargetKind.DELOAD, kg, REASON_NO_LOAD)
        }
        // Nothing lighter that the owner could load: keep the load rather than suggest zero or more.
        return Target(TargetKind.DELOAD, if (lighter > 0 && lighter <= kg + Progression.EPSILON) lighter else kg, REASON_LOAD)
    }

    private fun roundedDown(kg: Double, step: Double) = floor(kg * LOAD_FACTOR / step + Progression.EPSILON) * step
}
