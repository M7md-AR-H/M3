package com.m3.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.Masthead
import com.m3.core.designsystem.config.CardPlacement
import com.m3.core.designsystem.theme.M3
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(cards: List<CardPlacement>, modifier: Modifier = Modifier) {
    val issueLine = remember { "Issue 001 · ${todayLabel()}" }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(M3.spacing.l),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
    ) {
        Masthead(issueLine = issueLine)
        Kicker(text = "Today")
        Text(text = remember { greeting() }, style = M3.typography.headline, color = M3.colors.ink)
        cards.filter { it.visible }.forEach { placement ->
            CardRegistry[placement.type]?.invoke(placement)
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning."
    in 12..17 -> "Good afternoon."
    in 18..22 -> "Good evening."
    else -> "Rest well."
}

private fun todayLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
