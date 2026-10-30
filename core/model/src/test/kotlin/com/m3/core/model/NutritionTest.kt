package com.m3.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {
    private val year = 2026

    private fun profile(
        sex: Sex = Sex.MALE,
        heightCm: Int = 180,
        weightKg: Double = 80.0,
        birthYear: Int = 1996,
        goal: Goal = Goal.GENERAL_FITNESS,
    ) = Profile(birthYear, sex, heightCm, weightKg, goal, Experience.SOME, "")

    private fun entry(kcal: Int = 100, protein: Int = 10, name: String = "Oats") =
        FoodEntry(date = LocalDate.of(2026, 10, 30), atMillis = 1L, name = name, kcal = kcal, proteinG = protein, carbsG = 5, fatG = 2)

    @Test
    fun `bmr follows Mifflin-St Jeor`() {
        assertEquals(1_780.0, Nutrition.bmr(profile(), age = 30), 0.01)
        assertEquals(1_614.0, Nutrition.bmr(profile(sex = Sex.FEMALE), age = 30), 0.01)
    }

    @Test
    fun `activity follows the sessions in the plan`() {
        val rest = Nutrition.targets(profile(), year, sessionsPerWeek = 0)
        val three = Nutrition.targets(profile(), year, sessionsPerWeek = 3)
        val five = Nutrition.targets(profile(), year, sessionsPerWeek = 5)
        assertEquals(2_136, rest.maintenanceKcal)
        assertEquals(2_448, three.maintenanceKcal)
        assertEquals(2_759, five.maintenanceKcal)
    }

    @Test
    fun `goals move the target`() {
        val maintain = Nutrition.targets(profile(), year, 3)
        assertEquals(2_450, maintain.kcal)
        assertEquals(2_690, Nutrition.targets(profile(goal = Goal.BUILD_MUSCLE), year, 3).kcal)
        assertEquals(1_960, Nutrition.targets(profile(goal = Goal.LOSE_FAT), year, 3).kcal)
        assertFalse(maintain.heldBySafety)
    }

    @Test
    fun `macros add up to the target within rounding`() {
        val t = Nutrition.targets(profile(goal = Goal.BUILD_MUSCLE), year, 4)
        assertEquals(144, t.proteinG)
        assertEquals(64, t.fatG)
        assertEquals(t.kcal.toDouble(), (t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9).toDouble(), 4.0)
    }

    @Test
    fun `weekly loss never exceeds one percent of body weight`() {
        val tall = profile(heightCm = 200, weightKg = 75.0, birthYear = 2006, goal = Goal.LOSE_FAT)
        val t = Nutrition.targets(tall, year, 6)
        val maxDeficit = 75.0 * 0.01 * 7_700 / 7
        assertTrue(t.maintenanceKcal - t.kcal <= maxDeficit + 5)
    }

    @Test
    fun `nobody underweight gets a deficit`() {
        val thin = profile(heightCm = 185, weightKg = 55.0, goal = Goal.LOSE_FAT)
        val t = Nutrition.targets(thin, year, 3)
        assertEquals(t.maintenanceKcal.toDouble(), t.kcal.toDouble(), 5.0)
        assertTrue(t.heldBySafety)
    }

    @Test
    fun `the calorie floor holds whatever the goal`() {
        val small = profile(sex = Sex.FEMALE, heightCm = 150, weightKg = 45.0, birthYear = 1966, goal = Goal.LOSE_FAT)
        val t = Nutrition.targets(small, year, 3)
        assertTrue(t.kcal >= 1_200 || t.kcal >= t.maintenanceKcal - 5)
        assertTrue(t.heldBySafety)
    }

    @Test
    fun `the floor never tells a small person to eat above maintenance`() {
        val tiny = profile(sex = Sex.FEMALE, heightCm = 140, weightKg = 35.0, birthYear = 1950, goal = Goal.GENERAL_FITNESS)
        val t = Nutrition.targets(tiny, year, 0)
        assertTrue(t.kcal <= t.maintenanceKcal + 5)
    }

    @Test
    fun `nobody under 18 gets a deficit`() {
        val teen = profile(birthYear = 2010, goal = Goal.LOSE_FAT)
        val t = Nutrition.targets(teen, year, 3)
        assertEquals(t.maintenanceKcal.toDouble(), t.kcal.toDouble(), 5.0)
        assertTrue(t.heldBySafety)
    }

    @Test
    fun `macros stay sensible for a very heavy person`() {
        val heavy = profile(heightCm = 190, weightKg = 200.0, goal = Goal.LOSE_FAT)
        val t = Nutrition.targets(heavy, year, 3)
        assertTrue(t.carbsG > 0)
        assertTrue(t.proteinG * 4 <= t.kcal * 0.36)
    }

    @Test
    fun `totals add every entry`() {
        val totals = Nutrition.totals(listOf(entry(), entry(kcal = 250, protein = 20)))
        assertEquals(DayTotals(350, 30, 10, 4), totals)
    }

    @Test
    fun `entries are validated`() {
        assertTrue(Nutrition.validate(entry()).isEmpty())
        assertEquals(listOf(FoodEntryError.NAME_MISSING), Nutrition.validate(entry(name = "  ")))
        assertEquals(listOf(FoodEntryError.NAME_TOO_LONG), Nutrition.validate(entry(name = "x".repeat(81))))
        assertEquals(listOf(FoodEntryError.KCAL_OUT_OF_RANGE), Nutrition.validate(entry(kcal = 5_001)))
        assertEquals(listOf(FoodEntryError.KCAL_OUT_OF_RANGE), Nutrition.validate(entry(kcal = -1)))
        assertEquals(listOf(FoodEntryError.MACRO_OUT_OF_RANGE), Nutrition.validate(entry(protein = 501)))
    }

    @Test
    fun `energy and clock read metric and 24-hour`() {
        assertEquals("2,450 kcal", Units.kcal(2_450))
        assertEquals("52 g", Units.grams(52))
        assertEquals("08:05", Units.clock(java.time.LocalTime.of(8, 5)))
    }
}
