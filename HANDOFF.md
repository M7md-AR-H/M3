# HANDOFF — 2026-10-18 (session 4, in progress)

> Keep this file current **throughout** each session (owner's request): rewrite it after every meaningful step, not only at the end.

## Where things stand
Phase 1b is **built and emulator-verified** on branch `feat/phase-1b-foundation` (code commit `b59fbdc` on top of the ADR 0008 docs commit). Still to do in this session:
1. Apply the fixes from the Sonnet review (running).
2. Confirm the 5-minute background lock on the emulator (test running).
3. Commit the docs, push, open the PR to `main` and **ask the owner before merging**.
4. Phone install: needs the owner to plug in the USB cable (serial `c84e26c7`). See "Phone install" below.

## What Phase 1b added
- `:core:model`: equipment catalog (50 kinds, 4 groups), profile, training setup, validators, metric `Units`.
- `:core:security`: `TinkSecretCipher` (Keystore master key `m3_master_key`, keyset in prefs `m3_keyset_prefs`), `DatabasePassphrase` (`no_backup/db.key`), `AppLock` (5 min), `UnlockPrompt` (platform BiometricPrompt).
- `:core:data`: Room 2.8.5 + SQLCipher 4.19.1 (`m3.db`, schema v1 in `core/data/schemas`), `DatabaseOpener`, repositories.
- `:app`: `AppStartup` (open DB → restore UI config → save every change), lock gate in `MainActivity`, onboarding (`feature/onboarding`), You → Profile / Training setup.
- Tests: 69 unit tests; 4 instrumented tests in `:core:data` (emulator only).

## Phone install (Step 4 of the plan)
1. The owner plugs in the USB cable; `adb devices` shows `c84e26c7`.
2. `adb -s c84e26c7 install -r app/build/outputs/apk/debug/app-debug.apk` (upgrade in place; keeps the 3.66 GB model; same debug key).
3. The owner opens M³, unlocks and taps through onboarding (Xiaomi blocks injected taps). Read the screen with `uiautomator dump` to `/data/local/tmp/m3-ui.xml`, `cat`, then delete it in the same step.
4. `adb -s c84e26c7 shell am force-stop com.m3.app`, `adb kill-server`. Report what changed: app upgraded; new private files `databases/m3.db*`, `no_backup/db.key`, `shared_prefs/m3_keyset_prefs.xml`; a Keystore key `m3_master_key`.

## Traps found this session
- No Python on this PC; Bash heredocs with complex quoting can fail. Use the Write tool for source files.
- SQLCipher keeps the passphrase array to open more connections, so it must not be zeroed (decision log).
- `--write-verification-metadata` can fail at the end with "user-mapped section open" (Windows file lock); just re-run.
- `StateFlow.conflate()` is a deprecation error; StateFlow is already conflated.
- Emulator UI checks: helper functions in the session scratchpad (`ui.sh`); Compose radio selection shows as `checked="true"` on the parent node in `uiautomator` dumps. Field positions shift when error text appears, so re-dump before each tap.
- Instrumented tests: always set `ANDROID_SERIAL=emulator-5554` so they can never run on the phone.
- Emulator PIN for lock tests: `adb -e shell locksettings set-pin 1234` (emulator only, never the phone).

See the previous HANDOFF content in git history (`git show ebfcf61:HANDOFF.md`) for the older traps (model copying, Xiaomi, LiteRT-LM); they still apply.
