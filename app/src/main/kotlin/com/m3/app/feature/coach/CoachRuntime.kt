package com.m3.app.feature.coach

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import com.m3.ai.engine.LiteRtLmEngine
import com.m3.ai.engine.LlmBackend
import com.m3.ai.engine.LlmEngine
import com.m3.ai.engine.LlmLoadConfig
import com.m3.ai.model.ModelCatalog
import com.m3.ai.model.ModelSpec
import com.m3.ai.model.ModelStatus
import com.m3.ai.model.ModelStore
import com.m3.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ModelState {
    /** The model file isn't on the phone or hasn't been checked (You → AI model). */
    data object NoModel : ModelState
    data object NotLoaded : ModelState
    data object Loading : ModelState
    data class Ready(val backend: LlmBackend) : ModelState
    data object Failed : ModelState
}

/** What a model is needed for: words, or reading a photo. */
enum class ModelUse { CHAT, PHOTO }

/**
 * Owns the on-device models (ADR 0002): Qwen3-4B for chat and Gemma 4 E4B for photos, one in memory at a time, on
 * the GPU, falling back to the CPU if the GPU can't start. Chat falls back to E4B if Qwen isn't on the phone. Loaded
 * on first use, unloaded when the app goes to the background so it never holds memory while the owner uses other
 * apps. One generation at a time.
 */
@Singleton
class CoachRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ModelStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var engine: LlmEngine? = null
    private var loadedModel: ModelSpec? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun refresh() {
        if (engine == null) _state.value = if (modelReady()) ModelState.NotLoaded else ModelState.NoModel
    }

    /** The coroutine currently using the model, so [unload] can stop it. */
    @Volatile
    private var active: Job? = null

    @Volatile
    private var backgrounded = false

    /** Runs [block] with the right model, swapping it in if needed. Throws if there's no model or it can't load. */
    suspend fun <T> use(purpose: ModelUse = ModelUse.CHAT, block: suspend (LlmEngine) -> T): T = mutex.withLock {
        // An answer that was waiting while the app went to the background must not load the model now.
        if (backgrounded) throw CancellationException("App is in the background")
        active = currentCoroutineContext()[Job]
        try {
            val model = modelFor(purpose)
            val loaded = engine.takeIf { loadedModel == model } ?: load(model)
            block(loaded)
        } finally {
            active = null
        }
    }

    /** The current chat model's addition to each prompt; empty for a model that needs none. */
    fun chatPromptSuffix(): String = modelFor(ModelUse.CHAT).promptSuffix

    /** The app is visible again; answers may load the model. */
    fun onForeground() {
        backgrounded = false
    }

    /** Stops a running answer and frees the model's memory (app going to the background). */
    fun unload() {
        backgrounded = true
        active?.cancel()
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                closeEngine()
                _state.value = if (modelReady()) ModelState.NotLoaded else ModelState.NoModel
            }
        }
    }

    private fun closeEngine() {
        engine?.close()
        engine = null
        loadedModel = null
    }

    private suspend fun load(model: ModelSpec): LlmEngine = withContext(Dispatchers.IO) {
        // Never two models in memory: closing blocks until the first one is freed.
        closeEngine()
        if (store.status(model) != ModelStatus.Ready) {
            _state.value = ModelState.NoModel
            error("No model")
        }
        _state.value = ModelState.Loading
        val file = store.fileFor(model)
        // Compiled programs and weights are cached so later loads are fast. Kept out of cacheDir: Android may
        // purge that while the GPU cache (~300 MB) is being written, which makes the load fail.
        // Only cache with room to spare: a full disk during a cache write can abort the runtime.
        val cache = File(context.noBackupFilesDir, "llm-cache").apply { mkdirs() }
            .takeIf { freeBytes(it) >= MIN_FREE_FOR_CACHE_BYTES }
        val attempts = buildList {
            // Emulators only fake a GPU (very slow, then fails); real phones try the GPU first.
            if (!isEmulator()) add(Attempt(LlmBackend.GPU, mtp = model.speculativeDecoding, cache = cache))
            // No cache on the CPU: its weight cache grows to about the model's size, and a failed write
            // aborts the native runtime (seen on the emulator). The CPU is only the fallback.
            add(Attempt(LlmBackend.CPU, mtp = false, cache = null))
        }
        for ((backend, mtp, cacheDir) in attempts) {
            val candidate = LiteRtLmEngine()
            try {
                candidate.load(LlmLoadConfig(file, backend, speculativeDecoding = mtp, maxTokens = MAX_TOKENS, cacheDir = cacheDir, vision = model.vision))
                engine = candidate
                loadedModel = model
                _state.value = ModelState.Ready(backend)
                return@withContext candidate
            } catch (e: CancellationException) {
                candidate.close()
                _state.value = ModelState.NotLoaded
                throw e
            } catch (_: Throwable) {
                candidate.close()
            }
        }
        _state.value = ModelState.Failed
        error("Model failed to load")
    }

    private fun modelFor(purpose: ModelUse): ModelSpec =
        if (purpose == ModelUse.CHAT && store.status(CHAT_MODEL) == ModelStatus.Ready) CHAT_MODEL else PHOTO_MODEL

    /** The Coach can talk with either model. */
    private fun modelReady() = store.status(CHAT_MODEL) == ModelStatus.Ready || store.status(PHOTO_MODEL) == ModelStatus.Ready

    private data class Attempt(val backend: LlmBackend, val mtp: Boolean, val cache: File?)

    /** Free space including cached data Android can clear; 0 if it can't be read (then no cache is used). */
    private fun freeBytes(dir: File): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        return runCatching { storage.getAllocatableBytes(storage.getUuidForPath(dir)) }.getOrDefault(0L)
    }

    private fun isEmulator() = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.PRODUCT.startsWith("sdk_")

    companion object {
        val CHAT_MODEL = ModelCatalog.QWEN_3_4B
        val PHOTO_MODEL = ModelCatalog.GEMMA_4_E4B
        const val MAX_TOKENS = 4096

        /** The GPU cache is about 300 MB; keep a wide margin so its write never fills the disk. */
        const val MIN_FREE_FOR_CACHE_BYTES = 1_000_000_000L
    }
}
