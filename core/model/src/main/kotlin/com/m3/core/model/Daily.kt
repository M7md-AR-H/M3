package com.m3.core.model

import java.time.LocalDate

/** The morning check-in. Each answer is 1 (low) to 5 (high). */
data class CheckIn(val date: LocalDate, val sleep: Int, val energy: Int, val soreness: Int)

enum class Readiness { GOOD, TAKE_IT_EASY }

enum class Habit(val displayName: String, val detail: String) {
    WATER("Water", "2.5 L through the day"),
    MOBILITY("Mobility", "10 min, hips and upper back"),
    LIGHTS_OUT("Lights out", "On time tonight"),
}

object DailyCheck {
    const val MIN_SCORE = 1
    const val MAX_SCORE = 5
    private const val LOW = 2
    private const val SORE = 4

    fun valid(checkIn: CheckIn): Boolean =
        listOf(checkIn.sleep, checkIn.energy, checkIn.soreness).all { it in MIN_SCORE..MAX_SCORE }

    /** Advice only, in Kotlin: the plan itself doesn't change. */
    fun readiness(checkIn: CheckIn): Readiness =
        if (checkIn.sleep <= LOW || checkIn.energy <= LOW || checkIn.soreness >= SORE) Readiness.TAKE_IT_EASY else Readiness.GOOD
}
