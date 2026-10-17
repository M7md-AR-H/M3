# HANDOFF — 2026-10-17 (end of session 2)

## What was done
- **Session 1 (2026-10-16):** product spec, UI prototype (https://claude.ai/artifact/RATouP8aDxkhKRNxgivVSJ), ADRs 0001–0007, Phase 0 code written and reviewed.
- **Session 2 (2026-10-17):**
  - Owner installed Android Studio (SDK Platform 37, build-tools 36.0.0; bundled JDK 25 at `C:/Program Files/Android/Android Studio/jbr`).
  - Wrapper scripts regenerated with `./gradlew wrapper` (jar hash unchanged).
  - First build passed with no code changes. Fixed: `local.properties` escaping (lint `PropertyEscape`), `String.format` locale in `UiConfigValidator`.
  - Generated `gradle/verification-metadata.xml` (523 components, SHA-256). A clean `assembleDebug assembleRelease test lint` passes with verification enforced. 20/20 unit tests pass; lint shows 0 errors and 1 accepted warning (`OldTargetApi`, targetSdk 36).
  - Verified on the emulator (`Medium_Phone_API_37.0`): all 5 tabs render; Customize works; simulated Coach change moves Fuel to the top and shows the notice; Undo works; the Today-layout lock blocks the Coach with the right message; 0 crashes. Emulator, temp file and adb all cleaned up.
  - Paired with the owner's phone over Wireless debugging (Poco F5 `23049PCD8G`, Android 15). The install was blocked by Xiaomi's "Install via USB" setting, and **nothing was installed or changed on the phone**. The owner then chose to defer phone installs until something is worth testing.

## Current state
- Phase 0 is complete. Branch `chore/phase-0-setup`, PR #1 (https://github.com/M7md-AR-H/M3/pull/1) is ready for review. **Merging needs the owner's approval.**
- Nothing is installed on the owner's phone.

## Next steps, in order
1. Get the owner's OK to squash-merge PR #1, merge it, and start Phase 1 on a new branch.
2. **Phase 1 kickoff:** split Phase 1 into README checkboxes. First task is the **model benchmark** (ADR 0002):
   - Re-check the newest small multimodal Gemma `.litertlm` builds (Gemma 4 small variants vs Gemma 3n E4B/E2B).
   - Give the owner the exact Hugging Face links for OWNER_TASKS #3 (license + download, about 4.9 GB).
   - Build a minimal `:ai` benchmark screen (LiteRT-LM 0.18.0, GPU backend, `<uses-native-library>` for libOpenCL/libvndksupport).
   - The owner enables "Install via USB" and Wireless debugging. Install, run the benchmark, record the results in ADR 0002, clean up.
3. Then the Phase 1 foundation: `:core:security` (Keystore/Tink, biometric lock), `:core:data` (Room 2.8.5 + SQLCipher 4.19.1 `SupportOpenHelperFactory`; persist `UiConfig` + history), onboarding, then fitness features.

## Open questions for the owner
- Approve merging PR #1?
- Units: metric or imperial? Equipment: gym, home, or both? (Phase 1 onboarding.)

## Traps found
- **Phone rules (CLAUDE.md):** touch only `com.m3.app`, no settings changes, temp files in `/data/local/tmp/m3-*` deleted immediately, `adb disconnect` + `adb kill-server` when done, report what was done.
- **Xiaomi installs:** need Developer options → "Install via USB" (Mi account), plus tapping Install on the phone's popup. Otherwise you get `INSTALL_FAILED_USER_RESTRICTED`.
- **Wireless debugging:** `adb pair IP:PORT CODE` uses the pairing port; the connect port is different. Find it with `adb mdns services`. It switches off when the phone changes Wi-Fi.
- **Git Bash mangles device paths** (`/data/local/tmp` → `C:/Program Files/Git/...`): set `MSYS_NO_PATHCONV=1` for adb commands.
- **FLAG_SECURE makes screenshots black:** use `uiautomator dump` and read text/bounds. Wait about 2 seconds after taps before dumping.
- **The emulator leaves a `emulator -kill <pid> -sleep 20` helper** that exits on its own about 20 s after `adb emu kill`.
- **`local.properties`** must escape the drive colon: `sdk.dir=C\:/Users/...`.
- **Builds:** prefix with `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. A cold first build takes about 7 minutes.
- **After any dependency change:** regenerate verification metadata with `./gradlew --write-verification-metadata sha256 :app:assembleDebug :app:assembleRelease test lint`.
- **Gradle prints "deprecated features… incompatible with Gradle 10":** not yet investigated (`--warning-mode all`). It's harmless on 9.x.
- See also the session 1 traps in git history: the wrapper jar source, compileSdk 37 being required, AGP 9 built-in Kotlin, the `hiltViewModel` package, and LiteRT-LM vs MediaPipe.
