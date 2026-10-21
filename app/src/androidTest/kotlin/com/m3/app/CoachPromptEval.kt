package com.m3.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3.ai.engine.LiteRtLmEngine
import com.m3.ai.engine.LlmBackend
import com.m3.ai.engine.LlmLoadConfig
import com.m3.coach.CoachFacts
import com.m3.coach.CoachPrompt
import com.m3.coach.CoachTools
import com.m3.coach.ExerciseProgress
import com.m3.coach.ReplyParser
import com.m3.coach.ToolContext
import com.m3.coach.ToolResult
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
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
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt eval for the Coach (docs/WORKING_RULES.md: prompt wording is decided on measurements).
 * Emulator only, with a copy of the public model file at /data/local/tmp/m3-eval/model.litertlm.
 * Uses made-up test data only; results go to logcat under tag M3Eval (fixed prompts, no personal data).
 * Skipped when the model file isn't there, so normal test runs never need it.
 */
@RunWith(AndroidJUnit4::class)
class CoachPromptEval {
    /** [planLocked]: the owner locked "Your plan"; the model should still write the tool line (the app blocks it). */
    private data class Case(
        val message: String,
        val expectTool: String?,
        val planLocked: Boolean = false,
        /** Text the reply must contain, e.g. the app's next weight ("{NEXT_KG}" is filled in from the facts). */
        val expectText: String? = null,
    )

    private val cases = listOf(
        Case("Today I only have dumbbells and a pull-up bar.", "day_equipment"),
        Case("My left knee hurts when I do walking lunges.", "avoid_exercise"),
        Case("Why is Wednesday a rest day?", null),
        Case("Switch the app to dark mode please.", "theme"),
        Case("I can't train on Thursday any more.", "set_day"),
        Case("How many sets of bench press do I do on Monday?", null),
        Case("Hide the habits card.", "card"),
        Case("What should I eat after training?", null),
        Case("Can you make my plan harder?", null),
        Case("Tomorrow I'm stuck in a hotel room with no equipment.", "day_equipment"),
        // Seen on the phone (2026-10-21): no tool line, and the reply claimed the change was done.
        Case("Please suggest the dark theme", "theme"),
        Case("On Saturday I have no equipment, only body weight", "day_equipment", planLocked = true),
        // Phase 1c-3: weights must come from the app's progression, never the model.
        Case("What weight should I use for my first exercise today?", null, expectText = "{NEXT_KG}"),
    )

    @Test
    fun run(): Unit = runBlocking {
        val model = File("/data/local/tmp/m3-eval/model.litertlm")
        assumeTrue("no eval model", model.canRead())
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
            Progression.variantKey(e.exercise.id, e.equipment) to ExerciseProgress(last.date, last.sets, Progression.next(e, listOf(last)))
        }
        val firstNext = progress.getValue(plan.sessionOn(DayOfWeek.MONDAY)!!.exercises.first().let { Progression.variantKey(it.exercise.id, it.equipment) }).next
        val nextKg = firstNext.kg?.let { Units.number(it) }
        Log.i(TAG, "first exercise next target: ${firstNext.kind} $nextKg")
        val facts = CoachFacts(
            today = today,
            profile = profile,
            setup = setup,
            plan = plan,
            progress = progress,
            workoutsLast28Days = 4,
            lastWorkout = today.minusDays(3),
            avoided = emptyMap(),
            dayEquipment = emptyMap(),
            themeMode = ThemeMode.LIGHT,
            cards = UiConfig().todayCards,
            locks = emptySet(),
        )
        val system = CoachPrompt.system(facts)
        val lockedSystem = CoachPrompt.system(facts.copy(locks = setOf(LockKey.TRAINING_PLAN)))
        Log.i(TAG, "system prompt chars=${system.length}")
        val cache = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "llm-eval").apply { mkdirs() }
        LiteRtLmEngine().use { engine ->
            val loadSeconds = engine.load(LlmLoadConfig(model, LlmBackend.CPU, speculativeDecoding = false, maxTokens = 4096, cacheDir = cache))
            Log.i(TAG, "loaded in ${"%.1f".format(loadSeconds)} s")
            var passed = 0
            var claims = 0
            cases.forEachIndexed { index, case ->
                val result = engine.generate(
                    CoachPrompt.turn(emptyList(), case.message),
                    systemInstruction = if (case.planLocked) lockedSystem else system,
                    maxOutputTokens = 400,
                )
                val parsed = ReplyParser.parse(result.text)
                val tool = parsed.toolJson?.let { CoachTools.validate(it, ToolContext(today, setup, catalog)) }
                    ?.takeUnless { it == ToolResult.Ignored || (it is ToolResult.Valid && !CoachTools.relevantTo(it.action, case.message)) }
                val toolName = (tool as? ToolResult.Valid)?.action?.let { it::class.simpleName }
                val toolOk = when (case.expectTool) {
                    null -> tool == null
                    else -> tool is ToolResult.Valid && parsed.toolJson.orEmpty().contains("\"${case.expectTool}\"")
                }
                val wanted = case.expectText?.replace("{NEXT_KG}", nextKg ?: "no-kg-target")
                val ok = toolOk && (wanted == null || wanted in parsed.text)
                if (ok) passed++
                val claim = CoachTools.claimsChange(parsed.text)
                if (claim) claims++
                Log.i(TAG, "CASE ${index + 1} ${if (ok) "PASS" else "FAIL"} expect=${case.expectTool} got=$toolName claim=$claim " +
                    "invalid=${(tool as? ToolResult.Invalid)?.message} tps=${result.stats?.decodeTokensPerSecond}")
                Log.i(TAG, "  Q: ${case.message}")
                result.text.lines().forEach { Log.i(TAG, "  A: $it") }
            }
            Log.i(TAG, "SCORE $passed/${cases.size} claims=$claims")
        }
    }

    private companion object {
        const val TAG = "M3Eval"
    }
}
