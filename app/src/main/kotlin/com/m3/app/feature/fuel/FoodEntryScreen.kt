package com.m3.app.feature.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.ErrorText
import com.m3.coach.MealEstimate
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3TextField
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.FoodEntry
import com.m3.core.model.Nutrition
import com.m3.core.model.Units
import java.time.LocalDate

/**
 * Adds one food to today. Only the name and calories are needed; a macro left empty counts as 0.
 * With an [estimate] from a photo the form starts filled in, for the owner to check and correct before saving.
 */
@Composable
fun FoodEntryScreen(onBack: () -> Unit, estimate: MealEstimate? = null, viewModel: FuelViewModel = hiltViewModel()) {
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(estimate?.name.orEmpty()) }
    var kcal by rememberSaveable { mutableStateOf(estimate?.kcal?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(estimate?.proteinG?.toString().orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(estimate?.carbsG?.toString().orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(estimate?.fatG?.toString().orEmpty()) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saveFailed) { if (saveFailed) saving = false }

    val nameError = when {
        name.isBlank() -> "Say what you ate."
        name.length > Nutrition.MAX_FOOD_NAME_CHARS -> "Keep it under ${Nutrition.MAX_FOOD_NAME_CHARS} characters."
        else -> null
    }
    val kcalValue = Units.parseWhole(kcal)
    val kcalError = if (kcalValue == null || kcalValue > Nutrition.MAX_ENTRY_KCAL) {
        "Enter 0 to ${Units.count(Nutrition.MAX_ENTRY_KCAL)} kcal."
    } else {
        null
    }
    val macroError = "Enter 0 to ${Nutrition.MAX_ENTRY_MACRO_G} g, or leave it empty."
    fun macroProblem(text: String): String? {
        if (text.isBlank()) return null
        val value = Units.parseWhole(text)
        return if (value == null || value > Nutrition.MAX_ENTRY_MACRO_G) macroError else null
    }
    val valid = listOf(nameError, kcalError, macroProblem(protein), macroProblem(carbs), macroProblem(fat)).all { it == null }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
                TextAction(text = "← Fuel", onClick = onBack)
                Text(text = if (estimate == null) "Add food" else "Your plate", style = M3.typography.display, color = M3.colors.ink)
                Spacer(Modifier.size(M3.spacing.xs))
                DoubleRule()
            }
            estimate?.let {
                it.note?.let { note ->
                    Text(
                        text = "“$note”",
                        style = M3.typography.serifBody.copy(fontStyle = FontStyle.Italic),
                        color = M3.colors.ink,
                    )
                }
                Text(
                    text = "An estimate from the photo, often 20–40% off. Check the numbers before you save.",
                    style = M3.typography.bodySmall,
                    color = M3.colors.accent,
                )
            }
            M3TextField(
                value = name,
                onValueChange = { name = it.take(Nutrition.MAX_FOOD_NAME_CHARS + 1) },
                label = "What did you eat?",
                error = nameError.takeIf { showErrors },
            )
            M3TextField(
                value = kcal,
                onValueChange = { kcal = it.take(5) },
                label = "Calories",
                suffix = "kcal",
                keyboardType = KeyboardType.Number,
                error = kcalError.takeIf { showErrors },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                listOf(
                    Triple("Protein", protein) { text: String -> protein = text },
                    Triple("Carbs", carbs) { text: String -> carbs = text },
                    Triple("Fat", fat) { text: String -> fat = text },
                ).forEach { (label, value, onChange) ->
                    M3TextField(
                        value = value,
                        onValueChange = { onChange(it.take(3)) },
                        label = label,
                        suffix = "g",
                        keyboardType = KeyboardType.Number,
                        error = macroProblem(value).takeIf { showErrors },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = "Packets and kitchen scales give exact numbers. Stays on this phone.",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
            )
        }
        HairRule()
        Column(Modifier.padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m)) {
            if (saveFailed) ErrorText("Couldn't save that. Please try again.")
            M3Button(
                text = if (saving) "Saving…" else "Save",
                enabled = !saving,
                onClick = {
                    showErrors = true
                    if (valid && kcalValue != null) {
                        saving = true
                        viewModel.save(
                            FoodEntry(
                                date = LocalDate.now(),
                                atMillis = System.currentTimeMillis(),
                                name = name,
                                kcal = kcalValue,
                                proteinG = Units.parseWhole(protein) ?: 0,
                                carbsG = Units.parseWhole(carbs) ?: 0,
                                fatG = Units.parseWhole(fat) ?: 0,
                            ),
                            onSaved = onBack,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = M3.spacing.s),
            )
        }
    }
}
