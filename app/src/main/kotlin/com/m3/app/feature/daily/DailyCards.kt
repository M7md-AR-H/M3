package com.m3.app.feature.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.m3.app.feature.train.rememberToday
import com.m3.core.data.DailyRepository
import com.m3.core.designsystem.component.M3CheckRow
import com.m3.core.designsystem.component.M3SquareButton
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.CheckIn
import com.m3.core.model.DailyCheck
import com.m3.core.model.Habit
import com.m3.core.model.Readiness
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class DailyViewModel @Inject constructor(private val daily: DailyRepository) : ViewModel() {
    fun checkIn(date: LocalDate): Flow<CheckIn?> = daily.observeCheckIn(date)

    fun habits(date: LocalDate): Flow<Set<Habit>> = daily.observeHabits(date)

    fun save(checkIn: CheckIn) = write { daily.saveCheckIn(checkIn) }

    fun setHabit(date: LocalDate, habit: Habit, done: Boolean) = write { daily.setHabit(date, habit, done) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The row shows what's saved, so a failed write simply doesn't stick; tapping again retries.
            }
        }
    }
}

/** Three taps each morning. Saved once all three are answered; changing an answer saves again. */
@Composable
fun CheckInCard(viewModel: DailyViewModel = hiltViewModel()) {
    val today by rememberToday()
    val flow = remember(today) { viewModel.checkIn(today) }
    val saved by flow.collectAsStateWithLifecycle(initialValue = null)
    var sleep by rememberSaveable(today) { mutableStateOf<Int?>(null) }
    var energy by rememberSaveable(today) { mutableStateOf<Int?>(null) }
    var soreness by rememberSaveable(today) { mutableStateOf<Int?>(null) }
    LaunchedEffect(saved) {
        saved?.let {
            sleep = it.sleep
            energy = it.energy
            soreness = it.soreness
        }
    }
    val answer = {
        val s = sleep
        val e = energy
        val h = soreness
        if (s != null && e != null && h != null) viewModel.save(CheckIn(today, s, e, h))
    }
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        SectionHeader(title = "Morning check-in") {
            Text(text = "1 low · 5 high", style = M3.typography.bodySmall, color = M3.colors.muted)
        }
        ScoreRow("Sleep", sleep) { sleep = it; answer() }
        ScoreRow("Energy", energy) { energy = it; answer() }
        ScoreRow("Soreness", soreness) { soreness = it; answer() }
        Text(
            text = when (saved?.let(DailyCheck::readiness)) {
                null -> "Answer all three for today's advice."
                Readiness.GOOD -> "You're good to go: the full session as planned."
                Readiness.TAKE_IT_EASY -> "Take it easier today: drop a set per exercise if you need to, and recover first."
            },
            style = M3.typography.bodySmall,
            color = if (saved == null) M3.colors.muted else M3.colors.accent,
        )
    }
}

@Composable
private fun ScoreRow(label: String, selected: Int?, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = M3.typography.body.copy(fontWeight = FontWeight.Medium),
            color = M3.colors.ink,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
            (DailyCheck.MIN_SCORE..DailyCheck.MAX_SCORE).forEach { score ->
                val isSelected = score == selected
                M3SquareButton(
                    description = "$label $score of ${DailyCheck.MAX_SCORE}",
                    onClick = { onSelect(score) },
                    size = 44.dp,
                    filled = isSelected,
                    borderStroke = BorderStroke(1.dp, M3.colors.muted),
                ) {
                    Text(
                        text = score.toString(),
                        style = M3.typography.numeric,
                        color = if (isSelected) M3.colors.onAccent else M3.colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
fun HabitsCard(viewModel: DailyViewModel = hiltViewModel()) {
    val today by rememberToday()
    val flow = remember(today) { viewModel.habits(today) }
    val done by flow.collectAsStateWithLifecycle(initialValue = emptySet())
    Column {
        SectionHeader(title = "Habits") {
            Text(text = "${done.size} of ${Habit.entries.size}", style = M3.typography.bodySmall, color = M3.colors.muted)
        }
        Habit.entries.forEach { habit ->
            M3CheckRow(
                label = habit.displayName,
                supportingText = habit.detail,
                checked = habit in done,
                onCheckedChange = { viewModel.setHabit(today, habit, it) },
            )
        }
    }
}
