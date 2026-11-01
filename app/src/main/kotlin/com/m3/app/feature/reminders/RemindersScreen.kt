package com.m3.app.feature.reminders

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.app.feature.you.LabeledSwitch
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.M3SquareButton
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.component.TextAction
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.ReminderKind
import com.m3.core.model.ReminderSetting
import com.m3.core.model.Reminders

/** Which reminders fire and when. Android's own settings still decide sound, per channel. */
@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val canPost by viewModel.canPost.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPermission()
        onPauseOrDispose {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
            TextAction(text = "← You", onClick = onBack)
            Text(text = "Reminders", style = M3.typography.display, color = M3.colors.ink)
            Spacer(Modifier.size(M3.spacing.xs))
            DoubleRule()
        }
        Text(
            text = "Each one checks your logs first and stays silent if it's already done. They're made on this phone; nothing leaves it.",
            style = M3.typography.body,
            color = M3.colors.muted,
        )
        if (!canPost) PermissionAsk(onAnswered = viewModel::refreshPermission)
        Column {
            SectionHeader(title = "Your day")
            ReminderKind.entries.forEach { kind ->
                ReminderRow(
                    setting = settings[kind],
                    onOn = { viewModel.setOn(kind, it) },
                    onStepMinute = { viewModel.stepMinute(kind, it) },
                    onStepHours = { viewModel.stepEveryHours(kind, it) },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
            SectionHeader(title = "Privacy")
            LabeledSwitch(
                label = "Show my numbers",
                checked = settings.showDetails,
                onCheckedChange = viewModel::setShowDetails,
            )
            Text(
                text = "Adds calories left, water so far and today's session to the text. Anyone holding your unlocked " +
                    "phone could read them, even with M³ locked, so it's off by default. The lock screen only ever says M³.",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
            )
        }
        if (canPost) {
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
                SectionHeader(title = "If they don't arrive")
                Text(
                    text = "Xiaomi phones stop apps in the background. In Android settings, under M³, turn on Autostart and " +
                        "set Battery saver to No restrictions. Opening M³ also sets the next reminder again.",
                    style = M3.typography.bodySmall,
                    color = M3.colors.muted,
                )
                TextAction(text = "Send a test reminder", onClick = viewModel::sendTest, color = M3.colors.accent)
            }
        }
    }
}

@Composable
private fun PermissionAsk(onAnswered: () -> Unit) {
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refused = !granted
        onAnswered()
    }
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        if (refused) {
            Text(
                text = "Still off. If no prompt appeared, allow Notifications for M³ in Android settings.",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
            )
        }
        M3OutlineButton(
            text = "Turn on reminders",
            onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReminderRow(
    setting: ReminderSetting,
    onOn: (Boolean) -> Unit,
    onStepMinute: (Int) -> Unit,
    onStepHours: (Int) -> Unit,
) {
    val kind = setting.kind
    Column(Modifier.padding(top = M3.spacing.xs)) {
        LabeledSwitch(label = kind.displayName, checked = setting.on, onCheckedChange = onOn)
        Text(text = kind.detail, style = M3.typography.bodySmall, color = M3.colors.muted)
        if (setting.on && kind.defaultMinute != null) {
            Stepper(
                label = if (kind == ReminderKind.WATER) "First at" else "At",
                value = Reminders.clockText(setting.minute),
                onStep = onStepMinute,
            )
        }
        if (setting.on && kind == ReminderKind.WATER) {
            Stepper(label = "Every", value = "${setting.everyHours} h", onStep = onStepHours)
        }
        HairRule(Modifier.padding(top = M3.spacing.s))
    }
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = M3.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
    ) {
        Text(text = label, style = M3.typography.bodySmall, color = M3.colors.muted, modifier = Modifier.weight(1f))
        M3SquareButton(description = "$label earlier or less", onClick = { onStep(-1) }) {
            Text(text = "−", style = M3.typography.numeric, color = M3.colors.ink)
        }
        Text(text = value, style = M3.typography.numeric, color = M3.colors.ink)
        M3SquareButton(description = "$label later or more", onClick = { onStep(1) }) {
            Text(text = "+", style = M3.typography.numeric, color = M3.colors.ink)
        }
    }
}
