# HANDOFF — 2026-10-18 (session 4, Phase 1c-2 ready for review)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## ▶ Next steps, in order
1. **Owner decision:** review the PR "Phase 1c-2" (`feat/phase-1c-coach` → `main`) and approve the squash-merge. Don't merge without that.
2. **Phone check. The owner has disconnected the phone; wait until they say it's back.** This covers 1c-1 and 1c-2 together.
   1. Use USB `c84e26c7`, or the wireless mDNS serial if USB isn't present.
   2. `./gradlew :app:assembleDebug`, then `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`. This upgrades in place and keeps the model and data. **The DB migrates v1 → v2 automatically**, tested on the emulator from an encrypted v1 DB.
   3. The owner follows OWNER_TASKS "Try it yourself" for Phase 1c-1 and Phase 1c-2.
   4. On the phone, check that the model loads on the **GPU**: the Coach status line reads "Running on this phone's GPU". Time the first and second answers.
   5. Optionally delete the app's old benchmark cache `cache/llm`: `adb shell run-as com.m3.app rm -rf cache/llm`. It's the app's own file; the Coach now uses `no_backup/llm-cache`.
   6. Force-stop the app, run `adb disconnect` and `adb kill-server`, then report what changed:
      - The app was upgraded.
      - New private files: DB tables (same `m3.db`), `no_backup/llm-cache/` (GPU cache, ~300 MB).
3. **Phase 1c-3:** workout logging + focus mode + progressive overload (Kotlin), loads from the owner's kg lists. Give a time estimate first.

## What was done this session (session 4)
- **Phase 1b:** merged (PR #3), installed on the phone, owner onboarded. Feedback: the Coach should decide the split.
- **Phase 1c-1:** merged (PR #4). ADR 0009, `:core:training` (101 exercises, `PlanGenerator`), Train tab, Today card.
- **Phase 1c-2** on `feat/phase-1c-coach` (ADR 0010):
  - **`:coach` module:** `CoachPrompt`, `CoachTools` (schemas, validator, relevance guard), `ReplyParser`.
  - **App, `feature/coach`:** `CoachRuntime`, `CoachController`, `CoachViewModel`, `CoachScreen`.
  - **Planner:** `PlanOverrides` and `PlanDiff`.
  - **DB v2:** `chat_message`, `excluded_exercise`, and a DAO for `day_override`.
  - **`LlmEngine`:** streaming plus safe cancellation (`cancelProcess`, then wait, then close).
  - **Owner locks:** "Your plan" and "Your free days".
  - **Prompt eval** (`app/src/androidTest/.../CoachPromptEval.kt`, emulator only): 5/8 → 10/10. See the README decision log for what changed.
  - **Emulator end-to-end with the real model (CPU):**
    - Chat streams, and a proposal card with the plan diff appears.
    - Apply, Undo, two applies in a row, and Undo only on the latest all work.
    - Stop resets at once with no crash, and the next answer works.
    - The model unloads in the background.
    - The v1→v2 migration test passes.
  - **Sonnet review:** 9 actionable findings, all fixed. Unit tests: 110 pass; lint 0 errors.

## Current state
- **Git:**
  - `main` = `7da67df` (Phase 1c-1).
  - `feat/phase-1c-coach` is pushed with an open PR, not merged.
- **Phone:**
  - Phase 1b build.
  - The owner unplugged it; adb is disconnected and the app force-stopped.
- **PC:** emulator stopped, adb server stopped. The emulator has test data, PIN 1234, and a copy of the model in the app (emulator only).

## Not verified yet
- **The final prompt version.** It adds "Tomorrow is …", one-line sanitised owner text and the whole-prompt budget. It wasn't re-scored with the eval, only used in three live emulator chats, all correct. Re-run the eval if the prompt changes again:
  1. Push the model to `/data/local/tmp/m3-eval/model.litertlm` on the **emulator**, unlocked.
  2. Run `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.m3.app.CoachPromptEval`.
  3. Read `adb logcat -s M3Eval`.
  4. Delete the model copy afterwards (the emulator disk is only 10 GB).
- **GPU behaviour on the phone:** speed, the cache write, and cancelling mid-prefill on the GPU.

## Open questions for the owner
- Merge PR "Phase 1c-2"?
- Not urgent (OWNER_TASKS #5): before going public, keep the Gmail address in commit history, or switch to the GitHub noreply address?

## Traps found (this session)
- **LiteRT-LM:**
  - Closing a conversation while the runtime is still working is a **native crash** (SIGSEGV). Always use `cancelProcess()` and wait for `onDone`/`onError`.
  - A failed weight-cache write is a **native abort** (SIGABRT) that can't be caught; ensure free space, and use no cache on the CPU.
  - `cacheDir` can be purged mid-write.
  - Emulator GPU: about 7 minutes, then it fails; skip it.
- **Emulator:**
  - With a PIN set, the user's storage stays locked after boot until unlocked: the app's cacheDir isn't writable and uiautomator dumps fail. Unlock first (`input keyevent 82`, swipe, PIN 1234), and use `svc power stayon true` for long runs, then reset it.
  - Disk is 10 GB; two model copies plus caches fill it.
  - `connectedAndroidTest` on `:app` uninstalls the app, which wipes its data.
- **Instrumented tests:** a JUnit test method must return `Unit` (`fun run(): Unit = runBlocking { … }`).
- **uiautomator:** dump text is XML-escaped (`&amp;`).
- **Bash/sed:** `sed` replacement text loses `\\` escapes, so put regexes in with the Edit tool. Line-number `sed` edits are fragile; prefer Edit.
- **Older traps:** this file in git history (`git show ebfcf61:HANDOFF.md`, `git show 7da67df:HANDOFF.md`).
