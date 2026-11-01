package com.m3.app.feature.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.displayName
import com.m3.app.feature.train.rememberToday
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.LinkRow
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.M3Snackbar
import com.m3.core.designsystem.component.M3SquareButton
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.component.Skeleton
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.FoodEntry
import com.m3.core.model.NutritionTargets
import com.m3.core.model.Units
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun FuelScreen(onAddFood: () -> Unit, onSnapMeal: () -> Unit, onOpenGrocery: () -> Unit, viewModel: FuelViewModel = hiltViewModel()) {
    val today by rememberToday()
    val flow = remember(today) { viewModel.day(today) }
    val state by flow.collectAsStateWithLifecycle(initialValue = FuelState.Loading)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val remove = { entry: FoodEntry ->
        viewModel.remove(entry)
        scope.launch {
            val result = snackbar.showSnackbar(message = "Removed ${entry.name}", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.save(entry)
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
                Kicker(text = kickerFor(today), color = M3.colors.accent)
                Text(text = "Fuel", style = M3.typography.display, color = M3.colors.ink)
                Spacer(Modifier.size(M3.spacing.xs))
                DoubleRule()
            }
            when (val s = state) {
                FuelState.Loading -> Skeleton(widths = listOf(0.5f, 1f, 0.8f, 1f))
                FuelState.Failed ->
                    Text(text = "Couldn't load today's food.", style = M3.typography.body, color = M3.colors.accent)
                is FuelState.Ready -> {
                    s.targets?.let { targets -> Budget(s, targets) }
                    Row(horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                        M3Button(text = "Snap meal", onClick = onSnapMeal, modifier = Modifier.weight(1f))
                        M3OutlineButton(text = "Add food", onClick = onAddFood, modifier = Modifier.weight(1f))
                    }
                    Plates(entries = s.entries, onRemove = remove)
                    LinkRow(title = "Grocery list", supportingText = "This week's shop, from your targets", onClick = onOpenGrocery)
                    s.targets?.let { targets -> HowItWorks(targets) }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { data -> M3Snackbar(data) }
    }
}

@Composable
private fun Budget(state: FuelState.Ready, targets: NutritionTargets) {
    val left = targets.kcal - state.eaten.kcal
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.m)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
            ) {
                Text(text = Units.count(kotlin.math.abs(left)), style = M3.typography.figure, color = M3.colors.ink)
                Text(
                    text = if (left >= 0) "kcal left" else "kcal over",
                    style = M3.typography.body,
                    color = M3.colors.muted,
                    modifier = Modifier.padding(bottom = M3.spacing.xs),
                )
            }
            Text(
                text = "${Units.count(state.eaten.kcal)} / ${Units.count(targets.kcal)}",
                style = M3.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = M3.colors.muted,
                modifier = Modifier.padding(bottom = M3.spacing.xs),
            )
        }
        MacroMeters(state.eaten, targets)
    }
}

@Composable
private fun Plates(entries: List<FoodEntry>, onRemove: (FoodEntry) -> Unit) {
    Column {
        SectionHeader(title = "Today's plates")
        if (entries.isEmpty()) {
            Text(
                text = "Nothing logged yet. Add what you eat and the numbers above fill in.",
                style = M3.typography.body,
                color = M3.colors.muted,
                modifier = Modifier.padding(top = M3.spacing.m),
            )
        }
        entries.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = M3.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(M3.spacing.m),
            ) {
                Column(Modifier.weight(1f)) {
                    Kicker(text = timeOf(entry))
                    Text(
                        text = entry.name,
                        style = M3.typography.body.copy(fontWeight = FontWeight.Medium),
                        color = M3.colors.ink,
                    )
                    Text(
                        text = "${Units.kcal(entry.kcal)} · ${Units.grams(entry.proteinG)} protein",
                        style = M3.typography.bodySmall,
                        color = M3.colors.muted,
                    )
                }
                M3SquareButton(description = "Remove ${entry.name}", onClick = { onRemove(entry) }, size = 44.dp) {
                    Text(text = "×", style = M3.typography.numeric, color = M3.colors.muted)
                }
            }
            HairRule()
        }
    }
}

@Composable
private fun HowItWorks(targets: NutritionTargets) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
        Kicker(text = "How your target is set", color = M3.colors.ink)
        Text(
            text = "You burn about ${Units.kcal(targets.maintenanceKcal)} a day, from your body and your training week. " +
                "Your goal sets the target from there." +
                if (targets.heldBySafety) " It's held at a safe level, so you won't lose weight too fast." else "",
            style = M3.typography.bodySmall,
            color = M3.colors.muted,
        )
    }
}

private fun kickerFor(date: LocalDate): String = "${date.dayOfWeek.displayName} · today's targets"

private fun timeOf(entry: FoodEntry): String =
    Units.clock(Instant.ofEpochMilli(entry.atMillis).atZone(ZoneId.systemDefault()).toLocalTime())
