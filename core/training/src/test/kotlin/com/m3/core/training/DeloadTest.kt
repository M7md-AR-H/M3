package com.m3.core.training

import com.m3.core.model.CheckIn
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeloadTest {
    private val catalog = ExerciseCatalog.bundled()
    private val monday = LocalDate.of(2026, 11, 2)

    /** One workout in each of the [weeks] weeks before [monday], starting with last week. */
    private fun weeks(weeks: Int, skip: Set<Int> = emptySet()) =
        (1..weeks).filter { it !in skip }.map { monday.minusWeeks(it.toLong()).plusDays(2) }

    private fun checkIns(count: Int, soreness: Int = 2, energy: Int = 3) =
        (0 until count).map { CheckIn(monday.plusDays(6).minusDays(it.toLong()), 3, energy, soreness) }

    private fun suggest(
        dates: Collection<LocalDate>,
        experience: Experience = Experience.SOME,
        check: List<CheckIn> = emptyList(),
        deload: Set<LocalDate> = emptySet(),
        planned: Int = 3,
    ) = Deload.suggest(monday, dates, check, deload, experience, planned)

    @Test
    fun `no history gives no suggestion`() {
        assertNull(suggest(emptyList()))
        assertEquals(0, Deload.hardWeeks(monday, emptyList(), emptySet()))
    }

    @Test
    fun `a long block is suggested at 8, 6 and 5 weeks by experience`() {
        assertNull(suggest(weeks(7), Experience.NEW))
        assertEquals(DeloadReason.LONG_BLOCK, suggest(weeks(8), Experience.NEW)?.reason)
        assertNull(suggest(weeks(5), Experience.SOME))
        assertEquals(DeloadReason.LONG_BLOCK, suggest(weeks(6), Experience.SOME)?.reason)
        assertNull(suggest(weeks(4), Experience.EXPERIENCED))
        val s = suggest(weeks(5), Experience.EXPERIENCED)
        assertEquals(DeloadReason.LONG_BLOCK, s?.reason)
        assertEquals(5, s?.hardWeeks)
        assertTrue("5 weeks" in s!!.sentence)
    }

    @Test
    fun `a week without workouts resets the count`() {
        // Weeks 1-3 hard, week 4 rest, weeks 5-9 hard: the run is 3.
        val dates = weeks(9, skip = setOf(4))
        assertEquals(3, Deload.hardWeeks(monday, dates, emptySet()))
        assertNull(suggest(dates, Experience.EXPERIENCED))
    }

    @Test
    fun `workouts this week do not count as a finished week`() {
        assertEquals(0, Deload.hardWeeks(monday, listOf(monday.plusDays(1), monday.plusDays(3)), emptySet()))
    }

    @Test
    fun `an accepted deload week resets the count and blocks suggestions for 4 weeks`() {
        val accepted = monday.minusWeeks(2)
        val dates = weeks(10)
        assertEquals(1, Deload.hardWeeks(monday, dates, setOf(accepted)))
        assertNull(suggest(dates, Experience.EXPERIENCED, deload = setOf(accepted)))
        // Four weeks later it can come back; three weeks later it can't, however tired the check-ins say.
        val tired = checkIns(5, soreness = 5)
        assertNotNull(suggest(dates, Experience.NEW, check = tired, deload = setOf(monday.minusWeeks(4))))
        assertNull(suggest(dates, Experience.NEW, check = tired, deload = setOf(monday.minusWeeks(3))))
    }

    @Test
    fun `never when this week is already a deload or nothing is planned`() {
        assertNull(suggest(weeks(9), Experience.NEW, deload = setOf(monday)))
        assertNull(suggest(weeks(9), Experience.NEW, planned = 0))
    }

    @Test
    fun `fatigue needs 3 hard weeks and at least 4 check-ins`() {
        assertEquals(DeloadReason.FATIGUE, suggest(weeks(3), check = checkIns(4, soreness = 4))?.reason)
        assertEquals(DeloadReason.FATIGUE, suggest(weeks(3), check = checkIns(6, energy = 2))?.reason)
        assertNull(suggest(weeks(2), check = checkIns(6, soreness = 5)))
        assertNull(suggest(weeks(3), check = checkIns(3, soreness = 5)))
        assertNull(suggest(weeks(3), check = checkIns(6, soreness = 3, energy = 3)))
    }

    @Test
    fun `fatigue averages the last 14 days only`() {
        val old = (15..20).map { CheckIn(monday.plusDays(6).minusDays(it.toLong()), 3, 3, 5) }
        assertNull(suggest(weeks(3), check = old + checkIns(4, soreness = 2)))
        val mixed = listOf(5, 5, 4, 2).mapIndexed { i, s -> CheckIn(monday.plusDays(6).minusDays(i.toLong()), 3, 3, s) }
        assertEquals(DeloadReason.FATIGUE, suggest(weeks(3), check = mixed)?.reason)
    }

    @Test
    fun `a long block wins over fatigue`() {
        assertEquals(DeloadReason.LONG_BLOCK, suggest(weeks(6), Experience.SOME, check = checkIns(6, soreness = 5))?.reason)
    }

    private fun planned(id: String, role: SlotRole, sets: Int) =
        PlannedExercise(catalog[id]!!, emptySet(), role, Prescription(sets, 8, 12, ExerciseUnit.REPS, 90))

    @Test
    fun `apply cuts sets to 60 percent, at least 2, and leaves conditioning alone`() {
        val roles = listOf(SlotRole.MAIN to 4, SlotRole.ACCESSORY to 3, SlotRole.ISOLATION to 2, SlotRole.CORE to 1, SlotRole.CONDITIONING to 5, SlotRole.MAIN to 5, SlotRole.MAIN to 6)
        val session = PlannedSession(
            DayOfWeek.MONDAY, "gym", "Gym", SessionType.PUSH, roles.map { (r, s) -> planned("push_up", r, s) }, emptyList(),
        )
        val plan = WeekPlan(listOf(session), listOf(DayOfWeek.MONDAY), 1)
        val after = Deload.apply(plan).sessions.single().exercises
        assertEquals(listOf(3, 2, 2, 1, 5, 3, 4), after.map { it.prescription.sets })
        assertTrue(after.all { it.prescription.min == 8 && it.prescription.max == 12 && it.prescription.restSeconds == 90 })
        assertEquals(plan.availableDays, Deload.apply(plan).availableDays)
    }

    private val barbell = setOf(EquipmentKind.BARBELL, EquipmentKind.WEIGHT_PLATES, EquipmentKind.SQUAT_RACK)
    private val dumbbells = setOf(EquipmentKind.DUMBBELLS)
    private val hold = { kg: Double? -> Target(TargetKind.HOLD, kg, "x") }

    @Test
    fun `plates go about 10 percent lighter in 2_5 kg steps`() {
        assertEquals(90.0, Deload.target(hold(100.0), barbell, emptyList()).kg!!, 0.0)
        assertEquals(87.5, Deload.target(hold(97.5), barbell, emptyList()).kg!!, 0.0)
        assertEquals(52.5, Deload.target(hold(60.0), barbell, emptyList()).kg!!, 0.0)
        val t = Deload.target(hold(100.0), barbell, emptyList())
        assertEquals(TargetKind.DELOAD, t.kind)
        assertEquals(Deload.REASON_LOAD, t.reason)
    }

    @Test
    fun `stacks go down in 5 kg steps`() {
        assertEquals(45.0, Deload.target(hold(55.0), setOf(EquipmentKind.LAT_PULLDOWN), emptyList()).kg!!, 0.0)
        assertEquals(50.0, Deload.target(hold(60.0), setOf(EquipmentKind.LAT_PULLDOWN), emptyList()).kg!!, 0.0)
    }

    @Test
    fun `listed bells pick the heaviest listed value within 90 percent, else the lowest`() {
        val listed = listOf(8.0, 10.0, 12.0, 14.0, 16.0)
        assertEquals(14.0, Deload.target(hold(16.0), dumbbells, listed).kg!!, 0.0)
        assertEquals(10.0, Deload.target(hold(12.0), dumbbells, listed).kg!!, 0.0)
        assertEquals(8.0, Deload.target(hold(8.0), dumbbells, listed).kg!!, 0.0)
        assertEquals(8.0, Deload.target(hold(9.0), dumbbells, listOf(8.0, 10.0)).kg!!, 0.0)
    }

    @Test
    fun `unlisted bells go one step down and never to zero`() {
        assertEquals(18.0, Deload.target(hold(20.0), dumbbells, emptyList()).kg!!, 0.0)
        assertEquals(1.0, Deload.target(hold(1.0), dumbbells, emptyList()).kg!!, 0.0)
        assertEquals(2.0, Deload.target(hold(2.0), dumbbells, emptyList()).kg!!, 0.0)
        assertEquals(8.0, Deload.target(hold(12.0), setOf(EquipmentKind.KETTLEBELLS), emptyList()).kg!!, 0.0)
    }

    @Test
    fun `a plate load too small to lower is kept`() {
        assertEquals(2.5, Deload.target(hold(2.5), barbell, emptyList()).kg!!, 0.0)
    }

    @Test
    fun `no-load targets stay without a load and first time stays as it is`() {
        val none = Deload.target(hold(null), emptySet(), emptyList())
        assertNull(none.kg)
        assertEquals(TargetKind.DELOAD, none.kind)
        assertEquals(Deload.REASON_NO_LOAD, none.reason)
        val start = Target(TargetKind.START, null, "First time")
        assertEquals(start, Deload.target(start, barbell, emptyList()))
    }

    @Test
    fun `monday of finds the start of the week`() {
        assertEquals(monday, Deload.mondayOf(monday))
        assertEquals(monday, Deload.mondayOf(monday.plusDays(6)))
        assertEquals(monday.minusWeeks(1), Deload.mondayOf(monday.minusDays(1)))
    }
}
