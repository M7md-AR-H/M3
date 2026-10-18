package com.m3.app.feature.train

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.LocalDate
import kotlinx.coroutines.delay

/** Today's date that rolls over at midnight while the screen is open. */
@Composable
fun rememberToday(): State<LocalDate> = produceState(LocalDate.now()) {
    while (true) {
        delay(CHECK_EVERY_MILLIS)
        val now = LocalDate.now()
        if (now != value) value = now
    }
}

private const val CHECK_EVERY_MILLIS = 60_000L
