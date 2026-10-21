package com.m3.core.training

import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.model.Profile
import com.m3.core.model.Sex
import com.m3.core.model.TrainingLocation
import com.m3.core.model.TrainingSetup
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
    @Test
    fun `bundled catalog loads and is valid`() {
        val catalog = ExerciseCatalog.bundled()
        assertTrue(catalog.exercises.size >= 90)
        assertNotNull(catalog["back_squat"])
    }

    @Test
    fun `every movement pattern has a bodyweight or common option`() {
        val catalog = ExerciseCatalog.bundled()
        MovementPattern.entries.forEach { pattern ->
            assertTrue("no $pattern", catalog.exercises.any { it.pattern == pattern })
        }
    }

    @Test
    fun `validation catches broken entries`() {
        val bad = Exercise("Bad Id", "", MovementPattern.SQUAT, emptyList(), level = 4, compound = true, equipment = emptyList())
        assertEquals(5, ExerciseCatalog.validate(listOf(bad)).size)
    }
}

class PlanGeneratorTest {
    private val catalog = ExerciseCatalog.bundled()
    private val generator = PlanGenerator(catalog)
    private val everyKind = EquipmentKind.entries.map { EquipmentItem.Catalog(it) }
    private val gym = TrainingLocation("gym", "Gym", everyKind)
    private val home = TrainingLocation("home", "Home", emptyList())

    private fun profile(goal: Goal, experience: Experience) =
        Profile(1994, Sex.MALE, 180, 80.0, goal, experience, "")

    private fun all(id: String) = DayOfWeek.entries.associateWith { id }

    @Test
    fun `sessions per week follow experience and goal`() {
        assertEquals(3, PlanGenerator.targetSessions(Goal.BUILD_MUSCLE, Experience.NEW))
        assertEquals(4, PlanGenerator.targetSessions(Goal.BUILD_MUSCLE, Experience.SOME))
        assertEquals(3, PlanGenerator.targetSessions(Goal.LOSE_FAT, Experience.SOME))
        assertEquals(5, PlanGenerator.targetSessions(Goal.BUILD_MUSCLE, Experience.EXPERIENCED))
        assertEquals(4, PlanGenerator.targetSessions(Goal.GET_STRONGER, Experience.EXPERIENCED))
    }

    @Test
    fun `three sessions spread out with rest between`() {
        val days = PlanGenerator.chooseDays(DayOfWeek.entries.map { it to 1 }, 3)
        assertEquals(listOf(MONDAY, WEDNESDAY, FRIDAY), days)
    }

    @Test
    fun `four of seven days have only one back-to-back pair`() {
        val days = PlanGenerator.chooseDays(DayOfWeek.entries.map { it to 1 }, 4)
        assertEquals(listOf(MONDAY, TUESDAY, THURSDAY, SATURDAY), days)
    }

    @Test
    fun `sunday to monday counts as back to back`() {
        val days = PlanGenerator.chooseDays(listOf(SUNDAY to 1, MONDAY to 1, THURSDAY to 1), 2)
        assertFalse(days == listOf(MONDAY, SUNDAY))
    }

    @Test
    fun `ties prefer the better-equipped location`() {
        assertEquals(listOf(TUESDAY), PlanGenerator.chooseDays(listOf(MONDAY to 2, TUESDAY to 30), 1))
    }

    @Test
    fun `fewer free days than the target uses every free day`() {
        val plan = generator.generate(
            profile(Goal.BUILD_MUSCLE, Experience.SOME),
            TrainingSetup(listOf(gym), mapOf(SATURDAY to "gym", SUNDAY to "gym")),
        )
        assertEquals(listOf(SATURDAY, SUNDAY), plan.sessions.map { it.day })
        assertTrue(plan.limitedByAvailability)
        assertEquals(listOf(SessionType.FULL_BODY_A, SessionType.FULL_BODY_B), plan.sessions.map { it.type })
    }

    @Test
    fun `full gym fills every slot with supported, non-repeating exercises`() {
        Experience.entries.forEach { experience ->
            Goal.entries.forEach { goal ->
                val plan = generator.generate(profile(goal, experience), TrainingSetup(listOf(gym), all("gym")))
                assertEquals(PlanGenerator.targetSessions(goal, experience), plan.sessions.size)
                plan.sessions.forEach { session ->
                    assertTrue("$goal/$experience ${session.type} unfilled ${session.unfilled}", session.unfilled.isEmpty())
                    val ids = session.exercises.map { it.exercise.id }
                    assertEquals(ids.distinct(), ids)
                    session.exercises.filter { it.role == SlotRole.MAIN }.forEach { assertTrue(it.exercise.compound) }
                }
            }
        }
    }

    @Test
    fun `beginners get beginner-level main lifts when one exists`() {
        val plan = generator.generate(profile(Goal.BUILD_MUSCLE, Experience.NEW), TrainingSetup(listOf(gym), all("gym")))
        plan.sessions.flatMap { it.exercises }.forEach { assertEquals(it.exercise.name, 1, it.exercise.level) }
    }

    @Test
    fun `bodyweight-only location never plans gear and reports what it cannot fill`() {
        val plan = generator.generate(profile(Goal.GENERAL_FITNESS, Experience.SOME), TrainingSetup(listOf(home), all("home")))
        plan.sessions.flatMap { it.exercises }.forEach { assertTrue(it.equipment.isEmpty()) }
        assertTrue(plan.sessions.any { session -> session.unfilled.any { it.pattern == MovementPattern.HORIZONTAL_PULL } })
    }

    @Test
    fun `each day uses its own location's equipment`() {
        val dumbbells = TrainingLocation("h", "Home", listOf(EquipmentItem.Catalog(EquipmentKind.DUMBBELLS)))
        val plan = generator.generate(
            profile(Goal.BUILD_MUSCLE, Experience.SOME),
            TrainingSetup(listOf(gym, dumbbells), mapOf(MONDAY to "gym", WEDNESDAY to "h", FRIDAY to "gym", SATURDAY to "h")),
        )
        plan.sessions.filter { it.locationId == "h" }.flatMap { it.exercises }.forEach {
            assertTrue(it.exercise.name, it.equipment.all { kind -> kind == EquipmentKind.DUMBBELLS })
        }
    }

    @Test
    fun `prescriptions follow the goal`() {
        val strength = generator.generate(profile(Goal.GET_STRONGER, Experience.EXPERIENCED), TrainingSetup(listOf(gym), all("gym")))
        val main = strength.sessions.first().exercises.first { it.role == SlotRole.MAIN }.prescription
        assertEquals(Prescription(4, 3, 5, ExerciseUnit.REPS, 180), main)
        val fatLoss = generator.generate(profile(Goal.LOSE_FAT, Experience.NEW), TrainingSetup(listOf(gym), all("gym")))
        assertTrue(fatLoss.sessions.all { s -> s.exercises.last().role == SlotRole.CONDITIONING })
        assertTrue(fatLoss.sessions.first().exercises.first().prescription.sets <= 3)
    }

    @Test
    fun `no free days means no sessions, and output is deterministic`() {
        assertTrue(generator.generate(null, TrainingSetup(listOf(gym), emptyMap())).sessions.isEmpty())
        val setup = TrainingSetup(listOf(gym, home), mapOf(MONDAY to "gym", TUESDAY to "home", THURSDAY to "gym", SATURDAY to "home"))
        val p = profile(Goal.BUILD_MUSCLE, Experience.SOME)
        assertEquals(generator.generate(p, setup), generator.generate(p, setup))
    }

    @Test
    fun `strength ranges only for heavy lifts, bodyweight mains stay moderate`() {
        val plan = generator.generate(profile(Goal.GET_STRONGER, Experience.SOME), TrainingSetup(listOf(home), all("home")))
        plan.sessions.flatMap { it.exercises }.filter { it.prescription.unit == ExerciseUnit.REPS }.forEach {
            assertTrue("${it.exercise.name} ${it.prescription}", it.prescription.min >= 6 || it.exercise.repsMin != null)
        }
    }

    @Test
    fun `swings never take a main slot`() {
        val kettlebells = TrainingLocation("k", "Home", listOf(EquipmentItem.Catalog(EquipmentKind.KETTLEBELLS)))
        val plan = generator.generate(profile(Goal.GET_STRONGER, Experience.EXPERIENCED), TrainingSetup(listOf(kettlebells), all("k")))
        plan.sessions.flatMap { it.exercises }.filter { it.role == SlotRole.MAIN }.forEach { assertFalse(it.exercise.ballistic) }
    }

    @Test
    fun `high-intensity conditioning is prescribed as rounds`() {
        val ropes = TrainingLocation("r", "Park", listOf(EquipmentItem.Catalog(EquipmentKind.BATTLE_ROPES)))
        val ex = catalog["battle_ropes"]!!
        val p = PlanGenerator.prescribe(Slot(MovementPattern.CONDITIONING, SlotRole.CONDITIONING), ex, setOf(EquipmentKind.BATTLE_ROPES), Goal.LOSE_FAT, Experience.SOME)
        assertEquals(Prescription(8, 20, 30, ExerciseUnit.SECONDS, 40), p)
        assertTrue(ropes.equipment.isNotEmpty())
    }

    @Test
    fun `an adjustable bench stands in for a flat bench and is shown as such`() {
        val gear = setOf(EquipmentKind.DUMBBELLS, EquipmentKind.ADJUSTABLE_BENCH)
        assertEquals(setOf(EquipmentKind.EZ_BAR, EquipmentKind.WEIGHT_PLATES, EquipmentKind.ADJUSTABLE_BENCH),
            catalog["skull_crusher"]!!.supportedBy(gear + EquipmentKind.EZ_BAR + EquipmentKind.WEIGHT_PLATES))
        assertEquals(setOf(EquipmentKind.DUMBBELLS, EquipmentKind.ADJUSTABLE_BENCH), catalog["chest_fly"]!!.supportedBy(gear))
    }

    @Test
    fun `barbell work needs plates, and bench and overhead press need a rack`() {
        catalog.exercises.filter { it.id != "inverted_row" }.forEach { e ->
            e.equipment.filter { EquipmentKind.BARBELL in it || EquipmentKind.EZ_BAR in it }.forEach {
                assertTrue("${e.id} $it", EquipmentKind.WEIGHT_PLATES in it)
            }
        }
        listOf("bench_press", "overhead_press", "back_squat", "front_squat").forEach { id ->
            catalog[id]!!.equipment.filter { EquipmentKind.BARBELL in it }.forEach { assertTrue(id, EquipmentKind.SQUAT_RACK in it) }
        }
    }

    @Test
    fun `a day override changes only that day's exercises and the diff says so`() {
        val p = profile(Goal.BUILD_MUSCLE, Experience.SOME)
        val setup = TrainingSetup(listOf(gym), mapOf(MONDAY to "gym", TUESDAY to "gym", THURSDAY to "gym", SATURDAY to "gym"))
        val before = generator.generate(p, setup)
        val onlyDumbbells = PlanOverrides(dayInventory = mapOf(MONDAY to setOf(EquipmentKind.DUMBBELLS)))
        val after = generator.generate(p, setup, onlyDumbbells)
        assertEquals(before.sessions.map { it.day }, after.sessions.map { it.day })
        after.sessionOn(MONDAY)!!.exercises.forEach { assertTrue(it.equipment.all { k -> k == EquipmentKind.DUMBBELLS }) }
        assertEquals(setOf(MONDAY), after.overriddenDays)
        val diff = PlanDiff.describe(before, after)
        assertTrue(diff.isNotEmpty())
        assertTrue(diff.all { it.startsWith("Monday") })
        assertTrue(PlanDiff.describe(before, before).isEmpty())
        // Exercises that the day's new equipment still supports stay the same.
        val stillPossible = before.sessionOn(MONDAY)!!.exercises.filter { it.equipment.all { k -> k == EquipmentKind.DUMBBELLS } }.map { it.exercise.id }
        assertTrue(after.sessionOn(MONDAY)!!.exercises.map { it.exercise.id }.containsAll(stillPossible))
    }

    @Test
    fun `excluded exercises are never planned`() {
        val p = profile(Goal.BUILD_MUSCLE, Experience.SOME)
        val setup = TrainingSetup(listOf(gym), all("gym"))
        val before = generator.generate(p, setup)
        val used = before.sessions.flatMap { it.exercises }.map { it.exercise.id }.toSet()
        val after = generator.generate(p, setup, PlanOverrides(excludedExercises = used))
        assertTrue(after.sessions.flatMap { it.exercises }.none { it.exercise.id in used })
    }

    @Test
    fun `slots left empty by avoided exercises are told apart from missing equipment`() {
        val p = profile(Goal.BUILD_MUSCLE, Experience.SOME)
        val setup = TrainingSetup(listOf(gym), all("gym"))
        val lunges = ExerciseCatalog.bundled().exercises.filter { it.pattern == MovementPattern.LUNGE }.map { it.id }.toSet()
        val plan = generator.generate(p, setup, PlanOverrides(excludedExercises = lunges))
        val lungeSlots = plan.sessions.flatMap { s -> s.unfilled.filter { it.pattern == MovementPattern.LUNGE }.map { s to it } }
        assertTrue(lungeSlots.isNotEmpty())
        assertTrue(lungeSlots.all { (session, slot) -> slot in session.unfilledByAvoided })
        assertTrue(generator.generate(p, setup).sessions.all { it.unfilledByAvoided.isEmpty() })
    }

    @Test
    fun `availability changes show as new sessions or rest days`() {
        val p = profile(Goal.GENERAL_FITNESS, Experience.NEW)
        val before = generator.generate(p, TrainingSetup(listOf(gym), mapOf(MONDAY to "gym", WEDNESDAY to "gym", FRIDAY to "gym")))
        val after = generator.generate(p, TrainingSetup(listOf(gym), mapOf(MONDAY to "gym", WEDNESDAY to "gym", SATURDAY to "gym")))
        val diff = PlanDiff.describe(before, after)
        assertTrue(diff.any { it.startsWith("Friday: now a rest day") })
        assertTrue(diff.any { it.startsWith("Saturday: now") })
    }
}
