package com.m3.core.model

import java.math.BigDecimal
import java.time.LocalTime
import java.util.Locale

/**
 * The one place where units are formatted and parsed (ADR 0008: metric only).
 * Screens call these instead of writing "kg" or "cm" themselves.
 */
object Units {
    const val KG = "kg"
    const val CM = "cm"

    fun kg(value: Double): String = "${number(value)} $KG"

    fun cm(value: Int): String = "$value $CM"

    /** "6,214": whole counts such as steps. */
    fun count(value: Int): String = String.format(Locale.ROOT, "%,d", value)

    fun kcal(value: Int): String = "${count(value)} kcal"

    fun grams(value: Int): String = "$value g"

    /** 24-hour time, "08:05". */
    fun clock(time: LocalTime): String = String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)

    /** "12.5", "20": no trailing zeros, dot as the decimal separator. */
    fun number(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    /** Accepts "72", "72.5" or "72,5". Returns null for anything else. */
    fun parseDecimal(text: String): Double? {
        val cleaned = text.trim().replace(',', '.')
        if (!DECIMAL.matches(cleaned)) return null
        return cleaned.toDoubleOrNull()
    }

    fun parseWhole(text: String): Int? {
        val cleaned = text.trim()
        if (!WHOLE.matches(cleaned)) return null
        return cleaned.toIntOrNull()
    }

    /**
     * Parses a weight list such as "2, 4, 6-10, 12.5". A range "a-b" steps by 2 kg; "a-b/x" steps by x kg.
     * Returns the sorted, de-duplicated list, or null if any part is invalid, any weight is outside
     * [Limits.MIN_ITEM_WEIGHT_KG]..[Limits.MAX_ITEM_WEIGHT_KG] (the same rule the validator applies), or the list is too long.
     */
    fun parseWeightList(text: String): List<Double>? {
        val result = sortedSetOf<Double>()
        for (rawPart in text.split(',', ';', '\n')) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue
            val range = RANGE.matchEntire(part)
            if (range != null) {
                val from = parseDecimal(range.groupValues[1]) ?: return null
                val to = parseDecimal(range.groupValues[2]) ?: return null
                val stepText = range.groupValues[3]
                val step = if (stepText.isEmpty()) DEFAULT_STEP_KG else parseDecimal(stepText) ?: return null
                if (step <= 0.0 || to < from || (to - from) / step > Limits.MAX_WEIGHTS_PER_ITEM) return null
                var weight = BigDecimal.valueOf(from)
                val end = BigDecimal.valueOf(to)
                val increment = BigDecimal.valueOf(step)
                while (weight <= end) {
                    result += weight.toDouble()
                    weight += increment
                }
            } else {
                result += parseDecimal(part) ?: return null
            }
            if (result.size > Limits.MAX_WEIGHTS_PER_ITEM) return null
        }
        if (result.any { it < Limits.MIN_ITEM_WEIGHT_KG || it > Limits.MAX_ITEM_WEIGHT_KG }) return null
        return result.toList()
    }

    fun formatWeightList(weights: List<Double>): String = weights.joinToString(", ") { number(it) }

    private const val DEFAULT_STEP_KG = 2.0
    private val DECIMAL = Regex("""\d{1,4}(\.\d{1,2})?""")
    private val WHOLE = Regex("""\d{1,4}""")
    private val RANGE = Regex("""(\d[\d.,]*)\s*-\s*(\d[\d.,]*)(?:\s*/\s*(\d[\d.,]*))?""")
}

/** Time spans for workouts (sets, holds, rests). Seconds under two minutes, otherwise minutes. */
object Durations {
    fun seconds(value: Int): String = "$value s"

    fun minutes(value: Int): String = "$value min"

    fun rest(seconds: Int): String = when {
        seconds <= 0 -> "no rest"
        seconds < 120 -> seconds(seconds)
        seconds % 60 == 0 -> minutes(seconds / 60)
        else -> "${Units.number(seconds / 60.0)} min"
    }
}
