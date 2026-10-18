# HANDOFF — 2026-10-18 (session 3, in progress)

> The owner wants this file kept current **throughout** each session, not only at the end. Rewrite it after every meaningful step.

## What was done
- **Sessions 1–2 (2026-10-16/17):** spec, prototype, ADRs 0001–0007, the Phase 0 app shell. Verified on the emulator. PR #1 (`chore/phase-0-setup`) is ready and **waiting for the owner's merge approval**.
- **Session 3 (2026-10-18):**
  - **Model re-check (ADR 0002 updated).** Gemma 4 E4B (`gemma-4-E4B-it.litertlm`, 3.66 GB) and E2B (2.59 GB) from `litert-community` are Apache-2.0 and **ungated**, so the owner doesn't need a Hugging Face account. Gemma 3n is dropped. SHA-256 values are in `ModelCatalog`.
  - **New `:ai` module:**
    - `ModelStore`: hash-while-copy import, check, remove, and a `.verified` marker tied to size and mtime.
    - `LlmEngine` and `LiteRtLmEngine` (LiteRT-LM 0.18.0, one native thread, blocking close).
    - `BenchmarkRunner`, `AnswerChecks` and `BenchmarkReport`.
    - 14 unit tests.
  - **App screen:** You → "AI model & benchmark". It can add a file via the system picker, check, remove, run the benchmark (GPU, GPU+MTP and CPU per model) and copy the results. Debug builds also log results under the logcat tag `M3Bench`.
  - **Sonnet review:** all findings fixed except a load timeout (logged as a known limitation).
  - **Checks:** build, 34/34 tests, lint with 0 errors (warnings `OldTargetApi` and `ChromeOsAbiSupport` accepted). Release APK is 25.8 MB, arm64 only, with no INTERNET permission.
  - **Emulator:** the screen renders, and an unverified file goes through "Check file", gets rejected, then removed.
  - **Committed** `f863c1c` on `feat/phase-1-model-benchmark`, a branch stacked on `chore/phase-0-setup`. Not pushed yet.
  - **License:** `LICENSE` is PolyForm Noncommercial 1.0.0, plus a notice that commercial use needs the owner's permission. A README License section and a decision-log entry were added, and OWNER_TASKS #5 covers going public later. Not committed yet.

## Current state
- Model downloads are running on the PC into `M3/models/` (gitignored):
  - E4B was about 1.7 of 3.66 GB.
  - E2B is being resumed with `curl -C -` after a dropped connection.
  - **Verify both hashes** against `ModelCatalog` once done (`sha256sum models/*.litertlm`).
- The emulator `Medium_Phone_API_37.0` is **running** (started with `-memory 6144 -no-snapshot-save`), and the debug build is installed. **Shut it down when finished:** `adb -e emu kill`, then `adb kill-server`.
- Nothing is installed on the owner's phone.

## Next steps, in order
1. When E2B finishes and its hash matches, stream it into the emulator app and test end to end on CPU:
   - Stream: `adb -e exec-in run-as com.m3.app sh -c 'umask 077; mkdir -p no_backup/models && cat > no_backup/models/gemma-4-E2B-it.litertlm' < models/gemma-4-E2B-it.litertlm`.
   - Tap "Check file" and wait for Ready.
   - Run the benchmark. GPU cases will probably fail on the emulator, which is expected. The CPU case should answer.
   - Read results with `adb -e logcat -d -s M3Bench`.
2. Fix anything the end-to-end run shows. Commit the license and docs. Push the branch and open PR #2 (base `chore/phase-0-setup`, or `main` after PR #1 merges).
3. **Phone benchmark:**
   - Needs OWNER_TASKS #4 first: 8 GB free, "Install via USB", Wireless debugging, phone on charger.
   - Install, stream both models with the command above (no temp files on the phone), and run the benchmark.
   - Record the results in ADR 0002, pick the model, then clean up: force-stop, `adb disconnect`, `adb kill-server`.
4. Phase 1b: `:core:security` (Keystore/Tink, biometric lock), then `:core:data` (Room + SQLCipher, persist `UiConfig`), then onboarding. Phase 1c covers fitness features. All are listed as README checkboxes.

## Open questions for the owner
- **Approve merging PR #1?** (It was asked; the owner said "continue" without an explicit yes, so it isn't merged.)
- Units: metric or imperial? Equipment: gym, home, or both? (Needed for onboarding in 1b.)
- Before going public: keep the Gmail address in commit history, or switch to the GitHub noreply address? (OWNER_TASKS #5.)

## Traps found
- **LiteRT-LM API facts** (from `javap` on the 0.18.0 AAR):
  - `conversation.getBenchmarkInfo()` is a function, not a property, and needs `@OptIn(ExperimentalApi::class)`.
  - `ExperimentalFlags` are process-wide; set them before creating the `Engine`.
  - The AAR ships **no R8 rules**, so `ai/consumer-rules.pro` keeps `com.google.ai.edge.litertlm.**` because of JNI callbacks.
  - It bundles native code for `arm64-v8a` and `x86_64`.
- **AVD name** is `Medium_Phone_API_37.0` (the folder is `Medium_Phone.avd`). Use `-memory 6144` for model tests; the default is 2 GB.
- **`run-as` file permissions:** files written through `run-as` get mode 666 under the shell's umask. Always prefix with `umask 077`.
- **Windows file lock:** `./gradlew clean` can fail because a daemon holds `classes.jar`. Building without `clean` is fine, since dependency verification still runs.
- **Hugging Face downloads** over this connection drop partway through (`schannel: server closed abruptly`). Resume with `curl -C -` in a loop and always check the hash. `raw.githubusercontent.com` connections reset too; `gh api -H "Accept: application/vnd.github.raw"` works.
- Phone rules, Xiaomi "Install via USB", wireless pairing ports, `MSYS_NO_PATHCONV=1`, FLAG_SECURE (use `uiautomator dump` instead of screenshots): see CLAUDE.md and git history of this file (session 2).
- Builds: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. After dependency changes, run `./gradlew --write-verification-metadata sha256 :app:assembleDebug :app:assembleRelease test lint`.
