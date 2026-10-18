package com.m3.core.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.M3Database
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator only. The owner's phone has a v1 database; this proves v2 opens it and keeps the data. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = ByteArray(32) { (it * 3).toByte() }

    @get:Rule
    val helper: MigrationTestHelper = run {
        DatabaseOpener.loadSqlCipher()
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            M3Database::class.java,
            emptyList(),
            SupportOpenHelperFactory(key.copyOf()),
        )
    }

    @Before
    @After
    fun clean() {
        context.deleteDatabase(DatabaseOpener.DATABASE_NAME)
    }

    @Test
    fun encryptedV1WithDataOpensAsV2() = runTest {
        helper.createDatabase(DatabaseOpener.DATABASE_NAME, 1).apply {
            execSQL(
                "INSERT INTO profile (id, birthYear, sex, heightCm, weightKg, goal, experience, injuriesText, createdAt, updatedAt) " +
                    "VALUES (1, 1994, 'MALE', 181, 82.5, 'BUILD_MUSCLE', 'SOME', 'knee', 1, 1)",
            )
            execSQL("INSERT INTO training_location (id, name, sortOrder) VALUES ('g', 'Gym', 0)")
            execSQL("INSERT INTO schedule_day (dayOfWeek, locationId) VALUES (1, 'g')")
            execSQL("INSERT INTO app_state (id, onboardingCompleted, schemaVersion) VALUES (1, 1, 1)")
            close()
        }

        val opener = DatabaseOpener(context, { key.copyOf() })
        val profiles = ProfileRepository(opener, currentYear = { 2026 })
        assertTrue(profiles.isOnboardingCompleted())
        assertEquals(181, profiles.observe().first()!!.heightCm)
        assertEquals("g", TrainingSetupRepository(opener).observe().first().availability[DayOfWeek.MONDAY])

        // New v2 tables work.
        val chat = ChatRepository(opener)
        chat.add(ChatAuthor.OWNER, "hello")
        assertEquals("hello", chat.observe().first().single().text)
        val overrides = PlanOverridesRepository(opener)
        overrides.setDayEquipment(LocalDate.of(2026, 10, 19), emptySet())
        overrides.setExcluded("back_squat", "knee")
        val stored = overrides.observe().first()
        assertEquals(emptySet<Any>(), stored.dayEquipment[LocalDate.of(2026, 10, 19)])
        assertEquals("knee", stored.excluded["back_squat"])
        opener.get().close()
    }
}
