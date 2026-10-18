package com.m3.coach

import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.UiChange
import com.m3.core.designsystem.config.displayName
import com.m3.core.model.EquipmentKind
import com.m3.core.model.TrainingSetup
import com.m3.core.training.ExerciseCatalog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The fixed tool schemas the model may use. Field names match the prompt exactly. */
internal object ToolSchemas {
    @Serializable
    data class DayEquipment(val name: String, val date: String, val items: List<String>)

    @Serializable
    data class AvoidExercise(val name: String, val exercise: String, val reason: String = "")

    @Serializable
    data class AllowExercise(val name: String, val exercise: String)

    @Serializable
    data class SetDay(val name: String, val day: String, val place: String)

    @Serializable
    data class Theme(val name: String, val mode: String)

    @Serializable
    data class Card(val name: String, val card: String, val action: String)
}

/** What the validator needs to know about the owner's current state. */
data class ToolContext(
    val today: LocalDate,
    val setup: TrainingSetup,
    val catalog: ExerciseCatalog,
)

/**
 * Parses one tool call (JSON text from the model) against [ToolSchemas] and checks every value
 * against real data: known equipment, exercises, places, days in range. The model proposes; this decides.
 */
object CoachTools {
    const val MAX_REASON_CHARS = 200

    private val json = Json { ignoreUnknownKeys = true }

    fun validate(toolJson: String, context: ToolContext): ToolResult {
        val obj = try {
            json.parseToJsonElement(toolJson).jsonObject
        } catch (_: SerializationException) {
            return invalid("The Coach's suggestion wasn't readable.")
        } catch (_: IllegalArgumentException) {
            return invalid("The Coach's suggestion wasn't readable.")
        }
        val name = runCatching { obj["name"]?.jsonPrimitive?.content }.getOrNull()?.trim()?.lowercase()
        return try {
            when (name) {
                "day_equipment" -> dayEquipment(decode(ToolSchemas.DayEquipment.serializer(), obj), context)
                "avoid_exercise" -> avoid(decode(ToolSchemas.AvoidExercise.serializer(), obj), context)
                "allow_exercise" -> allow(decode(ToolSchemas.AllowExercise.serializer(), obj), context)
                "set_day" -> setDay(decode(ToolSchemas.SetDay.serializer(), obj), context)
                "theme" -> theme(decode(ToolSchemas.Theme.serializer(), obj))
                "card" -> card(decode(ToolSchemas.Card.serializer(), obj))
                else -> ToolResult.Ignored
            }
        } catch (_: SerializationException) {
            invalid("The Coach's suggestion was missing details.")
        } catch (_: IllegalArgumentException) {
            invalid("The Coach's suggestion was missing details.")
        }
    }

    private fun <T> decode(serializer: KSerializer<T>, obj: JsonObject): T = json.decodeFromJsonElement(serializer, obj)

    private fun dayEquipment(call: ToolSchemas.DayEquipment, context: ToolContext): ToolResult {
        val date = parseDate(call.date, context.today)
            ?: return invalid("The Coach picked a day I can't use (only the next 7 days).")
        val unknown = mutableListOf<String>()
        val items = call.items.flatMap { raw ->
            if (normalize(raw) in BODYWEIGHT_WORDS) return@flatMap emptyList()
            val kind = equipmentKind(raw)
            if (kind == null) unknown += raw.trim()
            listOfNotNull(kind)
        }.toSet()
        if (unknown.isNotEmpty()) {
            return invalid("The Coach named equipment I don't know: ${unknown.take(5).joinToString { it.take(30) }}. Try naming it differently.")
        }
        if (call.items.size > MAX_ITEMS) return invalid("That's more equipment than the app can take at once.")
        return ToolResult.Valid(CoachAction.SetDayEquipment(date, items))
    }

    private fun avoid(call: ToolSchemas.AvoidExercise, context: ToolContext): ToolResult {
        val exercise = findExercise(call.exercise, context) ?: return invalid("The Coach named an exercise I don't know.")
        // Model-written text is stored and shown again later; keep it to one plain line.
        val reason = call.reason.replace(Regex("\\s+"), " ").replace("<", "").replace(">", "").trim().take(MAX_REASON_CHARS)
        return ToolResult.Valid(CoachAction.AvoidExercise(exercise.id, exercise.name, reason))
    }

    private fun allow(call: ToolSchemas.AllowExercise, context: ToolContext): ToolResult {
        val exercise = findExercise(call.exercise, context) ?: return invalid("The Coach named an exercise I don't know.")
        return ToolResult.Valid(CoachAction.AllowExercise(exercise.id, exercise.name))
    }

    private fun setDay(call: ToolSchemas.SetDay, context: ToolContext): ToolResult {
        val day = DayOfWeek.entries.firstOrNull { it.name == normalize(call.day).uppercase() }
            ?: return invalid("The Coach picked a day I don't know.")
        if (normalize(call.place) in NOT_FREE_WORDS) return ToolResult.Valid(CoachAction.SetAvailability(day, null, null))
        val location = context.setup.locations.firstOrNull { normalize(it.name) == normalize(call.place) }
            ?: return invalid("The Coach named a place you haven't set up: ${call.place.trim().take(40)}.")
        return ToolResult.Valid(CoachAction.SetAvailability(day, location.id, location.name))
    }

    private fun theme(call: ToolSchemas.Theme): ToolResult {
        val mode = ThemeMode.entries.firstOrNull { it.name == normalize(call.mode).uppercase() }
            ?: return invalid("The Coach picked a theme I don't know.")
        return ToolResult.Valid(CoachAction.Ui(UiChange.SetThemeMode(mode)))
    }

    private fun card(call: ToolSchemas.Card): ToolResult {
        val type = CardType.entries.firstOrNull {
            it.name == normalize(call.card).uppercase() || normalize(it.displayName) == normalize(call.card)
        } ?: return invalid("The Coach named a Today card I don't know.")
        val change = when (normalize(call.action)) {
            "show" -> UiChange.AddCard(type)
            "hide" -> UiChange.SetCardVisible(type, false)
            "top" -> UiChange.SetCardOrder(listOf(type))
            else -> return invalid("The Coach asked for a card change I don't know.")
        }
        return ToolResult.Valid(CoachAction.Ui(change))
    }

    /**
     * Guard against the model attaching an unrelated look change to an answer (seen in evals): a theme or
     * Today-card change is only kept when the owner's message is about the theme or the cards.
     */
    fun relevantTo(action: CoachAction, ownerMessage: String): Boolean {
        val text = ownerMessage.lowercase()
        return when (action) {
            is CoachAction.Ui -> when (action.change) {
                is UiChange.SetThemeMode -> THEME_WORDS.any { it in text }
                // A card name alone ("workout", "habits") is too common; it needs "card" or a show/hide/move request.
                else -> CARD_WORDS.any { it in text } ||
                    (CardType.entries.any { it.displayName.lowercase() in text } && CARD_VERBS.any { it in text })
            }
            // Free days are the owner's usual week; a message about one day is a day_equipment matter (seen in evals).
            is CoachAction.SetAvailability -> ONE_DAY_WORDS.none { Regex("\\b$it\\b").containsMatchIn(text) }
            else -> true
        }
    }

    /** "today", "tomorrow", a weekday name or an ISO date, within the next 7 days (today + 6). */
    fun parseDate(text: String, today: LocalDate): LocalDate? {
        val value = normalize(text)
        val last = today.plusDays(MAX_DAYS_AHEAD)
        val date = when (value) {
            "today" -> today
            "tomorrow" -> today.plusDays(1)
            else -> DayOfWeek.entries.firstOrNull { it.name == value.uppercase() }
                ?.let { today.with(TemporalAdjusters.nextOrSame(it)) }
                ?: runCatching { LocalDate.parse(text.trim()) }.getOrNull()
        } ?: return null
        return date.takeIf { !it.isBefore(today) && !it.isAfter(last) }
    }

    /** Matches enum names and display names, ignoring case, spaces, hyphens and a plural "s". */
    fun equipmentKind(text: String): EquipmentKind? {
        val wanted = normalize(text)
        if (wanted.isEmpty()) return null
        fun forms(s: String) = setOf(s, s.removeSuffix("s"), s.removeSuffix("es"))
        val wantedForms = forms(wanted)
        wantedForms.firstNotNullOfOrNull { SYNONYMS[it] }?.let { return it }
        return EquipmentKind.entries.firstOrNull { kind ->
            val names = forms(normalize(kind.name)) + forms(normalize(kind.displayName)) +
                kind.displayName.split("/", "(").map { normalize(it) }.filter { it.isNotEmpty() }.flatMap(::forms)
            names.any { it in wantedForms }
        }
    }

    private fun findExercise(text: String, context: ToolContext) =
        context.catalog.exercises.firstOrNull { it.id == text.trim() }
            ?: context.catalog.exercises.firstOrNull { normalize(it.name) == normalize(text) }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("[\\s_\\-']+"), "_").trim('_')

    private fun invalid(message: String) = ToolResult.Invalid(message)

    private val SYNONYMS = mapOf(
        "band" to EquipmentKind.RESISTANCE_BANDS,
        "resistance_band" to EquipmentKind.RESISTANCE_BANDS,
        "bench" to EquipmentKind.FLAT_BENCH,
        "rack" to EquipmentKind.SQUAT_RACK,
        "pullup_bar" to EquipmentKind.PULL_UP_BAR,
        "chin_up_bar" to EquipmentKind.PULL_UP_BAR,
        "trx" to EquipmentKind.SUSPENSION_TRAINER,
        "cable" to EquipmentKind.CABLE_STATION,
        "cable_machine" to EquipmentKind.CABLE_STATION,
        "skipping_rope" to EquipmentKind.JUMP_ROPE,
        "ring" to EquipmentKind.GYMNASTIC_RINGS,
        "box" to EquipmentKind.PLYO_BOX,
        "mat" to EquipmentKind.EXERCISE_MAT,
        "yoga_mat" to EquipmentKind.EXERCISE_MAT,
        "bike" to EquipmentKind.EXERCISE_BIKE,
        "rower" to EquipmentKind.ROWING_MACHINE,
        "plate" to EquipmentKind.WEIGHT_PLATES,
        "kettlebell" to EquipmentKind.KETTLEBELLS,
        "dumbbell" to EquipmentKind.DUMBBELLS,
    )

    private val THEME_WORDS = listOf("theme", "dark", "light", "night", "mode", "colour", "color")
    private val CARD_WORDS = listOf("card", "today screen", "home screen", "today tab")
    private val ONE_DAY_WORDS = listOf("today", "tonight", "tomorrow", "this week")
    private val CARD_VERBS = listOf("show", "hide", "move", "remove", "put")

    private const val MAX_ITEMS = 60
    private const val MAX_DAYS_AHEAD = 6L
    private val BODYWEIGHT_WORDS = setOf("bodyweight", "body_weight", "none", "nothing")
    private val NOT_FREE_WORDS = setOf("not_free", "none", "rest", "off", "busy")
}
