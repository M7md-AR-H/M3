package com.m3.core.designsystem.config

import kotlin.math.pow

data class Rgb(val red: Int, val green: Int, val blue: Int)

/** WCAG 2.x contrast math. */
object Contrast {
    private val hexPattern = Regex("^#[0-9A-Fa-f]{6}$")

    /** Parses "#RRGGBB"; returns null for anything else. */
    fun parseHex(hex: String): Rgb? {
        if (!hexPattern.matches(hex)) return null
        return Rgb(
            red = hex.substring(1, 3).toInt(16),
            green = hex.substring(3, 5).toInt(16),
            blue = hex.substring(5, 7).toInt(16),
        )
    }

    fun relativeLuminance(color: Rgb): Double =
        0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

    fun ratio(first: Rgb, second: Rgb): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** Contrast ratio between two "#RRGGBB" strings, or null if either is invalid. */
    fun ratio(firstHex: String, secondHex: String): Double? {
        val first = parseHex(firstHex) ?: return null
        val second = parseHex(secondHex) ?: return null
        return ratio(first, second)
    }

    private fun linearize(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
