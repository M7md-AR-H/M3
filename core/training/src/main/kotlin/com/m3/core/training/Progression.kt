package com.m3.core.training

import com.m3.core.model.EquipmentKind
import com.m3.core.model.Units
import java.time.LocalDate
import kotlin.math.floor

/** One logged set. Reps or seconds, depending on the exercise; kg is null for bodyweight work. */
data class LoggedSet(
    val kg: Double?,
    val reps: Int? = null,
    val seconds: Int? = null,
    /** Optional effort, 6–10 (10 = nothing left). */
    val rpe: Double? = null,
)

/** The sets of one exercise variant from one past workout. */
data class PastPerformance(val date: LocalDate, val sets: List<LoggedSet>)

/** How the load is set for a variant, from the equipment it uses. */
enum class LoadKind {
    /** Dumbbells or kettlebells: steps come from the owner's kg list (kg per bell). */
    LISTED,

    /** Barbell, EZ/trap bar, Smith machine, landmine: plates in 2.5 kg steps. */
    PLATES,

    /** Cable and selectorised machines: stacks in 5 kg steps. */
    STACK,

    /** A fixed load (sandbag, medicine ball, vest) or no load: progress reps, not kg. */
    NONE,
}

enum class TargetKind { START, INCREASE, HOLD, DECREASE }

/** What to aim for next time. [kg] null = no load to suggest (bodyweight, fixed load, or first time). */
data class Target(val kind: TargetKind, val kg: Double?, val reason: String)

/**
 * Progressive overload (ADR 0003): double progression, decided in Kotlin, never by the model.
 * - Every planned set at the top of the rep range (and not at RPE 10) → one load step up next time.
 * - Below the bottom of the range in the last two workouts at the same load → one step down.
 * - Otherwise the same load, aiming for one more rep.
 * Never more than one step at a time. Tracked per variant (exercise + equipment, ADR 0008), so home
 * and gym stay separate.
 */
object Progression {
    const val PLATE_STEP_KG = 2.5
    const val STACK_STEP_KG = 5.0

    /** Unlisted dumbbells/kettlebells: the usual gaps between pairs. */
    private const val DUMBBELL_STEP_KG = 2.0
    private const val KETTLEBELL_STEP_KG = 4.0

    /** "back_squat@BARBELL+SQUAT_RACK+WEIGHT_PLATES", "push_up@bodyweight". */
    fun variantKey(exerciseId: String, equipment: Set<EquipmentKind>): String =
        exerciseId + "@" + if (equipment.isEmpty()) "bodyweight" else equipment.map { it.name }.sorted().joinToString("+")

    fun loadKind(equipment: Set<EquipmentKind>): LoadKind = when {
        EquipmentKind.DUMBBELLS in equipment || EquipmentKind.KETTLEBELLS in equipment -> LoadKind.LISTED
        equipment.any { it in PLATE_KINDS } -> LoadKind.PLATES
        equipment.any { it in STACK_KINDS } -> LoadKind.STACK
        else -> LoadKind.NONE
    }

    /**
     * The next target for [planned], from [history] (newest first) and the kg values the owner has for the
     * bells this variant uses ([listedKg], ascending; empty = not listed).
     */
    fun next(planned: PlannedExercise, history: List<PastPerformance>, listedKg: List<Double> = emptyList()): Target {
        val p = planned.prescription
        val kind = loadKind(planned.equipment)
        val last = history.firstOrNull { it.sets.isNotEmpty() }
            ?: return Target(TargetKind.START, null, if (kind == LoadKind.NONE) "First time: note how many you manage." else FIRST_TIME)
        if (p.unit == ExerciseUnit.MINUTES) return Target(TargetKind.HOLD, null, "Same as last time; go a little further if it felt easy.")

        val amounts = last.sets.map { if (p.unit == ExerciseUnit.SECONDS) it.seconds else it.reps }
        val load = workingLoad(last)
        val allAtTop = last.sets.size >= p.sets && amounts.all { it != null && it >= p.max }
        val maxedOut = last.sets.any { (it.rpe ?: 0.0) >= MAX_RPE }
        if (kind == LoadKind.NONE || load == null) {
            val reason = if (allAtTop) "Top of the range last time: slow the lowering or pause at the hardest point." else "Same as last time; try for one more."
            return Target(TargetKind.HOLD, load, reason)
        }
        if (allAtTop && !maxedOut) {
            val up = stepUp(load, planned.equipment, kind, listedKg)
                ?: return Target(TargetKind.HOLD, load, "Top of the range at your heaviest ${bellName(planned.equipment)}: add a pause or slow the lowering.")
            return Target(TargetKind.INCREASE, up, "All sets at the top of the range last time.")
        }
        val missedTwice = history.take(2).let { recent ->
            recent.size == 2 && recent.all { s -> workingLoad(s) == load && s.sets.any { set -> below(set, p) } }
        }
        if (missedTwice) {
            val down = stepDown(load, planned.equipment, kind, listedKg)
            if (down != null) return Target(TargetKind.DECREASE, down, "Below the range twice at ${Units.kg(load)}: one step lighter, then build back up.")
        }
        return Target(TargetKind.HOLD, load, "Same weight; aim for one more rep than last time.")
    }

    /** The heaviest load used in a workout's sets; null when none had a load. */
    private fun workingLoad(performance: PastPerformance): Double? = performance.sets.mapNotNull { it.kg }.maxOrNull()

    private fun below(set: LoggedSet, p: Prescription): Boolean {
        val amount = if (p.unit == ExerciseUnit.SECONDS) set.seconds else set.reps
        return amount == null || amount < p.min
    }

    private fun stepUp(load: Double, equipment: Set<EquipmentKind>, kind: LoadKind, listed: List<Double>): Double? = when (kind) {
        LoadKind.LISTED -> if (listed.isNotEmpty()) listed.sorted().firstOrNull { it > load + EPSILON } else load + bellStep(equipment)
        LoadKind.PLATES -> load + PLATE_STEP_KG
        LoadKind.STACK -> load + STACK_STEP_KG
        LoadKind.NONE -> null
    }

    /** About 10% lighter for plates (rounded to 2.5 kg), otherwise one step. Null when nothing lighter exists. */
    private fun stepDown(load: Double, equipment: Set<EquipmentKind>, kind: LoadKind, listed: List<Double>): Double? = when (kind) {
        LoadKind.LISTED -> if (listed.isNotEmpty()) listed.sorted().lastOrNull { it < load - EPSILON } else (load - bellStep(equipment)).takeIf { it > 0 }
        LoadKind.PLATES -> minOf(load - PLATE_STEP_KG, floor(load * 0.9 / PLATE_STEP_KG) * PLATE_STEP_KG).takeIf { it > 0 }
        LoadKind.STACK -> (load - STACK_STEP_KG).takeIf { it > 0 }
        LoadKind.NONE -> null
    }

    private fun bellStep(equipment: Set<EquipmentKind>) =
        if (EquipmentKind.DUMBBELLS in equipment) DUMBBELL_STEP_KG else KETTLEBELL_STEP_KG

    private fun bellName(equipment: Set<EquipmentKind>) = if (EquipmentKind.DUMBBELLS in equipment) "dumbbells" else "kettlebell"


    private const val FIRST_TIME = "First time: pick a weight you could lift 2–3 more times at the end of each set."
    private const val MAX_RPE = 10.0
    private const val EPSILON = 1e-6

    private val PLATE_KINDS = setOf(
        EquipmentKind.BARBELL, EquipmentKind.EZ_BAR, EquipmentKind.TRAP_BAR, EquipmentKind.SMITH_MACHINE, EquipmentKind.LANDMINE,
    )
    private val STACK_KINDS = setOf(
        EquipmentKind.CABLE_STATION, EquipmentKind.LAT_PULLDOWN, EquipmentKind.SEATED_ROW, EquipmentKind.CHEST_PRESS,
        EquipmentKind.SHOULDER_PRESS, EquipmentKind.PEC_DECK, EquipmentKind.LEG_PRESS, EquipmentKind.HACK_SQUAT,
        EquipmentKind.LEG_EXTENSION, EquipmentKind.LEG_CURL, EquipmentKind.HIP_THRUST, EquipmentKind.CALF_RAISE,
    )
}
