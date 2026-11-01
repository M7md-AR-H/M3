package com.m3.app.debug

import java.util.Locale

/** One finished case: what was asked, how it was checked and the model's full answer. */
data class EvalEntry(
    val index: Int,
    val case: EvalCase,
    val outcome: EvalOutcome,
    val seconds: Double,
    val tokensPerSecond: Double?,
    /** The model's raw reply, tool line included. The lead reads every answer. */
    val answer: String,
)

/** What the report says before any case ran. */
data class EvalHeader(
    val modelFile: String,
    val backend: String,
    val vision: Boolean,
    val loadSeconds: Double,
    val totalCases: Int,
    /** Failed load attempts (e.g. the GPU before a CPU fallback); short, no personal data. */
    val notes: List<String> = emptyList(),
)

/** Plain-text report, rewritten after every case so a crash loses nothing. Pure: easy to test. */
object EvalReport {
    const val DONE = "DONE"

    fun render(header: EvalHeader, entries: List<EvalEntry>, error: String? = null, finished: Boolean = false): String = buildString {
        appendLine("M3 prompt eval (debug build, made-up test data)")
        appendLine("model: ${header.modelFile}")
        appendLine("backend: ${header.backend}")
        appendLine("vision: ${header.vision}")
        appendLine("load: ${seconds(header.loadSeconds)} s")
        header.notes.forEach { appendLine("note: $it") }
        appendLine()
        entries.forEach { e ->
            val tps = e.tokensPerSecond?.let { ", ${"%.1f".format(Locale.ROOT, it)} tok/s" }.orEmpty()
            appendLine("=== Case ${e.index}/${header.totalCases} ${if (e.outcome.ok) "PASS" else "FAIL"} (${seconds(e.seconds)} s$tps) ===")
            appendLine("expect=${e.case.expectTool} got=${e.outcome.toolName} claim=${e.outcome.claim} invalid=${e.outcome.invalid}")
            appendLine("Q: ${e.case.message}")
            appendLine("A:")
            appendLine(e.answer)
            appendLine()
        }
        val passed = entries.count { it.outcome.ok }
        val claims = entries.count { it.outcome.claim }
        appendLine("SCORE $passed/${header.totalCases} claims=$claims" + if (finished) "" else " (so far: ${entries.size} cases)")
        // Cases that said a change was done, so the lead can find the false "done" claims quickly.
        val claimCases = entries.filter { it.outcome.claim }.joinToString { it.index.toString() }
        if (claimCases.isNotEmpty()) appendLine("claim cases: $claimCases")
        if (error != null) appendLine("ERROR: $error")
        if (finished) appendLine(DONE)
    }

    private fun seconds(value: Double) = "%.1f".format(Locale.ROOT, value)
}
