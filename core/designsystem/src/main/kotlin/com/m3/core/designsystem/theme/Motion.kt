package com.m3.core.designsystem.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/** Shared motion tokens. Durations are short; Compose scales them with the system animator setting. */
object Motion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 220
    const val LONG_MS = 300

    val Easing = FastOutSlowInEasing

    fun <T> short() = tween<T>(SHORT_MS, easing = Easing)
    fun <T> medium() = tween<T>(MEDIUM_MS, easing = Easing)
    fun <T> long() = tween<T>(LONG_MS, easing = Easing)
}
