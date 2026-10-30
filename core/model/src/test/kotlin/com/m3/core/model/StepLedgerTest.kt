package com.m3.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepLedgerTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val monday = LocalDate.of(2026, 10, 26)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun reading(counter: Long, date: LocalDate, hour: Int) = StepReading(counter, at(date, hour))

    @Test
    fun `the first reading only sets the starting point`() {
        assertTrue(StepLedger.stepsToAdd(null, reading(50_000, monday, 9), zone).isEmpty())
    }

    @Test
    fun `steps on the same day go to that day`() {
        val added = StepLedger.stepsToAdd(reading(1_000, monday, 9), reading(1_600, monday, 12), zone)
        assertEquals(mapOf(monday to 600), added)
    }

    @Test
    fun `no new steps add nothing`() {
        assertTrue(StepLedger.stepsToAdd(reading(1_000, monday, 9), reading(1_000, monday, 12), zone).isEmpty())
    }

    @Test
    fun `a lower counter means a restart, and only the steps since then count`() {
        val added = StepLedger.stepsToAdd(reading(9_000, monday, 9), reading(250, monday, 12), zone)
        assertEquals(mapOf(monday to 250), added)
    }

    @Test
    fun `a gap across midnight is shared by time and loses no step`() {
        val tuesday = monday.plusDays(1)
        val added = StepLedger.stepsToAdd(reading(0, monday, 22), reading(1_000, tuesday, 2), zone)
        assertEquals(mapOf(monday to 500, tuesday to 500), added)
    }

    @Test
    fun `a gap over several days keeps the total`() {
        val thursday = monday.plusDays(3)
        val added = StepLedger.stepsToAdd(reading(0, monday, 12), reading(7_001, thursday, 12), zone)
        assertEquals(setOf(monday, monday.plusDays(1), monday.plusDays(2), thursday), added.keys)
        assertEquals(7_001, added.values.sum())
    }

    @Test
    fun `a clock that moved backwards keeps the steps on the current day`() {
        val added = StepLedger.stepsToAdd(reading(100, monday.plusDays(1), 9), reading(400, monday, 9), zone)
        assertEquals(mapOf(monday to 300), added)
    }

    @Test
    fun `a very long gap goes to the newest day`() {
        val later = monday.plusDays(90)
        val added = StepLedger.stepsToAdd(reading(0, monday, 9), reading(5_000, later, 9), zone)
        assertEquals(mapOf(later to 5_000), added)
    }

    @Test
    fun `step counts read with thousands separators`() {
        assertEquals("6,214", Units.count(6_214))
        assertEquals("80", Units.count(80))
    }
}
