package com.m3.app.feature.coach

import com.m3.app.data.PlanRepository
import com.m3.coach.CoachAction
import com.m3.core.data.StoredOverrides
import com.m3.core.designsystem.config.CardType
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.UiChange
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.model.EquipmentKind
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachControllerTest {
    @Test
    fun `streamed text hides a tool block while it is being written`() {
        assertEquals("Here you go.", CoachController.visibleSoFar("Here you go.\n<tool>{\"name\":\"th"))
        assertEquals("Here you", CoachController.visibleSoFar("Here you"))
        assertEquals("Done", CoachController.visibleSoFar("Done <to"))
    }

    @Test
    fun `ui inverses restore the state before the change`() {
        val config = UiConfig()
        assertEquals(UiChange.SetThemeMode(ThemeMode.LIGHT), CoachController.inverseOf(UiChange.SetThemeMode(ThemeMode.DARK), config))
        assertEquals(UiChange.SetCardVisible(CardType.HABITS, true), CoachController.inverseOf(UiChange.SetCardVisible(CardType.HABITS, false), config))
        assertEquals(UiChange.SetCardVisible(CardType.WEIGHT_TREND, false), CoachController.inverseOf(UiChange.AddCard(CardType.WEIGHT_TREND), config))
        val order = CoachController.inverseOf(UiChange.SetCardOrder(listOf(CardType.HABITS)), config) as UiChange.SetCardOrder
        assertEquals(config.todayCards.map { it.type }, order.types)
        val moved = UiChange.SetCardOrder(listOf(CardType.HABITS)).applyTo(config)
        assertEquals(config.todayCards, order.applyTo(moved).todayCards)
    }

    @Test
    fun `summaries describe the proposal in plain words`() {
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(
            "Only Dumbbells, Pull-up bar on Monday 19 October (that day only)",
            CoachController.summary(CoachAction.SetDayEquipment(monday, setOf(EquipmentKind.PULL_UP_BAR, EquipmentKind.DUMBBELLS))),
        )
        assertEquals("Bodyweight only on Monday 19 October (that day only)", CoachController.summary(CoachAction.SetDayEquipment(monday, emptySet())))
        assertEquals("Friday: not free", CoachController.summary(CoachAction.SetAvailability(DayOfWeek.FRIDAY, null, null)))
        assertEquals("Stop using Plank (sore back) until you allow it again", CoachController.summary(CoachAction.AvoidExercise("plank", "Plank", "sore back")))
        assertEquals(
            "Stop using all lunges (knee pain) until you allow them again",
            CoachController.summary(CoachAction.AvoidExercise("walking_lunge", "Walking lunge", "knee pain", listOf("reverse_lunge"), "lunges")),
        )
        assertEquals("""{"name":"theme","mode":"DARK"}""", CoachController.themeJson(ThemeMode.DARK))
        assertEquals("Hide the Habits card on Today", CoachController.summary(CoachAction.Ui(UiChange.SetCardVisible(CardType.HABITS, false))))
    }

    @Test
    fun `day overrides only count from today to Sunday of this week`() {
        val wednesday = LocalDate.of(2026, 10, 14)
        val stored = StoredOverrides(
            dayEquipment = mapOf(
                wednesday.minusDays(1) to setOf(EquipmentKind.BARBELL),
                wednesday to setOf(EquipmentKind.DUMBBELLS),
                wednesday.plusDays(4) to emptySet(),
                wednesday.plusDays(5) to emptySet(),
            ),
            excluded = mapOf("plank" to "x"),
        )
        val overrides = PlanRepository.forThisWeek(stored, wednesday)
        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), overrides.dayInventory.keys)
        assertTrue("plank" in overrides.excludedExercises)
    }
}
