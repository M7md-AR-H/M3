# HANDOFF — 2026-10-18 (end of session 4)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## ▶ Start of the next session
1. Do the normal session start: CLAUDE.md, docs/WORKING_RULES.md, this file, README roadmap + decision log, `git status`, `git log -5`. Give a time estimate first.
2. **Phone check.** This is pending, and the owner decides when.
   - **Only when the owner says the phone is connected.** Use USB `c84e26c7`, or the wireless mDNS serial `adb-c84e26c7-…._adb-tls-connect._tcp` if USB isn't present.
   - It covers Phase 1c-1 and 1c-2 together. Neither has been on the phone yet; the phone still has the Phase 1b build.
   1. `./gradlew :app:assembleDebug`, then `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`. This upgrades in place and keeps the model and data. The DB migrates v1 → v2 automatically, tested on the emulator from an encrypted v1 DB with data.
   2. The owner follows OWNER_TASKS "Try it yourself — Phase 1c-1" and "— Phase 1c-2". Xiaomi blocks injected taps, so the owner taps; read the screen with `uiautomator dump /data/local/tmp/m3-ui.xml`, `cat` it, and `rm` it in the same command.
   3. Check the Coach status line reads "Running on this phone's **GPU**". Time the first answer (includes the load) and the second.
   4. Optional: delete the old benchmark cache with `adb -s <serial> shell run-as com.m3.app rm -rf cache/llm`. It's the app's own file; the Coach now uses `no_backup/llm-cache`.
   5. Force-stop the app, run `adb disconnect` and `adb kill-server`, then report what changed:
      - The app was upgraded.
      - New DB tables inside the same `m3.db`.
      - `no_backup/llm-cache/` (GPU cache, ~300 MB).
   6. Tick "Phone check" for 1c-1 and 1c-2 in the README. Fix anything the owner reports.
3. **Phase 1c-3: workout logging, live focus mode, progressive overload.** See the next section. Give the owner an estimate before starting. A reasonable guess is 2–3 h, but re-estimate.

## Phase 1c-3 notes (not started)
- **Logging:** a DB v3 migration (sessions, sets: exercise id, kg, reps/seconds, RPE optional, date). Log from Train/Today; a "focus mode" screen (inverted dark, per the decision log) for live sets with a rest timer.
- **Progressive overload, in deterministic Kotlin (ADR 0003):**
  - Per exercise *variant*: the exercise id plus the equipment used (ADR 0008: home and gym tracks stay separate).
  - Loads are picked from the owner's dumbbell/kettlebell kg lists. Those lists are already stored in `EquipmentDetails.weightsKg`; the planner ignores them so far (README known limitation).
- **Coach:** it should read the logged history facts. Keep the prompt budget (`CoachPrompt.PROMPT_CHARS`), and re-run the prompt eval after any prompt change (instructions below).
- **Steps (1c-4):** phone step counter, then the Phase 1 "Try it yourself" list.

## What was done in session 4 (2026-10-18)
- **Phase 1b** (PR #3, merged; installed on the phone, owner onboarded):
  - App lock.
  - SQLCipher DB with a Keystore/Tink-wrapped key.
  - Saved UI config.
  - Onboarding.
- **Phase 1c-1** (PR #4, merged), owner feedback "the Coach should decide the split" → ADR 0009:
  - Availability per day.
  - `:core:training`: 101 curated exercises and `PlanGenerator`.
  - Train tab and Today card.
- **Phase 1c-2** (PR #5, merged), ADR 0010:
  - On-device Coach chat.
  - `:coach` module: prompt, tool schemas, validator, relevance guard.
  - Validated proposals with a plan diff → Apply / Not now → Undo.
  - Owner locks "Your plan" / "Your free days".
  - DB v2 (chat, excluded exercises).
  - Model runtime (GPU+MTP → CPU, unload in the background, safe Stop).
  - **Checks:**
    - Prompt eval 5/8 → 10/10 (emulator, real model, test data).
    - Emulator end-to-end chat/apply/undo/stop.
    - 110 unit tests; 5 DB instrumented tests; lint 0 errors.
    - Sonnet reviews done for each phase and their findings fixed.

## Current state
- **Git:** `main` includes Phases 0–1c-2 (PRs #1–#5, all squash-merged). No open PRs.
- **Phone:**
  - Phase 1b debug build installed; the owner has onboarded; Gemma 4 E4B present.
  - App force-stopped, adb disconnected, phone unplugged by the owner.
- **PC:** emulator stopped, adb server stopped. The emulator (`Medium_Phone_API_37.0`) has test data, PIN 1234, and a copy of the model inside the app. That's emulator only and fine.

## Not verified yet
- **Coach speed and behaviour on the phone's GPU,** including the GPU cache write and Stop during prefill on the GPU.
- **The last prompt tweaks** ("Tomorrow is …", one-line sanitised owner text, whole-prompt budget) weren't re-scored. Three live emulator chats with them were all correct. To re-run the eval:
  1. On the **unlocked emulator**: `adb push <model> /data/local/tmp/m3-eval/model.litertlm` (Windows path, `MSYS_NO_PATHCONV=1`), then `chmod 644`.
  2. `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.m3.app.CoachPromptEval`.
  3. Read `adb logcat -s M3Eval`.
  4. Then `rm -rf /data/local/tmp/m3-eval`. The emulator disk is only 10 GB. Note that this test run uninstalls the app, which wipes its emulator data.

## Open questions for the owner
- When should the phone check happen? It needs the phone connected.
- Not urgent (OWNER_TASKS #5): before going public, keep the Gmail address in commit history, or switch to the GitHub noreply address?

## Traps found
- **LiteRT-LM native crashes** (they can't be caught, and they kill the app):
  - Closing a conversation while the runtime still works crashes it (SIGSEGV). `LiteRtLmEngine.generate` therefore calls `cancelProcess()` and waits for `onDone`/`onError` before closing.
  - A failed weight-cache write aborts it (SIGABRT). So: the GPU cache is used only with ≥ 1 GB free, and there's no cache on the CPU.
  - `cacheDir` can be purged mid-write, so the cache lives in `no_backup/llm-cache`.
- **The emulator GPU path** takes about 7 minutes and then fails, so emulators skip the GPU (`Build.HARDWARE` ranchu/goldfish).
- **Emulator:**
  - With a PIN, user storage stays locked after boot until unlocked: cacheDir isn't writable and uiautomator dumps fail. Unlock with `input keyevent 82`, swipe up, PIN 1234, Enter. For long runs use `svc power stayon true` and reset it afterwards.
  - Disk is 10 GB; two model copies plus caches fill it.
  - `connectedAndroidTest` on `:app` uninstalls the app, which wipes its data.
  - Instrumented tests: always set `ANDROID_SERIAL=emulator-5554`.
- **Tests:** a JUnit test method must return `Unit` (`fun run(): Unit = runBlocking { … }`).
- **uiautomator:** dump text is XML-escaped (`&amp;`). Compose radio selection shows as `checked="true"` on the parent node. Re-dump before every tap, because positions shift.
- **Bash and sed on this PC:**
  - There's no Python.
  - `sed` replacements lose `\\` escapes, and line-number edits are fragile. Use the Write/Edit tools for Kotlin source.
  - `sed` turns CRLF files into LF; that's harmless.
- **Gradle:** `--write-verification-metadata` may fail at the end on a Windows file lock; re-run it. `StateFlow.conflate()` is a compile error.
- **SQLCipher:** the passphrase array must not be zeroed (decision log).
- **Lock layer:** while locked, the app stays composed under the lock layer. Anything new that runs in the background or takes key input must respect `locked`.
- **Older traps** (model copying over USB with resumable `exec-in`, Xiaomi, `JAVA_HOME`): `git show ebfcf61:HANDOFF.md`.
