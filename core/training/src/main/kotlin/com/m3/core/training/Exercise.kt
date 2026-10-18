package com.m3.core.training

import com.m3.core.model.EquipmentKind
import kotlinx.serialization.Serializable

/** Movement patterns from ADR 0008, plus ISOLATION for single-muscle work. */
enum class MovementPattern {
    SQUAT,
    HINGE,
    LUNGE,
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    HORIZONTAL_PULL,
    VERTICAL_PULL,
    CARRY,
    CORE,
    ISOLATION,
    CONDITIONING,
}

enum class Muscle(val displayName: String) {
    CHEST("Chest"),
    UPPER_BACK("Upper back"),
    LATS("Lats"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    ABS("Abs"),
    LOWER_BACK("Lower back"),
}

/** What a set counts: repetitions, seconds held, or minutes of work. */
enum class ExerciseUnit { REPS, SECONDS, MINUTES }

/**
 * One exercise in the bundled catalog (`resources/m3/exercises.json`).
 *
 * [equipment] lists alternatives in order of preference: the exercise is possible if every kind in
 * any one alternative is available. An empty alternative means bodyweight only.
 */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val pattern: MovementPattern,
    val primary: List<Muscle>,
    val secondary: List<Muscle> = emptyList(),
    /** 1 = beginner-friendly, 2 = needs some practice, 3 = advanced. */
    val level: Int,
    val compound: Boolean,
    val unilateral: Boolean = false,
    /** A big barbell lift that belongs in a main slot, not as an accessory after another one. Only these get strength rep ranges (3–5). */
    val heavy: Boolean = false,
    /** Explosive work (e.g. swings): never a main lift, always moderate-to-high reps. */
    val ballistic: Boolean = false,
    /** High-intensity conditioning done as work/rest rounds, not one continuous block. */
    val intervals: Boolean = false,
    /** Fixed rep range for exercises that need one regardless of goal (e.g. slow negatives). */
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val unit: ExerciseUnit = ExerciseUnit.REPS,
    val equipment: List<Set<EquipmentKind>>,
) {
    /**
     * The first alternative the inventory supports, as the gear actually used there, or null.
     * Bodyweight is always available; an adjustable bench also works as a flat bench.
     */
    fun supportedBy(inventory: Set<EquipmentKind>): Set<EquipmentKind>? {
        val benchStandIn = EquipmentKind.FLAT_BENCH !in inventory && EquipmentKind.ADJUSTABLE_BENCH in inventory
        val effective = if (benchStandIn) inventory + EquipmentKind.FLAT_BENCH else inventory
        val alternative = equipment.firstOrNull { effective.containsAll(it) } ?: return null
        return if (benchStandIn && EquipmentKind.FLAT_BENCH in alternative) {
            alternative - EquipmentKind.FLAT_BENCH + EquipmentKind.ADJUSTABLE_BENCH
        } else {
            alternative
        }
    }
}
