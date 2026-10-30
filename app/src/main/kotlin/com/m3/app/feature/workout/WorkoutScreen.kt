package com.m3.app.feature.workout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.m3.app.ui.LocalAppLocked
import com.m3.core.designsystem.component.CheckGlyph
import com.m3.core.designsystem.component.HairRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3SquareButton
import com.m3.core.designsystem.component.TextAction
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
        val locked = LocalAppLocked.current
        KeepScreenOnWithLightIcons(enabled = !locked)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val finishFailed by viewModel.finishFailed.collectAsStateWithLifecycle()
        var confirmEnd by rememberSaveable { mutableStateOf(false) }
        var closing by remember { mutableStateOf(false) }
        val close = {
            if (!closing) {
                closing = true
                onClose()
            }
        }
        val end = {
            // Hide "No workout in progress" while finishing; the view model guards against a second Finish.
            closing = true
            viewModel.finish { onClose() }
        }
        LaunchedEffect(finishFailed) { if (finishFailed) closing = false }
        // The dialog is its own window and would sit above the lock layer, so it closes when the app locks.
        LaunchedEffect(locked) { if (locked) confirmEnd = false }

        BackHandler(enabled = !locked) { confirmEnd = true }
        Box(
            Modifier
                .fillMaxSize()
                .background(M3.colors.ground)
                .systemBarsPadding()
                .imePadding(),
        ) {
            when (val s = state) {
                WorkoutState.Loading -> Unit
                WorkoutState.NoWorkout -> if (!closing) Notice(text = "No workout in progress.", button = "Back", onClick = close)
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
                    finishFailed = finishFailed,
                    ticking = !locked,
                )
            }
        }
        if (confirmEnd && !locked) {
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

/**
 * Screen stays on while visible and unlocked (so a locked phone can still time out); system bar icons turn light
 * for the dark screen. Both are restored to what they were on leaving.
 */
@Composable
private fun KeepScreenOnWithLightIcons(enabled: Boolean) {
    val view = LocalView.current
    // A window flag, not View.keepScreenOn: a view change made while the activity restarts (the moment it locks)
    // can be dropped by the platform, leaving the screen held on behind the lock layer (found on the phone).
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val wasOn = window != null && (window.attributes.flags and flag) != 0
        if (enabled) window?.addFlags(flag) else window?.clearFlags(flag)
        onDispose { if (wasOn) window?.addFlags(flag) else window?.clearFlags(flag) }
    }
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            lightStatus?.let { controller.isAppearanceLightStatusBars = it }
            lightNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

/** Wall-clock time, updated while [ticking] and the screen is started; only its readers recompose. */
@Composable
private fun rememberNow(ticking: Boolean, periodMillis: Long = TICK_MILLIS): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(System.currentTimeMillis(), ticking, lifecycle) {
        if (!ticking) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(periodMillis)
            }
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
        Modifier.fillMaxSize().padding(M3.spacing.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = M3.typography.body, color = M3.colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.size(M3.spacing.l))
        M3Button(text = button, onClick = onClick)
    }
}

@Composable
private fun ReadyContent(
    s: WorkoutState.Ready,
    viewModel: WorkoutViewModel,
    onEnd: () -> Unit,
    onFinish: () -> Unit,
    finishFailed: Boolean,
    ticking: Boolean,
) {
    val now = rememberNow(ticking)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = M3.spacing.gutter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(text = "← End", onClick = onEnd)
            ElapsedKicker(index = s.current.index, count = s.count, startedAt = s.startedAt, now = now)
        }
        HairRule()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.l),
            verticalArrangement = Arrangement.spacedBy(M3.spacing.l),
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
            if (finishFailed) {
                Text(
                    text = "Couldn't end the workout. Your sets are saved; try again.",
                    style = M3.typography.bodySmall,
                    color = M3.colors.accent,
                )
            }
        }
        Footer(s = s, onPrevious = viewModel::previous, onNext = if (s.isLast) onFinish else viewModel::next)
    }
}

/** Reads the ticking clock here, so only this line recomposes every tick. */
@Composable
private fun ElapsedKicker(index: Int, count: Int, startedAt: Long, now: State<Long>) {
    Kicker(text = "Exercise ${index + 1} of $count · ${WorkoutLogic.clock(now.value - startedAt)}")
}

@Composable
private fun ExerciseBody(s: WorkoutState.Ready, ex: ExerciseUi, viewModel: WorkoutViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        Kicker(text = s.sessionName, color = M3.colors.accent)
        Text(text = ex.name, style = M3.typography.headline, color = M3.colors.ink)
        Text(text = ex.prescriptionLine, style = M3.typography.body, color = M3.colors.muted)
        Text(text = ex.reason, style = M3.typography.bodySmall, color = M3.colors.muted)
        Spacer(Modifier.size(M3.spacing.xs))
        HairRule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
            Kicker(text = if (ex.showKg) ex.kgLabel else "", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Kicker(text = amountLabel(ex.amountUnit), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Kicker(text = "Done", modifier = Modifier.width(Touch), textAlign = TextAlign.Center)
        }
        ex.rows.forEach { row ->
            SetRow(row = row, ex = ex, viewModel = viewModel)
            HairRule()
        }
        if (ex.canRate) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                Kicker(text = "Effort", modifier = Modifier.padding(end = M3.spacing.xs))
                EffortChoices.forEach { rpe ->
                    EffortChip(label = Units.number(rpe), selected = ex.effort == rpe, onClick = { viewModel.setEffort(ex.index, rpe) })
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
            Text(text = "Set ${row.setNumber}", style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold), color = M3.colors.ink)
            Text(text = "Last ${row.last}", style = M3.typography.bodySmall, color = M3.colors.muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(M3.spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (ex.showKg) {
                Stepper(
                    value = row.kg?.let { Units.number(it) }.orEmpty(),
                    description = "kg, set ${row.setNumber}",
                    keyboardType = KeyboardType.Decimal,
                    onMinus = { viewModel.stepKg(ex.index, row.setNumber, -1) },
                    onPlus = { viewModel.stepKg(ex.index, row.setNumber, +1) },
                    onText = { text ->
                        if (text.isBlank()) viewModel.setKg(ex.index, row.setNumber, null) else Units.parseDecimal(text)?.let { viewModel.setKg(ex.index, row.setNumber, it) }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(Modifier.weight(1f).heightIn(min = Touch), contentAlignment = Alignment.Center) {
                    Text(text = "BW", style = M3.typography.numeric, color = M3.colors.muted)
                }
            }
            Stepper(
                value = row.amount?.toString().orEmpty(),
                description = "${amountLabel(ex.amountUnit)}, set ${row.setNumber}",
                keyboardType = KeyboardType.Number,
                onMinus = { viewModel.stepAmount(ex.index, row.setNumber, -1) },
                onPlus = { viewModel.stepAmount(ex.index, row.setNumber, +1) },
                onText = { text ->
                    if (text.isBlank()) viewModel.setAmount(ex.index, row.setNumber, null) else Units.parseWhole(text)?.let { viewModel.setAmount(ex.index, row.setNumber, it) }
                },
                modifier = Modifier.weight(1f),
            )
            DoneToggle(done = row.done, label = "Set ${row.setNumber} done", onToggle = { viewModel.toggleDone(ex.index, row.setNumber) })
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
        var text by remember { mutableStateOf(value) }
        var focused by remember { mutableStateOf(false) }
        // While typing, the saved value echoes back a keystroke late; only take it over when not editing.
        LaunchedEffect(value, focused) { if (!focused) text = value }
        BasicTextField(
            value = text,
            onValueChange = { new ->
                // At most what Units can parse (4 digits), so the field never shows a value that wasn't saved.
                if (new.count { it.isDigit() } <= MAX_FIELD_DIGITS && new.all { it.isDigit() || it == '.' || it == ',' }) {
                    text = new
                    onText(new)
                }
            },
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 32.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = description },
            singleLine = true,
            textStyle = M3.typography.numeric.copy(color = M3.colors.ink, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(M3.colors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                Box(Modifier.heightIn(min = Touch), contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) Text("—", style = M3.typography.numeric, color = M3.colors.muted)
                    inner()
                }
            },
        )
        SquareButton(symbol = "+", description = "More $description", onClick = onPlus)
    }
}

@Composable
private fun SquareButton(symbol: String, description: String, onClick: () -> Unit) {
    M3SquareButton(description = description, onClick = onClick, size = Touch) {
        Text(text = symbol, style = M3.typography.numeric, color = M3.colors.ink)
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
        if (done) CheckGlyph(color = M3.colors.onAccent, size = 20.dp)
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
private fun RestPanel(rest: RestTimer, now: State<Long>, onSkip: () -> Unit) {
    val remaining = WorkoutLogic.restRemainingMillis(rest.endAt, now.value)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Kicker(text = "Rest")
                if (remaining > 0) {
                    Text(text = WorkoutLogic.restClock(remaining), style = M3.typography.figure, color = M3.colors.ink)
                } else {
                    Text(text = "Rest over — go", style = M3.typography.title, color = M3.colors.accent)
                }
            }
            TextAction(text = "Skip", onClick = onSkip)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(M3.colors.rule)) {
            Box(
                Modifier
                    .fillMaxWidth(WorkoutLogic.restFraction(rest.endAt, rest.totalMillis, now.value))
                    .height(4.dp)
                    .background(M3.colors.accent),
            )
        }
    }
}

@Composable
private fun Footer(s: WorkoutState.Ready, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HairRule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = M3.spacing.gutter, vertical = M3.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
        ) {
            Column(Modifier.weight(1f)) {
                val next = s.next
                if (next != null) {
                    Kicker(text = "Next")
                    Text(
                        text = next.name,
                        style = M3.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = M3.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = next.prescription,
                        style = M3.typography.bodySmall,
                        color = M3.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (s.current.index > 0) {
                M3SquareButton(description = "Previous exercise", onClick = onPrevious) {
                    Text(text = "←", style = M3.typography.numeric, color = M3.colors.ink)
                }
            }
            M3Button(text = if (s.isLast) "Finish" else "Next →", onClick = onNext)
        }
    }
}

private const val TICK_MILLIS = 250L
private const val DONE_ALPHA = 0.6f
private const val MAX_FIELD_DIGITS = 4
