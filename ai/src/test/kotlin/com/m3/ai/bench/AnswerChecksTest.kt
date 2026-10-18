package com.m3.ai.bench

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnswerChecksTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `strict JSON passes`() {
        val answer = """{"exercise": "Bench press", "sets": 3, "reps": 8, "weightKg": 60}"""
        assertEquals(CheckOutcome.PASS, AnswerChecks.evaluate(Check.JSON_WORKOUT, answer))
    }

    @Test
    fun `JSON inside code fences passes only after cleanup`() {
        val answer = "```json\n{\"exercise\":\"bench press\",\"sets\":3,\"reps\":8,\"weightKg\":60.0}\n```"
        assertEquals(CheckOutcome.PASS_AFTER_CLEANUP, AnswerChecks.evaluate(Check.JSON_WORKOUT, answer))
    }

    @Test
    fun `wrong values, extra keys or prose fail`() {
        val wrongReps = """{"exercise":"bench press","sets":3,"reps":10,"weightKg":60}"""
        val extraKey = """{"exercise":"bench press","sets":3,"reps":8,"weightKg":60,"rpe":8}"""
        listOf(wrongReps, extraKey, "Bench press, 3x8 at 60 kg", "").forEach {
            assertEquals(it, CheckOutcome.FAIL, AnswerChecks.evaluate(Check.JSON_WORKOUT, it))
        }
    }

    @Test
    fun `image answer needs both shapes and both colours`() {
        assertEquals(
            CheckOutcome.PASS,
            AnswerChecks.evaluate(Check.IMAGE_SHAPES, "A red circle and a blue square."),
        )
        assertEquals(
            CheckOutcome.FAIL,
            AnswerChecks.evaluate(Check.IMAGE_SHAPES, "A red circle and a blue rectangle."),
        )
    }

    @Test
    fun `process memory is parsed from proc status`() {
        val status = File(temp.root, "status").apply {
            writeText("Name:\tm3\nVmHWM:\t 4194304 kB\nVmRSS:\t 2097152 kB\n")
        }
        assertEquals(MemorySample(residentMb = 2048, peakResidentMb = 4096), readProcessMemory(status))
        assertNull(readProcessMemory(File(temp.root, "missing")))
    }
}
