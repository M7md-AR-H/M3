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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.displayName
import com.m3.app.feature.train.PlanText
import com.m3.app.feature.train.TrainState
import com.m3.app.feature.train.TrainViewModel
import com.m3.app.feature.train.rememberToday
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.Masthead
import com.m3.core.designsystem.component.Skeleton
import com.m3.core.designsystem.config.CardPlacement
import com.m3.core.designsystem.theme.M3
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(
    cards: List<CardPlacement>,
    modifier: Modifier = Modifier,
    viewModel: TrainViewModel = hiltViewModel(),
) {
    // Both roll over while the screen stays open (checked every minute).
    val today by rememberToday()
    val hour by produceState(LocalTime.now().hour) {
        while (true) {
            delay(60_000L)
            value = LocalTime.now().hour
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible = cards.filter { it.visible }
    val coming = visible.mapNotNull { ComingCards[it.type] }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.xl),
    ) {
        Masthead(
            issueLine = "Week ${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}",
            secondLine = remember(today) { todayLabel(today) },
        )
        TodayStory(state = state, today = today, hour = hour)
        visible.forEach { placement -> CardRegistry[placement.type]?.invoke(placement) }
        if (coming.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                HairRule()
                Kicker(text = "Coming to M³")
                Text(text = coming.joinToString(" · "), style = M3.typography.bodySmall, color = M3.colors.muted)
            }
        }
    }
}

/** The page's headline: what today is, from the plan. */
@Composable
private fun TodayStory(state: TrainState, today: LocalDate, hour: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        Kicker(text = "Today's story", color = M3.colors.accent)
        when (state) {
            TrainState.Loading -> Skeleton(widths = listOf(0.8f, 0.6f), barHeight = 28.dp)
            TrainState.Failed -> Text(text = greeting(hour), style = M3.typography.headline, color = M3.colors.ink)
            is TrainState.Ready -> {
                val session = state.plan.sessionOn(today.dayOfWeek)
                val lead = session?.type?.displayName ?: "Rest day"
                val line = if (session != null) PlanText.storyLine(state.profile?.goal) else "Growth happens here."
                Text(
                    text = buildAnnotatedString {
                        append("$lead. ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(line) }
                    },
                    style = M3.typography.headline,
                    color = M3.colors.ink,
                )
                val detail = session?.let { "${it.exercises.size} exercises at ${it.locationName}." }
                    ?: PlanText.nextSession(state.plan, today.dayOfWeek)
                Text(text = "${greeting(hour)} $detail", style = M3.typography.body, color = M3.colors.muted)
            }
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
    today.format(DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
