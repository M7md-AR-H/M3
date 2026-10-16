package com.m3.core.designsystem.config

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiConfigStoreTest {
    private val coach = Actor.Agent(Specialist.COACH)
    private val trainer = Actor.Agent(Specialist.TRAINER)

    private fun store() = UiConfigStore(clock = { 42L })

    @Test
    fun ownerChangeIsAppliedAndRecorded() = runTest {
        val store = store()
        store.config.test {
            assertEquals(ThemeMode.LIGHT, awaitItem().theme.mode)
            val result = store.apply(UiChange.SetThemeMode(ThemeMode.DARK), Actor.Owner)
            assertTrue(result is ApplyResult.Applied)
            assertEquals(ThemeMode.DARK, awaitItem().theme.mode)
        }
        val entry = store.history.value.single()
        assertEquals(Actor.Owner, entry.actor)
        assertEquals("switched to dark mode", entry.description)
        assertEquals(ThemeMode.LIGHT, entry.previous.theme.mode)
        assertEquals(42L, entry.timestampMillis)
    }

    @Test
    fun agentChangeIsBlockedByLock() {
        val store = store()
        store.setLock(LockKey.TODAY_LAYOUT, true)
        val result = store.apply(UiChange.SetCardOrder(listOf(CardType.FUEL_SUMMARY)), coach)
        assertEquals(ApplyResult.Rejected(RejectReason.Denied), result)
        assertEquals(CardType.CHECK_IN, store.config.value.todayCards.first().type)
    }

    @Test
    fun ownerIgnoresLocks() {
        val store = store()
        store.setLock(LockKey.THEME, true)
        val result = store.apply(UiChange.SetThemeMode(ThemeMode.DARK), Actor.Owner)
        assertTrue(result is ApplyResult.Applied)
    }

    @Test
    fun trainerCannotChangeTheme() {
        val result = store().apply(UiChange.SetThemeMode(ThemeMode.DARK), trainer)
        assertEquals(ApplyResult.Rejected(RejectReason.Denied), result)
    }

    @Test
    fun trainerCanHideOwnCardButNotOthers() {
        val store = store()
        val own = store.apply(UiChange.SetCardVisible(CardType.TODAY_WORKOUT, false), trainer)
        val other = store.apply(UiChange.SetCardVisible(CardType.FUEL_SUMMARY, false), trainer)
        assertTrue(own is ApplyResult.Applied)
        assertEquals(ApplyResult.Rejected(RejectReason.Denied), other)
        val cards = store.config.value.todayCards.associate { it.type to it.visible }
        assertEquals(false, cards[CardType.TODAY_WORKOUT])
        assertEquals(true, cards[CardType.FUEL_SUMMARY])
    }

    @Test
    fun invalidChangeLeavesConfigUnchanged() {
        val store = store()
        val before = store.config.value
        val result = store.apply(UiChange.SetTypeScale(2.0f), Actor.Owner)
        assertTrue(result is ApplyResult.Rejected && result.reason is RejectReason.Invalid)
        assertEquals(before, store.config.value)
        assertTrue(store.history.value.isEmpty())
    }

    @Test
    fun undoRestoresPreviousConfig() {
        val store = store()
        val before = store.config.value
        store.apply(UiChange.SetCornerRadius(12), Actor.Owner)
        assertTrue(store.undo())
        assertEquals(before, store.config.value)
        assertTrue(store.history.value.isEmpty())
        assertFalse(store.undo())
    }

    @Test
    fun agentCannotResetToDefault() {
        val store = store()
        store.apply(UiChange.SetCornerRadius(12), Actor.Owner)
        val result = store.apply(UiChange.ResetToDefault, coach)
        assertEquals(ApplyResult.Rejected(RejectReason.Denied), result)
        assertEquals(12, store.config.value.theme.cornerRadiusDp)
    }

    @Test
    fun ownerCanResetToDefault() {
        val store = store()
        store.apply(UiChange.SetCornerRadius(12), Actor.Owner)
        val result = store.apply(UiChange.ResetToDefault, Actor.Owner)
        assertTrue(result is ApplyResult.Applied)
        assertEquals(UiConfig(), store.config.value)
    }

    @Test
    fun historyIsCappedAt100() {
        val store = store()
        repeat(120) { index ->
            store.apply(UiChange.SetCornerRadius(index % 2), Actor.Owner)
        }
        assertEquals(100, store.history.value.size)
    }
}
