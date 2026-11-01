package com.m3.ai.engine

import java.io.File

enum class LlmBackend { GPU, CPU }

data class LlmLoadConfig(
    val modelFile: File,
    val backend: LlmBackend,
    /** Multi-token prediction; Google recommends it on GPU for Gemma 4. */
    val speculativeDecoding: Boolean,
    /** Context window (prompt + answer) in tokens. */
    val maxTokens: Int = 4096,
    /** Lets the runtime cache compiled GPU programs so the next load is faster. */
    val cacheDir: File? = null,
    /** False for text-only models: asking for a vision backend they don't have fails on every answer. */
    val vision: Boolean = true,
)

data class GenerationStats(
    val timeToFirstTokenSeconds: Double,
    val prefillTokens: Int,
    val prefillTokensPerSecond: Double,
    val decodeTokens: Int,
    val decodeTokensPerSecond: Double,
)

data class GenerationResult(val text: String, val stats: GenerationStats?)

/**
 * The only way features talk to an on-device language model (ADR 0002), so the runtime or model
 * can change without touching them. Output is untrusted text: callers parse and validate it.
 *
 * One model is loaded at a time; [close] frees its memory. Implementations are not re-entrant:
 * callers must not run two [generate] calls at once.
 */
interface LlmEngine : AutoCloseable {
    /** Loads the model and returns how long that took, in seconds. */
    suspend fun load(config: LlmLoadConfig): Double

    suspend fun generate(
        prompt: String,
        imagePng: ByteArray? = null,
        systemInstruction: String? = null,
        maxOutputTokens: Int = 512,
        /** Called with each new piece of text as it is generated, in the caller's coroutine. */
        onText: ((String) -> Unit)? = null,
    ): GenerationResult
}
