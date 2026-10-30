package com.m3.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

data class WeightEntry(val date: LocalDate, val kg: Double)

enum class BodyPart(val displayName: String) {
    WAIST("Waist"),
    CHEST("Chest"),
    ARM("Arm"),
    SHOULDERS("Shoulders"),
}

data class Measurement(val date: LocalDate, val part: BodyPart, val cm: Double)

/** A weigh-in with its smoothed value. */
data class TrendPoint(val date: LocalDate, val kg: Double, val trendKg: Double)

/** Daily weight is noise, the trend is the signal. An average that fades old days out, and copes with missed days. */
object WeightTrend {
    private const val DAILY_SMOOTHING = 0.1
    private const val MIN_DAYS_FOR_RATE = 7L
    private const val RATE_WINDOW_DAYS = 28L

    fun points(entries: List<WeightEntry>): List<TrendPoint> {
        val sorted = entries.sortedBy { it.date }
        val points = ArrayList<TrendPoint>(sorted.size)
        sorted.forEach { entry ->
            val previous = points.lastOrNull()
            val trend = if (previous == null) {
                entry.kg
            } else {
                val gap = ChronoUnit.DAYS.between(previous.date, entry.date).toDouble()
                val weight = 1.0 - (1.0 - DAILY_SMOOTHING).pow(gap)
                previous.trendKg + weight * (entry.kg - previous.trendKg)
            }
            points += TrendPoint(entry.date, entry.kg, trend)
        }
        return points
    }

    /** Trend change per week over the last four weeks, or null until there's a week of data. */
    fun weeklyChangeKg(points: List<TrendPoint>): Double? {
        val last = points.lastOrNull() ?: return null
        val from = points.firstOrNull { ChronoUnit.DAYS.between(it.date, last.date) <= RATE_WINDOW_DAYS } ?: return null
        val days = ChronoUnit.DAYS.between(from.date, last.date)
        if (days < MIN_DAYS_FOR_RATE) return null
        return (last.trendKg - from.trendKg) / days * 7.0
    }
}

object BodyLimits {
    const val MIN_CM = 10.0
    const val MAX_CM = 300.0

    fun validWeight(kg: Double): Boolean = kg.isFinite() && kg in Limits.MIN_WEIGHT_KG..Limits.MAX_WEIGHT_KG

    fun validCm(cm: Double): Boolean = cm.isFinite() && cm in MIN_CM..MAX_CM
}
