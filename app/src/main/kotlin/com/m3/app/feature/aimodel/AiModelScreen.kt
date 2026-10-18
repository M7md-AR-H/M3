package com.m3.app.feature.aimodel

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3.ai.bench.BenchmarkReport
import com.m3.ai.model.ModelStatus
import com.m3.core.designsystem.component.DoubleRule
import com.m3.core.designsystem.component.Kicker
import com.m3.core.designsystem.component.M3Button
import com.m3.core.designsystem.component.M3Card
import com.m3.core.designsystem.component.M3OutlineButton
import com.m3.core.designsystem.component.SectionHeader
import com.m3.core.designsystem.theme.M3
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun AiModelScreen(onBack: () -> Unit, viewModel: AiModelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Copying, checking and benchmarking take minutes; keep the screen awake meanwhile.
    val view = LocalView.current
    DisposableEffect(state.busy) {
        view.keepScreenOn = state.busy
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(M3.spacing.l),
        verticalArrangement = Arrangement.spacedBy(M3.spacing.m),
    ) {
        Kicker(text = "On-device AI")
        Text(text = "AI model", style = M3.typography.display, color = M3.colors.ink)
        DoubleRule()
        Text(
            text = "The Coach runs entirely on this phone. Pick the model file you downloaded; " +
                "M³ copies it into private storage and checks it's the official file.",
            style = M3.typography.body,
            color = M3.colors.muted,
        )

        state.activity?.let { activity ->
            M3Card(Modifier.fillMaxWidth()) {
                Kicker(text = "Working", color = M3.colors.accent)
                Text(text = activity, style = M3.typography.body, color = M3.colors.ink)
                M3OutlineButton(text = "Stop", onClick = viewModel::cancel)
            }
        }
        state.message?.let { message ->
            Text(text = message, style = M3.typography.body, color = M3.colors.ink)
        }

        SectionHeader(title = "Models")
        state.rows.forEach { row ->
            M3Card(Modifier.fillMaxWidth()) {
                Text(text = row.spec.displayName, style = M3.typography.title, color = M3.colors.ink)
                Text(
                    text = "${row.spec.fileName} · ${"%.2f".format(Locale.ROOT, row.spec.sizeBytes / 1e9)} GB",
                    style = M3.typography.bodySmall,
                    color = M3.colors.muted,
                )
                Text(text = statusLabel(row.status), style = M3.typography.label, color = M3.colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(M3.spacing.s)) {
                    when (row.status) {
                        ModelStatus.Missing -> Unit
                        ModelStatus.Unverified ->
                            M3Button(text = "Check file", onClick = { viewModel.verify(row.spec) }, enabled = !state.busy)
                        ModelStatus.Ready -> Unit
                    }
                    if (row.status != ModelStatus.Missing) {
                        M3OutlineButton(text = "Remove", onClick = { viewModel.delete(row.spec) }, enabled = !state.busy)
                    }
                }
            }
        }
        M3Button(
            text = "Add model file…",
            onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = "Benchmark")
        Text(
            text = "Runs four fixed test prompts on every ready model, on the GPU (with and without " +
                "multi-token prediction) and the CPU. Takes 5–15 minutes; keep the app open.",
            style = M3.typography.bodySmall,
            color = M3.colors.muted,
        )
        M3Button(
            text = "Run benchmark",
            onClick = viewModel::runBenchmark,
            enabled = state.canBenchmark,
            modifier = Modifier.fillMaxWidth(),
        )
        state.results.forEach { result ->
            M3Card(Modifier.fillMaxWidth()) {
                Text(text = result.case.label, style = M3.typography.title, color = M3.colors.ink)
                Text(text = BenchmarkReport.headline(result), style = M3.typography.body, color = M3.colors.ink)
                if (result.error == null) {
                    Text(text = BenchmarkReport.checks(result), style = M3.typography.bodySmall, color = M3.colors.muted)
                }
            }
        }
        if (state.results.isNotEmpty() && !state.busy) {
            M3OutlineButton(
                text = "Copy full results",
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("M3 benchmark", viewModel.reportText())))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        M3OutlineButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

private fun statusLabel(status: ModelStatus) = when (status) {
    ModelStatus.Missing -> "Not on this phone"
    ModelStatus.Unverified -> "File found — not checked yet"
    ModelStatus.Ready -> "Ready ✓"
}
