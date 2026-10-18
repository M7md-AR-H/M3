package com.m3.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.m3.app.feature.you.SegmentedChoice
import com.m3.core.designsystem.component.M3CheckRow
import com.m3.core.designsystem.component.M3Choice
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.M3TextField
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3
import com.m3.core.model.EquipmentGroup
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.model.Sex
import com.m3.core.model.Units
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Height, weight, birth year, sex. Errors are shown only once [showErrors] is true (after "Next"). */
@Composable
fun AboutYouSection(
    draft: ProfileDraft,
    errors: ProfileDraft.AboutErrors,
    showErrors: Boolean,
    onChange: (ProfileDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.m)) {
        M3TextField(
            value = draft.heightCm,
            onValueChange = { onChange(draft.copy(heightCm = it.take(4))) },
            label = "Height",
            suffix = Units.CM,
            keyboardType = KeyboardType.Number,
            error = errors.heightCm.takeIf { showErrors },
        )
        M3TextField(
            value = draft.weightKg,
            onValueChange = { onChange(draft.copy(weightKg = it.take(6))) },
            label = "Weight",
            suffix = Units.KG,
            keyboardType = KeyboardType.Decimal,
            error = errors.weightKg.takeIf { showErrors },
        )
        M3TextField(
            value = draft.birthYear,
            onValueChange = { onChange(draft.copy(birthYear = it.take(4))) },
            label = "Birth year",
            keyboardType = KeyboardType.Number,
            error = errors.birthYear.takeIf { showErrors },
        )
        Text(text = "Sex (used only for energy formulas)", style = M3.typography.bodySmall, color = M3.colors.muted)
        SegmentedChoice(
            options = Sex.entries.toList(),
            selected = draft.sex,
            label = { it?.displayName.orEmpty() },
            onSelect = { onChange(draft.copy(sex = it)) },
        )
        errors.sex?.takeIf { showErrors }?.let { ErrorText(it) }
    }
}

@Composable
fun GoalSection(selected: Goal?, onSelect: (Goal) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        Goal.entries.forEach { goal ->
            M3Choice(
                label = goal.displayName,
                selected = goal == selected,
                onClick = { onSelect(goal) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ExperienceSection(selected: Experience?, onSelect: (Experience) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        Experience.entries.forEach { level ->
            M3Choice(
                label = level.displayName,
                supportingText = level.description,
                selected = level == selected,
                onClick = { onSelect(level) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun InjuriesSection(draft: ProfileDraft, onChange: (ProfileDraft) -> Unit) {
    M3TextField(
        value = draft.injuries,
        onValueChange = { onChange(draft.copy(injuries = it)) },
        label = "Injuries or limitations (optional)",
        placeholder = "e.g. left knee hurts on deep squats",
        singleLine = false,
        minLines = 4,
        error = draft.injuriesError,
    )
    Text(
        text = "Stays on this phone. The Coach uses it to avoid exercises that could hurt.",
        style = M3.typography.bodySmall,
        color = M3.colors.muted,
    )
}

/** Gym/Home toggles, the owner's own locations, and "Add location". */
@Composable
fun LocationsSection(
    draft: SetupDraft,
    onTogglePreset: (name: String, on: Boolean) -> Unit,
    onAdd: (name: String) -> Unit,
    onRemove: (id: String) -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var addError by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        PresetLocations.forEach { preset ->
            M3CheckRow(
                label = preset,
                checked = draft.hasLocationNamed(preset),
                onCheckedChange = { onTogglePreset(preset, it) },
            )
        }
        draft.locations
            .filter { location -> PresetLocations.none { it.equals(location.name, ignoreCase = true) } }
            .forEach { location ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = location.name,
                        style = M3.typography.body,
                        color = M3.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    M3OutlineButton(text = "Remove", onClick = { onRemove(location.id) })
                }
            }
        M3TextField(
            value = newName,
            onValueChange = {
                newName = it
                addError = null
            },
            label = "Another place (e.g. Hotel, Park, Office)",
            error = addError,
        )
        M3OutlineButton(
            text = "Add location",
            onClick = {
                val error = draft.addLocationError(newName)
                if (error == null) {
                    onAdd(newName)
                    newName = ""
                }
                addError = error
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Tick-list for one location, grouped as in ADR 0008, with optional kg lists and custom items. */
@Composable
fun EquipmentSection(location: LocationDraft, onChange: (LocationDraft) -> Unit) {
    var customName by rememberSaveable(location.id) { mutableStateOf("") }
    var customError by rememberSaveable(location.id) { mutableStateOf<String?>(null) }
    val weightErrors = location.weightErrors
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
            M3OutlineButton(
                text = "Bodyweight only",
                onClick = { onChange(location.bodyweightOnly()) },
                modifier = Modifier.weight(1f),
            )
            M3OutlineButton(
                text = "Tick all",
                onClick = { onChange(location.selectAll(EquipmentKind.entries)) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (location.items.isEmpty() && location.custom.isEmpty()) {
                "Nothing ticked: bodyweight exercises only."
            } else {
                "${location.items.size + location.custom.size} ticked. Bodyweight exercises always count."
            },
            style = M3.typography.bodySmall,
            color = M3.colors.muted,
        )
        EquipmentGroup.entries.forEach { group ->
            SectionHeader(title = group.displayName, modifier = Modifier.padding(top = M3.spacing.s))
            EquipmentKind.byGroup(group).forEach { kind ->
                val checked = kind in location.items
                M3CheckRow(
                    label = kind.displayName,
                    checked = checked,
                    onCheckedChange = { onChange(location.toggle(kind, it)) },
                )
                if (checked && kind.hasWeights) {
                    M3TextField(
                        value = location.items[kind].orEmpty(),
                        onValueChange = { onChange(location.setWeights(kind, it.take(120))) },
                        label = "${kind.displayName}: weights available (optional)",
                        placeholder = "e.g. 2-24 or 4, 8, 12",
                        suffix = Units.KG,
                        error = weightErrors[kind],
                    )
                }
            }
        }
        SectionHeader(title = "Your own items", modifier = Modifier.padding(top = M3.spacing.s))
        location.custom.forEach { name ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = name, style = M3.typography.body, color = M3.colors.ink, modifier = Modifier.weight(1f))
                M3OutlineButton(text = "Remove", onClick = { onChange(location.removeCustom(name)) })
            }
        }
        M3TextField(
            value = customName,
            onValueChange = {
                customName = it
                customError = null
            },
            label = "Something not on the list",
            error = customError,
        )
        M3OutlineButton(
            text = "Add item",
            onClick = {
                val error = location.addCustomError(customName)
                if (error == null) {
                    onChange(location.addCustom(customName))
                    customName = ""
                }
                customError = error
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** For each weekday: Rest or one of the locations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleSection(draft: SetupDraft, onSetDay: (DayOfWeek, String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.m)) {
        DayOfWeek.entries.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(M3.spacing.xs)) {
                Text(text = day.displayName, style = M3.typography.title, color = M3.colors.ink)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(M3.spacing.s),
                    verticalArrangement = Arrangement.spacedBy(M3.spacing.s),
                ) {
                    val current = draft.schedule[day]
                    M3Choice(label = "Rest", selected = current == null, onClick = { onSetDay(day, null) })
                    draft.locations.forEach { location ->
                        M3Choice(
                            label = location.name,
                            selected = current == location.id,
                            onClick = { onSetDay(day, location.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorText(text: String) {
    Text(text = text, style = M3.typography.bodySmall, color = M3.colors.accent)
}

val DayOfWeek.displayName: String
    get() = getDisplayName(TextStyle.FULL, Locale.ENGLISH)
