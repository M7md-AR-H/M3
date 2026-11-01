package com.m3.coach

import com.m3.core.designsystem.config.CardPlacement
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.displayName
import com.m3.core.model.CheckIn
import com.m3.core.model.DayTotals
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.NutritionTargets
import com.m3.core.model.Profile
import com.m3.core.model.StepLedger
import com.m3.core.model.TrainingSetup
import com.m3.core.model.Units
import com.m3.core.training.ExerciseUnit
import com.m3.core.training.LoggedSet
import com.m3.core.training.PlannedExercise
import com.m3.core.training.Progression
import com.m3.core.training.Target
import com.m3.core.training.TargetKind
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
    /**
     * [progressKey] (day + variant) → last performance and next target, for this week's exercises. Per day, because the
     * same variant can have a different prescription, and so a different target, on another day.
     */
    val progress: Map<String, ExerciseProgress> = emptyMap(),
    /** Finished workouts in the 28 days up to today, and the date of the latest one. */
    val workoutsLast28Days: Int = 0,
    val lastWorkout: LocalDate? = null,
    val daily: DailyFacts = DailyFacts(),
    /** This week is an accepted deload week (the plan already has the lighter sets and loads). */
    val deloadThisWeek: Boolean = false,
)

/** How today is going, from the logs. Every number is the app's; the model only explains them. */
data class DailyFacts(
    val targets: NutritionTargets? = null,
    val eaten: DayTotals = DayTotals(),
    val foods: List<String> = emptyList(),
    val steps: Int? = null,
    val trendKg: Double? = null,
    val weeklyChangeKg: Double? = null,
    val checkIn: CheckIn? = null,
)

/** What the owner logged last time for one exercise variant, and what the app's progression picked next. */
data class ExerciseProgress(val lastDate: LocalDate, val lastSets: List<LoggedSet>, val next: Target)

/** The key of [CoachFacts.progress]: "MONDAY:back_squat@BARBELL+…". */
fun progressKey(day: DayOfWeek, variantKey: String) = "${day.name}:$variantKey"

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

    /** Foods named in the prompt; the totals always cover the whole day. */
    const val MAX_FOODS = 3

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
        val lastWorkout = f.lastWorkout?.let { "; last on ${it.format(SHORT_DATE)}" } ?: ""
        appendLine("Workouts in the last 4 weeks: ${f.workoutsLast28Days}$lastWorkout.")
        appendDaily(f.daily)
        // Inside the plan's header, not a line of its own: the worst-case prompt has no room to spare.
        if (f.deloadThisWeek) {
            appendLine("This week's deload plan (\"last\" = logged, \"next\" = target):")
        } else {
            appendLine("This week's plan (\"last\" = logged, \"next\" = the app's target):")
        }
        DayOfWeek.entries.forEach { day ->
            val todayMark = if (day == f.today.dayOfWeek) " (today)" else ""
            val session = f.plan.sessionOn(day)
            if (session == null) {
                appendLine("- ${day.label}$todayMark: rest")
            } else {
                val override = if (day in f.plan.overriddenDays) " [equipment set for this day only]" else ""
                val exercises = session.exercises.joinToString("; ") {
                    "${it.exercise.name} [${it.exercise.id}] ${amount(it)}" + progressNote(f.progress[progressKey(day, Progression.variantKey(it.exercise.id, it.equipment))], it)
                }
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
        appendLine("Theme: ${f.themeMode.name}. Today cards: " + f.cards.joinToString(", ") {
            it.type.name + if (it.visible) "" else " (hidden)"
        })
        val locked = f.locks.map { LOCK_NAMES.getValue(it) }
        append(
            "Locked by the owner: ${if (locked.isEmpty()) "nothing" else locked.joinToString(", ")}. " +
                "If the owner asks for a locked change, still write the tool line; the app shows it as locked.",
        )
    }

    private fun StringBuilder.appendDaily(daily: DailyFacts) {
        val eaten = daily.eaten
        daily.targets?.let { t ->
            val left = t.kcal - eaten.kcal
            // Remaining amounts are spelled out: the model got the subtraction wrong in the eval.
            fun macro(name: String, done: Int, target: Int) = "$name ${done}/${target}g, ${(target - done).coerceAtLeast(0)}g left"
            appendLine(
                "Food: ${eaten.kcal}/${t.kcal} kcal, ${if (left >= 0) "$left left" else "${-left} over"}; " +
                    "${macro("protein", eaten.proteinG, t.proteinG)}; ${macro("carbs", eaten.carbsG, t.carbsG)}; " +
                    macro("fat", eaten.fatG, t.fatG) +
                    if (daily.foods.isEmpty()) "." else "; ${daily.foods.joinToString(", ") { it.oneLine(18) }}.",
            )
        }
        val steps = daily.steps?.let { "$it, goal ${StepLedger.DAILY_GOAL}" } ?: "none"
        val trend = daily.trendKg?.let { kg ->
            "${Units.oneDecimal(kg)} kg" + (daily.weeklyChangeKg?.let { ", ${Units.signed(it).replace('−', '-')} kg a week" } ?: "")
        } ?: "no weigh-ins"
        val c = daily.checkIn
        val checkIn = if (c == null) "not done" else "sleep ${c.sleep}, energy ${c.energy}, soreness ${c.soreness} (1 low, 5 high)"
        appendLine("Steps today: $steps. Weight trend: $trend. Check-in: $checkIn.")
    }

    /** " (last 20 kg x 12,12,11; next 22.5 kg up)", or "" when never logged. Compact: it repeats per exercise. */
    private fun progressNote(progress: ExerciseProgress?, planned: PlannedExercise): String {
        if (progress == null || progress.lastSets.isEmpty()) return ""
        val unit = planned.prescription.unit
        val kg = progress.lastSets.mapNotNull { it.kg }.maxOrNull()
        // Minutes are stored as seconds.
        val amounts = progress.lastSets.joinToString(",") { set ->
            when (unit) {
                ExerciseUnit.REPS -> set.reps
                ExerciseUnit.SECONDS -> set.seconds
                ExerciseUnit.MINUTES -> set.seconds?.div(SECONDS_PER_MINUTE)
            }?.toString() ?: "-"
        }
        val suffix = when (unit) {
            ExerciseUnit.REPS -> ""
            ExerciseUnit.SECONDS -> " s"
            ExerciseUnit.MINUTES -> " min"
        }
        val last = (if (kg != null) "${Units.kg(kg)} x " else "") + amounts + suffix
        val next = progress.next.kg?.let { "; next ${Units.kg(it)} ${NEXT_WORDS.getValue(progress.next.kind)}" } ?: ""
        return " (last $last$next)"
    }

    private const val SECONDS_PER_MINUTE = 60

    private val SHORT_DATE =DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val NEXT_WORDS = mapOf(TargetKind.START to "first time", TargetKind.INCREASE to "up", TargetKind.HOLD to "same", TargetKind.DECREASE to "lighter", TargetKind.DELOAD to "deload")

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
        LockKey.REMINDERS to "reminders",
    )

    private val INTRO = """
        You are the Coach inside M³, a private fitness app that runs only on the owner's phone.
        How to answer:
        - Be brief and practical: at most 120 words, plain sentences, no headings or tables.
        - Use metric units (kg, cm, km, kcal) and 24-hour time.
        - Never make up sets, reps, weights or new workouts. The app's planner writes the plan; explain it with the facts below. For weights, use only the "next" values in the facts. For food, steps and body weight, use only numbers in the facts, and never suggest eating under the food target.
        - If you're unsure what the owner means or has, ask one short question instead of guessing.
        - You are not a doctor. For sharp pain, swelling or pain that lasts, tell the owner to see a professional. You may suggest avoiding an exercise.
    """.trimIndent()

    private val TOOLS = """
        Changing things:
        - Only propose a change when the owner asks for one, or tells you about missing equipment, pain or a new schedule. Questions get an answer only, with no tool line. If no tool fits the request, write no tool line and say what the owner can do instead.
        - Propose at most ONE change. Say "I suggest ..." in your reply, then put the change on the last line as JSON between <tool> and </tool>. The owner taps Apply to make it happen, so never say it is done or that the app will do it by itself.
        - Use only these tools, with these exact field names:
        1. The owner has only some equipment on a day in the next 7 days: <tool>{"name":"day_equipment","date":"today","items":["DUMBBELLS","PULL_UP_BAR"]}</tool> date is today, tomorrow or a weekday name; items are the equipment names below; [] means bodyweight only (e.g. a hotel room or a park with nothing). Always use this for equipment, never avoid_exercise or set_day.
        2. An exercise hurts or the owner dislikes it: <tool>{"name":"avoid_exercise","exercise":"walking_lunge","reason":"knee pain"}</tool> exercise is the id in [brackets] in the plan. It lasts until the owner allows it again, not just today; for pain the app also avoids similar exercises.
        3. Use an exercise again: <tool>{"name":"allow_exercise","exercise":"walking_lunge"}</tool>
        4. The owner's usual week changes, every week from now on ("I can't train on Thursdays any more"): <tool>{"name":"set_day","day":"THURSDAY","place":"not_free"}</tool> place is one of the owner's places, or "not_free". Never use it for just today or tomorrow.
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
