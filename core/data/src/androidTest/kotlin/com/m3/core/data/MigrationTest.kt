package com.m3.core.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.M3Database
import com.m3.core.model.BodyPart
import com.m3.core.model.FoodEntry
import com.m3.core.model.Measurement
import com.m3.core.model.StepReading
import com.m3.core.model.WeightEntry
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

    /** v6 adds weight and measurements and keeps the food log. */
    @Test
    fun encryptedV5WithDataOpensAsV6AndLogsBody() = runTest {
        helper.createDatabase(DatabaseOpener.DATABASE_NAME, 5).apply {
            execSQL("INSERT INTO app_state (id, onboardingCompleted, schemaVersion) VALUES (1, 1, 5)")
            execSQL(
                "INSERT INTO food_entry (date, atMillis, name, kcal, proteinG, carbsG, fatG) " +
                    "VALUES ('2026-10-30', 1, 'Oats', 300, 12, 50, 6)",
            )
            close()
        }

        val opener = DatabaseOpener(context, { key.copyOf() })
        val day = LocalDate.of(2026, 10, 30)
        assertEquals("Oats", FoodRepository(opener).observeDay(day).first().single().name)

        val body = BodyRepository(opener)
        body.saveWeight(WeightEntry(day, 80.0))
        // A second weigh-in on the same day replaces the first.
        body.saveWeight(WeightEntry(day, 79.5))
        body.saveWeight(WeightEntry(day.minusDays(1), 80.5))
        assertEquals(listOf(80.5, 79.5), body.observeWeights().first().map { it.kg })

        body.saveMeasurement(Measurement(day, BodyPart.WAIST, 82.5))
        assertEquals(BodyPart.WAIST, body.observeMeasurements().first().single().part)
        opener.get().close()
    }

    /** The phone has a v4 database since 2026-10-30; v5 adds the food log and keeps steps. */
    @Test
    fun encryptedV4WithDataOpensAsV5AndLogsFood() = runTest {
        helper.createDatabase(DatabaseOpener.DATABASE_NAME, 4).apply {
            execSQL("INSERT INTO app_state (id, onboardingCompleted, schemaVersion) VALUES (1, 1, 4)")
            execSQL("INSERT INTO step_day (date, steps) VALUES ('2026-10-30', 4200)")
            close()
        }

        val opener = DatabaseOpener(context, { key.copyOf() })
        val day = LocalDate.of(2026, 10, 30)
        assertEquals(4_200, StepRepository(opener).observeDay(day).first())

        val food = FoodRepository(opener)
        food.save(FoodEntry(date = day, atMillis = 2, name = " Chicken rice ", kcal = 780, proteinG = 52, carbsG = 80, fatG = 20))
        food.save(FoodEntry(date = day, atMillis = 1, name = "Oats", kcal = 300, proteinG = 12, carbsG = 50, fatG = 6))
        food.save(FoodEntry(date = day.plusDays(1), atMillis = 3, name = "Tomorrow", kcal = 1, proteinG = 0, carbsG = 0, fatG = 0))
        val entries = food.observeDay(day).first()
        assertEquals(listOf("Oats", "Chicken rice"), entries.map { it.name })

        // Undo puts a removed entry back under its old id.
        food.delete(entries[0].id)
        assertEquals(1, food.observeDay(day).first().size)
        food.save(entries[0])
        assertEquals(entries, food.observeDay(day).first())
        opener.get().close()
    }

    /** The phone has a v3 database since 2026-10-21; v4 adds the step tables and keeps workouts. */
    @Test
    fun encryptedV3WithDataOpensAsV4AndCountsSteps() = runTest {
        helper.createDatabase(DatabaseOpener.DATABASE_NAME, 3).apply {
            execSQL("INSERT INTO app_state (id, onboardingCompleted, schemaVersion) VALUES (1, 1, 3)")
            execSQL(
                "INSERT INTO workout_session (date, sessionType, locationId, locationName, startedAt, endedAt) " +
                    "VALUES ('2026-10-19', 'FULL_BODY_A', 'g', 'Gym', 1, 2)",
            )
            close()
        }

        val opener = DatabaseOpener(context, { key.copyOf() })
        val steps = StepRepository(opener, zone = { java.time.ZoneOffset.UTC })
        val day = LocalDate.of(2026, 10, 30)
        val morning = day.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        steps.record(StepReading(40_000, morning))
        assertEquals(0, steps.observeDay(day).first())
        steps.record(StepReading(40_600, morning + 60_000))
        steps.record(StepReading(40_750, morning + 120_000))
        assertEquals(750, steps.observeDay(day).first())
        assertEquals(1, WorkoutRepository(opener).observeRecent().first().size)
        opener.get().close()
    }

    /** The phone has a v2 database since 2026-10-21; v3 adds the workout tables and keeps chat and overrides. */
    @Test
    fun encryptedV2WithDataOpensAsV3AndLogsWorkouts() = runTest {
        helper.createDatabase(DatabaseOpener.DATABASE_NAME, 2).apply {
            execSQL("INSERT INTO app_state (id, onboardingCompleted, schemaVersion) VALUES (1, 1, 2)")
            execSQL("INSERT INTO chat_message (role, text, createdAt) VALUES ('owner', 'hi', 1)")
            execSQL("INSERT INTO excluded_exercise (exerciseId, reason, createdAt) VALUES ('walking_lunge', 'knee pain', 1)")
            close()
        }

        val opener = DatabaseOpener(context, { key.copyOf() })
        assertEquals("hi", ChatRepository(opener).observe().first().single().text)
        assertEquals("knee pain", PlanOverridesRepository(opener).observe().first().excluded["walking_lunge"])

        var now = 1_000L
        val workouts = WorkoutRepository(opener, clock = { now++ })
        val monday = LocalDate.of(2026, 10, 19)
        val w = workouts.startOrResume(monday, "FULL_BODY_A", "g", "Gym")
        // Starting again resumes the same open workout.
        assertEquals(w.id, workouts.startOrResume(monday, "FULL_BODY_A", "g", "Gym").id)
        workouts.logSet(StoredSet(w.id, "back_squat", "back_squat@BARBELL", 1, 60.0, 10, null, null, 0))
        workouts.logSet(StoredSet(w.id, "back_squat", "back_squat@BARBELL", 1, 60.0, 12, null, 8.0, 0))
        workouts.logSet(StoredSet(w.id, "back_squat", "back_squat@BARBELL", 2, 60.0, 11, null, null, 0))
        assertEquals(listOf(12, 11), workouts.observeSets(w.id).first().map { it.reps })
        assertEquals(w.id, workouts.observeOpen().first()!!.id)
        workouts.finish(w.id)
        assertEquals(null, workouts.observeOpen().first())
        val recent = workouts.observeRecent().first().single()
        assertEquals(2, recent.sets.size)

        // A workout with nothing logged is dropped, not kept empty.
        val empty = workouts.startOrResume(monday.plusDays(2), "FULL_BODY_B", "g", "Gym")
        workouts.finish(empty.id)
        assertEquals(1, workouts.observeRecent().first().size)
        opener.get().close()
    }
}
