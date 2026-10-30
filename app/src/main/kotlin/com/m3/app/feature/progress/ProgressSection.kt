package com.m3.app.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.ErrorText
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.LinkRow
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3TextField
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.BodyLimits
import com.m3.core.model.TrendPoint
import com.m3.core.model.Units

private enum class ChartRange(val label: String, val days: Long) {
    FOUR_WEEKS("4W", 28),
    TWELVE_WEEKS("12W", 84),
    SIX_MONTHS("6M", 182),
}

/** Weight and measurements, at the top of the You tab as in the prototype. */
@Composable
fun ProgressSection(onOpenMeasurements: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xl)) {
        WeightBlock(state, saveFailed, viewModel)
        MeasurementsBlock(state, onOpenMeasurements, viewModel::removeTodaysMeasurements)
    }
}

@Composable
private fun WeightBlock(state: ProgressState, saveFailed: Boolean, viewModel: ProgressViewModel) {
    var range by rememberSaveable { mutableStateOf(ChartRange.TWELVE_WEEKS) }
    val latest = state.points.lastOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.m)) {
        SectionHeader(title = "Weight") {
            if (state.points.size > 1) RangePicker(selected = range, onSelect = { range = it })
        }
        if (latest == null) {
            Text(
                text = "Weigh in a few mornings a week. Daily weight is noise; the trend shows what's really happening.",
                style = M3.typography.body,
                color = M3.colors.muted,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                Text(text = Units.oneDecimal(latest.trendKg), style = M3.typography.figure, color = M3.colors.ink)
                Text(
                    text = listOfNotNull(
                        "${Units.KG} trend",
                        state.weeklyChangeKg?.let { "${Units.signed(it)} ${Units.KG} a week" },
                    ).joinToString(" · "),
                    style = M3.typography.bodySmall,
                    color = M3.colors.muted,
                    modifier = Modifier.padding(bottom = M3.spacing.xs),
                )
            }
            WeightChart(points = inRange(state.points, range))
            Text(
                text = "Grey: weigh-ins. Colour: the smoothed trend. Your calorie target follows the trend.",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
            )
        }
        WeightEntryRow(saveFailed, viewModel)
        if (state.weighedToday) {
            TextAction(text = "Remove today's weigh-in", onClick = viewModel::removeTodaysWeight, color = M3.colors.muted)
        }
    }
}

private fun inRange(points: List<TrendPoint>, range: ChartRange): List<TrendPoint> {
    val from = points.last().date.minusDays(range.days)
    return points.filter { it.date >= from }
}

@Composable
private fun RangePicker(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    Row(Modifier.selectableGroup()) {
        ChartRange.entries.forEach { range ->
            val isSelected = range == selected
            Box(
                Modifier
                    .widthIn(min = 44.dp)
                    .heightIn(min = 44.dp)
                    .background(if (isSelected) M3.colors.ink else Color.Transparent, M3.shapes.shape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(range) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = range.label,
                    style = M3.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) M3.colors.ground else M3.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun WeightEntryRow(saveFailed: Boolean, viewModel: ProgressViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        M3TextField(
            value = text,
            onValueChange = {
                text = it.take(6)
                error = null
            },
            label = "Today's weight",
            suffix = Units.KG,
            keyboardType = KeyboardType.Decimal,
            error = error,
            modifier = Modifier.weight(1f),
        )
        M3Button(
            text = "Log",
            modifier = Modifier.padding(top = M3.spacing.s),
            onClick = {
                val kg = Units.parseDecimal(text)
                if (kg == null || !BodyLimits.validWeight(kg)) {
                    error = "Enter your weight in kg."
                } else {
                    viewModel.logWeight(kg) {
                        text = ""
                        focus.clearFocus()
                    }
                }
            },
        )
    }
    if (saveFailed) ErrorText("Couldn't save that. Please try again.")
}

@Composable
private fun MeasurementsBlock(state: ProgressState, onOpenMeasurements: () -> Unit, onRemoveToday: () -> Unit) {
    Column {
        SectionHeader(title = "Measurements")
        state.parts.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = M3.spacing.m), horizontalArrangement = Arrangement.spacedBy(M3.spacing.m)) {
                row.forEach { summary ->
                    Column(Modifier.weight(1f)) {
                        Kicker(text = summary.part.displayName)
                        Text(text = Units.cm(summary.cm), style = M3.typography.title, color = M3.colors.ink)
                        summary.changeCm?.let {
                            Text(text = "${Units.signed(it)} ${Units.CM} since the first", style = M3.typography.bodySmall, color = M3.colors.muted)
                        }
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
            HairRule()
        }
        LinkRow(
            title = if (state.parts.isEmpty()) "Add measurements" else "Update measurements",
            supportingText = "Waist, chest, arm, shoulders",
            onClick = onOpenMeasurements,
        )
        if (state.measuredToday) {
            TextAction(text = "Remove today's measurements", onClick = onRemoveToday, color = M3.colors.muted)
        }
    }
}
