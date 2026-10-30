package com.m3.app.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.onboarding.ErrorText
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3TextField
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.BodyLimits
import com.m3.core.model.BodyPart
import com.m3.core.model.Units

/** Today's measurements. Only the parts filled in are saved. */
@Composable
fun MeasurementsScreen(onBack: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val values = remember { mutableStateMapOf<BodyPart, String>() }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    fun parsed(part: BodyPart): Double? = values[part]?.let(Units::parseDecimal)?.takeIf(BodyLimits::validCm)
    fun invalid(part: BodyPart): Boolean = !values[part].isNullOrBlank() && parsed(part) == null

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
                TextAction(text = "← You", onClick = onBack)
                Text(text = "Measure", style = M3.typography.display, color = M3.colors.ink)
                Spacer(Modifier.size(M3.spacing.xs))
                DoubleRule()
            }
            Text(
                text = "Same tape, same spot, relaxed. Fill in what you measured today.",
                style = M3.typography.body,
                color = M3.colors.muted,
            )
            BodyPart.entries.forEach { part ->
                M3TextField(
                    value = values[part].orEmpty(),
                    onValueChange = { values[part] = it.take(6) },
                    label = part.displayName,
                    suffix = Units.CM,
                    keyboardType = KeyboardType.Decimal,
                    error = "Enter a size in cm.".takeIf { showErrors && invalid(part) },
                )
            }
        }
        HairRule()
        Column(Modifier.padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m)) {
            if (saveFailed) ErrorText("Couldn't save that. Please try again.")
            M3Button(
                text = "Save",
                onClick = {
                    showErrors = true
                    if (BodyPart.entries.none(::invalid)) {
                        val filled = BodyPart.entries.mapNotNull { part -> parsed(part)?.let { part to it } }.toMap()
                        if (filled.isEmpty()) onBack() else viewModel.saveMeasurements(filled, onBack)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = M3.spacing.s),
            )
        }
    }
}
