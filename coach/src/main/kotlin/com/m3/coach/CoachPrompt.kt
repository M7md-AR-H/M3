package com.m3.coach

import com.m3.core.designsystem.config.CardPlacement
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.displayName
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Profile
import com.m3.core.model.TrainingSetup
import com.m3.core.model.Units
import com.m3.core.training.ExerciseUnit
import com.m3.core.training.PlannedExercise
import com.m3.core.training.WeekPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Everything the Coach may know, straight from the app's own data. Never sent anywhere (ADR 0005). */
data class CoachFacts(
    val today: LocalDate,
    val profile: Profile?,
    val setup: TrainingSetup,
    val plan: WeekPlan,
    /** Exercise id → (name, reason). */
    val avoided: Map<String, Pair<String, String>>,
    val dayEquipment: Map<LocalDate, Set<EquipmentKind>>,
    val themeMode: ThemeMode,
    val cards: List<CardPlacement>,
    val locks: Set<LockKey>,
)

enum class ChatRole { OWNER, COACH }

data class ChatTurn(val role: ChatRole, val text: String)

/**
 * Builds the system instruction and the per-turn prompt. The wording is deliberately plain and example-led
 * for a 4B on-device model; change it only with an eval run (docs/WORKING_RULES.md).
 */
object CoachPrompt {
    /** Rough budget for earlier turns, in characters (~4 per token), so the prompt fits the 4096-token window. */
    const val HISTORY_CHARS = 3_600

    /** Whole prompt (system + turn) budget at a cautious ~3 characters per token, leaving room for the answer. */
    const val PROMPT_CHARS = 10_500

    /** Owner free text is shortened in the prompt so it can't crowd out the facts. */
    const val MAX_INJURY_CHARS = 400

    fun system(facts: CoachFacts): String = buildString {
        appendLine(INTRO)
        appendLine()
        appendLine(TOOLS)
        appendLine()
        appendLine("Equipment names: " + EquipmentKind.entries.joinToString(", ") { it.name })
        appendLine("Today card names: " + facts.cards.joinToString(", ") { it.type.name })
        appendLine()
        appendLine("Facts (from the app, always correct):")
        append(facts(facts))
    }.trim()

    /** The message sent for one turn: recent conversation (oldest first) and the owner's new message. */
    fun turn(history: List<ChatTurn>, message: String, systemChars: Int = 0): String = buildString {
        val budget = minOf(HISTORY_CHARS, PROMPT_CHARS - systemChars - message.length - TURN_OVERHEAD_CHARS)
        val recent = mutableListOf<ChatTurn>()
        var used = 0
        for (turn in history.asReversed()) {
            val cost = turn.text.length + 10
            if (used + cost > budget) break
            recent += turn
            used += cost
        }
        if (recent.isNotEmpty()) {
            appendLine("Conversation so far:")
            recent.asReversed().forEach { appendLine("${label(it.role)}: ${it.text}") }
            appendLine()
        }
        appendLine("Owner: $message")
        append("Reply as the Coach to the owner's last message.")
    }

    private fun label(role: ChatRole) = if (role == ChatRole.OWNER) "Owner" else "Coach"

    private const val TURN_OVERHEAD_CHARS = 200

    /** Free text on one line, without tag brackets, so it can't forge fact lines or a tool block. */
    internal fun String.oneLine(max: Int): String = replace(Regex("\\s+"), " ").replace("<", "").replace(">", "").trim().take(max)

    internal fun facts(f: CoachFacts): String = buildString {
        val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)
        appendLine("Today is ${f.today.format(dateFormat)}. Tomorrow is ${f.today.plusDays(1).format(dateFormat)}.")
        val p = f.profile
        if (p == null) {
            appendLine("Owner: profile not filled in yet.")
        } else {
            val age = f.today.year - p.birthYear
            appendLine(
                "Owner: ${p.sex.displayName.lowercase()}, about $age years, ${Units.cm(p.heightCm)}, ${Units.kg(p.weightKg)}; " +
                    "goal: ${p.goal.displayName.lowercase()}; experience: ${p.experience.displayName.lowercase()}.",
            )
            appendLine("Injuries or limitations (owner's words): ${p.injuriesText.ifBlank { "none given" }.oneLine(MAX_INJURY_CHARS)}")
        }
        if (f.setup.locations.isEmpty()) appendLine("Places: none set up.")
        f.setup.locations.forEach { location ->
            val kinds = location.equipment.filterIsInstance<EquipmentItem.Catalog>().map { it.kind.displayName }
            val own = location.equipment.filterIsInstance<EquipmentItem.Custom>().map { it.name }
            val gear = if (kinds.isEmpty()) "bodyweight only" else kinds.joinToString(", ")
            appendLine("Place \"${location.name.oneLine(40)}\": $gear" + if (own.isEmpty()) "" else "; own items: ${own.joinToString(", ") { it.oneLine(40) }}")
        }
        val free = DayOfWeek.entries.mapNotNull { day -> f.setup.locationFor(day)?.let { "${day.label} at ${it.name}" } }
        appendLine("Free to train: ${free.ifEmpty { listOf("no days set") }.joinToString(", ")}.")
        appendLine("This week's plan (made by the app's planner):")
        DayOfWeek.entries.forEach { day ->
            val todayMark = if (day == f.today.dayOfWeek) " (today)" else ""
            val session = f.plan.sessionOn(day)
            if (session == null) {
                appendLine("- ${day.label}$todayMark: rest")
            } else {
                val override = if (day in f.plan.overriddenDays) " [equipment set for this day only]" else ""
                val exercises = session.exercises.joinToString("; ") { "${it.exercise.name} [${it.exercise.id}] ${amount(it)}" }
                val missing = if (session.unfilled.isEmpty()) "" else " (${session.unfilled.size} slot(s) not possible with this equipment)"
                appendLine("- ${day.label}$todayMark: ${session.type.displayName} at ${session.locationName}$override: $exercises$missing")
            }
        }
        if (f.dayEquipment.isNotEmpty()) {
            appendLine(
                "Equipment set for single days: " + f.dayEquipment.entries.sortedBy { it.key }.joinToString("; ") { (date, kinds) ->
                    "${date.dayOfWeek.label}: ${if (kinds.isEmpty()) "bodyweight only" else kinds.joinToString(", ") { it.displayName }}"
                },
            )
        }
        appendLine(
            "Avoided exercises: " + if (f.avoided.isEmpty()) "none" else f.avoided.entries.joinToString("; ") { (id, v) ->
                "${v.first} [$id]" + if (v.second.isBlank()) "" else " (${v.second.oneLine(100)})"
            },
        )
        appendLine("Theme: ${f.themeMode.name}. Today cards in order: " + f.cards.joinToString(", ") {
            "${it.type.displayName} [${it.type.name}]" + if (it.visible) "" else " (hidden)"
        })
        val locked = f.locks.map { LOCK_NAMES.getValue(it) }
        append("Locked by the owner, don't propose changes to: ${if (locked.isEmpty()) "nothing" else locked.joinToString(", ")}.")
    }

    private fun amount(planned: PlannedExercise): String {
        val p = planned.prescription
        val range = if (p.min == p.max) "${p.min}" else "${p.min}-${p.max}"
        return when (p.unit) {
            ExerciseUnit.REPS -> "${p.sets}x$range reps"
            ExerciseUnit.SECONDS -> "${p.sets}x$range s"
            ExerciseUnit.MINUTES -> "$range min"
        }
    }

    private val DayOfWeek.label get() = getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    private val LOCK_NAMES = mapOf(
        LockKey.THEME to "theme",
        LockKey.TABS to "tabs",
        LockKey.TODAY_LAYOUT to "Today cards",
        LockKey.TRAINING_PLAN to "the plan (day equipment, avoided exercises)",
        LockKey.AVAILABILITY to "free days",
    )

    private val INTRO = """
        You are the Coach inside M³, a private fitness app that runs only on the owner's phone. You talk with the owner.
        How to answer:
        - Be brief and practical: at most 120 words, plain sentences, no headings or tables.
        - Use metric units (kg, cm, km, kcal) and 24-hour time.
        - Never make up sets, reps, weights or new workouts. The app's planner writes the plan; explain it with the facts below.
        - If you're unsure what the owner means or has, ask one short question instead of guessing.
        - You are not a doctor. For sharp pain, swelling or pain that lasts, tell the owner to see a professional. You may suggest avoiding an exercise.
    """.trimIndent()

    private val TOOLS = """
        Changing things:
        - Only propose a change when the owner asks for one, or tells you about missing equipment, pain or a new schedule. Questions get an answer only, with no tool line. If no tool fits the request, write no tool line and say what the owner can do instead.
        - Propose at most ONE change. Say "I suggest ..." in your reply, then put the change on the last line as JSON between <tool> and </tool>. The owner taps Apply to make it happen, so never say it is already done.
        - Use only these tools, with these exact field names:
        1. The owner has only some equipment on a day in the next 7 days: <tool>{"name":"day_equipment","date":"today","items":["DUMBBELLS","PULL_UP_BAR"]}</tool> date is today, tomorrow or a weekday name; items are the equipment names below; [] means bodyweight only (e.g. a hotel room or a park with nothing). The planner then picks exercises for that equipment. Always use this for equipment, never avoid_exercise or set_day.
        2. An exercise hurts or the owner dislikes it: <tool>{"name":"avoid_exercise","exercise":"walking_lunge","reason":"knee pain"}</tool> exercise is the id in [brackets] in the plan.
        3. Use an exercise again: <tool>{"name":"allow_exercise","exercise":"walking_lunge"}</tool>
        4. The owner's usual week changes, every week from now on ("I can't train on Thursdays any more"): <tool>{"name":"set_day","day":"THURSDAY","place":"not_free"}</tool> place is one of the owner's places, or "not_free". Never use it for just today or tomorrow. The planner moves sessions by itself.
        5. App theme: <tool>{"name":"theme","mode":"DARK"}</tool> mode is LIGHT, DARK or SYSTEM.
        6. A Today card: <tool>{"name":"card","card":"HABITS","action":"hide"}</tool> action is show, hide or top.
    """.trimIndent()
}

/** The model's reply split into what the owner reads and the (still unvalidated) tool JSON, if any. */
data class ParsedReply(val text: String, val toolJson: String?)

object ReplyParser {
    private val TOOL = Regex("<tool>(.*?)(</tool>|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val FENCE = Regex("^```(?:json)?\\s*|\\s*```$")
    private val LEADING_LABEL = Regex("^(Coach|Trainer)\\s*:\\s*", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedReply {
        val match = TOOL.find(raw)
        val toolJson = match?.groupValues?.get(1)?.trim()?.replace(FENCE, "")?.trim()?.takeIf { it.isNotEmpty() }
        // Only the first tool counts; later ones are dropped from the text as well.
        val text = TOOL.replace(raw, "").replace(LEADING_LABEL, "").trim()
        return ParsedReply(text, toolJson)
    }
}
