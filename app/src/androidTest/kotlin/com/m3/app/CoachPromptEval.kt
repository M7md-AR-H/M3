package com.m3.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3.ai.engine.LiteRtLmEngine
import com.m3.ai.engine.LlmBackend
import com.m3.ai.engine.LlmLoadConfig
import com.m3.app.debug.EvalCases
import com.m3.app.debug.EvalFixture
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt eval for the Coach (docs/WORKING_RULES.md: prompt wording is decided on measurements).
 * Emulator only, with a copy of the public model file at /data/local/tmp/m3-eval/model.litertlm.
 * Uses made-up test data only; results go to logcat under tag M3Eval (fixed prompts, no personal data).
 * Skipped when the model file isn't there, so normal test runs never need it.
 *
 * The cases, facts and checks live in com.m3.app.debug (shared with EvalActivity, which runs them on the phone).
 */
@RunWith(AndroidJUnit4::class)
class CoachPromptEval {
    @Test
    fun run(): Unit = runBlocking {
        val model = File("/data/local/tmp/m3-eval/model.litertlm")
        assumeTrue("no eval model", model.canRead())
        val fixture = EvalFixture.build()
        val cases = EvalCases.all
        Log.i(TAG, "first exercise next target: ${fixture.firstTarget}")
        Log.i(TAG, "system prompt chars=${fixture.system.length}")
        val cache = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "llm-eval").apply { mkdirs() }
        LiteRtLmEngine().use { engine ->
            val vision = InstrumentationRegistry.getArguments().getString("vision") != "false"
            val loadSeconds = engine.load(
                LlmLoadConfig(model, LlmBackend.CPU, speculativeDecoding = false, maxTokens = 4096, cacheDir = cache, vision = vision),
            )
            Log.i(TAG, "loaded in ${"%.1f".format(loadSeconds)} s")
            var passed = 0
            var claims = 0
            cases.forEachIndexed { index, case ->
                val result = engine.generate(
                    fixture.promptFor(case),
                    systemInstruction = fixture.systemFor(case),
                    maxOutputTokens = 400,
                )
                val outcome = fixture.check(case, result.text)
                if (outcome.ok) passed++
                if (outcome.claim) claims++
                Log.i(TAG, "CASE ${index + 1} ${if (outcome.ok) "PASS" else "FAIL"} expect=${case.expectTool} got=${outcome.toolName} claim=${outcome.claim} " +
                    "invalid=${outcome.invalid} tps=${result.stats?.decodeTokensPerSecond}")
                Log.i(TAG, "  Q: ${case.message}")
                result.text.lines().forEach { Log.i(TAG, "  A: $it") }
            }
            Log.i(TAG, "SCORE $passed/${cases.size} claims=$claims")
        }
    }

    private companion object {
        const val TAG = "M3Eval"
    }
}
