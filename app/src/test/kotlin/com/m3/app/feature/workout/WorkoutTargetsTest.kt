package com.m3.app.feature.workout

import com.m3.core.data.StoredSet
import com.m3.core.data.StoredWorkout
import com.m3.core.data.WorkoutWithSets
import com.m3.core.model.EquipmentKind
import com.m3.core.model.TrainingSetup
import com.m3.core.training.ExerciseCatalog
import com.m3.core.training.ExerciseUnit
import com.m3.core.training.PlannedExercise
import com.m3.core.training.PlannedSession
import com.m3.core.training.Prescription
import com.m3.core.training.Progression
import com.m3.core.training.SessionType
import com.m3.core.training.SlotRole
import com.m3.core.training.TargetKind
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTargetsTest {
    private val catalog = ExerciseCatalog.bundled()
    private val barbell = setOf(EquipmentKind.BARBELL, EquipmentKind.WEIGHT_PLATES, EquipmentKind.SQUAT_RACK)
    private val exercise = PlannedExercise(catalog["back_squat"]!!, barbell, SlotRole.MAIN, Prescription(3, 8, 12, ExerciseUnit.REPS, 120))
    private val session = PlannedSession(DayOfWeek.MONDAY, "g", "Gym", SessionType.FULL_BODY_A, listOf(exercise), emptyList())
    private val key = Progression.variantKey("back_squat", barbell)
    private val week1 = LocalDate.of(2026, 10, 19)
    private val week2 = week1.plusWeeks(1)

    private fun workout(id: Long, date: LocalDate, kg: Double, reps: Int) = WorkoutWithSets(
        StoredWorkout(id, date, "FULL_BODY_A", "g", "Gym", id, id + 1),
        (1..3).map { StoredSet(id, "back_squat", key, it, kg, reps, null, null, it.toLong()) },
    )

    // Newest first, as the repository returns them: a normal week at 100 kg, then a deload week at 90 kg.
    private val recent = listOf(workout(2, week2.plusDays(1), 90.0, 12), workout(1, week1, 100.0, 9))

    @Test
    fun `workouts in a deload week do not count as history`() {
        val withDeload = WorkoutTargets.forSession(session, TrainingSetup.Empty, recent, setOf(week2)).single()
        assertEquals(100.0, withDeload.last.first().kg!!, 0.0)
        assertEquals(1, WorkoutTargets.history(key, recent, setOf(week2)).size)
        // Without the exclusion the 90 kg top-of-range workout would set the next target (a step up from it).
        val without = WorkoutTargets.forSession(session, TrainingSetup.Empty, recent).single()
        assertEquals(90.0, without.last.first().kg!!, 0.0)
        assertEquals(92.5, without.target.kg!!, 0.0)
    }

    @Test
    fun `any day of the deload week is excluded`() {
        val sunday = listOf(workout(3, week2.plusDays(6), 90.0, 12))
        assertTrue(WorkoutTargets.history(key, sunday, setOf(week2)).isEmpty())
        assertEquals(1, WorkoutTargets.history(key, sunday, setOf(week2.plusWeeks(1))).size)
    }

    @Test
    fun `in a deload week the target is the lighter one`() {
        val t = WorkoutTargets.forSession(session, TrainingSetup.Empty, recent, setOf(week2), deload = true).single().target
        assertEquals(TargetKind.DELOAD, t.kind)
        // 100 kg last time at 9 reps: hold at 100, so 90 kg for the deload.
        assertEquals(90.0, t.kg!!, 0.0)
    }

    @Test
    fun `first time stays first time in a deload week`() {
        val t = WorkoutTargets.forSession(session, TrainingSetup.Empty, emptyList(), emptySet(), deload = true).single().target
        assertEquals(TargetKind.START, t.kind)
    }
}
