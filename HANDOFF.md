# HANDOFF — 2026-10-16 (end of session 1)

## What was done
- Product brainstorm → `docs/PRODUCT_SPEC.md`; interactive UI prototype (6 screens) at https://claude.ai/artifact/RATouP8aDxkhKRNxgivVSJ — the design source of truth.
- Owner's process rules copied to `docs/WORKING_RULES.md`; project rules in `CLAUDE.md`.
- ADRs 0001–0007 in `docs/adr/` (native Kotlin/Compose; LiteRT-LM + Gemma; one Coach + "model proposes, code decides"; SQLCipher/Tink/Keystore; research-only networking; small module layout; agent-customizable UI via `UiConfig`).
- Library/toolchain versions researched and spot-verified against Maven/Gradle metadata (see README decision log).
- Phase 0 Android skeleton written (Sonnet from an Opus spec), reviewed by a second Sonnet pass (3 fixes), permission/undo logic and manifest checked by Opus:
  - `:core:designsystem` — `UiConfig` model, `UiChange` (future agent `ui.*` tools), `Permissions`, `UiConfigValidator` (WCAG contrast etc.), `UiConfigStore` (apply/undo/history/locks), theme tokens → `M3Theme`, fonts (Bodoni Moda + Instrument Sans, OFL), components, unit tests.
  - `:app` — Hilt app, `MainActivity` (FLAG_SECURE, edge-to-edge), type-safe nav with 5 tabs from config, Today card registry, placeholder screens, You → Customize demo (theme, accent, font, text size, radius, card order/visibility, locks, Undo, Reset, "Simulate a Coach change").
  - Root build: version catalog, network-isolation check (only `:research` may use OkHttp/Retrofit/jsoup/Ktor).
  - Gradle wrapper jar extracted from the official 9.8.1 distribution; SHA-256 `3b8a2577…8e98` matches gradle.org.

## Current state
- **Nothing has been compiled.** No JDK/Android SDK on this PC yet. All code is reviewed by reading only.
- Work is on branch `chore/phase-0-setup` (see git log for whether it was pushed / PR opened).
- Expected-risky APIs if the first build fails: `FontVariation` in `M3Typography.kt`, `ImageVector.Builder.addPath` in `M3Icons.kt`, type-safe nav `hasRoute` in `M3BottomBar.kt`, the root `build.gradle.kts` network check under configuration cache, Hilt 2.60.1 with AGP 9 built-in Kotlin, KSP 2.3.12 with Kotlin 2.4.21.

## Next steps, in order
1. Confirm owner finished OWNER_TASKS #1 (Android Studio + SDK Platform 37). Check `C:\Users\moham\AppData\Local\Android\Sdk\platforms\android-37` exists.
2. Create `local.properties` with `sdk.dir=C\:\\Users\\moham\\AppData\\Local\\Android\\Sdk` (it's git-ignored). Use Android Studio's bundled JDK: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.
3. Haiku: `./gradlew wrapper --gradle-version 9.8.1` (regenerates official `gradlew` scripts; confirm the jar hash is unchanged), then `./gradlew :app:assembleDebug test lint` — report exact output.
4. Fix failures (Sonnet diagnoses; Opus final check). Then generate dependency verification: `./gradlew --write-verification-metadata sha256 help`, re-run the build, commit `gradle/verification-metadata.xml`.
5. Owner does OWNER_TASKS #2 (USB debugging). `./gradlew :app:installDebug`, then owner runs the Phase 0 "Try it yourself" list.
6. Tick the remaining Phase 0 boxes in README, update status, ask owner to approve merging the PR (squash).
7. Start Phase 1: split it into README checkboxes; first task is re-checking Gemma model options (ADR 0002) and giving the owner the exact Hugging Face link for OWNER_TASKS #3.

## Open questions for the owner
- Phone's Android version (OWNER_TASKS #2 step 1).
- Units: metric or imperial? Equipment: gym, home, or both? (Phase 1 onboarding.)

## Traps found
- **Gradle wrapper jar:** the one in Gradle's GitHub repo at the release tag is NOT the published wrapper (checksum mismatch). Take it from the distribution (`lib/plugins/gradle-wrapper-main-<v>.jar` → `gradle-wrapper.jar`) or run `gradle wrapper`.
- **compileSdk must be 37:** current AndroidX AARs declare `minCompileSdk=37`; 36 fails `checkAarMetadata`.
- **AGP 9 built-in Kotlin:** never apply `org.jetbrains.kotlin.android`; use `kotlin { compilerOptions {} }`; no kapt.
- **`hiltViewModel`** now lives in `androidx.hilt.lifecycle.viewmodel.compose` (old import is deprecated).
- **MediaPipe LLM Inference is maintenance-only** — use LiteRT-LM. Gemma 3n E4B `.litertlm` is ~4.9 GB and gated on Hugging Face.
- **No Python on this PC** — use bash/sed or the Edit tool for scripted edits.
- `gradlew` executable bit: run `git update-index --chmod=+x gradlew` when committing from Windows.
