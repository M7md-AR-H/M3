package com.m3.core.training

import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.model.Profile
import com.m3.core.model.TrainingLocation
import com.m3.core.model.TrainingSetup
import com.m3.core.training.MovementPattern.HINGE
import com.m3.core.training.MovementPattern.HORIZONTAL_PULL
import com.m3.core.training.MovementPattern.HORIZONTAL_PUSH
import com.m3.core.training.MovementPattern.LUNGE
import com.m3.core.training.MovementPattern.SQUAT
import com.m3.core.training.MovementPattern.VERTICAL_PULL
import com.m3.core.training.MovementPattern.VERTICAL_PUSH
import java.time.DayOfWeek

/**
 * Builds the week from the owner's availability, goal, experience and equipment (ADR 0009).
 * Pure and deterministic: the same inputs always give the same plan. The model never writes this (ADR 0003).
 */
class PlanGenerator(private val catalog: ExerciseCatalog) {

    fun generate(profile: Profile?, setup: TrainingSetup): WeekPlan {
        val goal = profile?.goal ?: Goal.GENERAL_FITNESS
        val experience = profile?.experience ?: Experience.NEW
        val available = DayOfWeek.entries.mapNotNull { day -> setup.locationFor(day)?.let { day to it } }
        val target = targetSessions(goal, experience)
        val chosen = chooseDays(available.map { (day, location) -> day to inventory(location).size }, target)
        val types = sessionTypes(chosen.size)
        val used = mutableSetOf<String>()
        val sessions = chosen.mapIndexed { index, day ->
            val location = available.first { it.first == day }.second
            buildSession(day, location, types[index], goal, experience, used)
        }
        return WeekPlan(sessions, available.map { it.first }, target)
    }

    private fun buildSession(
        day: DayOfWeek,
        location: TrainingLocation,
        type: SessionType,
        goal: Goal,
        experience: Experience,
        usedThisWeek: MutableSet<String>,
    ): PlannedSession {
        val inventory = inventory(location)
        val slots = slotsFor(type, goal, experience)
        val inSession = mutableSetOf<String>()
        val exercises = mutableListOf<PlannedExercise>()
        val unfilled = mutableListOf<Slot>()
        for (slot in slots) {
            val pick = pick(slot, inventory, goal, experience, usedThisWeek, inSession)
            if (pick == null) {
                unfilled += slot
                continue
            }
            val (exercise, equipment) = pick
            inSession += exercise.id
            usedThisWeek += exercise.id
            exercises += PlannedExercise(exercise, equipment, slot.role, prescribe(slot, exercise, equipment, goal, experience))
        }
        return PlannedSession(day, location.id, location.name, type, exercises, unfilled)
    }

    private fun pick(
        slot: Slot,
        inventory: Set<EquipmentKind>,
        goal: Goal,
        experience: Experience,
        usedThisWeek: Set<String>,
        inSession: Set<String>,
    ): Pair<Exercise, Set<EquipmentKind>>? {
        val level = experience.level
        val wantsLoad = goal == Goal.BUILD_MUSCLE || goal == Goal.GET_STRONGER
        return catalog.exercises.withIndex()
            .filter { (_, e) -> slot.matches(e) && e.id !in inSession && !(slot.role == SlotRole.MAIN && e.ballistic) }
            .mapNotNull { (index, e) -> e.supportedBy(inventory)?.let { Triple(index, e, it) } }
            .minWithOrNull(
                compareBy<Triple<Int, Exercise, Set<EquipmentKind>>> { (_, e, equipment) ->
                    var score = 0
                    if (e.level > level) score += 10
                    if (slot.role == SlotRole.MAIN) {
                        if (level - e.level >= 2) score += 1
                        if (goal == Goal.GET_STRONGER && !e.heavy) score += 2
                        if (!e.compound) score += 3
                        if (e.unilateral) score += 1
                    }
                    if (slot.role != SlotRole.MAIN && e.heavy) score += 2
                    // Experienced lifters shouldn't get the easiest option everywhere.
                    if (slot.role != SlotRole.MAIN && level - e.level >= 2) score += 1
                    if (wantsLoad && equipment.isEmpty() && slot.role != SlotRole.CORE) score += 2
                    if (e.id in usedThisWeek) score += 4
                    score
                }.thenBy { it.first },
            )
            ?.let { (_, e, equipment) -> e to equipment }
    }

    companion object {
        const val MAX_SESSIONS = 5

        /** How many sessions a week suit this goal and experience, before availability is applied. */
        fun targetSessions(goal: Goal, experience: Experience): Int = when (experience) {
            Experience.NEW -> 3
            Experience.SOME -> if (goal == Goal.BUILD_MUSCLE || goal == Goal.GET_STRONGER) 4 else 3
            Experience.EXPERIENCED -> if (goal == Goal.BUILD_MUSCLE) 5 else 4
        }

        /**
         * Picks [target] of the free days (or all of them if fewer). Best: fewest back-to-back training days
         * (Sunday→Monday counts), then the largest smallest gap, then the most equipment, then earliest days.
         */
        fun chooseDays(available: List<Pair<DayOfWeek, Int>>, target: Int): List<DayOfWeek> {
            val count = minOf(target, available.size, MAX_SESSIONS)
            if (count <= 0) return emptyList()
            val days = available.sortedBy { it.first }
            return combinations(days.indices.toList(), count)
                .map { indices -> indices.map { days[it] } }
                .minWithOrNull(
                    compareBy<List<Pair<DayOfWeek, Int>>> { pick -> backToBack(pick.map { it.first }) }
                        .thenByDescending { pick -> smallestGap(pick.map { it.first }) }
                        .thenByDescending { pick -> pick.sumOf { it.second } }
                        .thenBy { pick -> pick.joinToString { it.first.value.toString() } },
                )!!
                .map { it.first }
        }

        fun sessionTypes(count: Int): List<SessionType> = when (count) {
            0 -> emptyList()
            1 -> listOf(SessionType.FULL_BODY_A)
            2 -> listOf(SessionType.FULL_BODY_A, SessionType.FULL_BODY_B)
            3 -> listOf(SessionType.FULL_BODY_A, SessionType.FULL_BODY_B, SessionType.FULL_BODY_C)
            4 -> listOf(SessionType.UPPER_A, SessionType.LOWER_A, SessionType.UPPER_B, SessionType.LOWER_B)
            else -> listOf(SessionType.UPPER_A, SessionType.LOWER_A, SessionType.PUSH, SessionType.PULL, SessionType.LEGS)
        }

        fun slotsFor(type: SessionType, goal: Goal, experience: Experience): List<Slot> {
            val base = TEMPLATES.getValue(type)
            val trimmed = if (experience == Experience.NEW) base.take(5) else base
            val finisher = goal == Goal.LOSE_FAT || goal == Goal.GENERAL_FITNESS
            return if (finisher) trimmed + Slot(MovementPattern.CONDITIONING, SlotRole.CONDITIONING) else trimmed
        }

        /**
         * Sets, reps and rest from the goal, the slot's role and the exercise itself. Strength ranges (3–5) are
         * only for [Exercise.heavy] lifts; bodyweight and single-side work stays in moderate ranges; beginners
         * learn technique at 8–12.
         */
        fun prescribe(
            slot: Slot,
            exercise: Exercise,
            equipment: Set<EquipmentKind>,
            goal: Goal,
            experience: Experience,
        ): Prescription {
            val beginner = experience == Experience.NEW
            fun sets(n: Int) = if (beginner) maxOf(2, n - 1) else n
            val reps = ExerciseUnit.REPS
            val bodyweight = equipment.isEmpty()
            val light = bodyweight || exercise.unilateral
            return when {
                exercise.unit == ExerciseUnit.MINUTES && exercise.intervals ->
                    Prescription(if (beginner) 6 else 8, 20, 30, ExerciseUnit.SECONDS, 40)
                exercise.unit == ExerciseUnit.MINUTES ->
                    if (goal == Goal.LOSE_FAT) Prescription(1, 10, 15, ExerciseUnit.MINUTES, 0)
                    else Prescription(1, 8, 12, ExerciseUnit.MINUTES, 0)
                exercise.unit == ExerciseUnit.SECONDS ->
                    if (slot.pattern == MovementPattern.CARRY) Prescription(sets(3), 30, 45, ExerciseUnit.SECONDS, 90)
                    else if (beginner) Prescription(2, 20, 30, ExerciseUnit.SECONDS, 45)
                    else Prescription(3, 30, 45, ExerciseUnit.SECONDS, 45)
                exercise.repsMin != null && exercise.repsMax != null ->
                    Prescription(sets(3), exercise.repsMin, exercise.repsMax, reps, 120)
                exercise.ballistic -> Prescription(sets(3), 10, 15, reps, 60)
                slot.role == SlotRole.CORE || slot.role == SlotRole.CONDITIONING -> Prescription(sets(3), 8, 12, reps, 45)
                slot.role == SlotRole.MAIN -> when {
                    beginner -> Prescription(sets(3), 8, 12, reps, 90)
                    goal == Goal.GET_STRONGER && exercise.heavy -> Prescription(4, 3, 5, reps, 180)
                    goal == Goal.GET_STRONGER && !light -> Prescription(4, 5, 8, reps, 150)
                    goal == Goal.GET_STRONGER -> Prescription(3, 6, 10, reps, 120)
                    goal == Goal.BUILD_MUSCLE && bodyweight -> Prescription(3, 8, 15, reps, 90)
                    goal == Goal.BUILD_MUSCLE && light -> Prescription(3, 8, 12, reps, 90)
                    goal == Goal.BUILD_MUSCLE -> Prescription(4, 6, 10, reps, 120)
                    bodyweight -> Prescription(3, 10, 15, reps, 90)
                    else -> Prescription(3, 8, 12, reps, 90)
                }
                slot.role == SlotRole.ACCESSORY -> when (goal) {
                    Goal.GET_STRONGER -> if (light) Prescription(sets(3), 8, 12, reps, 90) else Prescription(sets(3), 6, 10, reps, 120)
                    Goal.BUILD_MUSCLE -> Prescription(sets(3), 8, 12, reps, 90)
                    Goal.LOSE_FAT, Goal.GENERAL_FITNESS -> Prescription(sets(3), 10, 15, reps, 60)
                }
                else ->
                    if (goal == Goal.LOSE_FAT || goal == Goal.GENERAL_FITNESS) Prescription(2, 12, 15, reps, 45)
                    else Prescription(sets(3), 10, 15, reps, 60)
            }
        }

        /** Catalog equipment at a location. Custom items aren't understood by the engine yet (ADR 0008). */
        fun inventory(location: TrainingLocation): Set<EquipmentKind> =
            location.equipment.filterIsInstance<EquipmentItem.Catalog>().map { it.kind }.toSet()

        private val Experience.level: Int
            get() = when (this) {
                Experience.NEW -> 1
                Experience.SOME -> 2
                Experience.EXPERIENCED -> 3
            }

        private fun backToBack(days: List<DayOfWeek>): Int {
            if (days.size < 2) return 0
            val set = days.toSet()
            return days.count { it.plus(1) in set }
        }

        private fun smallestGap(days: List<DayOfWeek>): Int {
            if (days.size < 2) return 7
            val sorted = days.map { it.value }.sorted()
            val gaps = sorted.zipWithNext { a, b -> b - a } + (sorted.first() + 7 - sorted.last())
            return gaps.min()
        }

        private fun combinations(items: List<Int>, k: Int): List<List<Int>> {
            if (k == 0) return listOf(emptyList())
            if (items.size < k) return emptyList()
            val head = items.first()
            val tail = items.drop(1)
            return combinations(tail, k - 1).map { listOf(head) + it } + combinations(tail, k)
        }

        private fun main(p: MovementPattern) = Slot(p, SlotRole.MAIN)
        private fun accessory(p: MovementPattern) = Slot(p, SlotRole.ACCESSORY)
        private fun iso(m: Muscle) = Slot(MovementPattern.ISOLATION, SlotRole.ISOLATION, m)
        private val core = Slot(MovementPattern.CORE, SlotRole.CORE)
        private val carry = Slot(MovementPattern.CARRY, SlotRole.CORE)

        private val TEMPLATES: Map<SessionType, List<Slot>> = run {
            mapOf(
                SessionType.FULL_BODY_A to listOf(main(SQUAT), main(HORIZONTAL_PUSH), main(HORIZONTAL_PULL), accessory(HINGE), core, iso(Muscle.SHOULDERS)),
                SessionType.FULL_BODY_B to listOf(main(HINGE), main(VERTICAL_PUSH), main(VERTICAL_PULL), accessory(LUNGE), carry, iso(Muscle.BICEPS)),
                SessionType.FULL_BODY_C to listOf(main(LUNGE), accessory(HORIZONTAL_PUSH), accessory(HORIZONTAL_PULL), accessory(HINGE), core, iso(Muscle.TRICEPS)),
                SessionType.UPPER_A to listOf(main(HORIZONTAL_PUSH), main(HORIZONTAL_PULL), accessory(VERTICAL_PUSH), accessory(VERTICAL_PULL), iso(Muscle.BICEPS), iso(Muscle.TRICEPS)),
                SessionType.LOWER_A to listOf(main(SQUAT), accessory(HINGE), accessory(LUNGE), iso(Muscle.HAMSTRINGS), core, iso(Muscle.CALVES)),
                SessionType.UPPER_B to listOf(main(VERTICAL_PUSH), main(VERTICAL_PULL), accessory(HORIZONTAL_PUSH), accessory(HORIZONTAL_PULL), iso(Muscle.SHOULDERS), iso(Muscle.BICEPS)),
                SessionType.LOWER_B to listOf(main(HINGE), accessory(SQUAT), accessory(LUNGE), iso(Muscle.QUADS), core, iso(Muscle.GLUTES)),
                SessionType.PUSH to listOf(main(HORIZONTAL_PUSH), accessory(VERTICAL_PUSH), accessory(HORIZONTAL_PUSH), iso(Muscle.SHOULDERS), iso(Muscle.TRICEPS), iso(Muscle.CHEST)),
                SessionType.PULL to listOf(main(VERTICAL_PULL), accessory(HORIZONTAL_PULL), accessory(HORIZONTAL_PULL), iso(Muscle.UPPER_BACK), iso(Muscle.BICEPS), carry),
                SessionType.LEGS to listOf(main(SQUAT), accessory(HINGE), accessory(LUNGE), iso(Muscle.QUADS), iso(Muscle.HAMSTRINGS), iso(Muscle.CALVES)),
            )
        }
    }
}
