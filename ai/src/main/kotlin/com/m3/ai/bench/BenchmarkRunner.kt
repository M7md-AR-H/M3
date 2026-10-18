package com.m3.ai.bench

import com.m3.ai.engine.GenerationStats
import com.m3.ai.engine.LlmBackend
import com.m3.ai.engine.LlmEngine
import com.m3.ai.engine.LlmLoadConfig
import com.m3.ai.model.ModelSpec
import com.m3.ai.model.ModelStatus
import com.m3.ai.model.ModelStore
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class BenchmarkCase(
    val model: ModelSpec,
    val backend: LlmBackend,
    val speculativeDecoding: Boolean,
) {
    val label: String
        get() = buildString {
            append(model.displayName).append(" · ").append(backend.name)
            if (speculativeDecoding) append(" + MTP")
        }
}

data class PromptResult(
    val promptId: String,
    val outcome: CheckOutcome,
    val wallSeconds: Double,
    val stats: GenerationStats?,
    val answer: String,
)

data class CaseResult(
    val case: BenchmarkCase,
    val loadSeconds: Double?,
    val residentMbAfterLoad: Long?,
    val peakResidentMb: Long?,
    val prompts: List<PromptResult>,
    val error: String?,
)

sealed interface BenchmarkEvent {
    data class Progress(val caseIndex: Int, val caseCount: Int, val step: String) : BenchmarkEvent
    data class CaseFinished(val result: CaseResult) : BenchmarkEvent
}

/** Resident memory of this process in MB, current and peak (Linux /proc). */
data class MemorySample(val residentMb: Long, val peakResidentMb: Long)

/**
 * Runs [BenchmarkPrompts] against each [BenchmarkCase], loading one model at a time.
 * A failing case (e.g. GPU not supported) is reported and the next case still runs.
 */
class BenchmarkRunner(
    private val store: ModelStore,
    private val newEngine: () -> LlmEngine,
    private val testImagePng: () -> ByteArray,
    private val memory: () -> MemorySample?,
    private val resetPeakMemory: () -> Unit,
    private val cacheDir: File?,
    private val prompts: List<BenchmarkPrompt> = BenchmarkPrompts.all,
) {
    fun casesFor(models: List<ModelSpec>): List<BenchmarkCase> =
        models.filter { store.status(it) == ModelStatus.Ready }.flatMap { model ->
            listOf(
                BenchmarkCase(model, LlmBackend.GPU, speculativeDecoding = false),
                BenchmarkCase(model, LlmBackend.GPU, speculativeDecoding = true),
                BenchmarkCase(model, LlmBackend.CPU, speculativeDecoding = false),
            )
        }

    fun run(cases: List<BenchmarkCase>): Flow<BenchmarkEvent> = flow {
        val image by lazy(testImagePng)
        cases.forEachIndexed { index, case ->
            fun progress(step: String) = BenchmarkEvent.Progress(index, cases.size, "${case.label} — $step")

            resetPeakMemory()
            val results = mutableListOf<PromptResult>()
            var loadSeconds: Double? = null
            var afterLoad: MemorySample? = null
            var error: String?
            val engine = newEngine()
            try {
                // emit() stays outside attempt{} so a failure downstream is never recorded as a case error.
                emit(progress("loading"))
                error = attempt {
                    loadSeconds = engine.load(
                        LlmLoadConfig(
                            modelFile = store.fileFor(case.model),
                            backend = case.backend,
                            speculativeDecoding = case.speculativeDecoding,
                            cacheDir = cacheDir,
                        ),
                    )
                    afterLoad = memory()
                }
                for ((promptIndex, prompt) in prompts.withIndex()) {
                    if (error != null) break
                    emit(progress("prompt ${promptIndex + 1}/${prompts.size} (${prompt.id})"))
                    error = attempt {
                        val start = System.nanoTime()
                        val result = engine.generate(
                            prompt = prompt.prompt,
                            imagePng = if (prompt.withImage) image else null,
                            systemInstruction = prompt.systemInstruction,
                            maxOutputTokens = prompt.maxOutputTokens,
                        )
                        results += PromptResult(
                            promptId = prompt.id,
                            outcome = AnswerChecks.evaluate(prompt.check, result.text),
                            wallSeconds = (System.nanoTime() - start) / NANOS_PER_SECOND,
                            stats = result.stats,
                            answer = result.text,
                        )
                    }
                }
            } finally {
                runCatching { engine.close() }
            }
            emit(
                BenchmarkEvent.CaseFinished(
                    CaseResult(
                        case = case,
                        loadSeconds = loadSeconds,
                        residentMbAfterLoad = afterLoad?.residentMb,
                        peakResidentMb = memory()?.peakResidentMb,
                        prompts = results,
                        error = error,
                    ),
                ),
            )
        }
    }

    /** Runs [block]; returns null on success or a short error description. Cancellation passes through. */
    private inline fun attempt(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "${e.javaClass.simpleName}: ${e.message.orEmpty().take(ERROR_MAX_CHARS)}"
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val ERROR_MAX_CHARS = 300
    }
}

/** Reads VmRSS / VmHWM from /proc/self/status; null where that file doesn't exist. */
fun readProcessMemory(statusFile: File = File("/proc/self/status")): MemorySample? = runCatching {
    val values = statusFile.readLines().associate { line ->
        line.substringBefore(':') to line.substringAfter(':').trim()
    }
    fun mb(key: String) = values.getValue(key).substringBefore(' ').toLong() / KB_PER_MB
    MemorySample(residentMb = mb("VmRSS"), peakResidentMb = mb("VmHWM"))
}.getOrNull()

private const val KB_PER_MB = 1024L

/**
 * Resets this process's peak-memory mark (VmHWM) so each benchmark case reports its own peak.
 * Writing "5" to clear_refs is the documented Linux way; ignored where it isn't allowed.
 */
fun resetProcessPeakMemory(clearRefsFile: File = File("/proc/self/clear_refs")) {
    runCatching { clearRefsFile.writeText("5") }
}
