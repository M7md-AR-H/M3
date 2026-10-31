# 0002 — On-device LLM: LiteRT-LM running Gemma 4

- **Date:** 2026-10-16, updated 2026-10-18
- **Status:** Accepted. Gemma 4 E4B on GPU + MTP chosen from the on-phone benchmark (2026-10-18).

## Context
M³ needs one multimodal model (text + images, ideally audio) running fully on a Poco F5 (12 GB RAM, Adreno 725 GPU).

Version research on 2026-10-16 found:
- Google's MediaPipe LLM Inference API (`tasks-genai`) is in **maintenance-only mode**.
- Google directs Android apps to **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`).
- LiteRT-LM supports CPU, GPU and NPU backends and image/audio input. GPU on Android needs `<uses-native-library>` entries for `libOpenCL.so` and `libvndksupport.so`.

Re-check on 2026-10-18 (Phase 1 start):
- Google publishes **Gemma 4** builds for LiteRT-LM under `litert-community`. They are **Apache-2.0 and ungated**: no Hugging Face account or license click needed.
- Files and sizes (Hugging Face API, LFS SHA-256 recorded in `ModelCatalog`):

  | File | Size | Notes |
  |---|---|---|
  | `gemma-4-E4B-it.litertlm` | 3.66 GB | Text + vision + audio; vision/audio parts load on demand |
  | `gemma-4-E2B-it.litertlm` | 2.59 GB | Same, smaller |
  | Gemma 3n E4B (`google/gemma-3n-E4B-it-litert-lm`) | 4.92 GB | Gated (Gemma license), older |

- Google's E4B model card gives these figures for a Galaxy S26 Ultra (GPU): about 0.8 s to first token, about 22 tok/s decode (46 tok/s with speculative decoding on summarization), and about 0.7–1 GB of CPU-side memory.
- Speculative decoding (multi-token prediction, "MTP") is recommended on GPU and is a runtime flag.
- The 0.18.0 AAR ships `arm64-v8a` and `x86_64` native code, so the emulator can run the CPU backend for integration tests.
- The AAR also has a built-in benchmark API (`ExperimentalFlags.enableBenchmark`, `Conversation.getBenchmarkInfo()`).
- Device-specific NPU builds exist only for chips other than the Poco's (Tensor G5/G6, SM8750, QCS8275). We use GPU/CPU.

## Decision
- **Runtime:** LiteRT-LM 0.18.0, not MediaPipe `tasks-genai` and not llama.cpp.
- **Candidates:** **Gemma 4 E4B** (preferred) and **Gemma 4 E2B** (fallback if E4B is too slow or memory-hungry). Gemma 3n is dropped: it is bigger, older and gated.
- **Pick by measurement on the phone.** The in-app benchmark (You → AI model) runs a fixed prompt set for each installed model:
  - Prompts: a short answer, a long plan, a strict-JSON extraction (to test "model proposes, code decides"), and an image with known shapes.
  - Each prompt runs on GPU, GPU + MTP, and CPU.
  - It records load time, time to first token, prefill and decode tok/s, photo latency, process peak RAM, and pass/fail checks.
- **Model files are owner-provided, never downloaded by the app** (ADR 0005). Two ways in:
  1. The owner picks the file with the system file picker. The app copies it into `noBackupFilesDir/models` while hashing it.
  2. Claude streams it over adb straight into that folder (`adb exec-in run-as com.m3.app …`, with no temp file on the phone), and the owner taps "Check file".
- **Integrity:** the file is only used once its size and SHA-256 match the catalog.
  - The verified state is remembered in a marker tied to size and modification time, because re-hashing 3.66 GB on every launch would take tens of seconds.
  - Any change to the file makes it unverified again.
- **Model files are not encrypted.** They are public files containing no personal data, and they already sit in app-private, non-backed-up storage.
- The runtime is wrapped behind `LlmEngine` in `:ai`, so the model or runtime can change without touching features. All native calls run on one dedicated thread, and engines are closed (blocking) before the next one loads, so two models never sit in memory together.

## Benchmark results (Poco F5, 2026-10-18)
Debug build with LiteRT-LM 0.18.0, a 4,096-token context, the fixed prompt set, and the phone on a charger.

| Case | Load (s) | First token (s) | Decode (tok/s) | Prefill (tok/s) | Photo (s) | Peak RAM (process) | JSON | Photo check |
|---|---|---|---|---|---|---|---|---|
| E4B · GPU | 28.2* | 0.8 | 8.1 | 123 | 4.3 | 1.09 GB | pass | pass |
| **E4B · GPU + MTP** | 16.9 | 0.7 | **15.6** | 136 | 3.7 | 1.14 GB | pass | pass |
| E4B · CPU | 12.9 | 2.1 | 7.9 | 28 | 19.2 | 4.21 GB | pass | pass |
| E2B · GPU | 21.9* | 0.4 | 17.5 | 255 | 2.6 | 1.16 GB | pass | pass |
| E2B · GPU + MTP | 10.0 | 0.2 | 26.2 | 426 | 2.1 | 1.17 GB | pass | pass |
| E2B · CPU | 6.8 | 0.8 | 16.7 | 86 | 9.8 | 2.68 GB | pass | pass |

\* First GPU load per model includes compiling GPU programs; later loads use the cache in `cacheDir`.

**Quality notes** (from reading every answer):
- Every case passed the strict JSON check without cleanup, and every case named the red circle and blue square.
- **E2B leaked foreign-script tokens** into the one-sentence answer in both GPU runs ("theलोड", "負荷"), and its answers were also more generic.
- E4B's answers were cleaner. It made one odd word choice ("doing more food, weights") and once wrote a 3-day plan as a weekly schedule.
- "Peak RAM" is the app process's resident memory. GPU-side memory isn't fully counted.
- During the run, Android closed about 10 cached background apps for memory, a normal low-memory action. M³ itself was never killed.

## Decision (2026-10-18, from the benchmark)
- **Default: Gemma 4 E4B on GPU with multi-token prediction.** It is fast enough for chat: about 16 tok/s, well above reading speed, with first words in under a second and a photo answered in under 4 s. It also writes noticeably better than E2B, and the Coach's quality matters more than the extra speed.
- **CPU is a fallback only**, used if the GPU fails to initialise. It is 2× slower, 5× slower on photos, and uses about 4 GB.
- **E2B is not shipped as a second model.** It was removed from the phone after the benchmark. It stays in `ModelCatalog` so it can be re-evaluated, e.g. as a fast "draft" model or if newer builds fix the token leaks.
- Model output stays untrusted. The JSON passes here don't change ADR 0003: tool calls are still schema-validated in Kotlin.

## Consequences
- 2.6–3.7 GB of storage for the main model, plus smaller vision/embedding models later. Importing from Downloads temporarily needs the space twice.
- Uninstalling the app deletes the model, which then has to be copied again.
- We depend on a 0.x library whose API may change between versions; the `LlmEngine` wrapper limits the impact.
- Stop can't interrupt a native model load mid-call; it takes effect when the load returns.

## Model options reviewed (2026-10-31)
Why: evals show E4B misquotes numbers and invents tools. Figures are from the model cards on Hugging Face, not measured on the Poco F5. The Galaxy S26 and desktop numbers will be better than this phone's. M³ runs LiteRT-LM 0.18.0.

| Model | File | Vision | Licence | On Android | Verdict |
|---|---|---|---|---|---|
| Qwen3-4B, `mixed_int4` | 2.17 GiB | No | Apache-2.0 | Android GPU listed. 19.5 tok/s on a Galaxy S26. A Pixel 8a (8 GB) fell to 243 MB free and the run was stopped | **Try first**, for chat, with E4B kept for photos. Needs LiteRT-LM **0.19.0**: GPU engines 0.18.0 and 0.18.1 crash on this file (LiteRT-LM#3986). Thinking is on by default, so it needs testing for answer time |
| LFM3-Omni-2B | 3.23 GB | Yes, plus audio | LFM Open License v1.0: not licensed for commercial use by organisations with $10M or more in revenue | Measured on Galaxy S26 and Pixel 8a GPU. 27 tok/s; 1.05 GB process + 3.08 GB GPU | **Try second**: the only single-file replacement. The licence is more restrictive than Apache. Needs 0.18.1. Set a sampler, or answers are greedy |
| WeVisOne-VL-2B, int8 | 2.9 GB | Yes (detection, OCR, counting) | Apache-2.0 | Galaxy S26 GPU, 2.7 GB peak | Not a coach: 2,048-token context, long text unreliable. Could read nutrition labels later |
| Gemma 4 12B | 6.9 GB | Yes | Apache-2.0 | **Not listed for Android.** 7.3–7.9 GB of GPU memory on desktops | **Don't use**: likely to be killed on a 12 GB phone |
| Qwen3.8-27B | 15.4 GB | No | Not checked | Desktop-class | Doesn't fit |

No card measures tool-calling accuracy, so only our own eval can say which is stronger. Order of work: raise LiteRT-LM to 0.19.0 (the crash above is in the runtime, so test E4B again first), run `CoachPromptEval` on each candidate on the emulator, then the on-phone benchmark. Keep E4B until a candidate beats it on both.
