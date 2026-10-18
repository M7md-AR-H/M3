package com.m3.ai.bench

import java.util.Locale

data class CaseSummary(
    val decodeTokensPerSecond: Double?,
    val prefillTokensPerSecond: Double?,
    val averageTimeToFirstTokenSeconds: Double?,
    val imageSeconds: Double?,
    val json: CheckOutcome?,
    val image: CheckOutcome?,
)

object BenchmarkReport {
    fun summarize(result: CaseResult): CaseSummary {
        val withStats = result.prompts.filter { it.stats != null }
        val stats = withStats.mapNotNull { it.stats }
        return CaseSummary(
            decodeTokensPerSecond = weightedRate(stats.map { it.decodeTokens to it.decodeTokensPerSecond }),
            prefillTokensPerSecond = weightedRate(stats.map { it.prefillTokens to it.prefillTokensPerSecond }),
            averageTimeToFirstTokenSeconds = withStats.filter { it.promptId != "image" }
                .mapNotNull { it.stats?.timeToFirstTokenSeconds }
                .takeIf { it.isNotEmpty() }?.average(),
            imageSeconds = result.prompts.firstOrNull { it.promptId == "image" }?.wallSeconds,
            json = result.prompts.firstOrNull { it.promptId == "json" }?.outcome,
            image = result.prompts.firstOrNull { it.promptId == "image" }?.outcome,
        )
    }

    /** Total tokens over total time, so long answers count for more than short ones. */
    internal fun weightedRate(samples: List<Pair<Int, Double>>): Double? {
        val valid = samples.filter { (tokens, rate) -> tokens > 0 && rate > 0.0 }
        if (valid.isEmpty()) return null
        val tokens = valid.sumOf { it.first }
        val seconds = valid.sumOf { (count, rate) -> count / rate }
        return tokens / seconds
    }

    /** One-line headline per case, for the screen. */
    fun headline(result: CaseResult): String {
        if (result.error != null) return "Failed — ${result.error}"
        val s = summarize(result)
        return listOfNotNull(
            result.loadSeconds?.let { "load ${fmt(it)} s" },
            s.averageTimeToFirstTokenSeconds?.let { "first word ${fmt(it)} s" },
            s.decodeTokensPerSecond?.let { "${fmt(it)} tok/s" },
            s.imageSeconds?.let { "photo ${fmt(it)} s" },
            result.peakResidentMb?.let { "peak RAM ${fmt(it / MB_PER_GB)} GB" },
        ).joinToString(" · ")
    }

    fun checks(result: CaseResult): String {
        val s = summarize(result)
        return "JSON ${label(s.json)} · photo ${label(s.image)}"
    }

    /** Plain-text report with every number and answer, for copying or debug logs. */
    fun fullText(results: List<CaseResult>): String = buildString {
        results.forEach { result ->
            val s = summarize(result)
            appendLine("== ${result.case.label}")
            result.error?.let { appendLine("error: $it") }
            appendLine(
                "load=${opt(result.loadSeconds)}s rssAfterLoad=${result.residentMbAfterLoad}MB " +
                    "peakRss=${result.peakResidentMb}MB decode=${opt(s.decodeTokensPerSecond)}tok/s " +
                    "prefill=${opt(s.prefillTokensPerSecond)}tok/s ttft=${opt(s.averageTimeToFirstTokenSeconds)}s " +
                    "image=${opt(s.imageSeconds)}s json=${s.json} imageCheck=${s.image}",
            )
            result.prompts.forEach { p ->
                val st = p.stats
                appendLine(
                    "-- ${p.promptId}: ${p.outcome} wall=${fmt(p.wallSeconds)}s " +
                        "ttft=${opt(st?.timeToFirstTokenSeconds)}s prefill=${st?.prefillTokens}@${opt(st?.prefillTokensPerSecond)} " +
                        "decode=${st?.decodeTokens}@${opt(st?.decodeTokensPerSecond)}",
                )
                appendLine(p.answer.trim().replace("\n", " ⏎ ").take(ANSWER_MAX_CHARS))
            }
        }
    }

    private fun label(outcome: CheckOutcome?) = when (outcome) {
        CheckOutcome.PASS -> "pass"
        CheckOutcome.PASS_AFTER_CLEANUP -> "pass (needed cleanup)"
        CheckOutcome.FAIL -> "fail"
        null -> "—"
    }

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.1f", value)
    private fun opt(value: Double?) = value?.let(::fmt) ?: "-"

    private const val MB_PER_GB = 1024.0
    private const val ANSWER_MAX_CHARS = 1200
}
