# HANDOFF — 2026-10-18 (end of session 4)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## ▶ Next steps, in order
1. **Owner decision:** review PR "Phase 1b" (`feat/phase-1b-foundation` → `main`) and say whether to squash-merge it. Don't merge without that.
2. **Phone install** (needs the owner to plug in the USB cable, serial `c84e26c7`). It can happen before or after the merge, since it's the same debug build:
   1. `adb devices` shows `c84e26c7` (`adb reconnect offline` if it's offline).
   2. `./gradlew :app:assembleDebug`, then `adb -s c84e26c7 install -r app/build/outputs/apk/debug/app-debug.apk`. This upgrades in place, keeps the 3.66 GB model and uses the same debug key.
   3. The owner opens M³, unlocks, and taps through onboarding following OWNER_TASKS "Try it yourself — Phase 1b" (Xiaomi blocks injected taps). Read the screen with `uiautomator dump /data/local/tmp/m3-ui.xml`, `cat` it, and `rm` it in the same command.
   4. `adb -s c84e26c7 shell am force-stop com.m3.app`, then `adb kill-server`.
   5. Report exactly what changed on the phone:
      - The app was upgraded.
      - New private files: `databases/m3.db` (+ `-wal`/`-shm`), `no_backup/db.key`, `shared_prefs/m3_keyset_prefs.xml`.
      - A Keystore key `m3_master_key`.
      - Nothing outside the app.
   6. Tick "Phone install + owner's check" in the README.
3. **Phase 1c** (README checkboxes):
   - Exercise DB tagged with equipment/pattern/muscles.
   - Adaptation engine + Coach tools (ADR 0008).
   - Plan generator.
   - Coach chat (E4B on GPU + MTP, CPU fallback, unload in background).
   - Logging, overload, step counter.
   - Equipment is in `:core:model` (`EquipmentKind`), and the setup is read via `TrainingSetupRepository.observe()`. `day_override` exists as a table but has no DAO yet.

## What was done this session
- **Phase 1b built** on `feat/phase-1b-foundation`. Commits: `b59fbdc` (code), `c024e1e` (review fixes + docs), plus this handoff.
  - **`:core:model`:** equipment catalog (50 kinds, 4 groups), profile, training setup, validators, `Units`.
  - **`:core:security`:**
    - `TinkSecretCipher`: Keystore master key `android-keystore://m3_master_key`, keyset in prefs `m3_keyset_prefs`.
    - `DatabasePassphrase`: `no_backup/db.key`, written atomically, never overwritten.
    - `AppLock`: 5 min, monotonic clock, one auto-prompt per lock.
    - `UnlockPrompt`: platform BiometricPrompt, strong biometric or device credential.
  - **`:core:data`:**
    - Room 2.8.5 + SQLCipher 4.19.1, `m3.db`, schema v1 JSON in `core/data/schemas`.
    - `DatabaseOpener`: lazy, IO, non-cancellable open, no destructive fallback.
    - Repositories.
  - **`:app`:**
    - `AppStartup`: open DB, restore UI config, save every change after the first.
    - `MainActivity` lock layer: the app stays composed under an opaque, touch- and Back-blocking lock screen.
    - Onboarding in `feature/onboarding`, with shared form sections.
    - You → Profile and You → Training setup.
- **Checks:**
  - 71 unit tests pass.
  - Lint: 0 errors. Warnings: `OldTargetApi`, `ChromeOsAbiSupport`, and `NewerVersionAvailable` for Tink 1.24.0, which is deliberately not used yet (see decision log).
  - 4 instrumented SQLCipher tests pass on the emulator.
  - Debug and release builds assemble.
- **Emulator check:**
  - Lock prompt on cold start (emulator PIN 1234).
  - Cancel shows the Locked screen; Unlock works.
  - Full onboarding: validation hints, Gym/Home/Park, kg list `2-24`, custom item, schedule, summary.
  - Edits saved; dark mode + profile survive a force-stop.
  - Short background stays unlocked; more than 5 min locks again (passed twice on the fixed build).
- **Sonnet review:** 10 findings. 1–8 fixed, 9 documented as a limitation. 10 is pre-existing debug-only benchmark logging that the decision log allows.

## Current state
- **Git:**
  - `main` = `3a7b029`.
  - `feat/phase-1b-foundation` is pushed with an open PR to `main`, not merged.
- **Phone:** unchanged since session 3. Phase 1a build installed, model present, adb disconnected.
- **PC:** emulator stopped, adb server stopped. The emulator still has the test app with test data and PIN 1234; that's fine, it's only the emulator.

## Open questions for the owner
- Merge the Phase 1b PR? (Required before merging.)
- Not urgent (OWNER_TASKS #5): before going public, keep the Gmail address in commit history, or switch to the GitHub noreply address?

## Things to know
- **Unexplained one-off:** the very first 5-minute lock test (build before the review fixes) came back unlocked. It wasn't timed, so it may not even have waited 5 minutes. The fixed build passed twice with uptime measured, including the exact same sequence (cancel → Unlock → PIN → Home → 310 s). The most likely cause is review finding 7: an unlock callback arriving after the activity stopped cleared the timer. That's now fixed and unit-tested. Re-check on the phone (OWNER_TASKS Phase 1b, step 7).
- **SQLCipher passphrase:** SQLCipher keeps the passphrase array to open more connections, so it must not be zeroed (decision log).
- **Locked state:** while locked, the app stays composed underneath. Anything new that runs in the background or takes key input must respect `locked`.

## Traps found
- **This PC:**
  - No Python. Bash heredocs with tricky quoting can fail, so write source files with the Write tool.
  - `--write-verification-metadata` can fail at the very end with "user-mapped section open" (Windows file lock); just re-run it.
- **Kotlin:** `StateFlow.conflate()` is a deprecation *error*, because StateFlow is already conflated.
- **Instrumented tests:**
  - Always set `ANDROID_SERIAL=emulator-5554` so they can never run on the phone.
  - The result file name has spaces; use `find … -print0 | xargs -0`.
- **Emulator UI checks:**
  - `adb -e shell locksettings set-pin 1234` (emulator only, never the phone).
  - In `uiautomator` dumps, Compose radio selection appears as `checked="true"` on the parent node.
  - Field positions shift when error text appears; re-dump before each tap.
  - The helper script from this session (`ui.sh`) lived in the session scratchpad and is gone. Its logic: dump → grep bounds → `input tap` at the centre.
- **Older traps** (model copying over USB with resumable `exec-in`, Xiaomi tap blocking, LiteRT-LM quirks, `JAVA_HOME`, no `./gradlew clean`): see `git show ebfcf61:HANDOFF.md`. They still apply.
