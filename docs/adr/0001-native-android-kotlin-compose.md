# 0001 — Native Android app in Kotlin + Jetpack Compose

- **Date:** 2026-10-16
- **Status:** Accepted

## Context
M³ runs on one phone (Poco F5, Android) and must run multimodal AI models on-device with GPU acceleration, use the camera in real time (pose tracking), and keep data encrypted with hardware-backed keys. The owner does not write code; Claude writes and maintains all of it.

## Decision
Build an Android-only native app in Kotlin with Jetpack Compose (Material 3 as the base, heavily restyled to the prototype). No web app, no cross-platform framework, no iOS.

## Reasons
- Google's on-device AI runtimes (LiteRT / MediaPipe / ML Kit), CameraX, Android Keystore and Health Connect are first-class Kotlin APIs. Cross-platform layers would add bridges and third-party plugins — more attack surface and more breakage.
- A browser cannot give a 4B-parameter model the memory and GPU access it needs, and cannot use Keystore.
- One platform halves the work; the owner only uses Android.

## Consequences
- Not portable to iPhone without a rewrite.
- Requires Android Studio / Android SDK on the dev machine (see OWNER_TASKS #1).
