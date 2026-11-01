package com.m3.app.feature.workout

import com.m3.core.data.StoredSet
import com.m3.core.data.WorkoutWithSets
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.TrainingSetup
import com.m3.core.training.Deload
import com.m3.core.training.LoggedSet
import com.m3.core.training.PastPerformance
import com.m3.core.training.PlannedExercise
import com.m3.core.training.PlannedSession
import com.m3.core.training.Progression
import com.m3.core.training.Target
import java.time.LocalDate

/** One exercise of a workout with what the owner did last time and what to aim for now. */
data class ExerciseTarget(
    val planned: PlannedExercise,
    val variantKey: String,
    val target: Target,
    /** The sets of the last finished workout with this variant; empty = never done. */
    val last: List<LoggedSet>,
)

/** Joins the plan, the owner's kg lists and the logged history into targets. All numbers come from [Progression]. */
object WorkoutTargets {
    /**
     * Workouts done in an accepted deload week ([deloadWeeks], their Mondays) don't count as history: their lighter
     * loads would pull the next target down. With [deload] the targets are the lighter deload ones.
     */
    fun forSession(
        session: PlannedSession,
        setup: TrainingSetup,
        recent: List<WorkoutWithSets>,
        deloadWeeks: Set<LocalDate> = emptySet(),
        deload: Boolean = false,
    ): List<ExerciseTarget> =
        session.exercises.map { planned ->
            val key = Progression.variantKey(planned.exercise.id, planned.equipment)
            val history = history(key, recent, deloadWeeks)
            val listed = listedKg(setup, session.locationId, planned.equipment)
            val next = Progression.next(planned, history, listed)
            ExerciseTarget(
                planned = planned,
                variantKey = key,
                target = if (deload) Deload.target(next, planned.equipment, listed) else next,
                last = history.firstOrNull()?.sets.orEmpty(),
            )
        }

    /** Past performances of one variant, newest first (one per finished workout), without those from [deloadWeeks]. */
    fun history(variantKey: String, recent: List<WorkoutWithSets>, deloadWeeks: Set<LocalDate> = emptySet()): List<PastPerformance> = recent.mapNotNull { w ->
        if (Deload.mondayOf(w.workout.date) in deloadWeeks) return@mapNotNull null
        val sets = w.sets.filter { it.variantKey == variantKey }.sortedBy { it.setNumber }
        if (sets.isEmpty()) null else PastPerformance(w.workout.date, sets.map { it.toLogged() })
    }

    /** The kg values listed at that place for the bells this variant uses; empty when not listed. */
    fun listedKg(setup: TrainingSetup, locationId: String, equipment: Set<EquipmentKind>): List<Double> {
        val bell = when {
            EquipmentKind.DUMBBELLS in equipment -> EquipmentKind.DUMBBELLS
            EquipmentKind.KETTLEBELLS in equipment -> EquipmentKind.KETTLEBELLS
            else -> return emptyList()
        }
        val location = setup.locations.firstOrNull { it.id == locationId } ?: return emptyList()
        return location.equipment.filterIsInstance<EquipmentItem.Catalog>().firstOrNull { it.kind == bell }?.details?.weightsKg.orEmpty().sorted()
    }

    fun StoredSet.toLogged() = LoggedSet(kg, reps, seconds, rpe)
}
