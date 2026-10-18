# 0002 — On-device LLM: LiteRT-LM running Gemma (3n E4B baseline)

- **Date:** 2026-10-16
- **Status:** Accepted (model choice re-checked at Phase 1 start)

## Context
M³ needs one multimodal model (text + images, ideally audio) running fully on a Poco F5 (12 GB RAM, Adreno 725 GPU). Version research on 2026-10-16 found:
- Google's MediaPipe LLM Inference API (`tasks-genai`) is in **maintenance-only mode**; Google directs Android apps to **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`, 0.18.0 at time of writing).
- LiteRT-LM supports CPU, GPU and NPU backends and image/audio input with multimodal models. GPU on Android requires `<uses-native-library>` entries for `libOpenCL.so` and `libvndksupport.so`.
- Official files are gated on Hugging Face (Gemma license must be accepted): `google/gemma-3n-E4B-it-litert-lm` → `gemma-3n-E4B-it-int4.litertlm` ≈ **4.9 GB**; `google/gemma-3n-E2B-it-litert-lm` → ≈ 3.7 GB.
- Newer LiteRT-LM releases add Gemma 4 support; availability of small mobile Gemma 4 builds was not verified.

## Decision
- Runtime: **LiteRT-LM** (not MediaPipe `tasks-genai`, not llama.cpp).
- Baseline model: **Gemma 3n E4B int4** (`.litertlm`), GPU backend, with E2B as the fallback if memory or speed is a problem.
- At the start of Phase 1, re-check whether a small, multimodal Gemma 4 (or newer) `.litertlm` build exists and benchmark it against Gemma 3n E4B on the phone (tokens/sec, time-to-first-token, image latency, quality on a fixed prompt set). Pick by measurement and record the result here.
- The model file is loaded from app-private storage after a one-time copy from the owner's download (OWNER_TASKS #3); the app never downloads it itself (ADR 0005). Its SHA-256 is verified before loading.
- Wrap the runtime behind an `LlmEngine` interface in `:ai` so the model/runtime can be swapped without touching features.

## Consequences
- ~4.9 GB of storage for the main model, plus smaller vision/embedding models.
- Dependency on a 0.x library: API may change between versions; the `LlmEngine` wrapper contains the blast radius.
