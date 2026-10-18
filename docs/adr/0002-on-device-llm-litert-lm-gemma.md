# 0002 — On-device LLM: LiteRT-LM running Gemma 4

- **Date:** 2026-10-16, updated 2026-10-18
- **Status:** Accepted. Runtime fixed; the final model is picked by the on-phone benchmark (results below once measured).

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

## Benchmark results (Poco F5)
_Not yet measured. To be filled in from the on-phone run._

## Consequences
- 2.6–3.7 GB of storage for the main model, plus smaller vision/embedding models later. Importing from Downloads temporarily needs the space twice.
- Uninstalling the app deletes the model, which then has to be copied again.
- We depend on a 0.x library whose API may change between versions; the `LlmEngine` wrapper limits the impact.
- Stop can't interrupt a native model load mid-call; it takes effect when the load returns.
