package com.m3.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.m3.app.feature.train.rememberToday
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.Masthead
import com.m3.core.designsystem.config.CardPlacement
import com.m3.core.designsystem.theme.M3
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(cards: List<CardPlacement>, modifier: Modifier = Modifier) {
    // Both roll over while the screen stays open (checked every minute).
    val today by rememberToday()
    val hour by produceState(LocalTime.now().hour) {
        while (true) {
            delay(60_000L)
            value = LocalTime.now().hour
        }
    }
    val issueLine = remember(today) { "Issue 001 · ${todayLabel(today)}" }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(M3.spacing.l),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
    ) {
        Masthead(issueLine = issueLine)
        Kicker(text = "Today")
        Text(text = greeting(hour), style = M3.typography.headline, color = M3.colors.ink)
        cards.filter { it.visible }.forEach { placement ->
            CardRegistry[placement.type]?.invoke(placement)
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning."
    in 12..17 -> "Good afternoon."
    in 18..22 -> "Good evening."
    else -> "Rest well."
}

private fun todayLabel(today: LocalDate): String =
    today.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
