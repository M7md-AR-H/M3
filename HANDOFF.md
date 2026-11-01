# HANDOFF — 2026-11-01 (session 7)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## Where things stand
- **`main`** holds everything through session 7: reminders (ADR 0012), the water log, deload weeks, the grocery list, DB v9, LiteRT-LM 0.19.0, Qwen3-4B for chat with E4B for photos (ADR 0002), and the debug eval runner.
- **Phone:** has this build. Checked on it with real data: DB v7 → v9 upgrade kept everything, Coach on Qwen on the GPU, meal photo swaps to E4B on the GPU, reminder cards with Apply and Undo, a test reminder, and a live alarm chain. No crash. Models on the phone: Qwen and E4B, both verified. Test chat removed.
- **Not confirmed:** whether the owner did OWNER_TASKS #7, and why the real 19:30 water reminder stayed silent. The alarm fired and moved on to 21:00, and silence is right if Water was ticked or 2.5 L reached. If it wasn't, this is a bug.
- **PC:** `models/` holds E4B, E2B and Qwen. The emulator has test data, PIN 1234 and E2B in M³'s model folder. Eval reports are in `build/eval/` (ignored by git).
- **Owner's setup:** works locally. Claude merges without asking once local checks pass and the final check is done (CLAUDE.md); there's no CI.

## ▶ Next steps, in order
1. Normal session start: CLAUDE.md, docs/WORKING_RULES.md, this file, README roadmap + decision log, `git status`, `git log -5`. Give a time estimate first.
2. **Ask the owner** about the 19:30 water reminder, and whether reminders arrived with M³ closed. If one was missed with Water open, debug it first. Check OWNER_TASKS #7 is done.
3. **Watch Qwen in daily use.** Answers take about 24 s. If the owner finds that too slow, or the label mix-ups keep coming, consider a Kotlin check of reply numbers against the facts. The "owner" wording in answers comes from the prompt: replace it, with an eval run.
4. **Look at the weekly review card on the phone.** It shows from the first Monday after a week with logs.
5. **Needs the phone or owner:** fridge photo ideas, progress photos with ghost overlay (needs CameraX: decision-log entry), barcode (needs `:research` and the owner's allowlist decision), live form check. Grooming and style come after.

## Phone GPU eval, 2026-11-01 (19 cases, read every answer)
- **E4B garbles digits on the phone's GPU** ("422.5 kg" for 42.5, "62,1414 steps"), on 0.18.0 and 0.19.0, with or without MTP. Never seen on the emulator's CPU. Scores hid it: 13–18/19.
- **Qwen3-4B with `/no_think`:** 17/19, 0 false claims, digits clean, about 24 s an answer. Without `/no_think`, 5 answers came back empty. It won't load on the GPU with MTP. Full table: ADR 0002.
- **Commands:** `M3_SERIAL=<serial> bash scripts/phone-eval.sh Qwen3-4B-int4.litertlm false false /no_think`; `... gemma-4-E4B-it.litertlm true`. Arguments: model, vision, MTP, prompt suffix. **The script force-stops M³, which clears its alarms:** open M³ afterwards.

## Session 7 (2026-11-01): what was done
- **Reminders (ADR 0012):** morning, three meals, workout, water, log your day, lights out, weekly review, rest over. Each reads the logs when due and stays silent if it's done. Private text unless "Show my numbers" is on. You → Reminders; plain requests to the Coach become cards with Undo and a `REMINDERS` lock.
- **Water log:** ml per day, the habit ticks itself at 2.5 L, +250 ml on Habits and on the notification.
- **Deload weeks and grocery list** (Sonnet from an Opus spec). A relaxed prompt-budget test was put back: the deload now sits in the plan header, and the 1,000-character margin stands.
- **Debug eval runner** (Sonnet): `EvalActivity` in `src/debug`, started only from adb, plus `scripts/phone-eval.sh`.
- **Review:** Sonnet reviewed the whole diff. Fixed: next alarm before sending, retry keeps the failed reminder, lateness in real time, tighter Coach wording, logged sets stay visible after a deload, grocery Undo can't duplicate. Left as limitations in the README: Undo over newer manual changes, workouts in a cleared deload week.
- **Emulator checks passed:** alarm fires from a dead process; private text and "M³" as the public version; tap asks for the unlock, then opens the tab, and the notification clears; alarm survives an update and a reboot (and is lost on a force-stop until M³ opens, as Android intends); Rest over on time in the background; +250 ml reaches Habits; deload 3 → 2 sets; grocery list reads sensibly; 12 of 12 database tests including v7 → v8 → v9.


## Older eval evidence (19 cases, CPU, emulator)
- **E4B on LiteRT-LM 0.19.0:** 17/19, 2 false "done" claims, no crash. Failures: theme with no tool line (the app's fallback covers it, the eval doesn't run that), "hotel tomorrow" got `set_day`. It still invented "78.0 kg last week".
- **E4B on 0.18.0:** 15/19. It said 53 g when told 58 g, and invented an `adjust_weight` tool.
- **Qwen3-4B on the emulator:** finished no case in 52 CPU minutes. Only test it on the phone.
- **Conclusion:** the model can't be trusted to repeat numbers, so plain number questions go to `DailyAnswers` in Kotlin. Free-form advice can still contain wrong numbers (known limitation).

## How to run the eval
- **Phone or emulator, real install:** `scripts/phone-eval.sh` (see above). The model file must be in `no_backup/models`. The report lands in `build/eval/<name>.txt`, and nothing stays on the phone.
- **Emulator only:** `bash scripts/run-eval.sh gemma-4-E4B-it.litertlm eval-e4b` (wipes test data on purpose; add `false` for a text-only model). About 40 minutes; run in the background.

## Open questions for the owner
- Not urgent (OWNER_TASKS #5): before going public, keep the Gmail address in commit history, or switch to the GitHub noreply address?
- Idle unload of the model in the foreground (5 min): wanted?
- Did camera meal photos land in the phone's gallery? Claude can't see that.
- Are the default reminder times right, especially Workout at 17:30? They're easy to change in You → Reminders.

## Traps found
- **Hidden CPU cache:** a model loaded on the CPU writes a `.xnnpack_cache` the size of the model into `no_backup/models`, even with no cache dir set. It appeared when Qwen fell back to the CPU, and it was deleted. After any CPU fallback on the phone, list that folder and delete it.
- **Clean up after phone work (owner, 2026-11-01):** keep only what the app uses. Currently `Qwen3-4B-int4.litertlm`, `gemma-4-E4B-it.litertlm`, their `.verified` markers, and `no_backup/llm-cache`.
- **Force-stop and reminders:** the phone rules say force-stop M³ after tests, and that clears its alarm until M³ is next opened. Say so to the owner.
- **Intermediate DB versions:** a build with a different v8 left on the emulator shows "Couldn't open your data". That's Room's identity check, not corruption. Uninstall on the emulator. Never let this reach the phone: don't install a build with an unmerged schema there.
- **Emulator plus Gradle:** with 15.7 GB of RAM, both at once dropped free memory to about 340 MB and gave ANRs that look like app bugs. Stop one first; check the load before blaming code.
- **Boot broadcast:** on the emulator it arrived about a minute after the unlock.
- **Coach without a model:** Send is disabled, so even Kotlin-only answers can't be tried on an emulator with no model.
- **Downloads:** never start a second download of a file while the first may still be running: two writers corrupted the Qwen file. Check `ps -W | grep curl` first, resume with `curl -C -` from one process, and compare the SHA-256 with the Hugging Face API (`/api/models/<repo>/tree/main`).
- **Timers:** a background `sleep` doesn't survive the PC sleeping. Don't promise an exact stop time from one.
- **Text-only models:** set `vision = false` or every answer fails with "tf_lite_vision_encoder not found".
- **Eval memory:** the CPU weight cache needs about 3 GB free or the native code aborts; with no cache the low-memory killer stops the run. `-wipe-data` frees the disk.
- **Reading evals:** a passing score can hide wrong numbers. An expected-text check only looks for one right number.
- **Prompt budget:** `system prompt plus a full history fits` caps the worst-case system prompt at 7,500 characters, and it's within a few characters of that. Adding facts means trimming elsewhere. Don't relax it without the owner's OK.
- **Bash tool on this PC:** a long heredoc containing apostrophes in the command can fail as a whole and write nothing. Use the Write tool for source files. adb paths need `MSYS_NO_PATHCONV=1`.
- **Tooling on this PC:** `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` before every Gradle call. adb is at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`, not on PATH. There's no Python and no `bc`. A new worktree needs a copy of `local.properties`.
- **Phone and emulator together:** always set `ANDROID_SERIAL=emulator-5554` for tests.
- **The phone over USB is flaky.** Wireless serial: `adb-c84e26c7-oQZ4D2._adb-tls-connect._tcp`. USB serial: `c84e26c7`. Check `adb devices` first.
- **Driving the phone:** `M3_SERIAL=<serial> bash scripts/phone.sh tap|type|key|swipe|screen|pos|wait …`. It only taps while `com.m3.app` has focus. The owner unlocks M³; send a PushNotification when that's needed, and don't block on it: the owner may be away.
  - `phone.sh pos` takes about 15 s. For timing, use a raw `uiautomator dump`, filtered to `package="com.m3.app"` (about 3 s).
  - The bottom tabs are best tapped by position (`tapxy 108|324|540|756|972 2229`).
  - `tap "Next"` is case-insensitive and hits the kicker: find the exact text.
  - `key BACK` leaves the You tab, and on a workout it opens the End dialog.
  - Clear's Undo window is 4 s (the delete is at 6 s). Undo sits at about (950, 1811) on the Coach tab; tap it right after Clear.
  - The owner often reads this chat in the Claude app on the same phone. Unfiltered dumps would read that app; never do that. That applies to reading the notification shade too.
  - The owner asked not to be asked "are you there?": poll the screen instead.
  - Leave no data: untick test sets and End (an empty workout is dropped), Undo applied cards, remove test food and weigh-ins, restore switches. Put reminder times back. If a weigh-in is needed, log the profile's weight, so removing it changes nothing.
- **Screenshots:** `FLAG_SECURE` blacks them out. For review on the emulator only, comment out the line in `MainActivity`, build, screenshot with `adb exec-out screencap -p`, and restore it. Never commit that.
- **Emulator:** with a PIN, storage stays locked after boot until unlocked (check `dumpsys user` says `RUNNING_UNLOCKED`, or instrumented tests fail with "Required key not available"). Tap PIN pads digit by digit; `input text` fails. `connectedAndroidTest` on `:app` uninstalls the app. M³ won't start without a screen lock, and a wiped emulator has none (`adb shell locksettings set-pin 1234`). `cmd alarm set-time <ms>` changes the date without root.
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
