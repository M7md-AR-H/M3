# HANDOFF — 2026-10-18 (end of session 3)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## ▶ If the owner's first message is just "go"
That means **start Phase 1b now**, exactly as planned below. The owner has already seen and accepted the estimate (2026-10-18). Work through these steps:

1. Do the normal session start: read CLAUDE.md, docs/WORKING_RULES.md, this file, then the README roadmap and decision log. Run `git status` and `git log -5`.
2. In your first reply, restate the estimate in one short table: **about 2–3½ h total**.
   - Security + app lock: 40–60 min.
   - Encrypted database + persisted `UiConfig`: 50–80 min.
   - Onboarding: 40–70 min.
   - Final phone install: needs the owner to plug in the USB cable.

   Then begin. Don't ask further questions: every decision needed is recorded below or in the ADRs.
3. Work on branch **`feat/phase-1b-foundation`**. It already exists locally and is pushed, and it contains the ADR 0008 docs commit. Open a PR to `main` when done, and **ask the owner before merging**.

## Phase 1b plan (decisions already made; record any new non-obvious ones in the README decision log)

### Step 0: verify versions (Maven metadata, as in earlier sessions)
- Check the latest stable versions of:
  - Room (planned 2.8.5)
  - `net.zetetic:sqlcipher-android` (4.19.1)
  - Tink Android `com.google.crypto.tink:tink-android` (1.23/1.24)
  - `androidx.biometric:biometric`
  - DataStore (only if needed)
- Biometric: prefer the newest **stable** version. If only 1.1.0 is stable, use it and note it in the decision log.
- Pin every version in `gradle/libs.versions.toml`, add a decision-log row per new dependency, then regenerate verification metadata (command under Traps).

### Step 1: `:core:security` (new Android library module; add it to settings.gradle.kts and README "Project structure")
- **Tink AEAD:**
  - Keyset AES256-GCM via `AndroidKeysetManager`.
  - Master key in the Android Keystore (`android-keystore://m3_master_key`).
  - Keyset stored in an app-private prefs file. Backups are already disabled.
- **DB passphrase:**
  - 32 random bytes from `SecureRandom`, generated once.
  - Encrypted with the Tink AEAD and stored in `noBackupFilesDir/db.key`.
  - Decrypted in memory at startup, and the byte array zeroed after handing it to SQLCipher.
- **App lock:**
  - `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
  - Shown on cold start and after **5 minutes** in the background. Keep the timeout as a constant now; it becomes a setting later.
  - Content must not be visible before unlock. `FLAG_SECURE` is already on.
- **Not doing now:** binding the Keystore key to user authentication. The lock is a UI gate, and the data is protected at rest by SQLCipher + Keystore. Log this as a known limitation with the reason: background work and the Coach need DB access without a prompt every time.
- **Unit tests:** pure logic only (timeout logic, passphrase wrapping through an injectable AEAD interface).

### Step 2: `:core:data` (new module)
- Room 2.8.5 with KSP. SQLCipher via `SupportOpenHelperFactory(passphrase)`. Call `System.loadLibrary("sqlcipher")` once before opening.
- **Tables (v1 schema, export the schema JSON):**
  - `ui_config`: a single row holding the `UiConfig` JSON (kotlinx.serialization) plus a version.
  - `ui_config_history`: id, json, actor, createdAt. Capped at 100, matching the current in-memory cap.
  - `profile`: single row with birthYear, sex (for energy formulas later), heightCm, weightKg, goal, experience, injuriesText, createdAt. **Metric only.**
  - `training_location`: id, name, sortOrder. Seeded with "Gym" and "Home" only if the owner picks them in onboarding.
  - `location_equipment`: locationId, kind (enum from ADR 0008), detailsJson (e.g. available dumbbell kg list), customName nullable.
  - `schedule_day`: dayOfWeek 1–7 and a nullable locationId (null = rest).
  - `day_override`: date, itemsJson, createdAt. Table only for now; the Coach uses it in 1c.
  - `app_state`: onboardingCompleted, schemaVersion.
- **Equipment enum:** put it in a module that both data and the future training engine can use, e.g. `:core:model` (plain Kotlin data/enums). Covers the ~40 kinds listed in ADR 0008.
- **Persist `UiConfigStore`:**
  - Give it a small persistence interface: load at startup, save on apply, undo and setLock.
  - The `:app` DI module wires it to Room. This removes the README limitation "UI customizations are lost when the app restarts" (delete that row when it's done).
- **Tests:** DAO/migration tests as **instrumented tests on the emulator only** (`Medium_Phone_API_37.0`), never on the phone. Mapper and validation logic as JVM unit tests.

### Step 3: Onboarding (`:app` feature package `onboarding`)
- Shown when `onboardingCompleted == false`, after unlock. Magazine style per the prototype; read all styling from `UiConfig`/M3 tokens.
- **Steps:**
  1. Welcome.
  2. About you: height cm, weight kg, birth year, sex. Validate ranges in code.
  3. Goal: lose fat, build muscle, get stronger, or general fitness.
  4. Experience: new, some, or experienced.
  5. **Locations:** toggles for Gym and Home, plus "Add location" with a name.
  6. **Equipment tick-list per location**, grouped as free weights, stations and small gear, with "Bodyweight only" as a shortcut. For dumbbells/kettlebells, an optional weight list in kg.
  7. **Weekly schedule:** for each day, choose a location or Rest.
  8. Injuries/limitations: free text, stored locally only.
  9. Done.
- Everything stays editable later from **You**. Add a "Training setup" entry; a simple list editor is enough for now.
- Never log any of this data.

### Step 4: Finish
- Build, run tests and lint, then run the emulator check (onboarding flow, lock prompt with an emulator PIN, customizations survive an app restart).
- Get a Sonnet review of the diff and fix its findings.
- **Phone:**
  1. The owner plugs in the USB cable (serial `c84e26c7`).
  2. `adb install -r` the debug APK. This upgrades in place and keeps the 3.66 GB model, same debug key.
  3. The owner taps through onboarding and the lock (Xiaomi blocks injected taps).
  4. Read the screen with `uiautomator dump`.
  5. Force-stop, `adb kill-server`, and report exactly what changed.
- **Docs:** README (ticks, status, decision log, limitations), OWNER_TASKS "Try it yourself — Phase 1b", this file.

## What was done (sessions 1–3)
- **Sessions 1–2 (2026-10-16/17):** spec, UI prototype, ADRs 0001–0007, the Phase 0 app shell (customizable `UiConfig`, 5 tabs, Coach-change simulation with undo and locks). Merged as PR #1.
- **Session 3 (2026-10-18):**
  - **Phase 1a (merged as PR #2):**
    - `:ai` module with the model catalog/store (SHA-256 verified), the `LlmEngine` over LiteRT-LM 0.18.0, and the benchmark.
    - You → "AI model & benchmark" screen.
    - **On the Poco F5: Gemma 4 E4B on GPU + MTP was chosen** (15.6 tok/s, 0.7 s first token, 3.7 s photo, about 1.1 GB). See ADR 0002.
  - **License:** PolyForm Noncommercial 1.0.0. OWNER_TASKS #5 has the go-public checklist.
  - **Owner decisions → ADR 0008:**
    - Metric only.
    - Training locations (Gym, Home, custom) each with equipment.
    - "I only have X and Y" makes the Coach adapt a session, day, week or plan. A deterministic Kotlin engine picks the substitutes and loads, and the owner applies or undoes the diff, with owner locks.

## Current state
- **Git:**
  - `main` = `3a7b029` (Phase 0 + 1a).
  - Branch `feat/phase-1b-foundation` = `main` + the ADR 0008/docs commit. It is pushed; check with `git log -3`.
  - No open PRs.
- **Builds:** 34/34 unit tests, lint 0 errors (accepted warnings: `OldTargetApi`, `ChromeOsAbiSupport`).
- **Phone (Poco F5, Android 15):**
  - `com.m3.app` debug is installed and force-stopped.
  - App-private storage holds only `no_backup/models/gemma-4-E4B-it.litertlm` and its `.verified` marker.
  - Nothing in `/data/local/tmp` or `/sdcard`. adb disconnected.
  - The owner may have turned Wireless debugging off; use USB.
- **PC:**
  - adb server stopped, emulator off.
  - Model files are in `M3/models/` (gitignored, hashes verified).
  - The full benchmark log is in the session-3 scratchpad (its numbers are already in ADR 0002).

## Later phases (for orientation)
- **Phase 1c:**
  - Exercise DB tagged with equipment alternatives, movement pattern and muscles.
  - Adaptation engine + Coach tools (ADR 0008).
  - Plan generator.
  - Coach chat: load E4B with `LlmBackend.GPU` + `speculativeDecoding = true`, fall back to CPU if GPU init fails, unload when backgrounded.
  - Logging, progressive overload, step counter.
- All of these are listed as README checkboxes.

## Open questions for the owner
- Before going public: keep the Gmail address in commit history, or switch to the GitHub noreply address? (OWNER_TASKS #5, not urgent.)

## Traps found
- **Copying models to the phone:**
  - Use the USB serial (`c84e26c7`); Wi-Fi is about 1.5 MB/s and drops.
  - `adb exec-in` streams reset every ~100–300 MB even over USB. Resume from the remote size: `adb shell run-as com.m3.app stat -c %s <path>` (treat "No such file" as 0), then `tail -c +N file | adb exec-in run-as com.m3.app sh -c 'umask 077; cat >> <path>'`, and repeat until the size matches.
  - **Never** use `sh -c "stat … || echo 0"` through adb. It always returns 0, and a resume built on it appends duplicate data.
- **Leftover processes:**
  - `TaskStop` on a background bash job doesn't kill its children on Windows; use PowerShell `Stop-Process`.
  - An interrupted `exec-in` leaves `sh` and `cat` on the phone under the app uid (`u0_a281`). Check with `adb shell ps -A -o USER,PID,NAME | grep u0_a281` and stop them with `adb shell run-as com.m3.app kill …`.
- **Xiaomi:**
  - `adb shell input tap/swipe` is blocked (INJECT_EVENTS). Don't ask the owner to change security settings: they tap, you read with `uiautomator dump`.
  - USB can go `offline`; `adb reconnect offline` fixes it.
  - Wireless: use the mDNS serial from `adb mdns services`. Wireless debugging switches itself off when the screen sleeps.
  - A failed install shows an empty error; "Install via USB" must be on (it is now).
- **LiteRT-LM:**
  - `getBenchmarkInfo()` is a function and needs `@OptIn(ExperimentalApi::class)`.
  - `ExperimentalFlags` are process-wide.
  - The AAR has no R8 rules (we keep `com.google.ai.edge.litertlm.**`).
  - The first GPU load compiles programs (≈20–28 s); later loads are faster.
- **Emulator:** the AVD name is `Medium_Phone_API_37.0`. Use `-memory 6144` for model tests. Kill it with `adb -e emu kill`.
- **Windows:** `./gradlew clean` can fail on a locked `classes.jar`; build without `clean`.
- **Network:** Hugging Face and raw.githubusercontent downloads drop or reset. Resume with `curl -C -`; `gh api -H "Accept: application/vnd.github.raw"` works for GitHub files.
- **Builds:** `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. After dependency changes, run `./gradlew --write-verification-metadata sha256 :app:assembleDebug :app:assembleRelease test lint`.
- **Phone rules, `MSYS_NO_PATHCONV=1`, FLAG_SECURE (use `uiautomator dump`, not screenshots):** see CLAUDE.md.
