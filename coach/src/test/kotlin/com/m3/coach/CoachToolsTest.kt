package com.m3.coach

import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.UiChange
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
import com.m3.core.training.ExerciseCatalog
import com.m3.core.training.PlanGenerator
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachToolsTest {
    private val catalog = ExerciseCatalog.bundled()
    private val gym = TrainingLocation("g", "Gym", listOf(EquipmentItem.Catalog(EquipmentKind.BARBELL)))
    private val setup = TrainingSetup(listOf(gym), mapOf(DayOfWeek.MONDAY to "g"))

    /** Wednesday 14 October 2026, so the next 7 days run to Tuesday 20 October. */
    private val wednesday = LocalDate.of(2026, 10, 14)
    private val context = ToolContext(wednesday, setup, catalog)

    private fun valid(json: String) = (CoachTools.validate(json, context) as ToolResult.Valid).action
    private fun invalid(json: String) = CoachTools.validate(json, context).let { it is ToolResult.Invalid || it == ToolResult.Ignored }

    @Test
    fun `day equipment accepts names, display names, synonyms and bodyweight`() {
        val action = valid("""{"name":"day_equipment","date":"today","items":["DUMBBELLS","pull-up bar","bands","bodyweight"]}""")
        assertEquals(
            CoachAction.SetDayEquipment(wednesday, setOf(EquipmentKind.DUMBBELLS, EquipmentKind.PULL_UP_BAR, EquipmentKind.RESISTANCE_BANDS)),
            action,
        )
        assertEquals(CoachAction.SetDayEquipment(wednesday.plusDays(1), emptySet()), valid("""{"name":"day_equipment","date":"tomorrow","items":[]}"""))
        assertEquals(EquipmentKind.KETTLEBELLS, CoachTools.equipmentKind("kettlebell"))
        assertEquals(EquipmentKind.SQUAT_RACK, CoachTools.equipmentKind("Power rack"))
    }

    @Test
    fun `day equipment rejects unknown gear and days outside this week`() {
        assertTrue(invalid("""{"name":"day_equipment","date":"today","items":["hoverboard"]}"""))
        assertTrue(invalid("""{"name":"day_equipment","date":"2026-10-13","items":[]}"""))
        assertTrue(invalid("""{"name":"day_equipment","date":"2026-10-21","items":[]}"""))
        assertTrue(invalid("""{"name":"day_equipment","items":[]}"""))
    }

    @Test
    fun `dates resolve within today to Sunday`() {
        assertEquals(LocalDate.of(2026, 10, 16), CoachTools.parseDate("friday", wednesday))
        assertEquals(wednesday, CoachTools.parseDate("Wednesday", wednesday))
        assertEquals(LocalDate.of(2026, 10, 19), CoachTools.parseDate("monday", wednesday))
        assertNull(CoachTools.parseDate("2026-10-21", wednesday))
        assertEquals(LocalDate.of(2026, 10, 18), CoachTools.parseDate("2026-10-18", wednesday))
    }

    @Test
    fun `exercises are matched by id or name`() {
        assertEquals("back_squat", (valid("""{"name":"avoid_exercise","exercise":"back_squat","reason":"knee"}""") as CoachAction.AvoidExercise).exerciseId)
        assertEquals("back_squat", (valid("""{"name":"allow_exercise","exercise":"Back squat"}""") as CoachAction.AllowExercise).exerciseId)
        assertTrue(invalid("""{"name":"avoid_exercise","exercise":"moon_jump"}"""))
        val long = "x".repeat(500)
        assertEquals(CoachTools.MAX_REASON_CHARS, (valid("""{"name":"avoid_exercise","exercise":"plank","reason":"$long"}""") as CoachAction.AvoidExercise).reason.length)
    }

    @Test
    fun `pain avoids the whole movement family, a dislike only the exercise`() {
        val pain = valid("""{"name":"avoid_exercise","exercise":"walking_lunge","reason":"knee pain"}""") as CoachAction.AvoidExercise
        assertEquals("lunges", pain.familyName)
        assertTrue(listOf("reverse_lunge", "split_squat", "step_up", "bulgarian_split_squat").all { it in pain.similar })
        assertTrue("walking_lunge" !in pain.similar && pain.allIds.first() == "walking_lunge")
        val dislike = valid("""{"name":"avoid_exercise","exercise":"walking_lunge","reason":"boring"}""") as CoachAction.AvoidExercise
        assertTrue(dislike.similar.isEmpty() && dislike.familyName == null)
        // Isolation and core work is too mixed to treat as one family.
        val plank = valid("""{"name":"avoid_exercise","exercise":"plank","reason":"my back hurts"}""") as CoachAction.AvoidExercise
        assertTrue(plank.similar.isEmpty())
        // Whole words only.
        val reached = valid("""{"name":"avoid_exercise","exercise":"walking_lunge","reason":"reached failure, too hard"}""") as CoachAction.AvoidExercise
        assertTrue(reached.similar.isEmpty())
    }

    @Test
    fun `allowing one exercise of a family avoided for pain lifts the whole family`() {
        val lunges = catalog.exercises.filter { it.pattern == com.m3.core.training.MovementPattern.LUNGE }.map { it.id }
        val excluded = lunges.associateWith { "knee pain" } + ("plank" to "boring")
        val withExcluded = context.copy(excluded = excluded)
        val allow = (CoachTools.validate("""{"name":"allow_exercise","exercise":"reverse_lunge"}""", withExcluded) as ToolResult.Valid).action as CoachAction.AllowExercise
        assertEquals(lunges.toSet(), allow.allIds.toSet())
        assertEquals("lunges", allow.familyName)
        val single = (CoachTools.validate("""{"name":"allow_exercise","exercise":"plank"}""", withExcluded) as ToolResult.Valid).action as CoachAction.AllowExercise
        assertTrue(single.similar.isEmpty())
    }

    @Test
    fun `plain theme requests are recognised, questions about other things are not`() {
        assertEquals(ThemeMode.DARK, CoachTools.themeRequest("Switch to dark mode"))
        assertEquals(ThemeMode.DARK, CoachTools.themeRequest("Please suggest the dark theme"))
        assertEquals(ThemeMode.DARK, CoachTools.themeRequest("set the theme to dark please"))
        assertEquals(ThemeMode.LIGHT, CoachTools.themeRequest("Turn off dark mode"))
        assertEquals(ThemeMode.LIGHT, CoachTools.themeRequest("light mode"))
        assertEquals(ThemeMode.SYSTEM, CoachTools.themeRequest("Follow the system theme"))
        assertNull(CoachTools.themeRequest("Why is Wednesday a rest day?"))
        assertNull(CoachTools.themeRequest("It's dark outside, can I run?"))
        assertNull(CoachTools.themeRequest("Today I only have a light dumbbell"))
        assertEquals(ThemeMode.LIGHT, CoachTools.themeRequest("dark mode off"))
        assertEquals(ThemeMode.DARK, CoachTools.themeRequest("Switch to dark mode, I'm off to bed"))
        assertNull(CoachTools.themeRequest("I don't want light mode"))
        assertNull(CoachTools.themeRequest("How do I turn off dark mode?"))
        assertNull(CoachTools.themeRequest("I hate dark mode"))
    }

    @Test
    fun `claims that a change happened are caught`() {
        assertTrue(CoachTools.claimsChange("I have switched the app theme to dark mode for you."))
        assertTrue(CoachTools.claimsChange("I have already set the app theme to DARK mode for you."))
        assertTrue(CoachTools.claimsChange("The planner will automatically select bodyweight exercises."))
        assertTrue(CoachTools.claimsChange("Your plan has been updated."))
        assertTrue(!CoachTools.claimsChange("I suggest using only dumbbells on Saturday. Tap Apply to use it."))
        assertTrue(!CoachTools.claimsChange("Tuesday is a rest day so your muscles can recover."))
        assertTrue(!CoachTools.claimsChange("Your body will automatically adapt to the load."))
        assertTrue(CoachTools.claimsChange("Dark mode is now on."))
    }

    @Test
    fun `set day needs a real place or not free`() {
        assertEquals(CoachAction.SetAvailability(DayOfWeek.FRIDAY, "g", "Gym"), valid("""{"name":"set_day","day":"friday","place":"gym"}"""))
        assertEquals(CoachAction.SetAvailability(DayOfWeek.MONDAY, null, null), valid("""{"name":"set_day","day":"MONDAY","place":"not_free"}"""))
        assertTrue(invalid("""{"name":"set_day","day":"FRIDAY","place":"Moon base"}"""))
        assertTrue(invalid("""{"name":"set_day","day":"Funday","place":"Gym"}"""))
    }

    @Test
    fun `ui tools map to validated UiChanges with their locks`() {
        val theme = valid("""{"name":"theme","mode":"dark"}""") as CoachAction.Ui
        assertEquals(UiChange.SetThemeMode(ThemeMode.DARK), theme.change)
        assertEquals(LockKey.THEME, theme.lock)
        assertEquals(UiChange.SetCardVisible(CardType.HABITS, false), (valid("""{"name":"card","card":"HABITS","action":"hide"}""") as CoachAction.Ui).change)
        assertEquals(UiChange.SetCardOrder(listOf(CardType.FUEL_SUMMARY)), (valid("""{"name":"card","card":"Fuel","action":"top"}""") as CoachAction.Ui).change)
        assertTrue(invalid("""{"name":"theme","mode":"neon"}"""))
    }

    @Test
    fun `garbage and unknown tools are rejected`() {
        assertTrue(invalid("not json"))
        assertTrue(invalid("[1,2]"))
        assertTrue(invalid("""{"name":"delete_everything"}"""))
        assertTrue(invalid("""{"items":[]}"""))
    }
}

class ReplyParserTest {
    @Test
    fun `splits text and the first tool block`() {
        val parsed = ReplyParser.parse("Coach: Sure, here's a home version.\n<tool>{\"name\":\"theme\",\"mode\":\"DARK\"}</tool>\n<tool>{\"x\":1}</tool>")
        assertEquals("Sure, here's a home version.", parsed.text)
        assertEquals("{\"name\":\"theme\",\"mode\":\"DARK\"}", parsed.toolJson)
    }

    @Test
    fun `handles fences, a missing closing tag and plain answers`() {
        assertEquals("{\"a\":1}", ReplyParser.parse("Ok <tool>```json\n{\"a\":1}\n```</tool>").toolJson)
        assertEquals("{\"a\":1}", ReplyParser.parse("Ok <tool>{\"a\":1}").toolJson)
        assertEquals(ParsedReply("Rest well today.", null), ReplyParser.parse("Rest well today."))
    }
}

class CoachPromptTest {
    private val catalog = ExerciseCatalog.bundled()
    private val gym = TrainingLocation("g", "Gym", EquipmentKind.entries.map { EquipmentItem.Catalog(it) } + EquipmentItem.Custom("Yoke"))
    private val setup = TrainingSetup(listOf(gym), DayOfWeek.entries.associateWith { "g" })
    private val profile = Profile(1994, Sex.MALE, 181, 82.5, Goal.BUILD_MUSCLE, Experience.SOME, "left knee on deep squats")

    private fun facts() = CoachFacts(
        today = LocalDate.of(2026, 10, 18),
        profile = profile,
        setup = setup,
        plan = PlanGenerator(catalog).generate(profile, setup),
        avoided = mapOf("front_squat" to ("Front squat" to "knee")),
        dayEquipment = emptyMap(),
        themeMode = ThemeMode.LIGHT,
        cards = UiConfig().todayCards,
        locks = setOf(LockKey.AVAILABILITY),
    )

    @Test
    fun `the Coach sees how today is going`() {
        val daily = DailyFacts(
            targets = NutritionTargets(2_450, 170, 280, 75, 1_780, 2_448, false),
            eaten = DayTotals(2_600, 112, 180, 48),
            foods = listOf("Chicken rice"),
            steps = 6_214,
            trendKg = 78.44,
            weeklyChangeKg = -0.44,
            checkIn = CheckIn(LocalDate.of(2026, 10, 18), 4, 3, 2),
        )
        val system = CoachPrompt.system(facts().copy(daily = daily))
        listOf("2600 of 2450 kcal", "150 over", "protein 112g, 58g left", "Chicken rice", "Steps today: 6214, goal 8000",
            "Weight trend: 78.4 kg, -0.4 kg a week", "sleep 4, energy 3, soreness 2").forEach {
            assertTrue("missing $it", it in system)
        }
    }

    @Test
    fun `an empty day says so instead of inventing`() {
        val system = CoachPrompt.system(facts())
        assertTrue("Weight trend: no weigh-ins." in system)
        assertTrue("Check-in: not done." in system)
        assertTrue("Food today" !in system)
    }

    @Test
    fun `system prompt carries the facts, tools and locks`() {
        val system = CoachPrompt.system(facts())
        listOf("Sunday 18 October 2026", "181 cm", "82.5 kg", "left knee", "[bench_press]", "Front squat [front_squat] (knee)",
            "own items: Yoke", "\"day_equipment\"", "free days", "PULL_UP_BAR", "(today)").forEach {
            assertTrue("missing $it", it in system)
        }
    }

    @Test
    fun `logged history shows last time and the app's next target`() {
        val f = facts()
        val benchSession = f.plan.sessions.first { s -> s.exercises.any { it.exercise.id == "bench_press" } }
        val bench = benchSession.exercises.first { it.exercise.id == "bench_press" }
        val key = progressKey(benchSession.day, com.m3.core.training.Progression.variantKey(bench.exercise.id, bench.equipment))
        val progress = ExerciseProgress(
            LocalDate.of(2026, 10, 12),
            List(3) { com.m3.core.training.LoggedSet(60.0, reps = 10) },
            com.m3.core.training.Target(com.m3.core.training.TargetKind.INCREASE, 62.5, "top"),
        )
        val system = CoachPrompt.system(f.copy(progress = mapOf(key to progress), workoutsLast28Days = 3, lastWorkout = LocalDate.of(2026, 10, 16)))
        assertTrue("(last 60 kg x 10,10,10; next 62.5 kg up)" in system)
        assertTrue("Workouts in the last 4 weeks: 3; last on 16 Oct." in system)
    }

    @Test
    fun `a deload week is named in the plan header`() {
        val header = "This week's deload plan"
        assertTrue(header !in CoachPrompt.system(facts()))
        assertTrue(header in CoachPrompt.system(facts().copy(deloadThisWeek = true)))
    }

    @Test
    fun `system prompt plus a full history fits the context window`() {
        val history = (1..40).map { ChatTurn(if (it % 2 == 0) ChatRole.COACH else ChatRole.OWNER, "message number $it ".repeat(12)) }
        val turn = CoachPrompt.turn(history, "Can I swap squats today?")
        // Worst case: every exercise of the week has logged history.
        val all = facts().plan.sessions.flatMap { s -> s.exercises.map { s.day to it } }.associate { (day, e) ->
            progressKey(day, com.m3.core.training.Progression.variantKey(e.exercise.id, e.equipment)) to ExerciseProgress(
                LocalDate.of(2026, 10, 12),
                List(e.prescription.sets) { com.m3.core.training.LoggedSet(102.5, reps = 12) },
                com.m3.core.training.Target(com.m3.core.training.TargetKind.DECREASE, 92.5, "x"),
            )
        }
        val fullDay = DailyFacts(
            targets = NutritionTargets(2_450, 170, 280, 75, 1_780, 2_448, false),
            eaten = DayTotals(1_640, 112, 180, 48),
            foods = List(CoachPrompt.MAX_FOODS) { "a long meal name ".repeat(4) },
            steps = 6_214,
            trendKg = 78.4,
            weeklyChangeKg = -0.4,
            checkIn = CheckIn(LocalDate.of(2026, 10, 18), 4, 3, 2),
        )
        val worst = facts().copy(progress = all, workoutsLast28Days = 20, lastWorkout = LocalDate.of(2026, 10, 16), daily = fullDay)
        val systemChars = CoachPrompt.system(worst).length
        // Leave room for the owner's message (2,000 max) and some history inside the app's own budget.
        assertTrue("system prompt too long: $systemChars chars", systemChars + 2_000 + 1_000 <= CoachPrompt.PROMPT_CHARS)
        val deloadChars = CoachPrompt.system(worst.copy(deloadThisWeek = true)).length
        assertTrue("deload prompt too long: $deloadChars chars", deloadChars + 2_000 + 1_000 <= CoachPrompt.PROMPT_CHARS)
        val totalChars = systemChars + turn.length
        // ~4 characters per token; leave room for a 512-token answer in 4096.
        assertTrue("prompt too long: $totalChars chars", totalChars / 4 < 4096 - 512)
        assertTrue(turn.endsWith("Reply as the Coach to the owner's last message."))
        assertTrue("message number 40" in turn)
        assertTrue("message number 1 " !in turn)
    }
}

class RelevanceTest {
    private val context = ToolContext(LocalDate.of(2026, 10, 14), TrainingSetup.Empty, ExerciseCatalog.bundled())
    @Test
    fun `a plain change to the usual week is proposed by the app`() {
        val places = listOf("Gym", "Home")
        val home = """{"name":"set_day","day":"FRIDAY","place":"Home"}"""
        val notFree = """{"name":"set_day","day":"THURSDAY","place":"not_free"}"""
        assertEquals(home, CoachTools.scheduleRequest("Make Friday a free day at Home", places))
        assertEquals(home, CoachTools.scheduleRequest("I can train on Fridays at Home from now on", places))
        assertEquals(notFree, CoachTools.scheduleRequest("I can't train on Thursdays any more", places))
        assertEquals(notFree, CoachTools.scheduleRequest("I'm busy on Thursday, not free to train", places))
    }

    @Test
    fun `questions, one-offs and unclear messages stay with the model`() {
        val places = listOf("Gym", "Home")
        assertNull(CoachTools.scheduleRequest("Why is Friday a free day?", places))
        assertNull(CoachTools.scheduleRequest("I can train this Friday at Home", places))
        assertNull(CoachTools.scheduleRequest("Tomorrow I'm free at Home on Friday", places))
        assertNull(CoachTools.scheduleRequest("I can train on Friday", places))
        assertNull(CoachTools.scheduleRequest("I can train on Friday or Saturday at Home", places))
        assertNull(CoachTools.scheduleRequest("I can train on Friday at Gym or Home", places))
        assertNull(CoachTools.scheduleRequest("My knee hurts on Friday at Home", places))
    }

    @Test
    fun `avoiding an exercise needs the owner to have named it`() {
        val bench = CoachAction.AvoidExercise("bench_press", "Bench press", "soreness")
        val lunge = CoachAction.AvoidExercise("walking_lunge", "Walking lunge", "knee pain")
        assertTrue(!CoachTools.relevantTo(bench, "I slept badly and I'm sore. Should I train hard today?"))
        assertTrue(CoachTools.relevantTo(bench, "Bench press hurts my shoulder"))
        assertTrue(CoachTools.relevantTo(lunge, "My knee hurts on lunges"))
    }

    @Test
    fun `look changes need a message about the look, training changes always pass`() {
        val card = CoachAction.Ui(UiChange.SetCardOrder(listOf(CardType.HABITS)))
        val theme = CoachAction.Ui(UiChange.SetThemeMode(ThemeMode.DARK))
        assertTrue(!CoachTools.relevantTo(card, "Can you make my plan harder?"))
        assertTrue(CoachTools.relevantTo(card, "Hide the habits card"))
        assertTrue(!CoachTools.relevantTo(theme, "Why is Wednesday a rest day?"))
        val thursday = CoachAction.SetAvailability(java.time.DayOfWeek.THURSDAY, null, null)
        assertTrue(CoachTools.relevantTo(thursday, "I can't train on Thursdays any more"))
        assertTrue(!CoachTools.relevantTo(thursday, "Tomorrow I'm in a hotel"))
        assertEquals(ToolResult.Ignored, CoachTools.validate("""{"name":"no_tool_line"}""", context))
        assertTrue(CoachTools.relevantTo(theme, "Switch to dark mode"))
        assertTrue(CoachTools.relevantTo(CoachAction.AllowExercise("plank", "Plank"), "anything"))
    }
}
