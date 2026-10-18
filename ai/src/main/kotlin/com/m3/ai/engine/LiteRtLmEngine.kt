package com.m3.ai.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.Executors
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * [LlmEngine] backed by Google's LiteRT-LM. All native calls run on one dedicated thread so the
 * engine's GPU context is never touched from two threads.
 */
class LiteRtLmEngine : LlmEngine {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "m3-llm").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: Engine? = null
    private var speculativeDecoding = false

    @OptIn(ExperimentalApi::class)
    override suspend fun load(config: LlmLoadConfig): Double = withContext(dispatcher) {
        require(config.modelFile.isFile) { "Model file is missing" }
        engine?.close()
        engine = null

        // Native logs could include prompt text; keep only errors.
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        // Process-wide flags, read when the engine is created.
        ExperimentalFlags.enableBenchmark = true
        ExperimentalFlags.enableSpeculativeDecoding = config.speculativeDecoding
        speculativeDecoding = config.speculativeDecoding

        val backend = when (config.backend) {
            LlmBackend.GPU -> Backend.GPU()
            LlmBackend.CPU -> Backend.CPU()
        }
        val created = Engine(
            EngineConfig(
                modelPath = config.modelFile.absolutePath,
                backend = backend,
                visionBackend = backend,
                audioBackend = null,
                maxNumTokens = config.maxTokens,
                maxNumImages = 1,
                cacheDir = config.cacheDir?.absolutePath,
            ),
        )
        val start = System.nanoTime()
        try {
            created.initialize()
        } catch (t: Throwable) {
            runCatching { created.close() }
            throw t
        }
        engine = created
        (System.nanoTime() - start) / NANOS_PER_SECOND
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun generate(
        prompt: String,
        imagePng: ByteArray?,
        systemInstruction: String?,
        maxOutputTokens: Int,
        onText: ((String) -> Unit)?,
    ): GenerationResult {
        // Not cancellable: a conversation created here must always reach the close in `finally`.
        val conversation = withContext(NonCancellable + dispatcher) {
            val loaded = checkNotNull(engine) { "No model loaded" }
            loaded.createConversation(
                ConversationConfig(
                    systemInstruction = systemInstruction?.let { Contents.of(it) },
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 1),
                    maxOutputToken = maxOutputTokens,
                    enableSpeculativeDecoding = speculativeDecoding,
                ),
            )
        }
        try {
            val message = if (imagePng == null) {
                Contents.of(Content.Text(prompt))
            } else {
                Contents.of(Content.ImageBytes(imagePng), Content.Text(prompt))
            }
            val text = StringBuilder()
            // The Flow cancels the native generation if this coroutine is cancelled.
            conversation.sendMessageAsync(message).flowOn(dispatcher).collect { chunk ->
                chunk.contents.contents.filterIsInstance<Content.Text>().forEach {
                    text.append(it.text)
                    onText?.invoke(it.text)
                }
            }
            val stats = withContext(dispatcher) {
                runCatching { conversation.getBenchmarkInfo() }.getOrNull()?.let { info ->
                    GenerationStats(
                        timeToFirstTokenSeconds = info.timeToFirstTokenInSecond,
                        prefillTokens = info.lastPrefillTokenCount,
                        prefillTokensPerSecond = info.lastPrefillTokensPerSecond,
                        decodeTokens = info.lastDecodeTokenCount,
                        decodeTokensPerSecond = info.lastDecodeTokensPerSecond,
                    )
                }
            }
            return GenerationResult(text.toString(), stats)
        } finally {
            withContext(NonCancellable + dispatcher) { conversation.close() }
        }
    }

    /** Blocks until the native engine is freed, so the next model never loads alongside it. */
    override fun close() {
        if (executor.isShutdown) return
        try {
            executor.submit {
                engine?.close()
                engine = null
            }.get()
        } finally {
            executor.shutdown()
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
