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

class UiConfigStoreRestoreTest {
    @Test
    fun restoreReplacesConfigAndHistoryAndSnapshotTracksChanges() {
        val store = UiConfigStore(clock = { 7L })
        val dark = UiConfig(theme = UiConfig().theme.copy(mode = ThemeMode.DARK))
        val entry = UiHistoryEntry(Actor.Owner, "switched to dark mode", UiConfig(), 1L)
        store.restore(UiConfigSnapshot(dark, listOf(entry)))
        assertEquals(dark, store.config.value)
        assertEquals(listOf(entry), store.history.value)
        assertEquals(UiConfigSnapshot(dark, listOf(entry)), store.snapshot.value)

        store.setLock(LockKey.THEME, true)
        assertEquals(setOf(LockKey.THEME), store.snapshot.value.config.locks)
        assertTrue(store.undo())
        assertEquals(ThemeMode.LIGHT, store.snapshot.value.config.theme.mode)
        assertTrue(store.snapshot.value.history.isEmpty())
    }

    @Test
    fun invalidSavedConfigFallsBackToDefaultButKeepsLocks() {
        val store = UiConfigStore()
        val broken = UiConfig(theme = UiConfig().theme.copy(typeScale = 9f), locks = setOf(LockKey.TABS))
        val badEntry = UiHistoryEntry(Actor.Owner, "x", broken, 1L)
        store.restore(UiConfigSnapshot(broken, listOf(badEntry)))
        assertEquals(UiConfig(locks = setOf(LockKey.TABS)), store.config.value)
        assertTrue(store.history.value.isEmpty())
    }

    @Test
    fun restoreAddsNewCardsAfterTheCardTheyFollowAndKeepsTheOwnersChoices() {
        val saved = UiConfig(
            todayCards = listOf(
                CardPlacement(CardType.COACH_NOTE),
                CardPlacement(CardType.TODAY_WORKOUT, visible = false),
                CardPlacement(CardType.CHECK_IN),
                CardPlacement(CardType.FUEL_SUMMARY),
                CardPlacement(CardType.HABITS),
            ),
        )
        val store = UiConfigStore()
        store.restore(UiConfigSnapshot(saved, emptyList()))
        assertEquals(
            listOf(CardType.COACH_NOTE, CardType.TODAY_WORKOUT, CardType.STEPS, CardType.CHECK_IN, CardType.FUEL_SUMMARY, CardType.HABITS),
            store.config.value.todayCards.map { it.type },
        )
        assertFalse(store.config.value.todayCards.first { it.type == CardType.TODAY_WORKOUT }.visible)
    }

    @Test
    fun restoreCapsHistory() {
        val store = UiConfigStore()
        val many = (1..150).map { UiHistoryEntry(Actor.Owner, "change $it", UiConfig(), it.toLong()) }
        store.restore(UiConfigSnapshot(UiConfig(), many))
        assertEquals(UiConfigStore.MAX_HISTORY, store.history.value.size)
        assertEquals("change 150", store.history.value.last().description)
    }
}
