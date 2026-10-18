# HANDOFF — 2026-10-18 (session 4, Phase 1c-1 ready for review)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## ▶ Next steps, in order
1. **Owner decision:** review the PR "Phase 1c-1" (`feat/phase-1c-plan` → `main`) and approve the squash-merge. Don't merge without that.
2. **Phone install. The owner has disconnected the phone; wait until they say it's back.** Use the USB serial `c84e26c7`, or the wireless mDNS serial `adb-c84e26c7-…._adb-tls-connect._tcp` if USB isn't present.
   1. `./gradlew :app:assembleDebug`
   2. `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
   3. The owner follows OWNER_TASKS "Try it yourself — Phase 1c-1".
   4. Force-stop the app, run `adb disconnect` and `adb kill-server`, then report what changed: only the app was upgraded; no new files. The plan isn't stored; it is recomputed from data already in `m3.db`.
3. **Phase 1c-2: Coach chat** (README checkboxes). Give the owner a time estimate first.
   - Load E4B with `LlmBackend.GPU` + `speculativeDecoding = true`. Fall back to CPU if GPU init fails, and unload when the app goes to the background.
   - Streaming chat UI on the COACH tab.
   - Schema-validated tool calls (kotlinx.serialization), with Kotlin deciding (ADR 0003):
     - `ui.*` (ADR 0007, via `UiConfigStore.apply` with `Actor.Agent`);
     - `training.setDayInventory` / `adaptPlan` / `setLocationInventory` / `assignLocation` (ADR 0008/0009).
   - Store Coach plan changes as versioned, undoable overrides on top of `PlanGenerator`'s base plan. `day_override` already exists as a table, with no DAO yet.
   - The Coach explains the plan from `WeekPlan` facts and reads injuries to propose swaps.

## What was done this session (session 4)
- **Phase 1b:** built, reviewed, merged (PR #3 → `main` `13ec534`), installed on the phone and checked by the owner. Owner feedback: the Coach should decide the training/rest split.
- **Phase 1c-1** on `feat/phase-1c-plan`:
  - **ADR 0009:** the owner gives availability; deterministic Kotlin decides the split.
  - **Rename:** `schedule` → `availability` in model, data and onboarding. Same `schedule_day` table, no migration.
  - **`:core:training`:**
    - `exercises.json`: 101 curated exercises tagged with pattern, muscles, level, equipment alternatives and the flags heavy, ballistic, intervals and fixed reps.
    - `ExerciseCatalog` validates the file on load.
    - `PlanGenerator` and `WeekPlan`.
  - **App:**
    - `PlanRepository` (combines profile + setup → plan, on `Dispatchers.Default`).
    - Train tab, Today card, onboarding "All set" preview.
    - `PlanText` for wording; `Durations` in core/model `Units.kt`.
  - **Sonnet review:** 12 findings. All fixed except dumbbell kg lists, which are now a README limitation until 1c-3.
  - **Checks:**
    - 91 unit tests pass; lint 0 errors.
    - Emulator: split + reasons; unfillable slots explained; editing availability re-plans; Today card shows rest + next session.

## Current state
- **Git:**
  - `main` = `13ec534` (Phase 1b).
  - `feat/phase-1c-plan` is pushed with an open PR, not merged.
- **Phone:**
  - Phase 1b debug build installed, owner onboarded.
  - Model and encrypted DB present.
  - App force-stopped, adb disconnected.
  - The phone is currently unplugged.
- **PC:** emulator stopped, adb server stopped. The emulator has test data and PIN 1234 (emulator only).

## Open questions for the owner
- Merge the Phase 1c-1 PR?
- Not urgent (OWNER_TASKS #5): before going public, keep the Gmail address in commit history, or switch to the GitHub noreply address?

## Things to know
- **The plan is a pure function** of profile + setup + catalog. Tuning knobs live in `PlanGenerator`: `targetSessions`, `TEMPLATES`, the `pick` scoring and `prescribe`. Catalog edits are checked by `ExerciseCatalog.validate` and by `PlanGeneratorTest` (plates/rack rules, full-gym coverage).
- **Locked state:** while locked, the app stays composed under the lock layer. Anything new that runs in the background or takes key input must respect `locked`.
- **SQLCipher passphrase:** must not be zeroed (decision log).

## Traps found
- **Printing a sample plan:** write a throw-away JUnit test that `println`s the plan, read `<system-out>` in `core/training/build/test-results/testDebugUnitTest/TEST-*.xml`, then delete the test.
- **Kotlin:** `with(EnumClass) { … }` doesn't compile; import the enum entries instead.
- **`sed` edits** turn CRLF files into LF. Git shows them as modified with no content diff; that's harmless.
- **Emulator:**
  - After boot it shows its own lock screen. Run `input keyevent 82`, swipe up, type PIN 1234, Enter.
  - Wait until "Unlock M³" appears before typing the app PIN.
  - Instrumented tests: always set `ANDROID_SERIAL=emulator-5554`.
- **Phone:** if USB isn't present, the phone may still be reachable over wireless debugging (mDNS serial). Always run `adb disconnect` + `adb kill-server` afterwards.
- **This PC:**
  - No Python; use the Write tool for source files.
  - `--write-verification-metadata` may fail on a Windows file lock; re-run it.
  - `StateFlow.conflate()` is a deprecation error.
- **Older traps** (model copying, Xiaomi, LiteRT-LM, `JAVA_HOME`): `git show ebfcf61:HANDOFF.md`.
