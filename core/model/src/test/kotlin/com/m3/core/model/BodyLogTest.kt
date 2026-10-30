package com.m3.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyLogTest {
    private val start = LocalDate.of(2026, 10, 1)

    private fun entry(day: Long, kg: Double) = WeightEntry(start.plusDays(day), kg)

    @Test
    fun `the first weigh-in starts the trend`() {
        assertEquals(80.0, WeightTrend.points(listOf(entry(0, 80.0))).single().trendKg, 0.001)
    }

    @Test
    fun `one odd day barely moves the trend`() {
        val points = WeightTrend.points(listOf(entry(0, 80.0), entry(1, 83.0)))
        assertEquals(80.3, points.last().trendKg, 0.001)
    }

    @Test
    fun `a long gap trusts the new weight more`() {
        val nextDay = WeightTrend.points(listOf(entry(0, 80.0), entry(1, 78.0))).last().trendKg
        val nextMonth = WeightTrend.points(listOf(entry(0, 80.0), entry(30, 78.0))).last().trendKg
        assertTrue(nextMonth < nextDay)
        assertEquals(78.1, nextMonth, 0.1)
    }

    @Test
    fun `entries are sorted by date first`() {
        val points = WeightTrend.points(listOf(entry(2, 79.0), entry(0, 80.0)))
        assertEquals(start, points.first().date)
    }

    @Test
    fun `no rate until there is a week of data`() {
        assertNull(WeightTrend.weeklyChangeKg(emptyList()))
        assertNull(WeightTrend.weeklyChangeKg(WeightTrend.points(listOf(entry(0, 80.0), entry(6, 79.0)))))
    }

    @Test
    fun `a steady loss shows as a negative weekly rate`() {
        val entries = (0L..28L).map { entry(it, 80.0 - it * 0.1) }
        val rate = WeightTrend.weeklyChangeKg(WeightTrend.points(entries))!!
        assertTrue(rate < -0.3)
        assertTrue(rate > -0.8)
    }

    @Test
    fun `the rate ignores anything older than four weeks`() {
        val old = entry(0, 120.0)
        val recent = (40L..68L).map { entry(it, 80.0) }
        val rate = WeightTrend.weeklyChangeKg(WeightTrend.points(listOf(old) + recent))!!
        assertTrue(kotlin.math.abs(rate) < 0.5)
    }

    @Test
    fun `limits reject nonsense`() {
        assertTrue(BodyLimits.validWeight(80.0))
        assertFalse(BodyLimits.validWeight(5.0))
        assertFalse(BodyLimits.validWeight(Double.NaN))
        assertTrue(BodyLimits.validCm(82.5))
        assertFalse(BodyLimits.validCm(500.0))
    }

    @Test
    fun `changes read with a sign and one decimal`() {
        assertEquals("−0.4", Units.signed(-0.44))
        assertEquals("+1.5", Units.signed(1.5))
        assertEquals("78.4", Units.oneDecimal(78.44))
    }
}
