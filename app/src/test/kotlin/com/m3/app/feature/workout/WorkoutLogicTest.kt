package com.m3.app.feature.workout

import com.m3.core.training.ExerciseUnit
import com.m3.core.training.LoadKind
import com.m3.core.training.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutLogicTest {
    private val bells = listOf(8.0, 12.0, 16.0, 20.0)

    @Test
    fun `listed kg moves through the list`() {
        assertEquals(16.0, WorkoutLogic.stepKg(12.0, +1, LoadKind.LISTED, bells)!!, 0.0)
        assertEquals(8.0, WorkoutLogic.stepKg(12.0, -1, LoadKind.LISTED, bells)!!, 0.0)
    }

    @Test
    fun `listed kg stops at the ends and handles values between entries`() {
        assertEquals(20.0, WorkoutLogic.stepKg(20.0, +1, LoadKind.LISTED, bells)!!, 0.0)
        assertEquals(8.0, WorkoutLogic.stepKg(8.0, -1, LoadKind.LISTED, bells)!!, 0.0)
        assertEquals(16.0, WorkoutLogic.stepKg(14.0, +1, LoadKind.LISTED, bells)!!, 0.0)
        assertEquals(12.0, WorkoutLogic.stepKg(14.0, -1, LoadKind.LISTED, bells)!!, 0.0)
    }

    @Test
    fun `listed kg from empty starts at the lightest and minus stays empty`() {
        assertEquals(8.0, WorkoutLogic.stepKg(null, +1, LoadKind.LISTED, bells)!!, 0.0)
        assertNull(WorkoutLogic.stepKg(null, -1, LoadKind.LISTED, bells))
    }

    @Test
    fun `listed kg without a list uses one kg steps`() {
        assertEquals(11.0, WorkoutLogic.stepKg(10.0, +1, LoadKind.LISTED, emptyList())!!, 0.0)
        assertEquals(9.0, WorkoutLogic.stepKg(10.0, -1, LoadKind.LISTED, emptyList())!!, 0.0)
    }

    @Test
    fun `plates and stacks use their steps and never go below zero`() {
        assertEquals(62.5, WorkoutLogic.stepKg(60.0, +1, LoadKind.PLATES, emptyList())!!, 0.0)
        assertEquals(55.0, WorkoutLogic.stepKg(60.0, -1, LoadKind.STACK, emptyList())!!, 0.0)
        assertEquals(0.0, WorkoutLogic.stepKg(2.5, -1, LoadKind.PLATES, emptyList())!!, 0.0)
        assertEquals(5.0, WorkoutLogic.stepKg(null, +1, LoadKind.STACK, emptyList())!!, 0.0)
    }

    @Test
    fun `reps step by one and seconds by five, never below zero`() {
        assertEquals(11, WorkoutLogic.stepAmount(10, +1, ExerciseUnit.REPS))
        assertEquals(40, WorkoutLogic.stepAmount(45, -1, ExerciseUnit.SECONDS))
        assertEquals(0, WorkoutLogic.stepAmount(0, -1, ExerciseUnit.REPS))
        assertEquals(1, WorkoutLogic.stepAmount(null, +1, ExerciseUnit.REPS))
    }

    @Test
    fun `starting values prefer target, then last time, then the top of the range`() {
        assertEquals(22.5, WorkoutLogic.initialKg(22.5, 20.0)!!, 0.0)
        assertEquals(20.0, WorkoutLogic.initialKg(null, 20.0)!!, 0.0)
        assertNull(WorkoutLogic.initialKg(null, null))
        assertEquals(10, WorkoutLogic.initialAmount(10, 12))
        assertEquals(12, WorkoutLogic.initialAmount(null, 12))
    }

    @Test
    fun `minutes are stored as seconds`() {
        assertEquals(null to 600, WorkoutLogic.toStored(10, ExerciseUnit.MINUTES))
        assertEquals(10, WorkoutLogic.amountOf(null, 600, ExerciseUnit.MINUTES))
        assertEquals(12 to null, WorkoutLogic.toStored(12, ExerciseUnit.REPS))
        assertEquals(null to 45, WorkoutLogic.toStored(45, ExerciseUnit.SECONDS))
    }

    @Test
    fun `last text shows kg times amount or a dash`() {
        assertEquals("20 × 12", WorkoutLogic.lastText(LoggedSet(20.0, reps = 12), ExerciseUnit.REPS))
        assertEquals("12", WorkoutLogic.lastText(LoggedSet(null, reps = 12), ExerciseUnit.REPS))
        assertEquals("10 × 45 s", WorkoutLogic.lastText(LoggedSet(10.0, seconds = 45), ExerciseUnit.SECONDS))
        assertEquals("—", WorkoutLogic.lastText(null, ExerciseUnit.REPS))
        assertEquals("—", WorkoutLogic.lastText(LoggedSet(20.0, seconds = 30), ExerciseUnit.REPS))
    }

    @Test
    fun `first incomplete exercise is the first with unlogged sets`() {
        val planned = listOf("a" to 3, "b" to 3, "c" to 2)
        assertEquals(0, WorkoutLogic.firstIncomplete(planned, emptyMap()))
        assertEquals(1, WorkoutLogic.firstIncomplete(planned, mapOf("a" to setOf(1, 2, 3))))
        assertEquals(1, WorkoutLogic.firstIncomplete(planned, mapOf("a" to setOf(1, 2, 3), "b" to setOf(1, 2))))
        // Skipping ahead: exercise 1 is still incomplete.
        assertEquals(0, WorkoutLogic.firstIncomplete(planned, mapOf("b" to setOf(1, 2, 3), "a" to setOf(1))))
    }

    @Test
    fun `all complete lands on the last exercise`() {
        val planned = listOf("a" to 1, "b" to 2)
        assertEquals(1, WorkoutLogic.firstIncomplete(planned, mapOf("a" to setOf(1), "b" to setOf(1, 2))))
        assertEquals(0, WorkoutLogic.firstIncomplete(emptyList(), emptyMap()))
    }

    @Test
    fun `clock formats minutes and hours`() {
        assertEquals("00:00", WorkoutLogic.clock(0))
        assertEquals("18:42", WorkoutLogic.clock((18 * 60 + 42) * 1000L))
        assertEquals("1:05:09", WorkoutLogic.clock((3600 + 5 * 60 + 9) * 1000L))
        assertEquals("00:00", WorkoutLogic.clock(-5000))
    }

    @Test
    fun `rest countdown rounds up and the bar drains`() {
        assertEquals("1:30", WorkoutLogic.restClock(90_000))
        assertEquals("0:01", WorkoutLogic.restClock(100))
        assertEquals("0:00", WorkoutLogic.restClock(0))
        assertEquals(0L, WorkoutLogic.restRemainingMillis(1_000, 5_000))
        assertEquals(0.5f, WorkoutLogic.restFraction(endAt = 10_000, totalMillis = 10_000, now = 5_000), 0.0001f)
        assertEquals(0f, WorkoutLogic.restFraction(endAt = 10_000, totalMillis = 10_000, now = 12_000), 0f)
    }
}
