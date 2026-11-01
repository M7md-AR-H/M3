package com.m3.app.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvalReportTest {
    private val header = EvalHeader("model.litertlm", "GPU", vision = false, loadSeconds = 12.34, totalCases = 2)

    private fun entry(index: Int, ok: Boolean, claim: Boolean, answer: String) = EvalEntry(
        index = index,
        case = EvalCase("Question $index?", "theme"),
        outcome = EvalOutcome(ok, toolName = null, claim = claim, invalid = null, text = answer),
        seconds = 3.0,
        tokensPerSecond = if (index == 1) 14.5 else null,
        answer = answer,
    )

    @Test
    fun `partial report keeps full answers and has no DONE`() {
        val text = EvalReport.render(header, listOf(entry(1, ok = true, claim = false, answer = "line one\nline two")))
        assertTrue("=== Case 1/2 PASS (3.0 s, 14.5 tok/s) ===" in text)
        assertTrue("line one\nline two" in text)
        assertTrue("SCORE 1/2 claims=0 (so far: 1 cases)" in text)
        assertFalse(text.lines().any { it == EvalReport.DONE })
    }

    @Test
    fun `finished report counts false done claims and ends with DONE`() {
        val text = EvalReport.render(
            header,
            listOf(entry(1, ok = true, claim = false, answer = "a"), entry(2, ok = false, claim = true, answer = "Done, I changed it.")),
            finished = true,
        )
        assertTrue("SCORE 1/2 claims=1" in text)
        assertTrue("claim cases: 2" in text)
        assertTrue("backend: GPU" in text)
        assertEquals(EvalReport.DONE, text.trimEnd().lines().last())
    }

    @Test
    fun `an error is reported before DONE`() {
        val text = EvalReport.render(header, emptyList(), error = "IllegalStateException: boom", finished = true)
        val lines = text.trimEnd().lines()
        assertEquals(EvalReport.DONE, lines.last())
        assertEquals("ERROR: IllegalStateException: boom", lines[lines.size - 2])
    }
}
