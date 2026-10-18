package com.m3.coach

import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.UiChange
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
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
    fun `system prompt carries the facts, tools and locks`() {
        val system = CoachPrompt.system(facts())
        listOf("Sunday 18 October 2026", "181 cm", "82.5 kg", "left knee", "[bench_press]", "Front squat [front_squat] (knee)",
            "own items: Yoke", "\"day_equipment\"", "free days", "PULL_UP_BAR", "(today)").forEach {
            assertTrue("missing $it", it in system)
        }
    }

    @Test
    fun `system prompt plus a full history fits the context window`() {
        val history = (1..40).map { ChatTurn(if (it % 2 == 0) ChatRole.COACH else ChatRole.OWNER, "message number $it ".repeat(12)) }
        val turn = CoachPrompt.turn(history, "Can I swap squats today?")
        val totalChars = CoachPrompt.system(facts()).length + turn.length
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
