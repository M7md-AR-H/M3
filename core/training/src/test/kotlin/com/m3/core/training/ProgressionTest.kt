package com.m3.core.training

import com.m3.core.model.EquipmentKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private val catalog = ExerciseCatalog.bundled()
    private val day = LocalDate.of(2026, 10, 19)

    private fun planned(id: String, equipment: Set<EquipmentKind>, sets: Int = 3, min: Int = 8, max: Int = 12, unit: ExerciseUnit = ExerciseUnit.REPS) =
        PlannedExercise(catalog[id]!!, equipment, SlotRole.ACCESSORY, Prescription(sets, min, max, unit, 90))

    private fun session(daysAgo: Long, kg: Double?, vararg reps: Int, rpe: Double? = null) =
        PastPerformance(day.minusDays(daysAgo), reps.map { LoggedSet(kg, reps = it, rpe = rpe) })

    private val dumbbells = setOf(EquipmentKind.DUMBBELLS)
    private val barbell = setOf(EquipmentKind.BARBELL, EquipmentKind.WEIGHT_PLATES, EquipmentKind.SQUAT_RACK)

    @Test
    fun `first time has no load to suggest`() {
        val t = Progression.next(planned("back_squat", barbell), emptyList())
        assertEquals(TargetKind.START, t.kind)
        assertNull(t.kg)
    }

    @Test
    fun `all sets at the top of the range step up once, from the owner's dumbbell list`() {
        val t = Progression.next(planned("dumbbell_bench_press", dumbbells), listOf(session(3, 20.0, 12, 12, 12)), listOf(10.0, 20.0, 22.5, 25.0))
        assertEquals(TargetKind.INCREASE, t.kind)
        assertEquals(22.5, t.kg!!, 0.0)
    }

    @Test
    fun `heaviest listed dumbbell holds with a technique cue`() {
        val t = Progression.next(planned("dumbbell_bench_press", dumbbells), listOf(session(3, 25.0, 12, 12, 12)), listOf(20.0, 25.0))
        assertEquals(TargetKind.HOLD, t.kind)
        assertEquals(25.0, t.kg!!, 0.0)
    }

    @Test
    fun `plates add 2_5 kg, a missing set or RPE 10 holds`() {
        assertEquals(62.5, Progression.next(planned("back_squat", barbell), listOf(session(3, 60.0, 12, 12, 12))).kg!!, 0.0)
        assertEquals(TargetKind.HOLD, Progression.next(planned("back_squat", barbell), listOf(session(3, 60.0, 12, 12))).kind)
        assertEquals(TargetKind.HOLD, Progression.next(planned("back_squat", barbell), listOf(session(3, 60.0, 12, 12, 12, rpe = 10.0))).kind)
    }

    @Test
    fun `below the range twice at the same load steps down about ten percent`() {
        val history = listOf(session(3, 100.0, 9, 7, 6), session(7, 100.0, 8, 7, 7))
        val t = Progression.next(planned("back_squat", barbell), history)
        assertEquals(TargetKind.DECREASE, t.kind)
        assertEquals(90.0, t.kg!!, 0.0)
        // Only once below the range: hold.
        assertEquals(TargetKind.HOLD, Progression.next(planned("back_squat", barbell), listOf(session(3, 100.0, 9, 7, 6), session(7, 97.5, 10, 9, 8))).kind)
    }

    @Test
    fun `one heavier top set doesn't move the target past the working load`() {
        val mixed = PastPerformance(day.minusDays(3), listOf(LoggedSet(20.0, 12), LoggedSet(20.0, 12), LoggedSet(25.0, 12)))
        val t = Progression.next(planned("dumbbell_bench_press", dumbbells), listOf(mixed), listOf(20.0, 22.5, 25.0, 27.5))
        assertEquals(TargetKind.INCREASE, t.kind)
        assertEquals(22.5, t.kg!!, 0.0)
        // A lighter back-off set counts as below the working load, so it isn't "every set at the load".
        val backOff = PastPerformance(day.minusDays(3), listOf(LoggedSet(30.0, 12), LoggedSet(30.0, 12), LoggedSet(20.0, 12)))
        assertEquals(TargetKind.HOLD, Progression.next(planned("dumbbell_bench_press", dumbbells), listOf(backOff), listOf(20.0, 30.0, 32.5)).kind)
    }

    @Test
    fun `stacks step 5 kg, listed bells step down to the next lighter one`() {
        val pulldown = planned("lat_pulldown", setOf(EquipmentKind.LAT_PULLDOWN))
        assertEquals(55.0, Progression.next(pulldown, listOf(session(3, 50.0, 12, 12, 12))).kg!!, 0.0)
        val missed = listOf(session(3, 22.5, 7, 6, 6), session(7, 22.5, 7, 7, 6))
        assertEquals(20.0, Progression.next(planned("dumbbell_bench_press", dumbbells), missed, listOf(15.0, 20.0, 22.5)).kg!!, 0.0)
    }

    @Test
    fun `timed sets use seconds, not reps`() {
        val plank = planned("plank", emptySet(), min = 30, max = 45, unit = ExerciseUnit.SECONDS)
        val atTop = PastPerformance(day.minusDays(3), List(3) { LoggedSet(null, seconds = 45) })
        assertEquals(TargetKind.HOLD, Progression.next(plank, listOf(atTop)).kind)
        assertTrue(Progression.next(plank, listOf(atTop)).reason.startsWith("Top of the range"))
    }

    @Test
    fun `bodyweight never gets a load`() {
        val t = Progression.next(planned("push_up", emptySet()), listOf(session(3, null, 12, 12, 12)))
        assertEquals(TargetKind.HOLD, t.kind)
        assertNull(t.kg)
    }

    @Test
    fun `variants keep home and gym apart`() {
        assertEquals("goblet_squat@DUMBBELLS", Progression.variantKey("goblet_squat", dumbbells))
        assertEquals("push_up@bodyweight", Progression.variantKey("push_up", emptySet()))
        assertEquals(LoadKind.STACK, Progression.loadKind(setOf(EquipmentKind.LAT_PULLDOWN)))
        assertEquals(LoadKind.NONE, Progression.loadKind(setOf(EquipmentKind.SANDBAG)))
    }
}
