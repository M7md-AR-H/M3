# HANDOFF — 2026-11-01 (end of session 6)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## Where things stand
- **`main`** holds everything through PR #14: Phase 1, the UI pass (ADR 0011), Phase 2 core with meal photos, Phase 3 core, check-in and habits, the Coach's view of the day, and the weekly review.
- **Branch `feat/runtime-0.19`** (pushed, **not merged**, stacked on `main`): LiteRT-LM 0.19.0, optional vision (`LlmLoadConfig.vision`), `scripts/run-eval.sh`. Tests and lint passed on the runtime bump. **Don't merge until M³ has run on the phone's GPU with 0.19.0**: the eval only covers the CPU.
- **Phone:** has the build from before the weekly review (PR #13 level, DB v7, LiteRT-LM 0.18.0). M³ is stopped; the owner unplugged it. Test meal and test chat removed.
- **PC:** no emulator or adb server left running. `models/` holds E4B, E2B and `Qwen3-4B-int4.litertlm` (checksum verified: 4877b236…, 2,272,935,936 bytes). The emulator was wiped and has no PIN and no M³.
- **Owner's setup:** works locally, not in the cloud. The owner decided Claude merges without asking once local checks pass and the final check is done (CLAUDE.md); there's no CI.

## ▶ Next steps, in order
1. Normal session start: CLAUDE.md, docs/WORKING_RULES.md, this file, README roadmap + decision log, `git status`, `git log -5`. Give a time estimate first.
2. **Qwen3-4B has no eval result, and the emulator can't give one.** It loaded (100–155 s), then finished no case in 52 CPU minutes on the emulator's 6 GB: `kswapd` at 78%, 3.1 GB resident. That says nothing about the phone's GPU, where the model is built to run. Don't rerun it on the emulator.
3. **Model decision (ADR 0002 has the candidates).** E4B stays until a candidate beats it on both the eval and the phone. Test Qwen on the phone's GPU: add a debug-only way to run the eval cases in the app with the model chosen in You → AI model (`connectedAndroidTest` on `:app` uninstalls M³ and wipes the owner's data, so never run it on the phone). Copy the model with resumable `adb exec-in run-as com.m3.app …` (README decision log). If Qwen wins: add it to `ModelCatalog` with its size and SHA-256, keep E4B for meal photos (one model loaded at a time, through `CoachRuntime`), set `vision = false`, and handle thinking output in `ReplyParser` if it shows. LFM3-Omni-2B is second (3.23 GB, reads photos, licence limited for organisations with $10M+ revenue).
4. **Phone check of `feat/runtime-0.19`:** install, unlock, ask the Coach one advice question and snap one meal on the GPU. The GPU engines of 0.18.x crash on Qwen's file, so also run You → AI model & benchmark if the model changes. Then merge.
5. **Deload weeks, grocery list** (no phone needed).
6. **Look at the weekly review card on the phone.** It shows from the first Monday after a week with logs; it has never been seen on a screen.
7. **Needs the phone or owner:** fridge photo ideas, progress photos with ghost overlay (needs CameraX: decision-log entry), barcode (needs `:research` and the owner's allowlist decision), live form check. Grooming and style come after.

## Session 6 (2026-10-30 and 31): what was done
- **UI pass:** owner's design guidelines applied inside the editorial system; glass styles rejected because the guidelines say the project's system wins.
- **New:** step counter, Fuel tab with targets and safety floors, food log, weigh-ins and trend chart, measurements, check-in, habits, meal photos, Coach facts, `DailyAnswers`, weekly review. README has the decisions and limitations.
- **Phone checks passed, real model, real data, test entries removed:** workout flow (made today a training day through the Coach, then Undo), Clear + Undo and a real Clear, Fuel, Progress, real steps (0 → 23 → 75 including catch-up), number questions, advice, meal photo by camera and gallery, non-food refused, no photo file left. Owner confirmed dark mode.
- **Emulator:** progression from a logged workout (3 kg × 12 → 5 kg next).
- **Not checked:** the Coach quoting the next weight with real history (needs the owner's first workout); weekly review on a screen; barcode, fridge and photos don't exist yet.

## Eval evidence so far (19 cases, CPU, emulator; read every answer)
- **E4B on LiteRT-LM 0.19.0:** 17/19, 2 false "done" claims, no crash. Failures: theme with no tool line (the app's fallback covers it, the eval doesn't run that), "hotel tomorrow" got `set_day`. It still invented "78.0 kg last week".
- **E4B on 0.18.0:** 15/19. It said 53 g when told 58 g, and invented an `adjust_weight` tool with made-up increases.
- **Conclusion:** the model can't be trusted to repeat numbers, so plain number questions go to `DailyAnswers` in Kotlin. Free-form advice can still contain wrong numbers (known limitation).

## How to run the eval
`bash scripts/run-eval.sh gemma-4-E4B-it.litertlm eval-e4b` then read `build/eval/eval-e4b.txt`. For a text-only model add `false` as the third argument: `bash scripts/run-eval.sh Qwen3-4B-int4.litertlm eval-qwen false`. About 40 minutes; run it in the background. It wipes the emulator's test data on purpose.

## Open questions for the owner
- Not urgent (OWNER_TASKS #5): before going public, keep the Gmail address in commit history, or switch to the GitHub noreply address?
- Idle unload of the model in the foreground (5 min): wanted?
- Did camera meal photos land in the phone's gallery? Claude can't see that.

## Traps found
- **Downloads:** never start a second download of a file while the first may still be running: two writers corrupted the Qwen file. Check `ps -W | grep curl` first, resume with `curl -C -` from one process, and compare the SHA-256 with the Hugging Face API (`/api/models/<repo>/tree/main`). A background curl can look dead while still running.
- **Timers:** a background `sleep` doesn't survive the PC sleeping. Don't promise an exact stop time from one.
- **Text-only models:** set `vision = false` or every answer fails with "tf_lite_vision_encoder not found".
- **Eval memory:** the CPU weight cache needs about 3 GB free or the native code aborts; with no cache the low-memory killer stops the run. `-wipe-data` frees the disk.
- **Reading evals:** a passing score can hide wrong numbers. An expected-text check only looks for one right number.
- **Prompt budget:** `system prompt plus a full history fits` caps the worst-case system prompt at 7,500 characters. Adding facts means trimming elsewhere.
- **Bash tool on this PC:** a long heredoc containing apostrophes in the command can fail as a whole and write nothing. Use the Write tool for source files. adb paths need `MSYS_NO_PATHCONV=1`.
- **Tooling on this PC:** `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` before every Gradle call. adb is at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`, not on PATH. There's no Python and no `bc`. The PC has 15.7 GB of RAM: `./gradlew --stop` before evals.
- **Phone and emulator together:** always set `ANDROID_SERIAL=emulator-5554` for tests.
- **The phone over USB is flaky.** Wireless serial: `adb-c84e26c7-oQZ4D2._adb-tls-connect._tcp`. USB serial: `c84e26c7`. Check `adb devices` first.
- **Driving the phone:** `M3_SERIAL=<serial> bash scripts/phone.sh tap|type|key|swipe|screen|pos|wait …`. It only taps while `com.m3.app` has focus. The owner unlocks M³; send a PushNotification when that's needed, and don't block on it: the owner may be away.
  - `phone.sh pos` takes about 15 s. For timing, use a raw `uiautomator dump`, filtered to `package="com.m3.app"` (about 3 s).
  - The bottom tabs are best tapped by position (`tapxy 108|324|540|756|972 2229`).
  - `tap "Next"` is case-insensitive and hits the kicker: find the exact text.
  - `key BACK` leaves the You tab, and on a workout it opens the End dialog.
  - Clear's Undo window is 4 s (the delete is at 6 s). Undo sits at about (950, 1811) on the Coach tab; tap it right after Clear.
  - The owner often reads this chat in the Claude app on the same phone. Unfiltered dumps would read that app; never do that.
  - The owner asked not to be asked "are you there?": poll the screen instead.
  - Leave no data: untick test sets and End (an empty workout is dropped), Undo applied cards, remove test food and weigh-ins, restore switches. If a weigh-in is needed, log the profile's weight, so removing it changes nothing.
- **Screenshots:** `FLAG_SECURE` blacks them out. For review on the emulator only, comment out the line in `MainActivity`, build, screenshot with `adb exec-out screencap -p`, and restore it. Never commit that.
- **Emulator:** with a PIN, storage stays locked after boot until unlocked (check `dumpsys user` says `RUNNING_UNLOCKED`, or instrumented tests fail with "Required key not available"). The BiometricPrompt PIN pad may need tapping digit by digit. `connectedAndroidTest` on `:app` uninstalls the app. M³ won't start without a screen lock, and a wiped emulator has none (`adb shell locksettings set-pin 1234`). `cmd alarm set-time <ms>` changes the date without root.
- **Keep-screen-on:** use the window flag, not `View.keepScreenOn`.
- **Model behaviour (E4B):** it repeats its own earlier false claims from chat history, so the app's notes are stored with the reply. It often attaches an unrelated tool; the relevance guard drops it.
- **LiteRT-LM native crashes** can't be caught and kill the app. Closing a conversation while the runtime still works crashes it, so `generate` calls `cancelProcess()` and waits. A failed weight-cache write aborts it, so the GPU cache needs ≥ 1 GB free, and the app uses no cache on the CPU. The cache lives in `no_backup/llm-cache`. The emulator GPU path takes about 7 minutes and fails, so emulators skip the GPU.
- **Agent worktrees:** `.claude/worktrees/` is in `.git/info/exclude`. Don't `git add -A` while one exists. Deleting one can hit "Filename too long": use PowerShell `Remove-Item -LiteralPath "\\?\C:\…" -Recurse -Force`.
- **Tests:** a JUnit test method must return `Unit` (`fun run(): Unit = runBlocking { … }`).
- **uiautomator:** dump text is XML-escaped (`&amp;`), and positions shift, so re-dump before every tap.
- **Editing:** `sed` loses `\\` escapes; use the Write or Edit tools for Kotlin.
- **Gradle:** `--write-verification-metadata` may fail at the end on a Windows file lock, though it still writes; check the diff. Only Linux and Windows `aapt2` are in verification metadata, and a new per-platform artifact needs both.
- **SQLCipher:** the passphrase array must not be zeroed.
- **Lock layer:** while locked, the app stays composed under the lock layer. Anything new that runs in the background or takes key input must respect `locked`.
- **Cloud sessions (not in use):** setup is in OWNER_TASKS #6. `dl.google.com` must be added to the allowlist.
- **Older traps:** `git show ebfcf61:HANDOFF.md`.
