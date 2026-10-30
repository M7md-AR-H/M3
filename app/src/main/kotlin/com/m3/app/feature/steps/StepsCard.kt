package com.m3.app.feature.steps

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.train.rememberToday
import com.m3.core.data.StepRepository
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.StepLedger
import com.m3.core.model.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class StepsViewModel @Inject constructor(
    private val tracker: StepTracker,
    private val steps: StepRepository,
) : ViewModel() {
    val hasSensor: Boolean = tracker.hasSensor
    val permitted: StateFlow<Boolean> = tracker.permitted

    fun stepsOn(date: LocalDate): Flow<Int> = steps.observeDay(date)

    fun permissionAnswered() = tracker.start()
}

/** Today's steps from the phone's own sensor. A phone without one shows nothing. */
@Composable
fun StepsCard(viewModel: StepsViewModel = hiltViewModel()) {
    if (!viewModel.hasSensor) return
    val permitted by viewModel.permitted.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        SectionHeader(title = "Steps")
        if (permitted) StepCount(viewModel) else StepPermission(viewModel)
    }
}

@Composable
private fun StepCount(viewModel: StepsViewModel) {
    val today by rememberToday()
    val flow = remember(today) { viewModel.stepsOn(today) }
    val steps by flow.collectAsStateWithLifecycle(initialValue = 0)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        Text(text = Units.count(steps), style = M3.typography.figure, color = M3.colors.ink)
        Text(
            text = "of ${Units.count(StepLedger.DAILY_GOAL)} · phone sensor",
            style = M3.typography.bodySmall,
            color = M3.colors.muted,
            modifier = Modifier.padding(bottom = M3.spacing.xs),
        )
    }
    Box(Modifier.fillMaxWidth().height(4.dp).background(M3.colors.rule)) {
        Box(
            Modifier
                .fillMaxWidth((steps.toFloat() / StepLedger.DAILY_GOAL).coerceIn(0f, 1f))
                .height(4.dp)
                .background(M3.colors.accent),
        )
    }
}

@Composable
private fun StepPermission(viewModel: StepsViewModel) {
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refused = !granted
        viewModel.permissionAnswered()
    }
    Text(
        text = if (refused) {
            "Still off. If no prompt appeared, allow Physical activity for M³ in Android settings."
        } else {
            "Count your steps with the phone's own sensor. No account, and the numbers stay on this phone."
        },
        style = M3.typography.bodySmall,
        color = M3.colors.muted,
    )
    M3OutlineButton(
        text = "Turn on step counting",
        onClick = { ask.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
        modifier = Modifier.fillMaxWidth(),
    )
}
