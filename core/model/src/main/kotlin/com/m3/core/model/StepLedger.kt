package com.m3.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** One value of the phone's step counter, which counts up from the last reboot. */
data class StepReading(val counter: Long, val atMillis: Long)

/**
 * Turns step counter readings into steps per day. Android stops sending this sensor's events to apps in the
 * background, so readings only arrive while M³ is open; the counter itself keeps counting in between.
 */
object StepLedger {
    const val DAILY_GOAL = 8_000

    /** A longer gap can't be spread sensibly (or the clock jumped), so its steps go to the newest day. */
    private const val MAX_SPREAD_DAYS = 31L

    /**
     * The steps to add per day for [reading], given the [last] one. The first reading only sets the starting point:
     * nobody knows when the steps before it happened. A counter lower than before means the phone restarted, so
     * only the steps since then are known. A gap across midnight is shared out by time, so no step is lost.
     */
    fun stepsToAdd(last: StepReading?, reading: StepReading, zone: ZoneId): Map<LocalDate, Int> {
        if (last == null) return emptyMap()
        val steps = if (reading.counter >= last.counter) reading.counter - last.counter else reading.counter
        if (steps <= 0L) return emptyMap()
        val newest = dateOf(reading.atMillis, zone)
        val oldest = dateOf(last.atMillis, zone)
        val spread = reading.atMillis > last.atMillis && oldest < newest && !oldest.plusDays(MAX_SPREAD_DAYS).isBefore(newest)
        if (!spread) return mapOf(newest to steps.toSafeInt())

        val gap = (reading.atMillis - last.atMillis).toDouble()
        var given = 0L
        val shares = linkedMapOf<LocalDate, Int>()
        var day = oldest
        var from = last.atMillis
        while (day < newest) {
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val share = (steps * ((midnight - from) / gap)).roundToLong()
            if (share > 0L) shares[day] = share.toSafeInt()
            given += share
            from = midnight
            day = day.plusDays(1)
        }
        if (steps > given) shares[newest] = (steps - given).toSafeInt()
        return shares
    }

    private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun Long.toSafeInt(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
