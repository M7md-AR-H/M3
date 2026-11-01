package com.m3.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = monday.plusDays(1)
    private val defaults = ReminderSettings()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute)

    private fun only(kind: ReminderKind, setting: ReminderSetting = ReminderSetting(kind, on = true)) =
        ReminderSettings(ReminderKind.entries.associateWith { ReminderSetting(it, on = false) } + (kind to setting))

    private fun day(now: LocalDateTime = at(tuesday, 12)) = DayFacts(
        now = now,
        session = null,
        workoutDone = false,
        workoutOpen = false,
        checkIn = null,
        weighedIn = false,
        foodEntries = 0,
        lastFoodAt = null,
        eaten = DayTotals(),
        targets = null,
        waterMl = 0,
        habitsDone = emptySet(),
        lastWeekHasLogs = false,
    )

    private val targets = NutritionTargets(2_400, 160, 250, 70, 1_700, 2_500, heldBySafety = false)
    private val session = SessionFacts("Legs", "Gym", 6, 55)

    @Test
    fun nextIsTheFirstTimeAfterNow() {
        val due = Reminders.next(defaults, at(tuesday, 8))
        assertEquals(at(tuesday, 9), due?.at)
        assertEquals(setOf(ReminderKind.BREAKFAST), due?.kinds)
    }

    @Test
    fun aReminderDueThisMinuteIsNotDueAgain() {
        assertEquals(at(tuesday, 10, 30), Reminders.next(defaults, at(tuesday, 9))?.at)
        assertEquals(at(tuesday, 10, 30), Reminders.next(defaults, at(tuesday, 9).plusSeconds(20))?.at)
    }

    @Test
    fun rollsOverToTomorrow() {
        val due = Reminders.next(only(ReminderKind.LUNCH), at(tuesday, 18))
        assertEquals(at(tuesday.plusDays(1), 13), due?.at)
    }

    @Test
    fun weeklyReviewOnlyOnMondays() {
        val due = Reminders.next(only(ReminderKind.WEEK_REVIEW), at(tuesday, 8))
        assertEquals(at(monday.plusWeeks(1), 9, 30), due?.at)
    }

    @Test
    fun remindersAtTheSameMinuteComeTogether() {
        val settings = ReminderSettings(
            ReminderKind.entries.associateWith { ReminderSetting(it, on = false) } +
                (ReminderKind.LUNCH to ReminderSetting(ReminderKind.LUNCH, on = true, minute = 720)) +
                (ReminderKind.WATER to ReminderSetting(ReminderKind.WATER, on = true, minute = 720)),
        )
        assertEquals(setOf(ReminderKind.LUNCH, ReminderKind.WATER), Reminders.next(settings, at(tuesday, 8))?.kinds)
    }

    @Test
    fun nothingOnMeansNothingDue() {
        val off = ReminderSettings(ReminderKind.entries.associateWith { ReminderSetting(it, on = false) })
        assertNull(Reminders.next(off, at(tuesday, 8)))
    }

    @Test
    fun waterRepeatsUntilTheEvening() {
        val water = ReminderSetting(ReminderKind.WATER, on = true, minute = 10 * 60 + 30, everyHours = 3)
        assertEquals(listOf(630, 810, 990, 1170), Reminders.times(water))
        assertEquals(1, Reminders.times(water.copy(minute = 21 * 60)).size)
    }

    @Test
    fun waterIntervalIsClamped() {
        val water = ReminderSetting(ReminderKind.WATER, on = true, minute = 8 * 60, everyHours = 0)
        assertEquals(480 + 60, Reminders.times(water)[1])
    }

    @Test
    fun eventRemindersHaveNoClockTime() {
        assertTrue(Reminders.times(ReminderSetting(ReminderKind.REST)).isEmpty())
    }

    @Test
    fun stepMinuteWrapsAtMidnight() {
        assertEquals(0, Reminders.stepMinute(1425, 1))
        assertEquals(1425, Reminders.stepMinute(0, -1))
        assertEquals("07:30", Reminders.clockText(450))
    }

    @Test
    fun morningSilentOnceBothAreDone() {
        val done = day().copy(checkIn = CheckIn(tuesday, 4, 4, 2), weighedIn = true)
        assertNull(ReminderRules.message(ReminderKind.MORNING, done, showDetails = true))
        val noWeight = done.copy(weighedIn = false)
        assertEquals("Step on the scale before breakfast.", ReminderRules.message(ReminderKind.MORNING, noWeight, false)?.text)
    }

    @Test
    fun morningNamesTheSessionOnlyWithDetails() {
        val facts = day().copy(session = session)
        assertFalse(ReminderRules.message(ReminderKind.MORNING, facts, false)!!.text.contains("Legs"))
        assertTrue(ReminderRules.message(ReminderKind.MORNING, facts, true)!!.text.contains("Legs at Gym"))
    }

    @Test
    fun mealSilentRightAfterEating() {
        val now = at(tuesday, 13)
        assertNull(ReminderRules.message(ReminderKind.LUNCH, day(now).copy(lastFoodAt = now.minusMinutes(90)), true))
        assertNotNull(ReminderRules.message(ReminderKind.LUNCH, day(now).copy(lastFoodAt = now.minusMinutes(150)), true))
    }

    @Test
    fun mealShowsWhatIsLeftOnlyWithDetails() {
        val facts = day().copy(targets = targets, eaten = DayTotals(kcal = 1_000, proteinG = 60))
        assertEquals("1,400 kcal and 100 g protein left today.", ReminderRules.message(ReminderKind.LUNCH, facts, true)?.text)
        assertEquals("Eat well, then log it in Fuel.", ReminderRules.message(ReminderKind.LUNCH, facts, false)?.text)
    }

    @Test
    fun mealNeverStatesANegativeAmount() {
        val over = day().copy(targets = targets, eaten = DayTotals(kcal = 2_600, proteinG = 170))
        assertEquals("Log it in Fuel.", ReminderRules.message(ReminderKind.DINNER, over.copy(foodEntries = 3), true)?.text)
        val proteinDone = day().copy(targets = targets, eaten = DayTotals(kcal = 2_000, proteinG = 170))
        assertEquals("400 kcal left today, protein done.", ReminderRules.message(ReminderKind.DINNER, proteinDone.copy(foodEntries = 3), true)?.text)
    }

    @Test
    fun emptyDinnerSaysSoGently() {
        val text = ReminderRules.message(ReminderKind.DINNER, day(), true)?.text
        assertEquals("Nothing logged yet today. Eat something, then log it.", text)
    }

    @Test
    fun workoutOnlyOnOpenTrainingDays() {
        assertNull(ReminderRules.message(ReminderKind.WORKOUT, day(), true))
        assertNull(ReminderRules.message(ReminderKind.WORKOUT, day().copy(session = session, workoutDone = true), true))
        assertNull(ReminderRules.message(ReminderKind.WORKOUT, day().copy(session = session, workoutOpen = true), true))
        val message = ReminderRules.message(ReminderKind.WORKOUT, day().copy(session = session), true)
        assertEquals("Legs at Gym", message?.title)
        assertEquals("6 exercises, about 55 min.", message?.text)
        assertEquals(ReminderTarget.TRAIN, message?.target)
    }

    @Test
    fun workoutKeepsThePlanPrivateByDefault() {
        val message = ReminderRules.message(ReminderKind.WORKOUT, day().copy(session = session), false)!!
        assertFalse((message.title + message.text).contains("Legs"))
        assertFalse((message.title + message.text).contains("Gym"))
    }

    @Test
    fun workoutPassesOnTheCheckInAdvice() {
        val tired = day().copy(session = session, checkIn = CheckIn(tuesday, 2, 3, 2))
        assertTrue(ReminderRules.message(ReminderKind.WORKOUT, tired, true)!!.text.endsWith("Take it easier today."))
    }

    @Test
    fun waterStopsAtTheGoalOrTheHabit() {
        assertNull(ReminderRules.message(ReminderKind.WATER, day().copy(waterMl = Hydration.GOAL_ML), true))
        assertNull(ReminderRules.message(ReminderKind.WATER, day().copy(habitsDone = setOf(Habit.WATER)), true))
        val message = ReminderRules.message(ReminderKind.WATER, day().copy(waterMl = 1_250), true)
        assertEquals("1.25 L of 2.5 L so far.", message?.text)
        assertTrue(message!!.offersWater)
    }

    @Test
    fun eveningOnlyWhenSomethingIsOpen() {
        val closed = day().copy(foodEntries = 2, habitsDone = setOf(Habit.WATER, Habit.MOBILITY))
        assertNull(ReminderRules.message(ReminderKind.EVENING, closed, true))
        val open = closed.copy(foodEntries = 0, session = session)
        assertEquals("Still open: Food, Today's session.", ReminderRules.message(ReminderKind.EVENING, open, true)?.text)
        assertEquals("A few things are still open.", ReminderRules.message(ReminderKind.EVENING, open, false)?.text)
    }

    @Test
    fun lightsOutAndReviewFollowTheirConditions() {
        assertNull(ReminderRules.message(ReminderKind.LIGHTS_OUT, day().copy(habitsDone = setOf(Habit.LIGHTS_OUT)), true))
        assertNotNull(ReminderRules.message(ReminderKind.LIGHTS_OUT, day(), true))
        assertNull(ReminderRules.message(ReminderKind.WEEK_REVIEW, day(), true))
        assertEquals(ReminderTarget.YOU, ReminderRules.message(ReminderKind.WEEK_REVIEW, day().copy(lastWeekHasLogs = true), true)?.target)
    }

    @Test
    fun withoutDetailsNoReminderCarriesADigit() {
        val facts = day().copy(session = session, targets = targets, eaten = DayTotals(kcal = 500, proteinG = 30), waterMl = 750)
        ReminderKind.entries.forEach { kind ->
            val message = ReminderRules.message(kind, facts, showDetails = false) ?: return@forEach
            assertFalse(kind.name, (message.title + message.text).any(Char::isDigit))
        }
    }

    @Test
    fun volumeFormat() {
        assertEquals("750 ml", Units.volume(750))
        assertEquals("2.5 L", Units.volume(2_500))
    }
}
