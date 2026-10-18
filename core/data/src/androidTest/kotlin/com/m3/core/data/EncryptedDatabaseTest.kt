package com.m3.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.designsystem.config.Actor
import com.m3.core.designsystem.config.ThemeMode
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.config.UiConfigSnapshot
import com.m3.core.designsystem.config.UiHistoryEntry
import com.m3.core.model.EquipmentDetails
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.model.Profile
import com.m3.core.model.Sex
import com.m3.core.model.TrainingLocation
import com.m3.core.model.TrainingSetup
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator only (never the owner's phone). Uses the real SQLCipher + Room stack with a test key. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = ByteArray(32) { it.toByte() }
    private val openers = mutableListOf<DatabaseOpener>()

    private fun opener(passphrase: ByteArray = key.copyOf()) =
        DatabaseOpener(context, { passphrase }).also(openers::add)

    private suspend fun closeAll() {
        openers.forEach { runCatching { it.get().close() } }
        openers.clear()
    }

    private val profile = Profile(1994, Sex.MALE, 181, 82.5, Goal.BUILD_MUSCLE, Experience.SOME, "left knee")
    private val setup = TrainingSetup(
        locations = listOf(
            TrainingLocation(
                "g",
                "Gym",
                listOf(
                    EquipmentItem.Catalog(EquipmentKind.BARBELL),
                    EquipmentItem.Catalog(EquipmentKind.DUMBBELLS, EquipmentDetails(listOf(2.0, 4.0))),
                ),
            ),
            TrainingLocation("h", "Home", listOf(EquipmentItem.Custom("Backpack"))),
        ),
        schedule = mapOf(DayOfWeek.MONDAY to "g", DayOfWeek.SATURDAY to "h"),
    )

    @Before
    fun clean() {
        context.deleteDatabase(DatabaseOpener.DATABASE_NAME)
    }

    @After
    fun tearDown() = runTest {
        closeAll()
        context.deleteDatabase(DatabaseOpener.DATABASE_NAME)
    }

    @Test
    fun fileIsEncryptedAndNeedsTheRightKey() = runTest {
        ProfileRepository(opener(), currentYear = { 2026 }).save(profile)
        closeAll()

        val file = context.getDatabasePath(DatabaseOpener.DATABASE_NAME)
        val header = file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        assertFalse("plain SQLite header found", String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("left knee"))

        try {
            opener(ByteArray(32) { 7 }).get()
            fail("opened with the wrong key")
        } catch (_: Exception) {
            // expected
        }
        closeAll()

        assertEquals(profile, ProfileRepository(opener(), currentYear = { 2026 }).observe().first())
    }

    @Test
    fun uiConfigSnapshotSurvivesReopen() = runTest {
        val dark = UiConfig(theme = UiConfig().theme.copy(mode = ThemeMode.DARK))
        val snapshot = UiConfigSnapshot(dark, listOf(UiHistoryEntry(Actor.Owner, "switched to dark mode", UiConfig(), 5L)))
        val repo = UiConfigRepository(opener(), clock = { 1L })
        assertNull(repo.load())
        repo.save(snapshot)
        repo.save(snapshot.copy(history = emptyList()))
        repo.save(snapshot)
        closeAll()
        assertEquals(snapshot, UiConfigRepository(opener()).load())
    }

    @Test
    fun onboardingIsSavedInOneGoAndSetupCanBeReplaced() = runTest {
        val opener = opener()
        val profiles = ProfileRepository(opener, currentYear = { 2026 })
        val setups = TrainingSetupRepository(opener)
        assertFalse(profiles.isOnboardingCompleted())

        profiles.completeOnboarding(profile, setup)
        assertTrue(profiles.isOnboardingCompleted())
        assertEquals(profile, profiles.observe().first())
        val saved = setups.observe().first()
        assertEquals(setup.locations, saved.locations)
        assertEquals("g", saved.schedule[DayOfWeek.MONDAY])

        val homeOnly = TrainingSetup(listOf(setup.locations[1]), mapOf(DayOfWeek.SUNDAY to "h"))
        setups.replace(homeOnly)
        val replaced = setups.observe().first()
        assertEquals(homeOnly.locations, replaced.locations)
        assertNull(replaced.schedule[DayOfWeek.MONDAY])
        assertEquals("h", replaced.schedule[DayOfWeek.SUNDAY])
    }

    @Test
    fun invalidDataIsRejectedAndNothingIsWritten() = runTest {
        val opener = opener()
        val profiles = ProfileRepository(opener, currentYear = { 2026 })
        try {
            profiles.completeOnboarding(profile.copy(heightCm = 20), setup)
            fail("invalid profile accepted")
        } catch (_: InvalidDataException) {
            // expected
        }
        assertFalse(profiles.isOnboardingCompleted())
        assertNull(profiles.observe().first())
        assertTrue(TrainingSetupRepository(opener).observe().first().locations.isEmpty())
    }
}
