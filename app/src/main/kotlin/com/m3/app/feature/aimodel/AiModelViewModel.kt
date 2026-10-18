package com.m3.app.feature.aimodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3.ai.bench.BenchmarkEvent
import com.m3.ai.bench.BenchmarkReport
import com.m3.ai.bench.BenchmarkRunner
import com.m3.ai.bench.CaseResult
import com.m3.ai.model.ModelCatalog
import com.m3.ai.model.ModelCheckResult
import com.m3.ai.model.ModelSpec
import com.m3.ai.model.ModelStatus
import com.m3.ai.model.ModelStore
import com.m3.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelRow(val spec: ModelSpec, val status: ModelStatus)

data class AiModelUiState(
    val rows: List<ModelRow> = emptyList(),
    /** What's running right now, e.g. "Copying Gemma 4 E4B — 42%"; null when idle. */
    val activity: String? = null,
    val results: List<CaseResult> = emptyList(),
    val message: String? = null,
) {
    val busy: Boolean get() = activity != null
    val canBenchmark: Boolean get() = !busy && rows.any { it.status == ModelStatus.Ready }
}

@HiltViewModel
class AiModelViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: ModelStore,
    private val runner: BenchmarkRunner,
) : ViewModel() {
    private val _state = MutableStateFlow(AiModelUiState(rows = rows()))
    val state: StateFlow<AiModelUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** The owner picked a file with the system file picker; copy it in if it's a known model. */
    fun import(uri: Uri) = launchExclusive {
        val size = withContext(Dispatchers.IO) { fileSize(uri) }
        val spec = size?.let(ModelCatalog::forSize)
        if (spec == null) {
            finish("That file isn't one of the supported models.")
            return@launchExclusive
        }
        val result = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                store.import(spec, stream) { copied ->
                    showProgress("Copying and checking ${spec.displayName}", copied, spec)
                }
            }
        }
        finish(result?.let { describe(spec, it) } ?: "Couldn't open that file.")
    }

    /** Checks a file that was copied in another way (e.g. over adb by Claude). */
    fun verify(spec: ModelSpec) = launchExclusive {
        val result = store.verify(spec) { hashed -> showProgress("Checking ${spec.displayName}", hashed, spec) }
        finish(describe(spec, result))
    }

    fun delete(spec: ModelSpec) = launchExclusive {
        withContext(Dispatchers.IO) { store.delete(spec) }
        finish("${spec.displayName} removed.")
    }

    fun runBenchmark() = launchExclusive {
        val cases = runner.casesFor(ModelCatalog.all)
        _state.update { it.copy(results = emptyList()) }
        runner.run(cases).flowOn(Dispatchers.Default).collect { event ->
            when (event) {
                is BenchmarkEvent.Progress -> _state.update {
                    it.copy(activity = "Test ${event.caseIndex + 1} of ${event.caseCount}: ${event.step}")
                }
                is BenchmarkEvent.CaseFinished -> {
                    // Fixed test prompts only — no personal data — so this is safe to log in debug builds.
                    if (BuildConfig.DEBUG) logChunked(BenchmarkReport.fullText(listOf(event.result)))
                    _state.update { it.copy(results = it.results + event.result) }
                }
            }
        }
        finish("Benchmark finished.")
    }

    fun cancel() {
        job?.cancel()
    }

    fun reportText(): String = BenchmarkReport.fullText(_state.value.results)

    private fun launchExclusive(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        _state.update { it.copy(activity = "Starting…", message = null) }
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                finish("Stopped.")
                throw e
            } catch (e: Exception) {
                finish("Something went wrong: ${e.javaClass.simpleName}")
            }
        }
    }

    private var lastPercent = -1

    private fun showProgress(label: String, done: Long, spec: ModelSpec) {
        val percent = (done * PERCENT / spec.sizeBytes).toInt()
        if (percent == lastPercent) return
        lastPercent = percent
        _state.update { it.copy(activity = "$label — $percent%") }
    }

    private fun finish(message: String) {
        lastPercent = -1
        _state.update { it.copy(rows = rows(), activity = null, message = message) }
    }

    // Logcat cuts lines at about 4 KB.
    private fun logChunked(text: String) {
        text.lines().flatMap { it.chunked(LOG_CHUNK_CHARS) }.forEach { Log.i(LOG_TAG, it) }
    }

    private fun rows() = ModelCatalog.all.map { ModelRow(it, store.status(it)) }

    private fun fileSize(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    private fun describe(spec: ModelSpec, result: ModelCheckResult): String = when (result) {
        ModelCheckResult.Ok -> "${spec.displayName} is ready."
        ModelCheckResult.WrongHash -> "${spec.displayName}: the file is damaged or not the official one (checksum mismatch)."
        is ModelCheckResult.WrongSize -> "${spec.displayName}: the file is incomplete or the wrong size."
        is ModelCheckResult.NotEnoughSpace ->
            "Not enough free space: about ${result.neededBytes / BYTES_PER_GB + 1} GB needed."
    }

    override fun onCleared() {
        job?.cancel()
    }

    private companion object {
        const val LOG_TAG = "M3Bench"
        const val LOG_CHUNK_CHARS = 3_000
        const val PERCENT = 100
        const val BYTES_PER_GB = 1_000_000_000L
    }
}
