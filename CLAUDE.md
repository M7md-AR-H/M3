# CLAUDE.md — M³ (Models Make Model)

Private, on-device AI coach (fitness → nutrition → progress → grooming → style) for one user's Xiaomi Poco F5 (12 GB RAM, Snapdragon 7+ Gen 2). Native Android, Kotlin + Jetpack Compose. Not commercial, not published to any store.

## Read first, every session
1. This file
2. `docs/WORKING_RULES.md` — the owner's process rules (model tiers, `.md` ownership, session protocol, git, attribution). Follow them; where they conflict with this file, this file wins.
3. `HANDOFF.md`, then `README.md` (status, roadmap, decision log, known limitations)
4. `docs/PRODUCT_SPEC.md` (full product/tech spec) and `docs/adr/` as needed
5. UI source of truth: the design prototype at https://claude.ai/artifact/RATouP8aDxkhKRNxgivVSJ

## Commands
Run from the repo root (Windows: `gradlew.bat`, Git Bash: `./gradlew`).
- Build debug APK: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew test`
- Lint: `./gradlew lint`
- Install on connected phone: `./gradlew :app:installDebug`
- Refresh dependency checksums after a deliberate version change: `./gradlew --write-verification-metadata sha256 help`

## Hard rules for this project
- **Privacy:** personal data (profile, logs, photos, chat, embeddings) never leaves the device. Only `:research` may do network I/O, only to allowlisted hosts, only after the user approves the exact outgoing request. No other module may depend on OkHttp/Retrofit/jsoup.
- **No telemetry:** no analytics, crash reporting, ads or tracking SDKs. Never log personal data (no `Log.*` of user content, even in debug).
- **Dependencies:** well-known, actively maintained libraries only (Google/AndroidX/JetBrains/Square first). Every version pinned in `gradle/libs.versions.toml`; no dynamic versions; Gradle dependency verification stays on. Adding a dependency needs a decision-log entry with the reason.
- **LLM output is untrusted:** the model proposes, code decides. Tool calls are parsed with kotlinx.serialization against fixed schemas and validated (ranges, enums) before any state changes. Numbers that matter (calories, macros, loads, safety floors) come from deterministic Kotlin, never from the model. Research claims must carry citations.
- **Safety floors in code:** minimum calorie intake and maximum weekly loss rate are enforced in Kotlin and cannot be overridden by the model.
- **Storage:** Room DB encrypted with SQLCipher; keys wrapped by Android Keystore via Tink; photos encrypted with Tink streaming AEAD in app-private storage. `allowBackup=false`.
- **Customizable by design (ADR 0007):** screens read colors, fonts, density and layout from `UiConfig`; never hard-code them in screens. New home-screen content is a registered card. Agents change UI only via scoped, schema-validated `ui.*` tools; every change is versioned and undoable; owner locks are respected; contrast/size/required-navigation guardrails are enforced in code.
- **Design:** the defaults match the prototype — off-white `#F6F4EF`, ink `#121212`, accent `#A3122A`, Bodoni Moda (headlines) + Instrument Sans (body), square corners, thin rules, 44 dp minimum touch targets.
- **Git:** never push to `main`; feature branch + PR + squash-merge; ask the owner before merging. Never add a "Generated with Claude Code" line anywhere.

## Module map
See README → "Project structure". Keep feature code out of `:core:*`; keep network code inside `:research`.
