# HANDOFF — 2026-10-21 (end of session 5)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## ▶ Next steps, in order
1. Do the normal session start: CLAUDE.md, docs/WORKING_RULES.md, this file, README roadmap + decision log, `git status`, `git log -5`. Give a time estimate first.
2. **Phone check of PR #6 and PR #7 together.** Only once the owner says the phone is connected.
   1. Check out `feat/phase-1c-logging`, which contains #6. Build with `./gradlew :app:assembleDebug`, then `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`. The DB migrates v2 → v3 by itself; the migration test passed on the emulator.
   2. The owner follows OWNER_TASKS "Phase 1c-2 fixes — re-check" and "Phase 1c-3 — log a workout". Claude reads the screen with the polling dump (see Traps) and doesn't ask "are you there?".
   3. Clean up: force-stop the app, then `adb disconnect` and `adb kill-server`. Report the phone changes:
      - The app was upgraded.
      - New workout tables in the same `m3.db`.
      - Any workouts the owner logged during the test (they're real data, so leave them).
3. **Merge, after asking the owner:** first #6 into `main`, then retarget #7 to `main` and merge it. Both are squash merges.
4. **Decide on the model, using evidence.** The owner is open to replacing Gemma 4 E4B (see memory and the decision log). Evidence so far:
   - The tool line is left out (phone).
   - The model claims changes were done (2–3 of 12–13 eval replies).
   - It attaches unrelated tools.
   - It used `set_day` for "tomorrow" or for "make my plan harder" (these vary run to run).

   If the phone check still shows real failures:
   - Research on-device candidates that run on LiteRT-LM on the Poco F5 and whose licence allows redistribution.
   - Benchmark them, and run `CoachPromptEval` on each.
   - Propose a switch.
5. **Phase 1c-4:** the phone step counter, then the Phase 1 "Try it yourself" list and phone check. Give an estimate first. The owner hasn't decided on the idle model unload (free the model after ~5 min with no Coach message).

## Session 5 (2026-10-21): what was done
- **Phone check of 1c-1 and 1c-2** (README ticked). The owner tapped; Claude read the screen.
  - **Passed:**
    - The Coach runs on the GPU.
    - Answer times: cold ~25–45 s, warm ~6 s.
    - Week, sessions and the Today card are correct.
    - Apply and Undo work.
    - Avoid exercise works.
    - Stop works.
    - Home frees the model (~164 MB afterwards).
  - **Phone changes:**
    - App upgraded.
    - DB v2 tables added.
    - `no_backup/llm-cache` written (33 MB).
    - Old `cache/llm` (3.2 GB) deleted.
- **PR #6, `fix/phase-1c-phone-feedback` (open, not merged):** fixes from that check. Details are in README "1c-2 fixes" and the decision log.
  - The app proposes plain theme requests itself.
  - A reply that claims a change gets a note from the app.
  - A request for a locked area shows the "Locked by you" card.
  - Pain avoids the whole movement family.
  - A new "Nothing to change" card state.
  - UI: `Motion` tokens, fades, keyboard closes on send, Today copy.
  - **Checks:** 115 unit tests, lint 0, eval 11/12, Sonnet review fixed.
  - **Owner re-check:** postponed by the owner.
- **Phase 1c-3, `feat/phase-1c-logging` (PR #7, stacked on #6):**
  - **DB v3:** auto-migration that adds `workout_session` and `workout_set`.
  - **`WorkoutRepository`:**
    - Start or resume a workout.
    - Log a set in one transaction, keeping the set's first completion time.
    - Remove a set.
    - Finish a workout; an empty one is dropped, and an abandoned one closes at its last set's time.
  - **`Progression` (core:training):**
    - Double progression per variant; the working load is the load used for most sets.
    - Load steps: the owner's kg lists, 2.5 kg plates, 5 kg stacks.
    - One step at a time; it steps down after two misses.
  - **`WorkoutTargets`** joins the plan, the kg lists and the history.
  - **Coach facts:** "last …; next …" per day and variant, plus workouts in the last 4 weeks. The prompt says weights come only from "next".
  - **Focus-mode screen** (`WorkoutScreen`, `WorkoutViewModel`, `WorkoutLogic`, `StartWorkout.kt`):
    - Built by Sonnet from the Opus spec, then reviewed and fixed.
    - Forced dark, with set rows, steppers, an effort score, a rest timer, Prev/Next/Finish and End with confirm.
    - Resume after the app closes; the screen stays on.
    - Start or Resume buttons on Train and Today.
  - **`LocalAppLocked`** (`app/ui/AppLocked.kt`, provided in MainActivity): screens know when the lock layer is up. The workout screen closes its dialog and pauses keep-screen-on and its timers while locked.
  - **Review fixes:**
    - Writes are serialised and the saved row is re-read inside the lock.
    - "Later sets follow" only applies to rows the owner hasn't set directly.
    - Finish can't run twice.
    - Text fields keep what's being typed.
  - **Checks:**
    - 139 unit tests pass; lint 0 errors.
    - `:core:data` instrumented tests (6 + 4 + 2, including the v2 → v3 migration with data).
    - Prompt eval **12/13**: the new "what weight" case answered with the app's 42.5 kg. The miss: "make my plan harder" got `set_day`.
  - **Not yet:** a phone check, and an automated test for the ViewModel (known limitation).

## Current state
- **Git:**
  - `main` holds Phases 0–1c-2.
  - PR #6 (`fix/phase-1c-phone-feedback`) is open.
  - PR #7 (`feat/phase-1c-logging`, base `fix/phase-1c-phone-feedback`) is open.
  - Nothing is merged without the owner's OK.
- **Phone:**
  - It has the 1c-2 build (`main`) from 2026-10-21. #6 and #7 aren't installed.
  - The app is force-stopped; adb is disconnected.
- **PC:**
  - Emulator stopped; adb server stopped.
  - The emulator's AVD RAM was raised to 6 GB (backup of the old config in the session scratchpad; old value `hw.ramSize=2048`).
  - The emulator has no M³ app or model; `/data/local/tmp/m3-eval` was removed.

## Phase 1c-4 notes (not started)
- **Steps:** the phone's step counter sensor (`TYPE_STEP_COUNTER`) needs the `ACTIVITY_RECOGNITION` runtime permission. Ask for it in context, with a clear reason, and store daily totals in the encrypted DB.
- **Today card:** show the steps, with no network and no Google Fit.
- **Then:** the Phase 1 "Try it yourself" list and the phone check.

## How to re-run the prompt eval
1. **Start the emulator:** `$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe -avd Medium_Phone_API_37.0 -no-snapshot-load -no-snapshot-save -no-audio -no-boot-anim -memory 6144` (run it in the background).
   - With 4 GB the low-memory killer kills the eval during case 2 (the model uses ~2.5 GB RSS).
   - `hw.ramSize` in `~/.android/avd/Medium_Phone.avd/config.ini` was raised from 2048 to 6144 on 2026-10-21.
   - Run `./gradlew --stop` first to free PC RAM; the PC has 15.7 GB.
2. **Unlock it** (PIN 1234), then run `svc power stayon true`.
   - Unlocking right after `sys.boot_completed` can silently fail.
   - Check with `adb shell dumpsys user | grep State` → it must say `RUNNING_UNLOCKED`.
   - Otherwise every instrumented test fails with "Required key not available".
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
