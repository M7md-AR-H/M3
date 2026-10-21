package com.m3.app.feature.workout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.ThemeTokens
import com.m3.core.designsystem.theme.M3
import com.m3.core.designsystem.theme.M3Theme
import com.m3.core.designsystem.theme.Motion
import com.m3.core.model.Units
import com.m3.core.training.ExerciseUnit
import kotlinx.coroutines.delay

private val Touch = 44.dp
private val EffortChoices = listOf(7.0, 8.0, 9.0, 10.0)

/** The live workout in "focus mode": always the dark palette, whatever the app theme is. */
@Composable
fun WorkoutScreen(tokens: ThemeTokens, onClose: () -> Unit, viewModel: WorkoutViewModel = hiltViewModel()) {
    M3Theme(tokens.copy(mode = ThemeMode.DARK)) {
        KeepScreenOnWithLightIcons()
        val state by viewModel.state.collectAsStateWithLifecycle()
        var confirmEnd by rememberSaveable { mutableStateOf(false) }
        var closing by remember { mutableStateOf(false) }
        val close = { closing = true; onClose() }
        val end = { viewModel.finish(close) }

        BackHandler { confirmEnd = true }
        Box(
            Modifier
                .fillMaxSize()
                .background(M3.colors.ground)
                .systemBarsPadding()
                .imePadding(),
        ) {
            when (val s = state) {
                WorkoutState.Loading -> Unit
                WorkoutState.NoWorkout -> if (!closing) Notice(text = "No workout in progress.", button = "Back", onClick = onClose)
                is WorkoutState.Missing -> Notice(
                    text = "This session isn't in your plan any more, so it can't be shown. Sets you logged are kept.",
                    button = "End workout",
                    onClick = end,
                )
                is WorkoutState.Ready -> ReadyContent(
                    s = s,
                    viewModel = viewModel,
                    onEnd = { confirmEnd = true },
                    onFinish = end,
                )
            }
        }
        if (confirmEnd) {
            AlertDialog(
                onDismissRequest = { confirmEnd = false },
                containerColor = M3.colors.surface,
                shape = M3.shapes.shape,
                title = { Text("End workout?", style = M3.typography.title, color = M3.colors.ink) },
                text = { Text("Logged sets are kept.", style = M3.typography.body, color = M3.colors.muted) },
                confirmButton = {
                    TextButton(onClick = { confirmEnd = false; end() }, modifier = Modifier.heightIn(min = Touch)) {
                        Text("End", style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold), color = M3.colors.accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmEnd = false }, modifier = Modifier.heightIn(min = Touch)) {
                        Text("Keep going", style = M3.typography.body, color = M3.colors.ink)
                    }
                },
            )
        }
    }
}

/** Screen stays on while visible; system bar icons turn light for the dark screen and are restored on leaving. */
@Composable
private fun KeepScreenOnWithLightIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            view.keepScreenOn = false
            lightStatus?.let { controller.isAppearanceLightStatusBars = it }
            lightNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun Notice(text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(M3.spacing.l),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = M3.typography.body, color = M3.colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.size(M3.spacing.l))
        M3Button(text = button, onClick = onClick)
    }
}

@Composable
private fun ReadyContent(s: WorkoutState.Ready, viewModel: WorkoutViewModel, onEnd: () -> Unit, onFinish: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = M3.spacing.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = Touch)) {
                Text("← End", style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold), color = M3.colors.ink)
            }
            Kicker(text = "Exercise ${s.current.index + 1} of ${s.count} · ${WorkoutLogic.clock(now - s.startedAt)}")
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = M3.spacing.l),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
        ) {
            AnimatedContent(
                targetState = s.current,
                contentKey = { it.index },
                transitionSpec = { fadeIn(Motion.medium()) togetherWith fadeOut(Motion.short()) },
                label = "exercise",
            ) { ex ->
                ExerciseBody(s = s, ex = ex, viewModel = viewModel)
            }
            val rest = s.rest
            if (rest != null) RestPanel(rest = rest, now = now, onSkip = viewModel::skipRest)
        }
        Footer(s = s, onPrevious = viewModel::previous, onNext = if (s.isLast) onFinish else viewModel::next)
    }
}

@Composable
private fun ExerciseBody(s: WorkoutState.Ready, ex: ExerciseUi, viewModel: WorkoutViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        Kicker(text = s.sessionName, color = M3.colors.accent)
        Text(text = ex.name, style = M3.typography.headline, color = M3.colors.ink)
        Text(text = ex.prescriptionLine, style = M3.typography.body, color = M3.colors.muted)
        Text(text = ex.reason, style = M3.typography.bodySmall, color = M3.colors.muted)
        Spacer(Modifier.size(M3.spacing.xs))
        Rule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(M3.spacing.m)) {
            Kicker(text = if (ex.showKg) ex.kgLabel else "", modifier = Modifier.weight(1f))
            Kicker(text = amountLabel(ex.amountUnit), modifier = Modifier.weight(1f))
        }
        ex.rows.forEach { row -> SetRow(row = row, ex = ex, viewModel = viewModel) }
        if (ex.canRate) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                Kicker(text = "Effort", modifier = Modifier.padding(end = M3.spacing.xs))
                EffortChoices.forEach { rpe ->
                    EffortChip(label = Units.number(rpe), selected = ex.effort == rpe, onClick = { viewModel.setEffort(rpe) })
                }
            }
        }
    }
}

private fun amountLabel(unit: ExerciseUnit) = when (unit) {
    ExerciseUnit.REPS -> "Reps"
    ExerciseUnit.SECONDS -> "s"
    ExerciseUnit.MINUTES -> "min"
}

@Composable
private fun SetRow(row: RowUi, ex: ExerciseUi, viewModel: WorkoutViewModel) {
    Column(
        Modifier.fillMaxWidth().alpha(if (row.done) DONE_ALPHA else 1f),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Set ${row.setNumber}", style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold), color = M3.colors.ink)
            Text(
                text = "  ·  Last ${row.last}",
                style = M3.typography.bodySmall,
                color = M3.colors.muted,
                modifier = Modifier.weight(1f),
            )
            DoneToggle(done = row.done, label = "Set ${row.setNumber} done", onToggle = { viewModel.toggleDone(row.setNumber) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(M3.spacing.m), verticalAlignment = Alignment.CenterVertically) {
            if (ex.showKg) {
                Stepper(
                    value = row.kg?.let { Units.number(it) }.orEmpty(),
                    description = "kg, set ${row.setNumber}",
                    keyboardType = KeyboardType.Decimal,
                    onMinus = { viewModel.stepKg(row.setNumber, -1) },
                    onPlus = { viewModel.stepKg(row.setNumber, +1) },
                    onText = { text ->
                        if (text.isBlank()) viewModel.setKg(row.setNumber, null) else Units.parseDecimal(text)?.let { viewModel.setKg(row.setNumber, it) }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(Modifier.weight(1f).heightIn(min = Touch), contentAlignment = Alignment.Center) {
                    Text(text = "BW", style = M3.typography.title, color = M3.colors.muted)
                }
            }
            Stepper(
                value = row.amount?.toString().orEmpty(),
                description = "${amountLabel(ex.amountUnit)}, set ${row.setNumber}",
                keyboardType = KeyboardType.Number,
                onMinus = { viewModel.stepAmount(row.setNumber, -1) },
                onPlus = { viewModel.stepAmount(row.setNumber, +1) },
                onText = { text ->
                    if (text.isBlank()) viewModel.setAmount(row.setNumber, null) else Units.parseWhole(text)?.let { viewModel.setAmount(row.setNumber, it) }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** − value + with an editable value; both buttons are 44 dp squares. */
@Composable
private fun Stepper(
    value: String,
    description: String,
    keyboardType: KeyboardType,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SquareButton(symbol = "−", description = "Less $description", onClick = onMinus)
        var text by remember(value) { mutableStateOf(value) }
        BasicTextField(
            value = text,
            onValueChange = { new ->
                if (new.length <= MAX_FIELD_CHARS && new.all { it.isDigit() || it == '.' || it == ',' }) {
                    text = new
                    onText(new)
                }
            },
            modifier = Modifier.weight(1f).widthIn(min = 32.dp).semantics { contentDescription = description },
            singleLine = true,
            textStyle = M3.typography.title.copy(color = M3.colors.ink, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(M3.colors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                Box(Modifier.heightIn(min = Touch), contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) Text("—", style = M3.typography.title, color = M3.colors.muted)
                    inner()
                }
            },
        )
        SquareButton(symbol = "+", description = "More $description", onClick = onPlus)
    }
}

@Composable
private fun SquareButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Touch)
            .border(1.dp, M3.colors.rule, M3.shapes.shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = M3.typography.title, color = M3.colors.ink)
    }
}

@Composable
private fun DoneToggle(done: Boolean, label: String, onToggle: () -> Unit) {
    val shape = M3.shapes.shape
    Box(
        Modifier
            .size(Touch)
            .background(if (done) M3.colors.accent else Color.Transparent, shape)
            .border(1.dp, if (done) M3.colors.accent else M3.colors.ink, shape)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (done) Text(text = "✓", style = M3.typography.title, color = M3.colors.onAccent)
    }
}

@Composable
private fun EffortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = M3.shapes.shape
    Box(
        Modifier
            .size(Touch)
            .background(if (selected) M3.colors.ink else Color.Transparent, shape)
            .border(1.dp, M3.colors.ink, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Effort $label out of 10" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold), color = if (selected) M3.colors.ground else M3.colors.ink)
    }
}

@Composable
private fun RestPanel(rest: RestTimer, now: Long, onSkip: () -> Unit) {
    val remaining = WorkoutLogic.restRemainingMillis(rest.endAt, now)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
        Rule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Kicker(text = "Rest")
                if (remaining > 0) {
                    Text(text = WorkoutLogic.restClock(remaining), style = M3.typography.display, color = M3.colors.ink)
                } else {
                    Text(text = "Rest over — go", style = M3.typography.title, color = M3.colors.accent)
                }
            }
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = Touch)) {
                Text("Skip", style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold), color = M3.colors.ink)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(M3.colors.rule)) {
            Box(
                Modifier
                    .fillMaxWidth(WorkoutLogic.restFraction(rest.endAt, rest.totalMillis, now))
                    .height(2.dp)
                    .background(M3.colors.accent),
            )
        }
    }
}

@Composable
private fun Footer(s: WorkoutState.Ready, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = M3.spacing.l, vertical = M3.spacing.m),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.s),
    ) {
        Rule()
        s.next?.let { next ->
            Column {
                Kicker(text = "Next")
                Text(text = next.name, style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold), color = M3.colors.ink)
                Text(text = next.prescription, style = M3.typography.bodySmall, color = M3.colors.muted)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(M3.spacing.m)) {
            if (s.current.index > 0) {
                TextButton(onClick = onPrevious, modifier = Modifier.heightIn(min = Touch)) {
                    Text("← Prev", style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold), color = M3.colors.ink)
                }
            }
            M3Button(text = if (s.isLast) "Finish" else "Next →", onClick = onNext, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(M3.colors.rule))
}

private const val TICK_MILLIS = 250L
private const val DONE_ALPHA = 0.6f
private const val MAX_FIELD_CHARS = 7
