package com.m3.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCheckTest {
    private val day = LocalDate.of(2026, 10, 30)

    private fun checkIn(sleep: Int = 4, energy: Int = 4, soreness: Int = 2) = CheckIn(day, sleep, energy, soreness)

    @Test
    fun `a good morning is good to go`() {
        assertEquals(Readiness.GOOD, DailyCheck.readiness(checkIn()))
        assertEquals(Readiness.GOOD, DailyCheck.readiness(checkIn(sleep = 3, energy = 3, soreness = 3)))
    }

    @Test
    fun `short sleep, low energy or sore muscles each say take it easy`() {
        assertEquals(Readiness.TAKE_IT_EASY, DailyCheck.readiness(checkIn(sleep = 2)))
        assertEquals(Readiness.TAKE_IT_EASY, DailyCheck.readiness(checkIn(energy = 1)))
        assertEquals(Readiness.TAKE_IT_EASY, DailyCheck.readiness(checkIn(soreness = 4)))
    }

    @Test
    fun `scores stay between 1 and 5`() {
        assertTrue(DailyCheck.valid(checkIn(sleep = 1, energy = 5, soreness = 1)))
        assertFalse(DailyCheck.valid(checkIn(sleep = 0)))
        assertFalse(DailyCheck.valid(checkIn(soreness = 6)))
    }
}
