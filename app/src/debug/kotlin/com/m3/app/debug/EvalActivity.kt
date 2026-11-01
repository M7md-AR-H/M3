package com.m3.app.debug

import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.m3.ai.engine.LiteRtLmEngine
import com.m3.ai.engine.LlmBackend
import com.m3.ai.engine.LlmLoadConfig
import com.m3.ai.model.ModelStore
import com.m3.app.feature.coach.CoachRuntime
import com.m3.app.feature.coach.ModelState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EvalEntryPoint {
    fun runtime(): CoachRuntime
    fun store(): ModelStore
}

/**
 * Debug-only: runs the Coach prompt eval on this device with the real engine config (GPU first, like the Coach).
 * Start: `adb shell am start -n com.m3.app/.debug.EvalActivity --ez vision true --es model <file name>`.
 * `model` is a file name in the app's model folder (default: the Coach's model); `vision` defaults to true.
 *
 * Uses only the made-up facts in [EvalFixture]. It never opens the database, profile, chat or photos, so it
 * needs no unlock. The report goes to filesDir/m3-eval/result.txt (app-private), rewritten after each case.
 *
 * Exported so `adb shell am start` can launch it, but guarded by the DUMP permission (see the debug manifest):
 * the adb shell holds it, third-party apps cannot get it.
 */
class EvalActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var view: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = TextView(this).apply {
            textSize = 16f
            setPadding(48, 96, 48, 48)
            gravity = Gravity.START
            text = "Starting"
        }
        setContentView(ScrollView(this).apply { addView(view) })
        // A second start (or a rotation) must not run two evals at once: only the first instance works.
        if (running) {
            show("An eval is already running; not starting another.")
            return
        }
        running = true
        val vision = intent.getBooleanExtra("vision", true)
        val model = intent.getStringExtra("model")
        scope.launch { run(vision, model) }
    }

    override fun onDestroy() {
        // Cancels the run; the engine is freed in run()'s finally.
        scope.cancel()
        super.onDestroy()
    }

    private fun show(text: String) {
        runOnUiThread { view.text = text }
    }

    private suspend fun run(vision: Boolean, requestedModel: String?) {
        val entries = mutableListOf<EvalEntry>()
        var header = EvalHeader(requestedModel ?: CoachRuntime.MODEL.fileName, "not loaded", vision, 0.0, EvalCases.all.size)
        var failure: String? = null
        val report = File(filesDir, "m3-eval").apply { mkdirs() }.let { File(it, "result.txt") }
        fun write(finished: Boolean = false) {
            val text = EvalReport.render(header, entries, failure, finished)
            // Write beside the report and rename, so a reader never sees half a file.
            val temp = File(report.parentFile, "result.tmp")
            temp.writeText(text)
            if (!temp.renameTo(report)) report.writeText(text)
        }
        try {
            write()
            val points = EntryPointAccessors.fromApplication(applicationContext, EvalEntryPoint::class.java)
            releaseCoachModel(points.runtime())
            val modelFile = File(File(noBackupFilesDir, "models"), File(header.modelFile).name)
            if (!modelFile.isFile) error("Model file not found in the app's model folder: ${modelFile.name}")

            val fixture = EvalFixture.build()
            show("Loading ${modelFile.name}")
            val loaded = loadEngine(modelFile, vision)
            header = header.copy(backend = loaded.backend, loadSeconds = loaded.seconds, notes = loaded.notes)
            write()
            try {
                EvalCases.all.forEachIndexed { index, case ->
                    show("Case ${index + 1} of ${EvalCases.all.size} · ${case.message}\n\n${header.backend}, ${modelFile.name}")
                    val start = System.nanoTime()
                    val result = loaded.engine.generate(
                        fixture.promptFor(case),
                        systemInstruction = fixture.systemFor(case),
                        // Same as the Coach (CoachController.MAX_OUTPUT_TOKENS).
                        maxOutputTokens = 400,
                    )
                    val seconds = (System.nanoTime() - start) / 1e9
                    entries += EvalEntry(index + 1, case, fixture.check(case, result.text), seconds, result.stats?.decodeTokensPerSecond, result.text)
                    write()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { loaded.engine.close() }
            }
            show("Done: ${entries.count { it.outcome.ok }}/${EvalCases.all.size} passed. Report written.")
        } catch (e: CancellationException) {
            failure = "cancelled"
            throw e
        } catch (e: Throwable) {
            // Class name and message only: the eval's inputs are made up, but keep logs and reports clean anyway.
            failure = "${e::class.java.simpleName}: ${e.message.orEmpty().take(200)}"
            show("Failed: $failure")
        } finally {
            withContext(NonCancellable) { write(finished = true) }
            running = false
        }
    }

    /** One model at a time: the Coach's model goes first. */
    private suspend fun releaseCoachModel(runtime: CoachRuntime) {
        runtime.unload()
        // unload() frees the engine on its own coroutine and then updates the state.
        delay(500)
        var waited = 0
        while (runtime.state.value.let { it is ModelState.Ready || it is ModelState.Loading } && waited < 60) {
            delay(500)
            waited++
        }
    }

    private class Loaded(val engine: LiteRtLmEngine, val backend: String, val seconds: Double, val notes: List<String>)

    /** The same attempts as CoachRuntime: GPU with multi-token prediction and a cache (real phones), then CPU. */
    private suspend fun loadEngine(file: File, vision: Boolean): Loaded = withContext(Dispatchers.IO) {
        val storage = getSystemService(StorageManager::class.java)
        val cache = File(noBackupFilesDir, "llm-cache").apply { mkdirs() }
            .takeIf { runCatching { storage.getAllocatableBytes(storage.getUuidForPath(it)) }.getOrDefault(0L) >= CoachRuntime.MIN_FREE_FOR_CACHE_BYTES }
        val emulator = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.PRODUCT.startsWith("sdk_")
        val attempts = buildList {
            if (!emulator) add(Triple(LlmBackend.GPU, true, cache))
            add(Triple(LlmBackend.CPU, false, null))
        }
        val notes = mutableListOf<String>()
        if (emulator) notes += "emulator: GPU not tried"
        for ((backend, mtp, cacheDir) in attempts) {
            val candidate = LiteRtLmEngine()
            try {
                val seconds = candidate.load(
                    LlmLoadConfig(file, backend, speculativeDecoding = mtp, maxTokens = CoachRuntime.MAX_TOKENS, cacheDir = cacheDir, vision = vision),
                )
                return@withContext Loaded(candidate, backend.name + if (mtp) " (multi-token prediction)" else "", seconds, notes)
            } catch (e: CancellationException) {
                candidate.close()
                throw e
            } catch (e: Throwable) {
                candidate.close()
                notes += "${backend.name} load failed: ${e::class.java.simpleName}: ${e.message.orEmpty().take(200)}"
            }
        }
        error("Model failed to load on every backend")
    }

    private companion object {
        /** Only touched on the main thread (onCreate) and in run()'s finally; a stale true needs a process restart at worst. */
        @Volatile
        var running = false
    }
}
