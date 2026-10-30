# HANDOFF — 2026-10-30 (session 6, in progress)

**Live state:** PRs #6 to #10 are merged, and Phase 3 core is merged or about to be (`feat/phase-3-progress`). The owner chose to continue locally, not in the cloud (the cloud sections stay for later), and gave standing permission to merge. Work through "Cloud work order" locally: items 1 to 3 are done, next is item 4 (check-in and habits), then 5 (meal photo).

**Phone state:** build from 2026-10-30 18:10 (DB v4, step permission granted, no steps verified). Doesn't include Phases 2 and 3. The owner unplugged the phone and M³ may still be open on it; adb server stopped. Real step counting is unchecked. Clear + Undo was left to the owner: Claude can't safely hit the 4-second Undo on the real chat. Install the next batch once (the phone goes v4 to v6) and do the phone check in one go. When both the phone and emulator are attached, always set `ANDROID_SERIAL=emulator-5554` for tests.

**Emulator state:** test profile with test food, weights and measurements; dark theme; clock moved to early November (`cmd alarm set-time` works without root; automatic time puts it back). A test workout is still open.

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## Cloud session prompt
Paste this into a new cloud session (OWNER_TASKS #6), on branch `feat/ui-editorial-pass` or newer:

> Read CLAUDE.md, docs/WORKING_RULES.md, HANDOFF.md and the README roadmap. You're in a cloud VM: there's no phone, no emulator and no AI model. First run `./gradlew test lint :app:assembleDebug` and fix any setup problem; dependency verification must stay on. Then work through "Cloud work order" in HANDOFF.md, one feature branch and PR per phase, each stacked on the last. Write unit tests for everything deterministic. Don't merge. List anything that needs the phone or model under "Needs the phone" in HANDOFF.md. Update README and HANDOFF after every phase. Never add a "Generated with" line.

## Cloud work order
Goal: a complete app for daily use in one day. Stop after each item if time or usage runs short; the order is by value.
1. **1c-4:** step counter (`TYPE_STEP_COUNTER`, `ACTIVITY_RECOGNITION` asked in context, daily totals in the encrypted DB, on Today). Planned deload weeks if simple.
2. **Phase 2 core:** TDEE and macro targets (Mifflin-St Jeor) in Kotlin, calorie floor and max weekly loss enforced in code, a manual food log, the Fuel tab and Today's Fuel card from the prototype (`project/Fuel.dc.html`). Copy the prototype's layout.
3. **Phase 3 core:** weight and measurements with a moving-average trend, the Progress design from the prototype.
4. **Daily loop:** morning check-in and habits cards.
5. **Meal photo estimate:** Gemma vision via the existing engine, shown as an estimate. Code only in the cloud; it needs a phone check.
6. **Only if everything above is done:** progress photos (Tink streaming AEAD, ghost overlay). Barcode and `:research` need network code and an owner decision on the allowlist; form check, grooming and style are out of scope for this push.

## Needs the phone
- Everything in "Next steps" below, plus any new permission, sensor, camera or model behaviour built in the cloud.

## ▶ Next steps, in order
1. Do the normal session start: CLAUDE.md, docs/WORKING_RULES.md, this file, README roadmap + decision log, `git status`, `git log -5`. Give a time estimate first.
2. **Install the UI pass and the keep-screen-on fix on the phone** (`feat/ui-editorial-pass` contains both) when the owner next connects it, run OWNER_TASKS "UI pass", and re-check: start a workout, Home, wait 5½ min, come back → `dumpsys window windows` shows no `KEEP_SCREEN_ON` on M³'s window while it's locked, and it's back after unlocking. Claude drives the phone (see Traps → "Driving the phone").
3. **Merge, after asking the owner:** first #6 into `main`, then retarget #7 to `main` and merge it, then the UI pass the same way. All are squash merges. The phone check of #6 and #7 is done (session 5, below).
   - Ask the owner whether to tap **Clear** in COACH: the chat holds only test messages from the two phone checks.
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

## Session 6 (2026-10-30): what was done
- **UI pass on `feat/ui-editorial-pass`** (stacked on #7, see README and ADR 0011). The owner's design guidelines were applied to the editorial system; glass and gradient styles were not used, because the guidelines themselves say the project's system wins.
- **Checks:** unit tests, lint and debug build pass. Real screenshots reviewed on the emulator: all five tabs, workout, Customize, dark mode. Not reviewed visually: onboarding, Profile and Training setup edit screens, the rest-day Train view, the Coach with real messages, and Clear + Undo (the emulator has no model).
- **Screenshots on the emulator:** temporarily comment out the `FLAG_SECURE` line in `MainActivity`, rebuild, `adb exec-out screencap -p`, then restore it. Never commit that. This is the way to review UI from now on.
- **Cloud support:** Linux `aapt2` checksum, `scripts/cloud-setup.sh`, OWNER_TASKS #6. Not yet tried in a real cloud session.
- **Emulator:** has a test workout left open (no sets logged) and the theme set to dark. Test data only. It was stopped; adb server stopped. The phone wasn't touched this session.

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
  - **Not yet:** an automated test for the ViewModel (known limitation).
- **Phone check of PR #6 + #7 (evening):** the owner turned on Xiaomi's "USB debugging (Security settings)", so Claude tapped through everything over adb (`scripts/phone.sh`); the owner only unlocked M³ twice. All items in README passed.
  - **Bug found and fixed:** after the 5-minute lock, M³'s window kept `KEEP_SCREEN_ON` (and a screen wake lock) behind the lock layer. Debug logging on the emulator showed Compose set `view.keepScreenOn = false` and every view was off, but the change never reached the window. Fix: `KeepScreenOnWithLightIcons` uses `window.addFlags/clearFlags(FLAG_KEEP_SCREEN_ON)`. Verified on the emulator (with a temporary 10 s lock timeout, reverted): on in the workout, off while locked, on after unlock, off after End. Tests + lint pass. Not yet on the phone.
  - **Not checked:** progression and the Coach's weight with real history (would need a saved workout; all test sets were unticked, the empty workout dropped).
  - **Noted for the owner:** an avoided family leaves its slot empty (no substitute); the Coach once said "not specified in the current facts" (both in Known limitations).
  - **Phone changes:** app upgraded (2026-10-21 20:06); DB v2 → v3 (workout tables, empty); Coach chat gained test messages; theme, lock and plan are as before (all undone). Nothing in `/data/local/tmp`; M³ force-stopped.

## Current state
- **Git:**
  - `main` holds Phases 0–1c-2.
  - PR #6 (`fix/phase-1c-phone-feedback`) is open.
  - PR #7 (`feat/phase-1c-logging`, base `fix/phase-1c-phone-feedback`) is open.
  - `feat/ui-editorial-pass` (base `feat/phase-1c-logging`) has its own open PR.
  - Nothing is merged without the owner's OK.
- **Phone:**
  - It has the #6 + #7 build from 2026-10-21 20:06, **without** the keep-screen-on fix.
  - The app is force-stopped; the owner unplugged the phone.
- **PC:**
  - Emulator stopped; adb server stopped. The emulator now has M³ installed and onboarded (test profile, Home with dumbbells, free every day) but no model.
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
- **Cloud VM:** no `dl.google.com` on the default allowlist (Maven artifacts redirect there too), so the environment needs Custom network access. A new Android dependency with a per-platform artifact needs its Linux checksum in `gradle/verification-metadata.xml`. Foreground commands time out at 2 minutes by default: run Gradle in the background.
- **Bash tool on this PC:** a long heredoc containing apostrophes fails as a whole and writes nothing. Use the Write tool for source files. adb paths need `MSYS_NO_PATHCONV=1`.
- **Emulator after boot:** the app can't launch until the PIN is entered ("No activity found"). `keyevent 224`, swipe up, `input text 1234`, Enter; check for `RUNNING_UNLOCKED`.
- **Tooling on this PC:**
  - `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` before every Gradle call.
  - adb is at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe` and isn't on PATH.
  - There's no Python and no `bc`.
- **The phone over USB is flaky.** Wireless serial: `adb-c84e26c7-oQZ4D2._adb-tls-connect._tcp`. USB serial: `c84e26c7`.
- **Driving the phone (since session 5):** `M3_SERIAL=<serial> bash scripts/phone.sh tap|type|key|swipe|screen|pos|wait …`.
  - It only taps while `com.m3.app` has focus, so it can't hit another app or the system prompt. The owner unlocks M³ (fingerprint/PIN); send a PushNotification when that's needed.
  - The bottom tabs are best tapped by position (`tapxy 108|324|540|756|972 2229`): chat labels also say "YOU"/"COACH".
  - Switch state isn't in the text; read `checked="true"` from the raw dump (the node just before the label).
  - `uiautomator dump` can stall for a long time while the workout timer ticks; the script wraps calls in `timeout`. Keep one action per command when timing matters.
  - Leave no data: untick test sets and Finish/End (an empty workout is dropped); Undo every applied card; restore any switch.
  - USB drops right after connecting sometimes; wait for it with a loop on `adb devices`.
- **Keep-screen-on:** don't use `View.keepScreenOn` for anything that changes when the app restarts or locks; use the window flag (decision log).
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
