package com.m3.app.feature.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Meter
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.DayTotals
import com.m3.core.model.NutritionTargets
import com.m3.core.model.Units

/** Protein in the accent, as the one number to chase; carbs and fat in ink. */
@Composable
fun MacroMeters(eaten: DayTotals, targets: NutritionTargets) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(M3.spacing.m)) {
        Macro("Protein", eaten.proteinG, targets.proteinG, M3.colors.accent, Modifier.weight(1f))
        Macro("Carbs", eaten.carbsG, targets.carbsG, M3.colors.ink, Modifier.weight(1f))
        Macro("Fat", eaten.fatG, targets.fatG, M3.colors.ink, Modifier.weight(1f))
    }
}

@Composable
private fun Macro(name: String, eaten: Int, target: Int, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
        Kicker(text = name, color = M3.colors.ink)
        M3Meter(fraction = if (target > 0) eaten.toFloat() / target else 0f, color = color)
        Text(text = "$eaten / ${Units.grams(target)}", style = M3.typography.bodySmall, color = M3.colors.muted)
    }
}
