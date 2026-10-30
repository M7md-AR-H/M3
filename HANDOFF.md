# HANDOFF — 2026-10-21 (session 5)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## ▶ Next steps, in order
1. Do the normal session start: CLAUDE.md, docs/WORKING_RULES.md, this file, README roadmap + decision log, `git status`, `git log -5`. Give a time estimate first.
2. **Owner re-check of `fix/phase-1c-phone-feedback` on the phone.** Only once the owner says the phone is connected.
   1. Build with `./gradlew :app:assembleDebug`, then `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`.
   2. The owner follows OWNER_TASKS "Phase 1c-2 fixes — re-check". Claude reads the screen with the polling dump (see Traps).
   3. Clean up: force-stop the app, then `adb disconnect` and `adb kill-server`. Report the changes on the phone; this time it's only the app upgrade.
3. **Open a PR** for `fix/phase-1c-phone-feedback`. Ask the owner before squash-merging.
4. **Decide on the model, using evidence.** The owner is open to replacing Gemma 4 E4B if it proves unreliable. Signs so far:
   - It sometimes leaves out the tool line.
   - It claims changes were done (3 of 12 replies in the eval).
   - It attaches unrelated tools (dropped by the relevance guard).
   - It used `set_day` for "tomorrow".

   If the re-check still shows real failures:
   - Research on-device candidates that run on LiteRT-LM on the Poco F5 and whose licence allows redistribution.
   - Run them through the existing benchmark and `CoachPromptEval`.
   - Propose a switch, recording it in ADR 0002 and the decision log.
5. **Phase 1c-3: workout logging, focus mode, progressive overload.** See the notes below. Give an estimate first (2–3 h guess). It could include the idle unload (free the model after ~5 min with no Coach message); the owner was offered it but hasn't agreed yet.

## Session 5 (2026-10-21): what was done
- **Phone check of 1c-1 and 1c-2** (README ticked). The owner tapped; Claude read the screen.
  - **Passed:**
    - Runs on the GPU: the status line says so, and there's an `mldrift` program cache of 33 MB.
    - Answer times: cold ~25–45 s including the load, warm ~6 s.
    - Week, sessions and the Today card are correct.
    - One-day bodyweight change: Apply, then Undo, both work.
    - Avoid exercise works.
    - A plain question gets no card.
    - Stop works.
    - Home frees the model; the app is ~164 MB afterwards.
  - **Phone changes:**
    - App upgraded in place.
    - DB v2 tables added.
    - `no_backup/llm-cache` written (33 MB).
    - Old `cache/llm` (3.2 GB) deleted.
    - Force-stopped, disconnected, adb server killed.
- **Fixes on branch `fix/phase-1c-phone-feedback`** (README "1c-2 fixes" and decision log, 2026-10-21):
  - **Theme fallback:** a plain theme request gets an app-made proposal when the model gives no usable tool line, including after a rejected one. Negations and questions don't trigger it.
  - **Claim notes:** a reply that claims a change gets a note from the app, with three variants: not done yet, nothing changed, or locked.
  - **Locks:** the prompt asks the model to still write the tool line for locked changes, so the "Locked by you" card appears.
  - **Pain:** a pain reason avoids the whole movement family, and "use … again" lifts the family again. One transaction, one Undo (`UndoInfo.Exercises`). Train says when a slot is empty because of avoided exercises (`PlannedSession.unfilledByAvoided`).
  - **`ProposalStatus.NO_CHANGE`:** "Nothing to change", with no Apply.
  - **UI (Sonnet):**
    - `Motion` tokens; NavHost fades; animated chat items and cards; theme colours cross-fade.
    - The keyboard closes on send, and the Enter key sends.
    - Today shows a greeting and honest placeholder cards.
    - The "Loads" note shows only when the session uses equipment.
  - **Checks:**
    - 115 unit tests pass; lint 0 errors; debug APK builds.
    - Prompt eval: **11/12** (emulator CPU; new cases for the locked request and "suggest the dark theme"); claims in 3 of 12.
    - Sonnet review: findings fixed, except the theme fade cost, which is listed as a known limitation.
  - **Not yet:** phone re-check, PR.

## Current state
- **Git:**
  - `main` holds Phases 0–1c-2 (PRs #1–#5).
  - Branch `fix/phase-1c-phone-feedback` is committed locally and not pushed. No PR yet.
- **Phone:**
  - It has the 1c-2 build (main) from 2026-10-21, not the fix branch.
  - The app is force-stopped and adb is disconnected.
- **PC:**
  - Emulator stopped; adb server stopped.
  - The emulator's app was uninstalled for the eval, so its test data and its model copy are gone. Re-onboard it if it's needed.

## Phase 1c-3 notes (not started)
- **Logging:**
  - A DB v3 migration: sessions and sets (exercise id, kg, reps/seconds, optional RPE, date).
  - Log from Train or Today.
  - A "focus mode" screen (inverted dark, per the decision log) for live sets, with a rest timer.
- **Progressive overload, in deterministic Kotlin (ADR 0003):**
  - Per exercise *variant*: the exercise id plus the equipment used (ADR 0008).
  - Loads come from the owner's dumbbell/kettlebell kg lists (`EquipmentDetails.weightsKg`).
- **Coach:**
  - It reads the logged history facts.
  - Keep the prompt budget.
  - Re-run the prompt eval after any prompt change.
- **Steps (1c-4):** phone step counter, then the Phase 1 "Try it yourself" list.

## How to re-run the prompt eval
1. **Start the emulator:** `$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe -avd Medium_Phone_API_37.0 -no-snapshot-save -no-audio -no-boot-anim` (run it in the background).
2. **Unlock it** (PIN 1234), then run `svc power stayon true`.
3. **Free space:** the disk is 10 GB. Uninstall `com.m3.app` on the emulator if it holds a model copy.
4. **Push the model:** `adb push models/gemma-4-E4B-it.litertlm /data/local/tmp/m3-eval/model.litertlm` (needs `MSYS_NO_PATHCONV=1`), then `chmod 644`.
5. **Run the eval:** `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.m3.app.CoachPromptEval`.
   - It takes **~2 min per case** (CPU, 4 GB RAM, swapping), so about 25 min for 12 cases.
   - Run it in the background. Don't start another Gradle build meanwhile: it fails on a file lock.
6. **Read the results:** `adb logcat -d -s M3Eval:I`.
7. **Clean up:**
   - `rm -rf /data/local/tmp/m3-eval`.
   - `svc power stayon false`.
   - `adb emu kill`.

## Open questions for the owner
- Not urgent (OWNER_TASKS #5): before going public, keep the Gmail address in commit history, or switch to the GitHub noreply address?
- Idle unload of the model in the foreground (5 min): wanted?

## Traps found
- **Tooling on this PC:**
  - `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` before every Gradle call.
  - adb is at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe` and isn't on PATH.
  - There's no Python and no `bc`.
- **The phone over USB is flaky.** Wireless serial: `adb-c84e26c7-oQZ4D2._adb-tls-connect._tcp`. USB serial: `c84e26c7`.
- **Reading the phone screen while the owner taps:**
  - Use a loop of `uiautomator dump /data/local/tmp/m3-ui.xml; cat …; rm …`, filtered to `package="com.m3.app"` nodes.
  - The owner often reads this chat in the Claude app on the same phone. Unfiltered dumps would read that app; never do that.
  - The owner asked not to be asked "are you there?": poll the screen instead.
- **Model behaviour (E4B):**
  - It repeats its own earlier false claims from chat history. That's why the app's notes are stored with the reply.
  - It sometimes writes a valid but unrelated tool; the relevance guard drops it.
- **Agent worktrees:** `.claude/worktrees/` is now in `.git/info/exclude`.
  - Don't `git add -A` while a worktree exists.
  - Deleting one can hit "Filename too long". Use PowerShell `Remove-Item -LiteralPath "\\?\C:\…" -Recurse -Force`.
- **LiteRT-LM native crashes** (they can't be caught, and they kill the app):
  - Closing a conversation while the runtime still works crashes it (SIGSEGV). `LiteRtLmEngine.generate` therefore calls `cancelProcess()` and waits for `onDone`/`onError` before closing.
  - A failed weight-cache write aborts it (SIGABRT). So the GPU cache is used only with ≥ 1 GB free, and there's no cache on the CPU.
  - `cacheDir` can be purged mid-write, so the cache lives in `no_backup/llm-cache`.
- **The emulator GPU path** takes about 7 minutes and then fails, so emulators skip the GPU.
- **Emulator:**
  - With a PIN, user storage stays locked after boot until it's unlocked.
  - `connectedAndroidTest` on `:app` uninstalls the app, which wipes its data.
  - Always set `ANDROID_SERIAL=emulator-5554`.
- **Tests:** a JUnit test method must return `Unit` (`fun run(): Unit = runBlocking { … }`).
- **uiautomator:**
  - Dump text is XML-escaped (`&amp;`).
  - Re-dump before every tap, because positions shift.
- **Editing:**
  - `sed` loses `\\` escapes. Use the Write/Edit tools for Kotlin.
  - Complex `awk` with backticks breaks inside bash `eval`; use Edit instead.
- **Gradle:**
  - `--write-verification-metadata` may fail at the end on a Windows file lock; re-run it.
  - `StateFlow.conflate()` is a compile error.
- **SQLCipher:** the passphrase array must not be zeroed.
- **Lock layer:** while locked, the app stays composed under the lock layer. Anything new that runs in the background or takes key input must respect `locked`.
- **Older traps:** `git show ebfcf61:HANDOFF.md`.
