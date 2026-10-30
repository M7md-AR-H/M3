package com.m3.coach

import com.m3.core.model.DayTotals
import com.m3.core.model.NutritionTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyAnswersTest {
    private val day = DailyFacts(
        targets = NutritionTargets(2_450, 170, 280, 75, 1_780, 2_448, false),
        eaten = DayTotals(1_640, 112, 180, 48),
        steps = 6_214,
        trendKg = 78.44,
        weeklyChangeKg = -0.44,
    )

    private fun answer(message: String, facts: DailyFacts = day) = DailyAnswers.answer(message, facts)

    @Test
    fun `protein questions use the app's numbers`() {
        val expected = "You've had 112 g of protein today, out of 170 g, so 58 g to go."
        assertEquals(expected, answer("How much more protein do I need today?"))
        assertEquals(expected, answer("How much protein have I eaten today?"))
        assertEquals(expected, answer("protein left?"))
    }

    @Test
    fun `calorie questions use the app's numbers, including going over`() {
        assertEquals("You've had 1,640 kcal today, out of 2,450 kcal, so 810 kcal left.", answer("How many calories do I have left today?"))
        val over = day.copy(eaten = DayTotals(2_600, 112, 180, 48))
        assertEquals("You've had 2,600 kcal today, out of 2,450 kcal, so 150 kcal over.", answer("How many kcal so far?", over))
    }

    @Test
    fun `steps and weight trend use the app's numbers`() {
        assertEquals("6,214 steps so far today. Your goal is 8,000.", answer("How many steps have I done today?"))
        assertEquals("Your trend is 78.4 kg, −0.4 kg a week.", answer("Is my weight going down?"))
    }

    @Test
    fun `missing data is said plainly, never guessed`() {
        assertEquals(
            "There are no weigh-ins yet. Log your weight on the You tab and I'll track the trend.",
            answer("Is my weight going down?", DailyFacts()),
        )
        assertEquals(
            "Your trend is 78.4 kg. After a week of weigh-ins I can tell you which way it's moving.",
            answer("Is my weight going down?", day.copy(weeklyChangeKg = null)),
        )
        assertNull(answer("How much protein do I need?", DailyFacts()))
    }

    @Test
    fun `advice and anything else stays with the model`() {
        assertNull(answer("What should I eat after training?"))
        assertNull(answer("Give me some high protein meal ideas"))
        assertNull(answer("Why is my protein target so high?"))
        assertNull(answer("What weight should I use for my first exercise today?"))
        assertNull(answer("Why is Wednesday a rest day?"))
        assertNull(answer("What weight do I lift on bench, is it going up?"))
    }
}
