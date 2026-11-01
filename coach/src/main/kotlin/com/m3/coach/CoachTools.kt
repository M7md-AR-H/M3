package com.m3.coach

import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.UiChange
import com.m3.core.designsystem.config.displayName
import com.m3.core.model.EquipmentKind
import com.m3.core.model.ReminderKind
import com.m3.core.model.Reminders
import com.m3.core.model.TrainingSetup
import com.m3.core.training.ExerciseCatalog
import com.m3.core.training.MovementPattern
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

    /** Written by the app from the owner's own words, never by the model; checked the same way. */
    @Serializable
    data class SetReminder(val name: String, val kind: String, val on: Boolean, val minute: Int? = null, val every: Int? = null)
}

/** What the validator needs to know about the owner's current state. */
data class ToolContext(
    val today: LocalDate,
    val setup: TrainingSetup,
    val catalog: ExerciseCatalog,
    /** Avoided exercise id → reason, so allowing one again can lift the family it was avoided with. */
    val excluded: Map<String, String> = emptyMap(),
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
                "set_reminder" -> setReminder(decode(ToolSchemas.SetReminder.serializer(), obj))
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
        // Pain: avoid the whole movement family, or the planner swaps in an exercise that loads the joint the
        // same way (seen on the phone: knee pain on walking lunges → reverse lunge). Dislikes stay one exercise.
        val family = FAMILY_NAMES[exercise.pattern]
        if (family != null && isPain(reason)) {
            val similar = context.catalog.exercises.filter { it.pattern == exercise.pattern && it.id != exercise.id }.map { it.id }
            return ToolResult.Valid(CoachAction.AvoidExercise(exercise.id, exercise.name, reason, similar, family))
        }
        return ToolResult.Valid(CoachAction.AvoidExercise(exercise.id, exercise.name, reason))
    }

    private fun allow(call: ToolSchemas.AllowExercise, context: ToolContext): ToolResult {
        val exercise = findExercise(call.exercise, context) ?: return invalid("The Coach named an exercise I don't know.")
        // Undo a family avoided for pain as a whole: the same pattern, avoided with the same reason.
        val reason = context.excluded[exercise.id]
        val family = FAMILY_NAMES[exercise.pattern]
        if (reason != null && family != null && isPain(reason)) {
            val similar = context.catalog.exercises
                .filter { it.pattern == exercise.pattern && it.id != exercise.id && context.excluded[it.id] == reason }
                .map { it.id }
            if (similar.isNotEmpty()) return ToolResult.Valid(CoachAction.AllowExercise(exercise.id, exercise.name, similar, family))
        }
        return ToolResult.Valid(CoachAction.AllowExercise(exercise.id, exercise.name))
    }

    private fun isPain(reason: String) = PAIN.containsMatchIn(reason)

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

    private fun setReminder(call: ToolSchemas.SetReminder): ToolResult {
        val kind = ReminderKind.entries.firstOrNull { it.name == call.kind } ?: return invalid("That isn't a reminder M³ has.")
        if (call.minute != null && (kind.defaultMinute == null || !Reminders.validMinute(call.minute))) {
            return invalid("That reminder can't take that time.")
        }
        if (call.every != null && (kind != ReminderKind.WATER || call.every !in Reminders.WATER_HOURS)) {
            return invalid("Water reminders can be 1 to 6 hours apart.")
        }
        return ToolResult.Valid(CoachAction.SetReminder(kind, call.on, call.minute, call.every))
    }

    /**
     * The `set_reminder` call for a plain request ("turn off water reminders", "remind me to train at 18:00",
     * "water reminder every 2 hours"), or null. The model isn't asked: this needs no prompt space, the app's words
     * can't be wrong, and it still needs Apply, so a false match costs one tap. One reminder at a time.
     */
    fun reminderRequest(ownerMessage: String): String? {
        val text = ownerMessage.lowercase()
        if (!REMINDER_WORDS.containsMatchIn(text) || NOT_A_REQUEST.containsMatchIn(text)) return null
        val kind = REMINDER_KINDS.filter { (_, words) -> words.containsMatchIn(text) }.map { it.first }.singleOrNull() ?: return null
        val at = CLOCK_TIME.find(text)
        val minute = at?.let { clockMinute(it) ?: return null }
        val every = EVERY_HOURS.find(text)?.groupValues?.get(1)?.toIntOrNull()
        return buildJsonObject {
            put("name", "set_reminder")
            put("kind", kind.name)
            put("on", !REMINDER_OFF.containsMatchIn(text))
            minute?.let { put("minute", it) }
            every?.let { put("every", it) }
        }.toString()
    }

    /** "at 18", "at 7:30 pm", "18:00" as minutes after midnight, or null when it isn't a real time. */
    private fun clockMinute(match: MatchResult): Int? {
        val (hourText, minuteText, half) = match.destructured
        val hour = hourText.ifEmpty { match.groupValues[4] }.toIntOrNull() ?: return null
        val minutes = minuteText.ifEmpty { match.groupValues[5] }.ifEmpty { "0" }.toInt()
        val hour24 = when {
            half == "pm" && hour in 1..11 -> hour + 12
            half == "am" && hour == 12 -> 0
            else -> hour
        }
        return (hour24 * 60 + minutes).takeIf { hour24 < 24 && minutes < 60 }
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
            // Soreness or a bad night isn't a reason to drop an exercise for good (seen in the eval: bench press for
            // "I slept badly and I'm sore"), so the owner has to have named it.
            is CoachAction.AvoidExercise -> action.exerciseName.lowercase().split(" ", "-")
                .any { word -> word.length >= MIN_NAME_WORD && word.removeSuffix("s") in text }
            else -> true
        }
    }

    /**
     * The theme the owner plainly asks for ("switch to dark mode", "light theme please", "turn off dark mode"),
     * or null. Used when the model answers a theme request without a tool line (seen on the phone): the app then
     * proposes the change itself. A proposal still needs the owner's Apply, so a false positive costs one tap.
     */
    fun themeRequest(ownerMessage: String): ThemeMode? {
        val text = ownerMessage.lowercase()
        // "I don't want dark mode", "how do I turn off dark mode?": not a plain request, leave it to the model.
        if (Regex("\\b(don'?t|do not|never|hate|why|how|what)\\b").containsMatchIn(text)) return null
        val dark = Regex("\\b(dark|night)\\s*(mode|theme)\\b|\\b(mode|theme)\\s+(to\\s+)?dark\\b|\\bmake\\s+(it|the app)\\s+dark\\b").containsMatchIn(text)
        val light = Regex("\\blight\\s*(mode|theme)\\b|\\b(mode|theme)\\s+(to\\s+)?light\\b|\\bmake\\s+(it|the app)\\s+light\\b").containsMatchIn(text)
        val system = Regex("\\b(system|phone'?s?|automatic|auto)\\s+(mode|theme|setting)\\b|\\bfollow\\s+(the\\s+|my\\s+)?(system|phone)\\b").containsMatchIn(text)
        val off = Regex("\\b(turn|switch)\\s+off\\b|\\b(mode|theme)\\s+off\\b|\\bdisable\\b|\\bno\\s+more\\b|\\bstop\\s+using\\b").containsMatchIn(text)
        return when {
            system && !dark && !light -> ThemeMode.SYSTEM
            dark && !light -> if (off) ThemeMode.LIGHT else ThemeMode.DARK
            light && !dark -> if (off) ThemeMode.DARK else ThemeMode.LIGHT
            else -> null
        }
    }

    /**
     * The `set_day` call for a plain change to the usual week ("I can train on Fridays at Home", "I can't train on
     * Thursdays any more"), or null. On the phone the model wrote "not_free" for the first and no tool line at all
     * for the second, so the owner's own words decide. It still needs Apply, so a false positive costs one tap.
     * Questions and one-offs ("this Friday", "today") stay with the model.
     */
    fun scheduleRequest(ownerMessage: String, places: List<String>): String? {
        val text = ownerMessage.lowercase()
        if ('?' in text || !SCHEDULE_WORDS.containsMatchIn(text) || ONE_OFF_WORDS.containsMatchIn(text)) return null
        val day = DayOfWeek.entries
            .filter { Regex("\\b${it.name.lowercase()}s?\\b").containsMatchIn(text) }
            .singleOrNull() ?: return null
        val place = if (UNAVAILABLE.containsMatchIn(text)) {
            "not_free"
        } else {
            places.singleOrNull { Regex("\\b${Regex.escape(it.lowercase())}\\b").containsMatchIn(text) } ?: return null
        }
        return buildJsonObject {
            put("name", "set_day")
            put("day", day.name)
            put("place", place)
        }.toString()
    }

    /**
     * True when the reply says a change already happened or will happen by itself ("I have switched…",
     * "the planner will automatically…"). Only the owner's Apply changes anything, so the app adds a correction.
     */
    fun claimsChange(reply: String): Boolean = CLAIM_PATTERNS.any { it.containsMatchIn(reply) }

    private val REMINDER_WORDS = Regex("\\b(remind|reminding|reminders?|notifications?|notify)\\b")
    private val NOT_A_REQUEST = Regex("\\b(how|why|what|when|which|did|does|are)\\b")
    private val REMINDER_OFF = Regex(
        "\\b(turn|switch)\\b.*\\boff\\b|\\b(stop|disable|no more|mute)\\b|\\b(don'?t|do not)\\s+(remind|notify)\\b|\\b(reminders?|notifications?)\\s+off\\b",
    )
    private val EVERY_HOURS = Regex("\\bevery\\s+(\\d{1,2})\\s*(?:h|hrs?|hours?)\\b")
    private val CLOCK_TIME = Regex("\\bat\\s+(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?\\b|\\b(\\d{1,2}):(\\d{2})\\b")
    private val REMINDER_KINDS = listOf(
        ReminderKind.BREAKFAST to Regex("\\bbreakfast\\b"),
        ReminderKind.LUNCH to Regex("\\blunch\\b"),
        ReminderKind.DINNER to Regex("\\b(dinner|supper)\\b"),
        ReminderKind.WATER to Regex("\\b(water|drink|hydrate)\\b"),
        ReminderKind.WORKOUT to Regex("\\b(workouts?|train|training|gym|session)\\b"),
        ReminderKind.MORNING to Regex("\\b(morning|check-?in|weigh|weigh-?in|weight)\\b"),
        ReminderKind.EVENING to Regex("\\b(evening|log (my )?(day|food|meals?))\\b"),
        ReminderKind.LIGHTS_OUT to Regex("\\b(lights out|bedtime|bed|sleep)\\b"),
        ReminderKind.WEEK_REVIEW to Regex("\\breview\\b"),
        ReminderKind.REST to Regex("\\brest\\b"),
    )

    private val CLAIM_PATTERNS = listOf(
        Regex(
            "\\bI(?:'ve|\\s+have)?\\s+(?:now\\s+|just\\s+|already\\s+)*" +
                "(?:switched|set|changed|updated|applied|turned|enabled|disabled|adjusted|removed|added|swapped|replaced|excluded)\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex("\\b(?:has|have)\\s+(?:now\\s+|already\\s+)*been\\s+(?:switched|set|changed|updated|applied|turned|enabled|adjusted|removed|added|swapped|replaced)\\b", RegexOption.IGNORE_CASE),
        // "Your body will automatically recover" is advice; only the app or planner acting by itself is a claim.
        Regex("\\b(?:app|planner|plan|coach|system)\\s+will\\s+automatically\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:mode|theme|plan|it)\\s+is\\s+now\\s+(?:on|off|set|enabled|disabled|active|dark|light)\\b", RegexOption.IGNORE_CASE),
    )

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
    private const val MIN_NAME_WORD = 4
    private val SCHEDULE_WORDS = Regex("\\b(free|train|training|available|work out)\\b")
    private val ONE_OFF_WORDS = Regex("\\b(today|tomorrow|tonight|this|next|only)\\b")
    private val UNAVAILABLE = Regex("\\b(can'?t|cannot|can not|no longer|not free|not available|busy|any ?more)\\b")
    private val CARD_WORDS = listOf("card", "today screen", "home screen", "today tab")
    private val ONE_DAY_WORDS = listOf("today", "tonight", "tomorrow", "this week")
    private val CARD_VERBS = listOf("show", "hide", "move", "remove", "put")

    /** Patterns whose exercises load the same joints alike; isolation, core, carries and conditioning are too mixed. */
    private val FAMILY_NAMES = mapOf(
        MovementPattern.SQUAT to "squats",
        MovementPattern.HINGE to "hip hinges",
        MovementPattern.LUNGE to "lunges",
        MovementPattern.HORIZONTAL_PUSH to "presses",
        MovementPattern.VERTICAL_PUSH to "overhead presses",
        MovementPattern.HORIZONTAL_PULL to "rows",
        MovementPattern.VERTICAL_PULL to "pull-ups and pulldowns",
    )
    /** Whole words only: "reached" or "teacher" must not count as pain. */
    private val PAIN = Regex("\\b(pain\\w*|hurts?|hurting|injur\\w*|sore\\w*|aches?|aching|strain\\w*|sprain\\w*|twinge\\w*|sharp)\\b", RegexOption.IGNORE_CASE)

    private const val MAX_ITEMS = 60
    private const val MAX_DAYS_AHEAD = 6L
    private val BODYWEIGHT_WORDS = setOf("bodyweight", "body_weight", "none", "nothing")
    private val NOT_FREE_WORDS = setOf("not_free", "none", "rest", "off", "busy")
}
