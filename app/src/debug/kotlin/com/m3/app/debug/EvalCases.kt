package com.m3.app.debug

import com.m3.coach.CoachFacts
import com.m3.coach.CoachPrompt
import com.m3.coach.CoachTools
import com.m3.coach.DailyFacts
import com.m3.coach.ExerciseProgress
import com.m3.coach.ReplyParser
import com.m3.coach.ToolContext
import com.m3.coach.ToolResult
import com.m3.coach.progressKey
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.model.CheckIn
import com.m3.core.model.DayTotals
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.model.NutritionTargets
import com.m3.core.model.Profile
import com.m3.core.model.Sex
import com.m3.core.model.TrainingLocation
import com.m3.core.model.TrainingSetup
import com.m3.core.model.Units
import com.m3.core.training.ExerciseCatalog
import com.m3.core.training.LoggedSet
import com.m3.core.training.PastPerformance
import com.m3.core.training.PlanGenerator
import com.m3.core.training.Progression
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * Debug-only. The Coach prompt eval's cases, made-up facts and checks, shared by the instrumented test
 * (CoachPromptEval, emulator) and EvalActivity (phone). Nothing here reads the owner's data.
 */

/** [planLocked]: the owner locked "Your plan"; the model should still write the tool line (the app blocks it). */
data class EvalCase(
    val message: String,
    val expectTool: String?,
    val planLocked: Boolean = false,
    /** Text the reply must contain, e.g. the app's next weight ("{NEXT_KG}" is filled in from the facts). */
    val expectText: String? = null,
)

/** The result of checking one raw reply against its case. */
data class EvalOutcome(
    val ok: Boolean,
    /** Simple class name of the validated tool action, or null if there was none. */
    val toolName: String?,
    /** The reply says a change was made ("done"), whether or not a tool backs it up. */
    val claim: Boolean,
    val invalid: String?,
    /** The reply text without the tool line. */
    val text: String,
)

object EvalCases {
    val all: List<EvalCase> = listOf(
        EvalCase("Today I only have dumbbells and a pull-up bar.", "day_equipment"),
        EvalCase("My left knee hurts when I do walking lunges.", "avoid_exercise"),
        EvalCase("Why is Wednesday a rest day?", null),
        EvalCase("Switch the app to dark mode please.", "theme"),
        EvalCase("I can't train on Thursday any more.", "set_day"),
        EvalCase("How many sets of bench press do I do on Monday?", null),
        EvalCase("Hide the habits card.", "card"),
        EvalCase("What should I eat after training?", null),
        EvalCase("Can you make my plan harder?", null),
        EvalCase("Tomorrow I'm stuck in a hotel room with no equipment.", "day_equipment"),
        // Seen on the phone (2026-10-21): no tool line, and the reply claimed the change was done.
        EvalCase("Please suggest the dark theme", "theme"),
        EvalCase("On Saturday I have no equipment, only body weight", "day_equipment", planLocked = true),
        // Phase 1c-3: weights must come from the app's progression, never the model.
        EvalCase("What weight should I use for my first exercise today?", null, expectText = "{NEXT_KG}"),
        EvalCase("How many calories do I have left today?", null, expectText = "810"),
        EvalCase("How much protein have I eaten today?", null, expectText = "112"),
        EvalCase("How much more protein do I need today?", null, expectText = "58"),
        EvalCase("How many steps have I done today?", null, expectText = "6214"),
        EvalCase("Is my weight going down?", null, expectText = "0.4"),
        EvalCase("I slept badly and I'm sore. Should I train hard today?", null),
    )
}

/** Made-up facts and the system prompts built from them. A fixed Monday so results don't depend on the date. */
class EvalFixture private constructor(
    val today: LocalDate,
    val setup: TrainingSetup,
    val catalog: ExerciseCatalog,
    val system: String,
    val lockedSystem: String,
    /** The app's next weight for the first exercise, as the Coach should quote it; null if it has no kg target. */
    val nextKg: String?,
    /** For logging: the kind and weight of that first target. */
    val firstTarget: String,
) {
    fun systemFor(case: EvalCase): String = if (case.planLocked) lockedSystem else system

    /** The prompt the Coach would send for [case]. */
    fun promptFor(case: EvalCase): String = CoachPrompt.turn(emptyList(), case.message)

    fun check(case: EvalCase, rawText: String): EvalOutcome {
        val parsed = ReplyParser.parse(rawText)
        val tool = parsed.toolJson?.let { CoachTools.validate(it, ToolContext(today, setup, catalog)) }
            ?.takeUnless { it == ToolResult.Ignored || (it is ToolResult.Valid && !CoachTools.relevantTo(it.action, case.message)) }
        val toolName = (tool as? ToolResult.Valid)?.action?.let { it::class.simpleName }
        val toolOk = when (case.expectTool) {
            null -> tool == null
            else -> tool is ToolResult.Valid && parsed.toolJson.orEmpty().contains("\"${case.expectTool}\"")
        }
        val wanted = case.expectText?.replace("{NEXT_KG}", nextKg ?: "no-kg-target")
        return EvalOutcome(
            ok = toolOk && (wanted == null || wanted in parsed.text),
            toolName = toolName,
            claim = CoachTools.claimsChange(parsed.text),
            invalid = (tool as? ToolResult.Invalid)?.message,
            text = parsed.text,
        )
    }

    companion object {
        fun build(): EvalFixture {
            val catalog = ExerciseCatalog.bundled()
            val gym = TrainingLocation("g", "Gym", EquipmentKind.entries.map { EquipmentItem.Catalog(it) })
            val home = TrainingLocation("h", "Home", listOf(EquipmentItem.Catalog(EquipmentKind.DUMBBELLS)))
            val setup = TrainingSetup(
                listOf(gym, home),
                mapOf(DayOfWeek.MONDAY to "g", DayOfWeek.TUESDAY to "g", DayOfWeek.THURSDAY to "g", DayOfWeek.SATURDAY to "h"),
            )
            val profile = Profile(1990, Sex.FEMALE, 170, 65.0, Goal.BUILD_MUSCLE, Experience.SOME, "")
            // A fixed training day (Monday, gym) so results don't depend on when the eval runs.
            val today = LocalDate.of(2026, 10, 19)
            val plan = PlanGenerator(catalog).generate(profile, setup)
            // Last Monday every set was at the top of the range, so the app's progression steps each load up.
            val progress = plan.sessionOn(DayOfWeek.MONDAY)!!.exercises.associate { e ->
                val last = PastPerformance(today.minusDays(7), List(e.prescription.sets) { LoggedSet(40.0, reps = e.prescription.max, seconds = e.prescription.max) })
                progressKey(DayOfWeek.MONDAY, Progression.variantKey(e.exercise.id, e.equipment)) to
                    ExerciseProgress(last.date, last.sets, Progression.next(e, listOf(last)))
            }
            val first = plan.sessionOn(DayOfWeek.MONDAY)!!.exercises.first()
            val firstNext = progress.getValue(progressKey(DayOfWeek.MONDAY, Progression.variantKey(first.exercise.id, first.equipment))).next
            val nextKg = firstNext.kg?.let { Units.number(it) }
            val facts = CoachFacts(
                today = today,
                profile = profile,
                setup = setup,
                plan = plan,
                progress = progress,
                workoutsLast28Days = 4,
                lastWorkout = today.minusDays(3),
                daily = DailyFacts(
                    targets = NutritionTargets(2_450, 170, 280, 75, 1_780, 2_448, false),
                    eaten = DayTotals(1_640, 112, 180, 48),
                    foods = listOf("Greek yogurt with oats", "Chicken rice bowl", "Whey shake"),
                    steps = 6_214,
                    trendKg = 78.4,
                    weeklyChangeKg = -0.4,
                    checkIn = CheckIn(today, sleep = 2, energy = 3, soreness = 4),
                ),
                avoided = emptyMap(),
                dayEquipment = emptyMap(),
                themeMode = ThemeMode.LIGHT,
                cards = UiConfig().todayCards,
                locks = emptySet(),
            )
            return EvalFixture(
                today = today,
                setup = setup,
                catalog = catalog,
                system = CoachPrompt.system(facts),
                lockedSystem = CoachPrompt.system(facts.copy(locks = setOf(LockKey.TRAINING_PLAN))),
                nextKg = nextKg,
                firstTarget = "${firstNext.kind} $nextKg",
            )
        }
    }
}
