# HANDOFF — 2026-10-18 (session 3)

> The owner wants this file kept current **throughout** each session, not only at the end. Rewrite it after every meaningful step.

## What was done
- **Sessions 1–2 (2026-10-16/17):** spec, prototype, ADRs 0001–0007, the Phase 0 app shell verified on the emulator. PR #1 (`chore/phase-0-setup`) is **waiting for the owner's merge approval**.
- **Session 3 (2026-10-18), Phase 1a, on-device model. Done.**
  - **Model re-check:** the Gemma 4 E4B and E2B `.litertlm` builds are Apache-2.0 and ungated. Gemma 3n is dropped.
  - **New `:ai` module:**
    - `ModelCatalog`, with SHA-256 values.
    - `ModelStore`: hash-while-copy import, check, remove, and a `.verified` marker.
    - `LlmEngine` / `LiteRtLmEngine`: LiteRT-LM 0.18.0, one native thread.
    - `BenchmarkRunner`, `AnswerChecks`, `BenchmarkReport`.
  - **Screen:** You → "AI model & benchmark".
  - **Quality checks:** a Sonnet review found issues, all fixed except a load timeout, which is logged as a limitation. 34/34 tests pass. Lint shows 0 errors (warnings `OldTargetApi` and `ChromeOsAbiSupport` accepted).
  - **Phone:** M³ debug build installed on the Poco F5. Both models were streamed into app-private storage over USB and verified in the app.
  - **Benchmark run on the phone:** all 6 cases passed all checks. **Decision: Gemma 4 E4B on GPU + MTP** (15.6 tok/s, 0.7 s first token, 3.7 s photo, about 1.1 GB). Full table and quality notes are in ADR 0002. E2B leaked foreign-script tokens and was removed from the phone.
  - **License:** PolyForm Noncommercial 1.0.0 (`LICENSE`). OWNER_TASKS #5 holds the go-public checklist.
  - **Docs updated:** README (status, Phase 1 checkboxes, decision log, limitations), OWNER_TASKS (#3 not needed, #4 done, Phase 1a test list), ADR 0002, PRODUCT_SPEC, CLAUDE.md.

## Current state
- **Phone:**
  - `com.m3.app` (debug) is installed and force-stopped.
  - App-private storage holds only `no_backup/models/gemma-4-E4B-it.litertlm` (3.66 GB) plus its `.verified` marker.
  - Nothing of ours is in `/data/local/tmp` or `/sdcard`, and no M³ processes are running.
  - Android's low-memory killer closed about 10 cached apps during the benchmark (normal, and reported to the owner).
- **PC:**
  - adb server stopped, emulator off, temp scripts deleted.
  - Both model files remain in `M3/models/` (gitignored, hashes verified) for re-installs.
  - The full benchmark log is in the session scratchpad (`m3-bench-results.txt`); its numbers are already in ADR 0002.
- **Git:**
  - The owner approved, and **PR #1 and PR #2 are squash-merged into `main`** (`b1da31f`, `3a7b029`). PR #2 was rebased onto the new main first, with the tree verified identical to the tested commit. The old branches are deleted.
  - Current branch: `feat/phase-1b-foundation`, holding the docs commit for ADR 0008.
- **Owner decisions (2026-10-18):**
  - **Metric only.**
  - **Training locations + equipment, Coach-adaptable:** "I only have X and Y" revises a session, day or week. Recorded in **ADR 0008**, PRODUCT_SPEC, README tasks and CLAUDE.md.

## Next steps, in order
1. Give the owner a time estimate for Phase 1b, then build:
   - `:core:security`: Keystore-wrapped Tink keyset, biometric/credential lock (decide the biometric version).
   - `:core:data`: Room 2.8.5 + SQLCipher 4.19.1, persisting `UiConfig` + history. Include the schema for locations, inventories and the weekly schedule (ADR 0008).
   - Onboarding: profile (metric), goal, experience, locations with equipment tick-lists, schedule, injuries.
2. **Phase 1c:** exercise DB with equipment/pattern/muscle tags, adaptation engine (ADR 0008), plan generator, Coach chat. Load E4B with `LlmBackend.GPU` + `speculativeDecoding = true` and fall back to CPU if GPU init fails. Unload when the app goes to the background. Then the fitness features (README checkboxes).

## Open questions for the owner
- (Answered: merges approved, metric, gym + home with Coach-driven equipment adaptation.)

- Before going public: keep the Gmail address in commit history, or switch to the GitHub noreply address? (OWNER_TASKS #5.)

## Traps found
- **Copying models to the phone:**
  - Use the USB serial (`c84e26c7`); Wi-Fi is about 1.5 MB/s and drops.
  - `adb exec-in` streams reset every ~100–300 MB even over USB. Resume from the remote size: `adb shell run-as com.m3.app stat -c %s <path>` (treat "No such file" as 0), then `tail -c +N file | adb exec-in run-as com.m3.app sh -c 'umask 077; cat >> <path>'`, and repeat until the size matches. The app's SHA-256 check is the final guard.
  - **Never** wrap the size check in `sh -c "… || echo 0"`. adb re-splits it, so it always returns 0, and a "resume" built on it appends duplicate data. This happened once; the corrupt file was deleted.
- **Leftover processes:**
  - `TaskStop` on a background bash job doesn't kill its children on Windows; kill them with PowerShell `Stop-Process`.
  - An interrupted `exec-in` leaves `sh` and `cat` running on the phone under the app uid. Check with `adb shell ps -A -o USER,PID,NAME | grep u0_a281` and stop them with `adb shell run-as com.m3.app kill …`.
- **Xiaomi input:**
  - Xiaomi blocks `adb shell input tap/swipe` (INJECT_EVENTS) unless the owner enables "USB debugging (Security settings)". Don't ask for that: let the owner tap, and read the screen with `uiautomator dump` plus results from `logcat -s M3Bench` (debug builds only).
  - The USB device can go `offline`; `adb reconnect offline` fixes it.
  - Wireless: use the mDNS serial (`adb mdns services`); the IP:port transport goes `offline`. Wireless debugging switches itself off when the screen sleeps.
- **First install** on Xiaomi needed "Install via USB". A failed install shows an empty error.
- **LiteRT-LM:**
  - `getBenchmarkInfo()` is a function and needs `@OptIn(ExperimentalApi::class)`.
  - `ExperimentalFlags` are process-wide.
  - The AAR has no R8 rules; we keep `com.google.ai.edge.litertlm.**`.
  - The first GPU load compiles programs (≈20–28 s); later loads use the cache (≈10–17 s).
- **Emulator:** the AVD name is `Medium_Phone_API_37.0`. Use `-memory 6144` for model tests.
- **Windows:** `./gradlew clean` can fail on a locked `classes.jar`; build without `clean`.
- **Network:** Hugging Face and raw.githubusercontent downloads drop or reset. Resume with `curl -C -`; `gh api -H "Accept: application/vnd.github.raw"` works for GitHub files.
- **Builds:** `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. After dependency changes, run `./gradlew --write-verification-metadata sha256 :app:assembleDebug :app:assembleRelease test lint`.
- **Phone rules, `MSYS_NO_PATHCONV=1`, FLAG_SECURE:** see CLAUDE.md.
