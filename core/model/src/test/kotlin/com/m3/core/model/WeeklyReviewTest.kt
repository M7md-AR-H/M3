package com.m3.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewTest {
    private val monday = LocalDate.of(2026, 10, 19)
    private val targets = NutritionTargets(2_400, 150, 280, 70, 1_780, 2_448, false)

    private fun food(days: Int, kcal: Int = 2_400, protein: Int = 150) =
        (0 until days).associate { monday.plusDays(it.toLong()) to DayTotals(kcal, protein, 200, 60) }

    private fun week(
        planned: Int = 4,
        done: Int = 4,
        food: Map<LocalDate, DayTotals> = food(6),
        change: Double? = -0.4,
        weight: Double? = 80.0,
        goal: Goal = Goal.LOSE_FAT,
        sleep: Int? = null,
    ) = WeekLogs(
        monday, planned, done, food, targets, emptyMap(), weight, change, goal,
        sleep?.let { s -> (0L..3L).map { CheckIn(monday.plusDays(it), s, 3, 2) } }.orEmpty(),
    )

    private fun advice(week: WeekLogs) = WeeklyReview.review(week)!!.advice

    @Test
    fun `an empty week has no review`() {
        assertNull(WeeklyReview.review(week(done = 0, food = emptyMap(), change = null)))
    }

    @Test
    fun `the headline and facts come from the logs`() {
        val review = WeeklyReview.review(week(done = 3, sleep = 4))!!
        assertEquals("3 of 4 sessions done.", review.headline)
        assertEquals(
            listOf(
                "Food: 2,400 of 2,400 kcal and 150 g protein a day, over 6 days logged",
                "Weight trend: −0.4 kg a week",
                "Sleep: 4.0 of 5 on average",
            ),
            review.facts,
        )
    }

    @Test
    fun `a good week says so`() {
        assertEquals("Every session done.", WeeklyReview.review(week())!!.headline)
        assertEquals("Keep going. Nothing needs changing.", advice(week()))
    }

    @Test
    fun `eating far too little comes first and points to help`() {
        val text = advice(week(food = food(5, kcal = 900), done = 0))
        assertTrue(text.contains("doctor or dietitian"))
    }

    @Test
    fun `losing too fast is called out`() {
        assertTrue(advice(week(change = -1.2)).contains("faster than is safe"))
    }

    @Test
    fun `no sessions, low protein, poor sleep and a flat trend each get their fix`() {
        assertTrue(advice(week(done = 0)).contains("one session"))
        assertEquals("Protein is the one fix: aim for 150 g a day.", advice(week(food = food(6, protein = 100))))
        assertTrue(advice(week(sleep = 2)).contains("Sleep is the one fix"))
        assertTrue(advice(week(change = 0.0)).contains("Log every bite"))
        assertTrue(advice(week(change = -0.2, goal = Goal.BUILD_MUSCLE)).contains("isn't moving up"))
    }

    @Test
    fun `too few logged days aren't judged`() {
        assertEquals("Keep going. Nothing needs changing.", advice(week(food = food(2, protein = 50))))
    }
}
