# M³ — Models Make Model

A private AI coach that lives on your phone: personal trainer, nutritionist and food critic, progress analyst, and later groomer and stylist. All AI runs on-device; personal data never leaves the phone.

**Status (2026-11-01):** Phases 1 to 3 core, the UI pass ([ADR 0011](docs/adr/0011-design-guidelines-editorial-pass.md)), check-in and habits, reminders ([ADR 0012](docs/adr/0012-reminders.md)), the water log, deload weeks and the grocery list are merged. The Coach now runs on Qwen3-4B, with Gemma 4 E4B for meal photos: on the phone's GPU, E4B garbled digits and Qwen didn't ([ADR 0002](docs/adr/0002-on-device-llm-litert-lm-gemma.md)). Plain number questions stay in Kotlin. Checked on the phone with real data, except deload, the grocery list and the weekly review, which only ran on the emulator or not yet. Next: daily use of Qwen and the reminders.

**Earlier (2026-10-21):** Phase 1c-1 and 1c-2 checked on the phone. Fixes from that check: PR #6. Phase 1c-3 (workout logging, focus mode, progressive overload): PR #7 on `feat/phase-1c-logging`, stacked on PR #6. Both re-checked on the phone (2026-10-21, Claude driving the phone over adb); one bug found and fixed (screen kept on behind the lock). Earlier:  Phase 0 complete. Phase 1a done: M³ is installed on the Poco F5, Gemma 4 E4B runs on the phone's GPU (about 16 tokens/s, first words in under a second). Phase 1b done: app lock, encrypted database (SQLCipher + Keystore), saved customizations, onboarding with training locations, equipment and weekly schedule. Phase 1b is on the phone (PR #3 merged). Phase 1c-1 merged (PR #4): the Coach plans the week from your availability, goal and equipment. Phase 1c-2 (branch `feat/phase-1c-coach`): chat with the on-device Coach, which proposes validated, undoable changes. Next: phone check, then logging and progression (1c-3).

- Product & tech spec: [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md)
- UI prototype (design source of truth): https://claude.ai/artifact/RATouP8aDxkhKRNxgivVSJ
- Architecture decisions: [docs/adr/](docs/adr/)
- Owner-only steps and test lists: [OWNER_TASKS.md](OWNER_TASKS.md)
- Session handoff: [HANDOFF.md](HANDOFF.md)

Target device: Xiaomi Poco F5, 12 GB RAM (Snapdragon 7+ Gen 2). Android only.

---

## Roadmap

### Phase 0 — Setup and app shell
- [x] Feature branch, docs structure, working rules in repo
- [x] Project docs: README, CLAUDE.md, OWNER_TASKS.md, ADRs 0001–0006
- [x] Verify current stable versions of all build tools and libraries
- [x] ADR 0002 (on-device LLM) and ADR 0007 (customizable UI)
- [x] Gradle project: wrapper (jar SHA-256 verified against gradle.org), version catalog, pinned versions, network-isolation build check
- [x] Gradle dependency verification metadata (523 components, SHA-256, enforced on a clean build)
- [x] Regenerate `gradlew`/`gradlew.bat` with `./gradlew wrapper` (jar hash unchanged)
- [x] Modules `:app` and `:core:designsystem`
- [x] Token-based design system: `UiConfig` theme tokens (colors, light/dark, font pairing, type scale, density, radius), bundled Bodoni Moda + Instrument Sans, base components — no hard-coded colors/fonts in screens (ADR 0007)
- [x] Five-tab navigation shell (Today, Train, Fuel, You, Coach) with tab order read from `UiConfig`
- [x] Today screen rendered from a card registry + ordered card list in `UiConfig` (placeholder cards)
- [x] `UiConfig` guardrails in code (contrast ≥ 4.5:1, type-scale range, required tabs) + unit tests, plus You → Customize demo with Undo and "Simulate a Coach change"
- [x] Manifest hardening: no INTERNET permission, `allowBackup=false`, data-extraction rules, `FLAG_SECURE`
- [x] Debug + release build, lint (0 errors) and unit tests (20/20) pass
- [x] App installs and runs on the emulator (API 37): all 5 tabs render, Customize works, simulated Coach change + Undo + lock verified, 0 crashes
- [ ] ~~App installs on the Poco F5~~ → deferred by owner to the Phase 1 model benchmark (first install that's worth it)

### Phase 1 — Foundation + Fitness (v0.1)
Onboarding, encrypted storage + biometric lock, on-device Coach chat (Gemma 4), workout plan generator, workout logging, progressive overload, offline exercise database, phone step counter.

**1a — On-device model (branch `feat/phase-1-model-benchmark`)**
- [x] Re-check models: Gemma 4 E4B (3.66 GB) and E2B (2.59 GB) `.litertlm`, Apache-2.0, ungated; Gemma 3n dropped (ADR 0002 updated)
- [x] `:ai` module: model catalog with SHA-256, `ModelStore` (hash-while-copy import, check, remove), `LlmEngine` + LiteRT-LM 0.18.0 implementation, benchmark runner, answer checks, report; unit tests (14)
- [x] You → "AI model & benchmark" screen: add model file (system picker), check, remove, run benchmark, copy results
- [x] Review fixes (Sonnet review): per-case peak RAM, cancellation-safe conversation, streaming on the engine thread, off-main-thread delete, free-space check
- [x] Emulator check: screen renders; unchecked file → "Check file" → rejected → removed
- [x] ~~Emulator end-to-end~~ → skipped; went straight to the phone (real target)
- [x] **Phone benchmark** on the Poco F5: all 6 cases ran, all checks passed → **Gemma 4 E4B on GPU + MTP** chosen (15.6 tok/s, 0.7 s first token, 3.7 s photo, ~1.1 GB); results in ADR 0002

**1b — Foundation (branch `feat/phase-1b-foundation`)**
- [x] Versions checked: Room 2.8.5, SQLCipher 4.19.1, Tink 1.23.0; platform `BiometricPrompt` instead of `androidx.biometric`
- [x] `:core:model`: equipment catalog (50 kinds in 4 groups, ADR 0008), profile, training setup, validators, metric `Units`
- [x] `:core:security`: Tink AES256-GCM keyset under a Keystore master key, wrapped DB passphrase (`no_backup/db.key`), app lock (cold start + 5 min in background, fingerprint/face or screen-lock PIN)
- [x] `:core:data`: Room + SQLCipher, v1 schema exported; repositories for UI config + history, profile, training setup; onboarding saved in one transaction
- [x] Saved customizations: `UiConfigStore` snapshot is restored at start and saved on every change
- [x] Onboarding: profile (metric), goal, experience, training locations (Gym, Home, custom) with equipment tick-lists and kg lists, weekly schedule (location per day), injuries
- [x] You → Profile and You → Training setup editors
- [x] Tests: 69 unit tests; 4 instrumented SQLCipher tests (emulator only)
- [x] Emulator check: lock prompt (PIN), cancel → Unlock, onboarding end to end, edits saved, dark mode + profile survive a restart, short background stays unlocked
- [x] Sonnet review fixes: weight lists checked against the same 0.5–200 kg range as the validator; re-lock keeps unsaved edits (app stays composed under an opaque lock layer that takes touches and Back); edit screens show a load error instead of crashing; unreadable saved UI config is never overwritten until the owner changes something; DB open can't leak on cancellation; Retry always restarts; unlock while backgrounded re-arms the timer; no auto-prompt again after rotation
- [x] Phone install + owner's check (2026-10-18; PR #3 merged). Owner feedback: the Coach should decide the training/rest split → ADR 0009

**1c-1 — Availability + Coach-planned week (branch `feat/phase-1c-plan`)**
- [x] ADR 0009: the owner gives availability (place or "Not free" per day); Kotlin decides how many sessions and which days
- [x] `schedule` → `availability` in model, data and onboarding ("When can you train?"; at least one free day required); same table, no migration
- [x] `:core:training`: curated catalog of 101 exercises tagged with pattern, muscles, level and equipment alternatives (ADR 0008)
- [x] Plan generator: sessions/week from goal + experience, day choice with rest in between, full body / upper-lower / push-pull-legs, exercises from each day's equipment, sets/reps/rest by goal; says when a slot can't be filled; 15 unit tests
- [x] Train tab (week, reasons, session details), Today card (today's session or rest + next), onboarding summary shows the split
- [x] Emulator check: split shown with reasons; unfillable slot explained; adding free days in Training setup re-plans (6 free → 4 sessions with rest)
- [x] Sonnet review fixes: strength rep ranges only for heavy lifts (bodyweight/single-side stay moderate, beginners 8–12); swings never a main lift; high-intensity conditioning as 20–30 s rounds; slow negatives 3–5 reps; barbell work needs plates, bench/overhead press need a rack; adjustable bench counts as flat; ring dips split out as advanced; main slots prefer heavy lifts for strength and avoid too-easy picks; onboarding preview race fixed; "today" rolls over at midnight; collapsed cards say when something isn't possible
- [x] Phone install + owner's check (2026-10-21, together with 1c-2): week, session details, Today card and one-day Apply/Undo correct on the phone

**1c-2 — Coach chat (branch `feat/phase-1c-coach`, [ADR 0010](docs/adr/0010-coach-chat-tools.md))**
- [x] Model runtime: E4B loads on the first message (GPU + MTP, CPU fallback), one answer at a time, stopped and unloaded when the app goes to the background
- [x] Streaming chat on the COACH tab; history in the encrypted DB (v2, Room auto-migration from v1)
- [x] `:coach` module: prompt with the app's facts (profile, places, free days, this week's plan with ids, avoided exercises, look, locks), fixed tool schemas, reply parser, validator (equipment names + synonyms, catalog exercises, own places, this week's dates), relevance guard
- [x] Tools: equipment for one day ("today I only have X and Y"), avoid/allow an exercise (injuries), change a free day, theme, Today cards; ask first (proposal card with plan diff → Apply / Not now), re-validated on Apply, Undo, owner locks "Your plan" / "Your free days"
- [x] Planner overrides: one-day equipment (never moves days or ripples into other days), excluded exercises
- [x] Prompt eval on the emulator (real model, test data): 5/8 → 8/10 → 8/10 → see decision log
- [x] Sonnet review fixes: Apply/Not now/Undo serialised (no double apply); owner message saved together with the answer (failed or stopped answers give the text back); whole-prompt length budget, owner free text one line and capped; Undo only on the latest applied change and only marked undone if it worked; look-rule refusals shown as such, locked proposals can be applied after unlocking; no model load after the app went to the background; streaming scroll without jank; model text sanitised; case-insensitive tag hiding; card guard needs "card" or show/hide/move
- [x] Phone check (2026-10-21): runs on the GPU (status line + GPU program cache), cold first answer ~25–45 s incl. load, warm answer ~6 s; Apply/Undo, avoid exercise, question-only answer, Stop, Home (model freed, app ~164 MB) all work. Found: theme request gets no card and the Coach claims it is done; locked requests show no "Locked by you" card; knee-pain swap picks another lunge → fixes on `fix/phase-1c-phone-feedback`

**1c-2 fixes from the phone check (branch `fix/phase-1c-phone-feedback`)**
- [x] Theme request without a tool line: the app proposes it itself
- [x] "It's done" claims get a correction note from the app
- [x] Locked changes show "Locked by you" (prompt asks the model to still write the tool line)
- [x] Pain avoids the whole movement family (one transaction, one Undo); Train says when a slot is empty because of avoided exercises
- [x] "Nothing to change" card state (no Apply)
- [x] Keyboard closes on send; Motion tokens; smoother tab, chat, card and theme transitions
- [x] Today: current header and honest placeholder cards; Train: "Loads" note only when weights are used
- [x] Prompt eval re-run (emulator CPU, real model, 12 cases incl. the locked and "suggest the dark theme" cases): 11/12; 3 replies claimed a change (now corrected by the app note); the miss: "tomorrow in a hotel" got `set_day` (dropped by the guard)
- [x] Sonnet review and fixes: "use lunges again" lifts the whole family; theme fallback also after a rejected tool line and not on negations ("I don't want…", "how do I…"); pain words matched as whole words; narrower claim patterns plus a "locked" note; "nothing to change" compares whole sessions incl. equipment; plan previews off the main thread; no item animation on the streaming answer; Today greeting and date roll over
- [x] Re-check on the phone (2026-10-21, Claude tapping over adb): dark-mode card + app note, Apply/Undo; "Locked by you"; knee pain avoids all lunges, Undo brings them back; "Nothing to change"; keyboard closes on send; Today copy. Smoothness is the owner's to judge
- Later: change a place's saved equipment via the Coach, labelled specialists

**1c-3 — Logging and progression (branch `feat/phase-1c-logging`, on top of the 1c-2 fixes)**
- [x] DB v3 (auto-migration, new tables only): `workout_session` (an open one can be resumed) and `workout_set` (one row per session, variant and set number)
- [x] `WorkoutRepository`: start or resume, log or remove a set, finish (empty workouts are dropped), recent history
- [x] Progressive overload in Kotlin (`Progression`), per variant (exercise + equipment), from the owner's kg lists; unit tests
- [x] Coach facts: last performance and the app's next target per planned exercise, workouts in the last 4 weeks; worst-case prompt budget test
- [x] Focus-mode screen (Sonnet, from the Opus spec): forced dark palette, set rows with kg/reps steppers and Done, optional effort 7–10, rest timer (wall clock), Prev/Next/Finish, End with confirm, resume after the app closes, screen kept on; Start/Resume on Train and Today
- [x] Instrumented: v2 → v3 migration with data (6+4+2 DB tests pass); prompt eval 12/13 (13 cases incl. "the reply quotes the app's next weight": it said 42.5 kg, the app's target). The miss: "make my plan harder" got a `set_day` proposal (needs Apply; the same case passed in the previous run)
- [x] Sonnet review and fixes: writes serialised and re-read inside the lock; `logSet` in one transaction keeping the first completion time; "later sets follow" only for rows not set directly; untick keeps values; Finish can't run twice and shows a failure; no End dialog, keep-screen-on or timers while locked (`LocalAppLocked`) or in the background; text fields keep typing; working load = the load of most sets; abandoned workouts close at their last set; Coach targets per day + variant; minutes shown as minutes
- [x] Phone check (2026-10-21, Claude tapping over adb; test sets unticked and the empty workout dropped, so no data left): start, steppers (the owner's dumbbell list), Done, rest timer, Skip, effort, Next/Prev, later sets follow set 1's kg but not a row set by hand, resume after a force-stop (ticks kept, lands on the first unfinished exercise), lock after 5 min with no End dialog, Finish. Coach with no history names no weight. Found: **the screen stayed on behind the lock** → fixed (window flag; checked on the emulator). Not checked: progression and the Coach's weight with real history (would need a saved workout)

**UI pass (branch `feat/ui-editorial-pass`, [ADR 0011](docs/adr/0011-design-guidelines-editorial-pass.md))**
- [x] Tokens: Bodoni optical sizes, tabular figures, balanced headlines, spacing scale with `gutter`, `accentOnInk`
- [x] Components: `Skeleton`, `LinkRow`, `TextAction`, `M3SquareButton`, `InkPanel`, `HairRule`, `M3Snackbar`, tick glyph
- [x] Today: story headline from the plan, one boxed workout card, ink note, unbuilt cards named once
- [x] Train: Mon–Sun strip, selected day with a numbered list, rest-day panel, duration estimate
- [x] Coach: ink bubbles, serif answers, try-asking rows, square send, Clear with Undo
- [x] Workout: one control row per set, tabular numbers, compact footer, rest bar
- [x] You, Fuel, onboarding, edit and AI model screens; square switch and thin slider
- [x] Checked on the emulator with real screenshots (temporary local `FLAG_SECURE` removal, not committed): all five tabs, workout, Customize, dark mode. Unit tests and lint pass
- [ ] Phone check (owner's eye: OWNER_TASKS "UI pass")

**Cloud build support**
- [x] Linux `aapt2` checksum in dependency verification; `scripts/cloud-setup.sh`; click-by-click guide in OWNER_TASKS #6
- [ ] First cloud session confirms the build (not testable from this PC)

**1c-4 — Steps + check**
- [x] `StepLedger`: counter readings to daily totals; restarts and gaps across midnight handled; unit tests
- [x] DB v4 (auto-migration, new tables only): `step_day`, `step_reading`; emulator test from an encrypted v3 DB with data, all `:core:data` instrumented tests pass
- [x] `StepTracker` reads the sensor while M³ is in front; Steps card on Today with the permission asked in context; saved layouts gain the card after the workout
- [ ] Phone check of the sensor (the emulator has no step sensor)
- [ ] Phase 1 "Try it yourself" list + phone check

### Phase 2 — Nutrition (v0.2)
TDEE/macros, meal photo → estimate + critique, barcode scanning, fridge mode, grocery list.

**Core (branch `feat/phase-2-nutrition`)**
- [x] `Nutrition` in Kotlin: Mifflin-St Jeor, activity from the planned sessions, goal adjustment, macros; unit tests
- [x] Safety floors in code: calorie floor, at most 1% of body weight lost per week, no deficit under 18 or underweight
- [x] DB v5 (auto-migration, new table only): `food_entry`; repository validates every entry; emulator test from an encrypted v4 DB with data
- [x] Fuel tab and Today's Fuel card from the prototype; Add food form; Remove with Undo
- [ ] Phone check
- [x] Meal photo: camera or gallery, read by the on-device model, parsed and range-checked, opened in the form to check before saving; no photo kept; emulator check up to the model
- [x] Meal photo with the real model on the phone (2026-10-31): camera and gallery give estimates in about 30 s, a non-food photo is refused, no file stays behind
- [x] The Coach sees the food log, steps, weight trend and check-in; number questions answered instantly on the phone
- [x] Grocery list on Fuel: the week's shop worked out from your targets, plus foods you log often and your own items; refresh keeps what you ticked, added or removed
- [ ] Barcode, fridge mode

**Deload weeks (branch `feat/deload-grocery`)**
- [x] `Deload` in Kotlin: suggests a lighter week after 5–8 hard weeks, or 3 with tired check-ins; about 60% of the sets, about 10% lighter, rounded to weights you can load; unit tests
- [x] DB v8 (auto-migration, new tables only): `deload_week`, `grocery_item`; emulator test from an encrypted v7 DB with data
- [x] Train: suggestion with Apply and Not now, "Take a deload week", Undo, "Back to normal". Progression skips deload weeks. The Coach sees it in the plan header
- [x] Emulator check: 3 sets became 2, Back to normal shown, grocery list read through
- [ ] Phone check

**Reminders (branch `feat/notifications`, [ADR 0012](docs/adr/0012-reminders.md))**
- [x] `Reminders` and `ReminderRules` in Kotlin: when each one is due, and whether it's still worth sending; unit tests
- [x] Nine daily reminders plus Rest over: You → Reminders with switches, times, water interval and "Show my numbers"
- [x] Water log: DB v9 (auto-migration, new tables only), Habits card with +250 ml, the habit ticks itself at 2.5 L, +250 ml on the notification
- [x] One exact alarm, set again after boot, update, clock change and every start; retries a failed send
- [x] Plain requests to the Coach ("turn off water reminders") become proposal cards, with Undo and a `REMINDERS` lock
- [x] Emulator check: fires from a dead process, private text and "M³" on the lock screen, tap opens the right tab behind the unlock, alarm survives update and reboot, Rest over on time
- [x] Phone check (2026-11-01): test reminder, alarm chain, Coach card with Apply and Undo
- [ ] A real reminder seen with M³ closed; Autostart and battery settings (OWNER_TASKS #7)

### Phase 3 — Progress (v0.3)
Ghost-overlay progress photos, measurements, weight trend, weekly review that adjusts plans.

**Core (branch `feat/phase-3-progress`)**
- [x] `WeightTrend`: smoothed trend that copes with missed days, weekly rate over the last four weeks; unit tests
- [x] DB v6 (auto-migration, new tables only): `weight_entry`, `measurement_entry`; emulator test from an encrypted v5 DB with data
- [x] You tab: weight, chart with 4W/12W/6M, today's weigh-in, measurements and their screen
- [x] Each weigh-in updates the Profile weight from the trend, so calorie targets follow it
- [ ] Phone check
- [x] Weekly review card on You: last finished week from the logs, one piece of advice, all in Kotlin; unit tests; range queries tested on the emulator
- [ ] Weekly review on the phone with real data, and plan or calorie changes from it
- [ ] Progress photos with ghost overlay, side-by-side and timeline views

### Phase 4 — Live form check (v0.4)
Camera rep counting and form cues, voice cues.

### Phase 5 — Grooming (v0.5)
### Phase 6 — Style (v0.6)
### Research mode
Built alongside Phase 1–2 as the `:research` module (ADR 0005).

---

## Project structure
```
M3/
  app/                    # Application, navigation, feature screens (packages: today, train, fuel, you, coach, aimodel)
  app/.../feature/onboarding/  # Onboarding flow + You → Profile / Training setup editors (shared form sections)
  core/designsystem/      # Colors, typography, fonts, shared components, UiConfig + store
  core/training/          # Exercise catalog (JSON) + deterministic plan generator (ADR 0009)
  core/model/             # Plain data shared by data and the training engine: equipment catalog, profile, training setup, validators, metric Units
  core/security/          # Tink + Keystore secret cipher, wrapped DB passphrase, app lock + unlock prompt
  core/data/              # Room + SQLCipher database, schema JSON (core/data/schemas), repositories
  ai/                     # On-device LLM: model catalog + verified store, LlmEngine (LiteRT-LM), benchmark; later prompts, tool schemas, vision
  research/               # (later) the ONLY module allowed network access
  docs/                   # Spec, ADRs, working rules
```

---

## Decision log
| Date | Decision | Reason |
|---|---|---|
| 2026-10-16 | Native Android, Kotlin + Compose, Android-only | On-device AI, camera and Keystore are first-class there ([ADR 0001](docs/adr/0001-native-android-kotlin-compose.md)) |
| 2026-10-16 | LiteRT-LM runtime; Gemma 3n E4B int4 as baseline, re-benchmarked vs newer small Gemma at Phase 1 start | MediaPipe LLM API is maintenance-only; LiteRT-LM is Google's supported path with GPU + image input ([ADR 0002](docs/adr/0002-on-device-llm-litert-lm-gemma.md)) |
| 2026-10-18 | Model candidates switched to **Gemma 4 E4B** (preferred) and **E2B** (fallback); final pick by on-phone benchmark | Gemma 4 `.litertlm` builds are Apache-2.0 and ungated (no Hugging Face account needed), smaller than Gemma 3n E4B (3.66 vs 4.92 GB), newer, and support GPU speculative decoding ([ADR 0002](docs/adr/0002-on-device-llm-litert-lm-gemma.md)) |
| 2026-10-18 | Added `litertlm-android` 0.18.0 (pulls in Gson 2.14.0 and kotlin-reflect 2.4.0) in `:ai` only | Google's supported on-device LLM runtime; Gson/kotlin-reflect are its own dependencies (Google/JetBrains). Checksums added to dependency verification |
| 2026-10-18 | Model files: owner picks the file (system picker) or Claude streams it over adb into app-private storage; used only after size + SHA-256 match; verified state cached in a marker tied to size + mtime | App has no network (ADR 0005); hashing 3.66 GB on every launch would cost tens of seconds; any change to the file forces a re-check |
| 2026-10-18 | Model files are not encrypted at rest | Public files with no personal data; already in app-private, non-backed-up storage |
| 2026-10-18 | One native thread per engine; engine close blocks until freed before the next model loads | GPU contexts shouldn't be touched from several threads; two 3 GB models in memory at once could get the app killed |
| 2026-10-18 | Release builds ship arm64 native code only; debug keeps x86_64 | The phone is arm64; x86_64 is only for the emulator. Saves ~26 MB. Lint's `ChromeOsAbiSupport` warning accepted |
| 2026-10-18 | Benchmark prompts/answers may be logged (tag `M3Bench`) in debug builds only | They are fixed test prompts with no personal data; lets Claude read results over adb. User content is never logged |
| 2026-10-18 | **Gemma 4 E4B on GPU + multi-token prediction** is the Coach model; CPU only as fallback; E2B removed from the phone | On-phone benchmark: 15.6 tok/s, 0.7 s first token, 3.7 s photo, ~1.1 GB; E2B is faster but leaked foreign-script tokens and wrote weaker answers ([ADR 0002](docs/adr/0002-on-device-llm-litert-lm-gemma.md)) |
| 2026-10-18 | Models reach the phone by resumable `adb exec-in run-as … cat >>` over USB, never through shared storage | Leaves no files outside the app; `exec-in` streams reset every ~100–300 MB on this Windows setup, so the copy resumes from the remote size; the app's SHA-256 check is the final guard |
| 2026-10-18 | **Metric only** (kg, cm/m, km, kcal, 24-hour time); no imperial option | Owner's choice ("not the American stuff"); one formatting layer keeps units out of screens ([ADR 0008](docs/adr/0008-training-locations-equipment-adaptation.md)) |
| 2026-10-18 | Training locations (Gym, Home, custom) each with an equipment inventory; per-day "only X and Y" overrides; the Coach adapts a session/day/week/plan via schema-validated tools while a Kotlin engine picks substitutes and loads; changes are diffs the owner applies/undoes, with owner locks | Owner wants plans that fit available tools, driven by the Coach; small models can't be trusted with substitutions or loads (ADR 0003); same versioning/undo model as UI customisation (ADR 0007) ([ADR 0008](docs/adr/0008-training-locations-equipment-adaptation.md)) |
| 2026-10-18 | PR #1 and PR #2 squash-merged into `main` (owner approved) | Phase 0 + Phase 1a complete |
| 2026-10-18 | License: PolyForm Noncommercial 1.0.0 (`LICENSE`), with commercial licenses only by separate agreement with the owner | Owner wants the code public to view and download but no commercial use without compensating them. PolyForm NC is written for software (unlike CC BY-NC, which Creative Commons advises against for code); MIT/Apache/GPL all allow commercial use. Not OSI "open source" — it is source-available |
| 2026-10-18 | Phase 1 built on a branch stacked on the Phase 0 branch | PR #1 awaits the owner's merge approval; work continues without merging |
| 2026-10-18 | Added Room 2.8.5 (+ KSP compiler, Room Gradle plugin), `net.zetetic:sqlcipher-android` 4.19.1, `com.google.crypto.tink:tink-android` **1.23.0**; test-only `androidx.test:runner` 1.7.0 | Encrypted storage per ADR 0004 (Google/AndroidX, Zetetic — the SQLCipher authors — and Google). Tink 1.24.0 exists but was published two days earlier, so the previous stable is used until it has been out a while. Checksums added to dependency verification |
| 2026-10-18 | App lock uses the platform `android.hardware.biometrics.BiometricPrompt` (fingerprint/face "strong" or the screen-lock PIN), not `androidx.biometric` | minSdk is 33 and the platform API supports this combination since Android 11; `androidx.biometric`'s only stable release is 1.1.0 from 2021 (1.4 is still alpha) and would also need `FragmentActivity`. One dependency fewer |
| 2026-10-18 | The DB passphrase byte array handed to SQLCipher is not zeroed; every other copy is | SQLCipher keeps a reference to that array and uses it whenever it opens another connection (WAL readers, reopened idle connections). Zeroing it, as first planned, would break those opens. The derived key is in SQLCipher's native memory anyway |
| 2026-10-18 | If the phone has no screen lock, M³ stays locked and asks the owner to set one | The data is health data; a lock that can be skipped is no lock. The owner's phone has one |
| 2026-10-18 | The database opens off the main thread at app start; the UI shows the lock, then a blank ground until the DB and saved UI config are loaded | Keystore + Tink + SQLCipher key derivation take a moment; loading first avoids a flash of the default theme and of onboarding |
| 2026-10-18 | No destructive migration fallback; an unreadable DB or key file shows "Couldn't open your data — nothing was deleted" with Try again | Silently wiping the owner's data is worse than a visible error |
| 2026-10-18 | UI config + its undo history are saved as one snapshot (history rewritten each time, ≤ 100 rows) whenever the store changes; the latest snapshot wins if writes queue up | Simple and always consistent; the history is tiny. Writes run on an application-wide scope so leaving a screen never drops a save |
| 2026-10-18 | Equipment: 50 kinds in 4 groups (free weights, stations & machines, cardio, small gear); bodyweight is not an item; only dumbbells and kettlebells take a kg list; custom items by name | ADR 0008 asked for ~40 kinds; a separate cardio group keeps the long stations list readable. "Bodyweight only" is just an empty list, which avoids contradictory states. Enums are stored by name, so reordering never changes saved data; unknown names from a newer build are skipped |
| 2026-10-18 | Weight lists accept `2-24` (2 kg steps), `5-30/2.5` and `4, 8, 12`; parsed and range-checked in `Units` | Fast to type on a phone; one parser for all screens (metric only, ADR 0008) |
| 2026-10-18 | Onboarding has one equipment page per location; everything is saved in one transaction only when the owner taps Start | No half-finished profile in the DB; the steps adapt as locations are added or removed |
| 2026-10-18 | The owner gives availability; the Coach decides the training/rest split, in deterministic Kotlin | Owner's request after trying onboarding ("I want the coach to decide how the workout/rest split looks like"). Same `schedule_day` table with the meaning "free at" ([ADR 0009](docs/adr/0009-availability-coach-decides-split.md)) |
| 2026-10-18 | Own curated exercise catalog (101 exercises, JSON in `:core:training` resources), not an import of free-exercise-db | free-exercise-db (Unlicense) only tags equipment as "machine"/"other", too coarse for per-location planning (ADR 0008). A smaller hand-tagged set gives reliable picks; free-exercise-db stays an option for instructions and images later |
| 2026-10-18 | Equipment alternatives list the gear an exercise needs, in order of preference; generic names ("Biceps curl") with the chosen gear shown ("Dumbbells") | Fewer duplicate entries, more coverage per location, and the plan still says exactly what to use |
| 2026-10-18 | Plan is recomputed from profile + availability + equipment on every change; nothing stored yet | It's a pure function, so it's always consistent. Stored, versioned overrides arrive with the Coach's tools (1c-2), as ADR 0008 plans |
| 2026-10-18 | Max 5 sessions/week; heavy barbell lifts only in main slots; beginners get level-1 exercises; fat-loss and general-fitness sessions end with a 8–15 min conditioning finisher | Standard programming defaults (recovery, safety, adherence); all are constants in `PlanGenerator`, easy to tune |
| 2026-10-18 | Coach chat: stateless turns (fresh facts + recent conversation each turn), one `<tool>{…}</tool>` proposal per reply, validated against fixed schemas, ask first (Apply / Not now), re-validated and lock-checked on Apply, undoable | ADR 0003/0007/0008 applied to chat; a 4B model follows simple formats well but must not be trusted with state ([ADR 0010](docs/adr/0010-coach-chat-tools.md)) |
| 2026-10-18 | Prompt wording decided by an emulator eval (real E4B on CPU, made-up data, 10 cases): 5/8 → 8/10 → 8/10 → **10/10** | Fixes, in order: only propose changes when asked or for equipment/pain/schedule news, say "I suggest", equipment always via `day_equipment`, `set_day` only for the usual week; code guards drop look changes not asked for, `set_day` for "today/tomorrow" messages, and made-up tool names (the model proposes, code decides) |
| 2026-10-18 | Coach day changes cover the next 7 days (today + 6); a change for next week takes effect when that week starts | "Tomorrow" on a Sunday is next Monday (found in the emulator end-to-end test) |
| 2026-10-18 | Model cache in `no_backup/llm-cache`, GPU only and only with ≥ 1 GB free; the CPU fallback never uses a weight cache; emulators skip the GPU | On the emulator: Android purged `cacheDir` mid-write (GPU load failed), and the CPU weight cache grew past 2 GB and filled the disk, which **aborts the whole app** (native SIGABRT, can't be caught) |
| 2026-10-18 | Stopping an answer: `cancelProcess()`, then wait (up to 30 s) for the runtime to confirm before the conversation is closed; if it never confirms, the conversation is leaked rather than freed. The screen resets at once | Closing the conversation while LiteRT-LM was still reading the prompt crashed the app (native null-pointer, seen on the emulator after Stop) |
| 2026-10-18 | Chat history in the encrypted DB (v2 via Room auto-migration, tested from an encrypted v1 DB with data) | Personal data stays encrypted at rest (ADR 0004); auto-migration is safe because v2 only adds tables |
| 2026-10-18 | `:core:model` is a plain-Kotlin Android library (no Android code) | The training engine (Phase 1c) and `:core:data` both need the equipment enum; an Android library avoids adding the Kotlin JVM plugin and its verification entries |
| 2026-10-16 | Toolchain: Gradle 9.8.1, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12, compileSdk 37, targetSdk 36, minSdk 33 | Latest stable versions verified against Maven/Gradle metadata. compileSdk 37 is required: core-ktx 1.19.1, Compose UI 1.12.1, lifecycle 2.11.0 and hilt 1.4.0 declare `minCompileSdk=37` in their AAR metadata. targetSdk stays 36 until Android 17 behaviour changes are reviewed; minSdk 33 because the Poco F5 shipped with Android 13 |
| 2026-10-17 | Keep targetSdk 36 for now and accept lint's `OldTargetApi` warning | Owner's phone runs Android 15 (AQ3A.250226.002); targeting 37 means adopting Android 17 behaviour changes, which can wait until there's a reason |
| 2026-10-17 | Don't install on the phone until there's something worth testing; first phone install is the Phase 1 model benchmark. Use the emulator meanwhile | Owner's call: the Phase 0 shell is mostly empty; the emulator covers UI checks without touching the daily phone |
| 2026-10-17 | Automated UI checks read the accessibility tree (`uiautomator dump`), not screenshots | `FLAG_SECURE` makes screenshots black — by design |
| 2026-10-17 | Strict "leave no trace" rules for the owner's phone (only `com.m3.app`, no settings changes, no leftover files/processes) | It's the owner's daily phone; recorded in CLAUDE.md |
| 2026-10-16 | Gradle wrapper jar taken from the official 9.8.1 distribution (embedded in `gradle-wrapper-main-9.8.1.jar`), SHA-256 matched gradle.org | The jar in Gradle's source repo at the release tag is not the published wrapper and failed checksum verification |
| 2026-10-16 | Navigation Compose 2.x with type-safe routes (not Navigation 3) | Mature, documented, enough for 5 tabs; Nav3 can be revisited if needed |
| 2026-10-16 | Room 2.8.x + SQLCipher `SupportOpenHelperFactory` planned for Phase 1 (not Room 3) | The Room 2 + SQLCipher path is documented; Room 3's SQLCipher driver integration could not be verified |
| 2026-10-16 | Hilt (KSP) for dependency injection from Phase 0 | Structure from day one avoids a refactor when storage/AI modules arrive |
| 2026-10-16 | One Coach chat; specialists answer with labels; model proposes, code decides | Avoids confusing parallel chats; small models are unreliable with numbers ([ADR 0003](docs/adr/0003-one-coach-llm-proposes-code-decides.md)) |
| 2026-10-16 | SQLCipher + Tink + Keystore; no `security-crypto` | Health data and body photos must be unreadable off-device; `security-crypto` is deprecated ([ADR 0004](docs/adr/0004-encryption-at-rest.md)) |
| 2026-10-16 | Only `:research` may use the network; every request approved by the user | Owner wants research mode without leaking personal data ([ADR 0005](docs/adr/0005-research-mode-network-isolation.md)) |
| 2026-10-16 | Start with few modules, add per phase | Single developer, single device; keep the network boundary ([ADR 0006](docs/adr/0006-module-layout.md)) |
| 2026-10-16 | Fonts: Bodoni Moda + Instrument Sans, bundled in the APK | Magazine feel from the prototype; bundling avoids runtime font downloads (no network) |
| 2026-10-16 | Live workout screen uses an inverted dark "focus mode" | Readable at a glance mid-set |
| 2026-10-16 | UI driven by a versioned `UiConfig` (theme tokens + card layout); agents customize it only via scoped, validated tools, with undo and owner locks | Owner wants a highly customizable app the Coach and specialists can reshape, safely ([ADR 0007](docs/adr/0007-customizable-ui-agent-editable.md)) |
| 2026-10-16 | Owner's working rules copied to `docs/WORKING_RULES.md` | Versioned with the repo so every session loads them via CLAUDE.md |
| 2026-10-21 | A plain theme request ("switch to dark mode") that the model answers without a tool line gets a proposal written by the app, validated like the model's | On the phone the model twice answered without a tool line and claimed the change was done. Still a proposal, so a false match costs one tap |
| 2026-10-21 | Replies that claim a change happened ("I have switched…", "…will automatically…") get a note from the app, stored with the reply | Only Apply changes anything. Storing the note also shows it to the model in later turns, which stops it repeating its own false claim (seen on the phone) |
| 2026-10-21 | Locks: the prompt now asks the model to still write the tool line for a locked change; code shows "Locked by you" | With "don't propose locked changes" the model stayed silent, so the owner got no feedback (phone check) |
| 2026-10-21 | Avoiding an exercise for pain avoids its whole movement family (squats, hinges, lunges, presses, rows, pull-ups) in one transaction and one Undo. Dislikes, and isolation, core, carry and conditioning work, stay single-exercise | Knee pain on walking lunges swapped in reverse lunge and split squat. Those patterns load the same joints alike; the mixed patterns don't |
| 2026-10-21 | `ProposalStatus.NO_CHANGE`: a valid proposal that changes nothing shows "Nothing to change" and no Apply | The phone showed Apply on "No change: Wednesday already fits". Stored by enum name, so no DB migration |
| 2026-10-21 | Progressive overload is double progression per variant: every planned set at the working load (the load of most sets) and at the top of the rep range (not at RPE 10) → one load step up; below the range in the last two workouts at the same load → one step down (~10% for plates); otherwise the same load, one more rep. Steps: the owner's dumbbell/kettlebell kg list (2 kg / 4 kg if not listed), 2.5 kg plates, 5 kg machine stacks; bodyweight and fixed loads progress reps only | Simple, well-known, safe for a beginner to intermediate lifter; one step at a time can't overshoot. Numbers stay in Kotlin (ADR 0003); variant keys keep home and gym apart (ADR 0008) |
| 2026-10-21 | `LocalAppLocked` (set by MainActivity) tells screens the lock layer is up; the workout screen closes its dialog and stops keep-screen-on and timers while locked | The app stays composed under the lock layer, and a dialog is its own window above it (found in review) |
| 2026-10-21 | Workouts are stored set by set the moment a set is ticked; only one workout is open at a time; finishing a workout with no sets drops it | A closed app or a dead battery loses nothing; history stays free of empty workouts |
| 2026-10-21 | The Coach sees "last … ; next …" per planned exercise in a compact form, and the prompt says weights come only from "next" | It can explain progress without inventing loads; compact so the worst case (history on every exercise) still fits the prompt budget |
| 2026-10-21 | Gemma 4 E4B stays for now but may be replaced: the owner is open to switching models if it proves unreliable. A switch needs on-phone benchmark + prompt eval evidence (ADR 0002) | Phone check and eval show tool lines left out, false "done" claims and unrelated tools. Code guards cover them today; if failures continue, a better model beats more workarounds |
| 2026-10-21 | The workout screen keeps the screen on with the window flag (`FLAG_KEEP_SCREEN_ON`), not `View.keepScreenOn` | On the phone and the emulator, turning the view flag off at the moment the app comes back locked never reached the window (all views off, window still on), so the screen stayed on behind the lock. The window flag is re-sent reliably |
| 2026-10-21 | Claude can drive the phone over adb (taps and typing in `com.m3.app` only) for phone checks; the owner only unlocks M³ | The owner turned on Xiaomi's "USB debugging (Security settings)" themselves. Claude still changes no settings, taps only while M³ is in front, and leaves no test data behind |
| 2026-10-21 | Motion tokens (`Motion`, 150/220/300 ms, FastOutSlowIn): fades between tabs, animated chat items and cards, theme colours cross-fade. Coach keyboard closes on send; its Enter key sends | Owner asked for smoother animations and for the keyboard to close. Short durations, scaled by the system animation setting |
| 2026-10-30 | Keep the editorial look; apply the owner's design guidelines except their glass and gradient styles | The guidelines say the project's design system wins on conflicts; CLAUDE.md fixes square corners, palette and fonts ([ADR 0011](docs/adr/0011-design-guidelines-editorial-pass.md)) |
| 2026-10-30 | Bodoni Moda uses one font family per text size, each with its own `opsz` | Compose doesn't set the optical-size axis, so headlines lacked the prototype's fine strokes |
| 2026-10-30 | Unbuilt Today cards are named once in "Coming to M³" instead of drawn as empty boxes; their switches and order stay in Customize | Four empty boxes on the main page; removing them from the config would lose the owner's choices |
| 2026-10-30 | Masthead shows the ISO week, not "Issue 001" | A number that never changes reads as fake |
| 2026-10-30 | Coach Clear hides the chat and deletes it 6 s later; sending a message deletes it at once | Guidelines prefer Undo to confirmation. If the app closes first, nothing is lost |
| 2026-10-30 | Session length is an estimate in `PlanText`: 5 min warm-up, 45 s per rep set, plus rest, to the nearest 5 min | Display only; no decision depends on it |
| 2026-10-30 | Steps are read only while M³ is open, with no background service or notification; the sensor's running total fills the gaps | Since Android 9, background apps get no events from on-change sensors, so WorkManager wouldn't help. A foreground service would mean a permanent notification on the owner's daily phone |
| 2026-10-30 | The first reading only sets a starting point; a gap across midnight is shared out by time; a lower counter means a restart | Nothing shows when earlier steps happened. Sharing by time loses no steps, though each day's split is an estimate |
| 2026-10-30 | Calorie target: Mifflin-St Jeor × activity (1.2 / 1.375 / 1.55 / 1.725 by planned sessions), then −20% to lose fat, +10% to build muscle, +5% for strength. Protein 1.6–2.0 g/kg, capped at 35% of energy; fat 0.8 g/kg, capped at 30%; carbs the rest | Standard formulas; the caps keep macros sensible at very high body weights. All numbers are in Kotlin (ADR 0003) |
| 2026-10-30 | Safety floors: 1,200 kcal (female) or 1,500 kcal (male), never below maintenance − 1% of body weight a week, and no deficit under 18 or with a BMI under 18.5. A floor never pushes the target above maintenance | CLAUDE.md requires floors in code. The age and BMI rules go further: a weight-loss target is wrong for both groups whatever the owner picks |
| 2026-10-30 | Food log is manual first, with removal done at once plus Undo | Exact numbers, no model needed. Meal photos build on the same entries |
| 2026-10-30 | Weight trend: an exponential average (10% a day), adjusted for missed days; the Profile weight takes the trend, rounded to 0.1 kg, after each weigh-in | A single day's weight is mostly water, so targets shouldn't move with it. This also covers the spec's "adjusted by weekly trend" in its simplest form |
| 2026-10-30 | One weigh-in per day, a second replaces the first; no delete | Fixing a typo is just logging again. Keeps the log simple |
| 2026-10-30 | Progress lives at the top of the You tab, as in the prototype; there's no separate tab | The prototype's fourth tab is exactly this page, and tabs are limited to five |
| 2026-10-30 | A plain change to the usual week ("I can train on Fridays at Home") is proposed by the app, and the owner's words beat the model's tool line. Questions and one-offs stay with the model | On the phone the model wrote `not_free` for the first wording and no tool line for the second. Still needs Apply, so a false match costs one tap |
| 2026-10-30 | Today's weigh-in and measurements can be removed | A wrong entry otherwise stayed for good, and it let the phone check leave no test data |
| 2026-10-30 | The morning check-in gives advice in Kotlin and doesn't change the plan | Rule: sleep or energy at 2 or below, or soreness at 4 or above, says take it easier. Changing volume needs its own undoable tool |
| 2026-10-30 | Meal photos use the system camera through one private FileProvider folder, or the system photo picker; no CAMERA permission. The image is cut to 768 px, sent only to the on-device model, and deleted | The app needs no new permission, and a meal photo has no use after the estimate |
| 2026-10-30 | A photo estimate never saves by itself: it opens in the Add food form. Replies are parsed and range-checked, and calories more than 25% from the macro sum are replaced by the sum | LLM output is untrusted (ADR 0003), and photo estimates are often 20–40% off |
| 2026-10-30 | Plain questions about today's protein, calories, steps and weight trend are answered by `DailyAnswers` in Kotlin, without the model; advice stays with the model | Eval on real E4B: told "58g left" it said 53g, and it made up last week's weight. Reliable numbers can't come from a prompt |
| 2026-10-30 | The model sees today's logs, with each remaining amount worked out for it, and keeps them for advice | It needs the context to advise, and it can't be trusted to subtract ("158g of protein remaining") |
| 2026-10-30 | Avoiding an exercise needs the owner's message to name it | Eval: "I slept badly and I'm sore" got a proposal to avoid bench press for good |
| 2026-10-30 | Eval read answer by answer, not by score: 17/17 hid three wrong numbers. Latest run 15/19 on 19 cases: theme with no tool line (the app's fallback covers it, the eval doesn't run that), invented `adjust_weight` tool with made-up increases (rejected, but the words remain), wrong protein (now Kotlin), bench press for soreness (now guarded) | Evidence for the model decision. Scores vary run to run; expected-number checks only look for one correct number |
| 2026-10-31 | The weekly review is plain Kotlin from the logs, with one piece of advice per week, urgent rules first: eating far too little (points to a doctor or dietitian), losing over 1% a week, then sessions, protein, sleep, a flat trend. It advises and doesn't change anything yet | The model can't be trusted with these numbers, and the spec requires a change of tone for very low intake. A real plan or calorie change needs an undoable tool |
| 2026-10-31 | Next model to try: Qwen3-4B (int4) for chat with E4B kept for photos, then LFM3-Omni-2B as an all-in-one. Not Gemma 4 12B: its file is 6.9 GB, it needs about 7.3–7.9 GB of GPU memory, and no Android GPU support is listed | From the model cards ([ADR 0002](docs/adr/0002-on-device-llm-litert-lm-gemma.md)); none is proven on this phone. A switch still needs the on-phone benchmark and the eval |
| 2026-10-30 | Standing permission to merge: squash-merge once local checks pass and the Opus final check is done | Owner's OK ("any merges"). The repo has no CI, so local checks are the gate |
| 2026-11-01 | Reminders decide when due, from the logs, and stay silent if the thing is done; one exact alarm at a time with `USE_EXACT_ALARM`, no WorkManager | Owner asked for meaningful reminders on their daily phone. A plain timer can't know what's done. The Play policy on that permission doesn't apply: M³ isn't on any store. No new dependency ([ADR 0012](docs/adr/0012-reminders.md)) |
| 2026-11-01 | Notification text carries no numbers or plan details unless "Show my numbers" is on; the lock screen only shows "M³" | The shade can be read without M³'s unlock, and notification listeners can read it too. Privacy is the default; useful numbers are the owner's choice |
| 2026-11-01 | Water is a real log (ml per day), the habit ticks itself at 2.5 L, and "+250 ml" works from the notification without unlocking M³ | A yes/no habit can't say when to stop reminding. The action only adds a glass and shows nothing |
| 2026-11-01 | Plain reminder requests to the Coach are parsed in Kotlin and skip the model | No prompt space or eval needed, and it can't state a wrong time. Still a card with Apply, Undo and a lock (ADR 0007) |
| 2026-11-01 | The weigh-in nudge waits 3 days since the last weigh-in | The You tab says a few mornings a week, and daily weight is noise |
| 2026-11-01 | Deload weeks are suggested, never automatic; workouts in a deload week don't count for progression | Plan changes need the owner's OK (ADR 0008). Light loads would otherwise pull the next target down |
| 2026-11-01 | The deload is named in the plan header, not in a line of its own | A separate line went 52 characters past the 7,500-character prompt budget. The budget stays as it was |
| 2026-11-01 | Grocery amounts come from a table of staples and fixed shares of the week's macros, rounded to what shops sell. No pork or alcohol in the table | Numbers stay in Kotlin (ADR 0003). The owner removes what they don't want, and a removed line stays gone after a refresh |
| 2026-11-01 | **Qwen3-4B with `/no_think` for chat, Gemma 4 E4B for photos**, one model in memory at a time; chat falls back to E4B if Qwen is missing | On the phone's GPU E4B garbled digits ("422.5 kg" for 42.5) on both runtimes. Qwen scored 17/19 with clean digits and no false claims, at about 24 s an answer instead of 10. It can't read images and won't load with MTP ([ADR 0002](docs/adr/0002-on-device-llm-litert-lm-gemma.md)) |
| 2026-11-01 | LiteRT-LM 0.19.0 replaces 0.18.0 | Qwen's file crashes the GPU engines of 0.18.x. Checked on the phone: chat, meal photo, no crash. Digit garbling was the same on both |
| 2026-11-01 | The Coach's facts show eaten and target together (`protein 112/170g, 58g left`) | Qwen called the amount left the target and subtracted again on the phone. Fits the prompt budget |
| 2026-11-01 | Debug-only eval runner (`EvalActivity`, `scripts/phone-eval.sh`), in `src/debug` only, startable only from adb (`DUMP` permission) | `connectedAndroidTest` on `:app` wipes the owner's data, so the eval couldn't run on the phone's GPU any other way |
| 2026-10-30 | Added the Linux `aapt2` jar to dependency verification (SHA-256 of the jar from Google Maven, whose published SHA-1 matched) | Only the Windows jar was listed, so Linux builds would fail. Verification stays on |

---

## Known limitations
| Limitation | Why | What would remove it |
|---|---|---|
| Android only, single device | Built for the owner's Poco F5; on-device AI and Keystore APIs are Android-native | A rewrite for iOS |
| No cloud backup | Privacy: data never leaves the phone (ADR 0004) | Planned password-protected encrypted export file |
| Photo-based calorie estimates are approximate (often ±20–40%) | Portion size can't be measured reliably from one photo | Barcode or weighed entries (exact) |
| Undo restores the whole previous config, and only the owner can undo | Simple, predictable history for Phase 0; agent tools don't exist yet | Per-change inverse operations and a permission-checked agent undo when agent tools arrive (Phase 1) |
| Card options (`SetCardOption`) aren't validated | No card reads options yet | Per-card option schemas when real cards are built |
| Accent-vs-background contrast isn't checked (only text pairs are) | Accent is used for fills and small marks, not body text | Add accent/ground check if accent is ever used for text |
| Screenshots and screen recording of the app are impossible (black) | `FLAG_SECURE` protects body photos and health data | An owner setting to allow screenshots, if ever wanted |
| Dark mode may flash light for a moment at app start | Window background is the light ground colour before Compose draws | Day/night window background resources (`values-night`) |
| No wearable data (heart rate, sleep tracking) | Owner has no watch/band | Health Connect integration if a wearable is added |
| The model benchmark has no timeout, and Stop can't interrupt a model load in progress | LiteRT-LM's load is one blocking native call | A watchdog/timeout around load, if hangs are seen on the phone |
| Benchmark results disappear when you leave the screen, and leaving stops a running benchmark (copy results first) | Benchmark is a one-off diagnostic, not user data | Save results to the encrypted DB if they become useful over time |
| "Peak RAM" in the benchmark is the app process's RAM; GPU memory isn't fully counted | Android exposes no simple per-app GPU memory figure | Read GPU memory from vendor tools during the phone run |
| Importing a model from Downloads temporarily needs its size twice in free space (up to ~7.5 GB) | The app can't read the Downloads file in place (no storage permission, by design) and copies it into private storage | Claude can stream it over adb instead (needs the size once), then delete the Downloads copy |
| Uninstalling M³ deletes the AI model; it must be copied again | App-private storage is removed with the app | — (intended: nothing is left behind) |
| While the AI model is running, Android may close other apps that are idle in the background | The model needs about 1 GB of RAM (4 GB on CPU); Android frees memory by closing cached apps, which restart normally when opened. Seen during the benchmark (~10 cached apps) | Unload the model when M³ goes to the background (planned with the Coach chat); smaller future models |
| The model sometimes picks an odd word or misreads the format (e.g. "doing more food, weights") | 4B-parameter on-device model | Coach answers are advice text only; numbers and plans come from Kotlin (ADR 0003); revisit with newer models |
| The app lock is a screen gate; the database key is not tied to unlocking | Background work and the Coach (Phase 1c) need the DB without a prompt every time. Data at rest is still protected by SQLCipher + a Keystore key that can't leave the phone | A second, auth-bound key for the most sensitive data (e.g. photos), if wanted |
| The database key stays in the app's memory while M³ runs | SQLCipher keeps the passphrase to open new connections (see decision log) | — (inherent to SQLCipher; the app's memory is private to it) |
| The lock timeout is fixed at 5 minutes | Kept as a constant for Phase 1b | An owner setting under You (planned) |
| M³ won't open on a phone without a screen lock | A lock that can't be enforced isn't a lock (decision log) | — (intended) |
| If Android closes M³ in the middle of onboarding, the answers so far are lost and onboarding starts again | Onboarding is saved in one go at the end, so there's never a half-finished profile | Keep the draft in saved state if this ever bites |
| If the Keystore key is lost (very rare, e.g. a broken system update), the data can't be opened | That's what makes the data unreadable off the phone (ADR 0004). M³ then shows an error and deletes nothing | The planned encrypted export file as a backup |
| Avoiding a whole family (e.g. all lunges for knee pain) leaves that slot empty; nothing else is put in | Another knee-heavy exercise could hurt too, and the planner can't judge which ones are safe | A pain-aware substitute (e.g. a hinge or glute exercise), if the owner wants one |
| The Coach can say internal words like "the current facts" | The prompt calls the app's data "facts" and the model sometimes echoes it | Reword the prompt (needs a prompt eval re-run) |
| Leaving Profile or Training setup with Back drops unsaved changes without asking | Simple list editors for Phase 1b | An "unsaved changes" prompt if it turns out to be annoying |
| While locked, the app's screens stay loaded in memory behind the lock layer (hidden, untouchable, not readable by accessibility) | Keeps half-finished edits and navigation when the 5-minute lock kicks in | — (intended) |
| The planner doesn't read injuries or custom equipment items itself | Free text; guessing would be unsafe | Since 1c-2 the Coach reads them and can propose avoiding an exercise; custom items still aren't mapped to catalog kinds |
| The Coach proposes at most one change per reply, and can't change a place's saved equipment | One validated change at a time keeps proposals clear; saved inventories are edited in You → Training setup | More tools (e.g. `setLocationInventory`) once the basics are proven on the phone |
| Each Coach turn re-reads the whole context, so answers take a few seconds longer than a kept conversation | Stateless turns keep facts current and the prompt within 4096 tokens | Keep the conversation open between turns if the delay bothers |
| Coach answers take about 24 s, and the first after opening the app also waits for the model to load (GPU: about 20 s; CPU fallback: ~1–2 min) | The model is unloaded in the background to free ~1 GB | Keep it loaded for a short time after leaving the app, if wanted |
| Only the most recent applied Coach change can be undone from the chat | Undoing an older one would overwrite changes made after it | Per-target undo history if needed |
| The model sometimes words a suggestion as if it were already done ("I've hidden the card") | 4B model; seen in evals even with the instruction | The proposal card always says "Suggested change" and nothing changes until Apply |
| Prompt evals ran on the emulator CPU, not the phone's GPU | The emulator can't run the GPU path | Re-check answers on the phone (OWNER_TASKS 1c-2) |
| The plan's rep ranges ignore how heavy your dumbbells are (very light dumbbells still get a normal range); the kg list is used for the next load | Choosing exercises by load needs history first | Use logged loads to pick harder variants when the heaviest bell is too easy |
| The first workout of an exercise has no suggested weight ("pick a weight you could lift 2–3 more times") | No history to base it on, and guessing a load is unsafe | The first logged workout sets the starting point |
| No exercise instructions or pictures yet | The catalog has tags only; mapping 101 exercises to a dataset is its own task | Add instructions (e.g. from free-exercise-db, Unlicense) in a later step |
| The exercises of a week stay the same; only loads and reps progress | Rotating exercises is a separate planning rule | Variation rules once there are weeks of history |
| Release (minified) builds compile but weren't run on a device in Phase 1b | The phone gets debug builds (same key as the installed app); R8 rules for Tink/SQLCipher come from their own packages | Run the release build on the emulator before the first release install |
| The app can't make the model propose something it chose not to; only theme requests have a code fallback (eval: 11/12, and the model still claims changes in about 1 in 4 replies) | Equipment, pain and schedule need the model to read free text; a keyword guess would be unsafe | Better prompts measured by the eval, or a larger model |
| The workout screen's tap handling (queue, "later sets follow", double-tap Finish) has no automated test; the pure parts (`WorkoutLogic`, `Progression`) do | It needs a fake repository and a coroutine test setup that isn't in the project yet | A `WorkoutViewModel` test with an in-memory Room DB |
| The deload rule's thresholds (5–8 hard weeks, soreness 4, energy 2) are standard defaults, not tuned to the owner | Needs months of logged history | Tune them from the logs |
| "Back to normal" after training in a deload week puts those light workouts back into progression; workouts done before accepting a mid-week deload drop out of it | Whether a workout counts follows its week, not the workout | Store the deload flag on each workout |
| A workout left open from Sunday into a deload Monday gets deload sets with normal loads | The plan follows today's week, targets follow the workout's week | Freeze the week on the workout, if it's ever seen |
| Reminders stop if M³ is force-stopped or Xiaomi stops it, until M³ is next opened | Android delivers no alarm or broadcast to a stopped app | Autostart and No restrictions (OWNER_TASKS #7) |
| No Snooze on reminders, and they don't learn when you usually train | Smallest useful version | A snooze action; a workout time from logged start times |
| Reminders don't mention steps | Android gives background apps no sensor events | A foreground service, which means a permanent notification |
| The Coach changes one reminder per message, and can't turn them all off at once; a bare "at 5" means 05:00 | One proposal per reply; 24-hour time. The card shows the time before Apply | Several proposals in one card |
| Undo on a Coach reminder card overwrites a manual change made after it | Same as other Coach cards: Undo restores what was there | Compare before undoing |
| "+250 ml" on a notification works without unlocking M³ | It's one small write and reveals nothing; requiring an unlock would defeat the point | `setAuthenticationRequired`, if ever wanted |
| Grocery amounts are a general week of staples, not a meal plan, and ignore allergies and what's already at home | No meal planner or diet preferences yet | Diet settings; meal plans |
| The phone holds two models (5.9 GB), and going between chat and a meal photo reloads the other one, about 20 s | Qwen can't read images; E4B garbles digits in chat | A single model that does both well (LFM3-Omni-2B is the candidate) |
| Machine stacks are assumed to move in 5 kg steps | Stacks differ between gyms and aren't in the equipment details yet | A per-place step setting, like the kg lists |
| The rest timer has no sound or vibration while M³ is on screen; a notification only comes when you've left it | The screen stays on during a workout | Add a vibration (no runtime permission) if the owner wants it |
| Progression doesn't use RPE beyond "10 = don't increase" | Most sets won't have RPE; the rule must work without it | RPE-based autoregulation once enough RPE is logged |
| The theme cross-fade recomposes every colour reader for ~220 ms | Seven animated colour tokens; simplest correct approach | Animate one progress value and lerp, if it ever stutters on the phone |
| Steps taken since the last app open are lost if the phone restarts first, and a day you don't open M³ gets an estimated share of the gap | Android gives background apps no step events, and the sensor's total resets at reboot | A foreground service with a permanent notification, if the owner ever wants exact days |
| Calorie targets ignore steps, and don't yet learn your real burn from how your weight responds to what you eat | Needs weeks of matched food and weight data | Compare logged intake with the trend in the weekly review |
| Weight can only be logged for today, and only today's entries can be removed | Smallest useful log | Past-day entry and an edit list |
| The Coach can still mix up labels ("eaten" and "left") or add small inventions in advice; digits themselves stopped being garbled with Qwen | 4B model; only plain number questions and validated tools are protected. The prompt tells it to use only the facts, and it doesn't always | A stronger model (needs an on-phone benchmark), or a check of numbers in replies against the facts |
| The Coach sees only the last 3 foods by name; the totals cover the whole day | Prompt budget | A larger context window |
| The photo critique shows once and isn't saved with the entry | No DB change for a first version | A note column on `food_entry` |
| The system camera holds the photo in M³'s private folder as a plain file until it's read, for a few seconds | Only the camera app can write it; encryption starts after that | — (it's deleted right after reading) |
| The morning check-in can be changed but not cleared, and the Coach doesn't read it | Advice only for now | Add it to the Coach facts and a volume tool |
| Removing a weigh-in has no Undo | The number is quick to type again | An Undo toast, if it's ever missed |
| The model still wrongly says "I have updated" with a schedule card | 4B model; the app's note corrects it on screen | A better model or prompt (model decision) |
| A food can only be added for today, and an entry can't be edited, only removed and added again | Smallest useful log | Past-day logging and editing |
| The step goal is fixed at 8,000, and the Coach doesn't see steps yet | Kept simple for 1c-4 | A goal setting; add steps to the Coach facts |
| Customize still lists unbuilt Today cards, and their order has no visible effect yet | They're real config entries for cards that come later | Build the cards |
| Skeletons are generic bars, not the exact shape of each page | One component covers every load; most loads end before it shows | Page-specific skeletons if a load is ever slow on the phone |
| Cloud sessions can't run the phone check, the emulator eval or the Coach model | The VM has no phone, and the 3.66 GB model isn't in the repo | Run those on the PC; cloud does code, unit tests, lint and APK builds |
| In the Coach box, Enter sends; there's no newline key | Chat style, and the owner wanted the keyboard gone after sending | A setting, if newlines are ever needed |

---

## License
Source-available under the **[PolyForm Noncommercial License 1.0.0](LICENSE)**. Anyone may download, read, run and modify the code for personal, research, hobby, educational or other noncommercial purposes. **Commercial use is not allowed** without a separate license from the copyright holder (contact via https://github.com/M7md-AR-H). Third-party fonts and libraries keep their own licenses.
