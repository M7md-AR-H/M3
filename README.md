# M³ — Models Make Model

A private AI coach that lives on your phone: personal trainer, nutritionist and food critic, progress analyst, and later groomer and stylist. All AI runs on-device; personal data never leaves the phone.

**Status (2026-10-17):** Phase 0 complete — builds, tests and lint pass; verified on the emulator. Not yet on the phone (by choice). Next: Phase 1, starting with the on-phone AI model benchmark.

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
- [ ] Emulator end-to-end: Gemma 4 E2B on CPU answers the benchmark prompts
- [ ] **Phone benchmark** (needs owner: "Install via USB" + Wireless debugging) → record results in ADR 0002, pick the model

**1b — Foundation**
- [ ] `:core:security`: Keystore-wrapped Tink keys, biometric/credential app lock
- [ ] `:core:data`: Room + SQLCipher; persist `UiConfig` + change history (removes "customizations lost on restart")
- [ ] Onboarding: profile, goal, units, equipment, experience, schedule

**1c — Fitness**
- [ ] Offline exercise database (bundled, licensed data)
- [ ] Coach chat: streaming answers, labelled specialists, scoped schema-validated tools (incl. `ui.*`)
- [ ] Workout plan generator (deterministic Kotlin; the model proposes, code validates)
- [ ] Workout logging + live "focus mode" screen
- [ ] Progressive overload rules (Kotlin)
- [ ] Phone step counter (no wearable)
- [ ] Phase 1 "Try it yourself" list + phone check

### Phase 2 — Nutrition (v0.2)
TDEE/macros, meal photo → estimate + critique, barcode scanning, fridge mode, grocery list.

### Phase 3 — Progress (v0.3)
Ghost-overlay progress photos, measurements, weight trend, weekly review that adjusts plans.

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
  core/designsystem/      # Colors, typography, fonts, shared components
  core/security/          # (Phase 1) Keystore/Tink, biometric lock
  core/data/              # (Phase 1) Room + SQLCipher, repositories
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
| 2026-10-18 | Phase 1 built on a branch stacked on the Phase 0 branch | PR #1 awaits the owner's merge approval; work continues without merging |
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

---

## Known limitations
| Limitation | Why | What would remove it |
|---|---|---|
| Android only, single device | Built for the owner's Poco F5; on-device AI and Keystore APIs are Android-native | A rewrite for iOS |
| No cloud backup | Privacy: data never leaves the phone (ADR 0004) | Planned password-protected encrypted export file |
| Photo-based calorie estimates are approximate (often ±20–40%) | Portion size can't be measured reliably from one photo | Barcode or weighed entries (exact) |
| Undo restores the whole previous config, and only the owner can undo | Simple, predictable history for Phase 0; agent tools don't exist yet | Per-change inverse operations and a permission-checked agent undo when agent tools arrive (Phase 1) |
| UI customizations are lost when the app restarts | Persistence belongs in the encrypted DB, which arrives in Phase 1 (ADR 0004) | Phase 1: store `UiConfig` + history in SQLCipher |
| Card options (`SetCardOption`) aren't validated | No card reads options yet | Per-card option schemas when real cards are built |
| Accent-vs-background contrast isn't checked (only text pairs are) | Accent is used for fills and small marks, not body text | Add accent/ground check if accent is ever used for text |
| Screenshots and screen recording of the app are impossible (black) | `FLAG_SECURE` protects body photos and health data | An owner setting to allow screenshots, if ever wanted |
| Dark mode, colours and fonts were only checked structurally on the emulator, not visually | `FLAG_SECURE` blocks screenshots in automated checks | The owner's visual check on the phone in Phase 1 |
| Dark mode may flash light for a moment at app start | Window background is the light ground colour before Compose draws | Day/night window background resources (`values-night`) |
| No wearable data (heart rate, sleep tracking) | Owner has no watch/band | Health Connect integration if a wearable is added |
| The model benchmark has no timeout, and Stop can't interrupt a model load in progress | LiteRT-LM's load is one blocking native call | A watchdog/timeout around load, if hangs are seen on the phone |
| Benchmark results disappear when you leave the screen, and leaving stops a running benchmark (copy results first) | Benchmark is a one-off diagnostic, not user data | Save results to the encrypted DB if they become useful over time |
| "Peak RAM" in the benchmark is the app process's RAM; GPU memory isn't fully counted | Android exposes no simple per-app GPU memory figure | Read GPU memory from vendor tools during the phone run |
| Importing a model from Downloads temporarily needs its size twice in free space (up to ~7.5 GB) | The app can't read the Downloads file in place (no storage permission, by design) and copies it into private storage | Claude can stream it over adb instead (needs the size once), then delete the Downloads copy |
| Uninstalling M³ deletes the AI model; it must be copied again | App-private storage is removed with the app | — (intended: nothing is left behind) |
