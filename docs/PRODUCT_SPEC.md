# M³ — Models Make Model

A private, on-device AI coach for fitness, nutrition, progress, grooming and style.
Target device: **Xiaomi Poco F5, 12 GB RAM** (Snapdragon 7+ Gen 2, Adreno 725). Android only.

---

## 1. Principles

1. **Local first.** Every model runs on the phone. Personal data (profile, logs, photos, chat) never leaves the device.
2. **Research mode is the only network path.** It sends only generic, user-approved queries — never personal data.
3. **Well-known, secure dependencies only.** Google/AndroidX/JetBrains/Square first; nothing obscure. Versions pinned, checksums verified.
4. **The LLM talks, code calculates.** Calories, macros, progressive overload and safety limits are deterministic Kotlin, not model output.
5. **Healthy by design.** Hard floors on deficits, trend-based (not judgmental) body feedback, eating-disorder signal detection.

---

## 2. Priorities (build order)

1. Fitness
2. Nutrition
3. Progress
4. Grooming
5. Style

---

## 3. AI architecture

One multimodal LLM plays all coach personas via system prompts + tool calls. Small specialized models handle vision tasks.

```
User ──► Coach (Gemma 4 E4B + persona prompt + memory)
              │ tool calls
              ├─► calc_tdee(), plan_workout(), log_meal(), check_safety()  ← Kotlin (deterministic)
              ├─► search_knowledge(...)                                   ← RAG over bundled + researched docs
              ├─► analyze_pose(), compare_photos(), face_landmarks()      ← MediaPipe
              ├─► recall_memory(...)                                       ← embeddings + encrypted SQLite
              └─► research(query)                                          ← Research Mode (user approves each request)
```

### Models

| Role | Model | Runtime |
|---|---|---|
| Main brain (text + image + audio) | **Gemma 4 E4B** (`.litertlm`, 3.66 GB, Apache-2.0; E2B 2.59 GB as fallback; final pick by on-phone benchmark — ADR 0002) | LiteRT-LM (GPU) |
| Pose / rep counting / form | MediaPipe Pose Landmarker | MediaPipe Tasks |
| Face shape / grooming | MediaPipe Face Landmarker | MediaPipe Tasks |
| Body/clothing segmentation | MediaPipe Image Segmenter | MediaPipe Tasks |
| Embeddings (memory, RAG) | EmbeddingGemma 300M | LiteRT |
| Barcodes | ML Kit Barcode Scanning (bundled, on-device) | ML Kit |

With 12 GB RAM the main model can stay resident while the app is in the foreground; it is unloaded when backgrounded.
Re-evaluate model choice at build time — newer small multimodal models appear frequently.

### Coach personas
The user talks to **one Coach** in a single chat. The Coach sees all data, handles cross-domain decisions
(sleep → training → food), and routes questions to specialists, whose replies are labelled by name:
Trainer · Nutritionist/Food Critic · Progress Analyst · Groomer · Stylist · Recovery.
Specialists have narrower tools and write access (e.g. only the Trainer can change the workout plan).
Tone slider: *Supportive friend ↔ Brutally honest agency booker.*

---

## 4. Research mode

Purpose: let the coach look up things it doesn't know (new exercises, studies, foods, products).

**Flow**
1. The coach (or user) decides research is needed.
2. The local model writes a **generic** query (no name, weight, photos, health details).
3. A filter strips anything matching profile data; **the user sees the exact outgoing request and taps Approve.**
4. The app queries allowlisted sources only.
5. Results are fetched, text extracted, summarized **locally**, cited, and stored in the local knowledge base (encrypted).

**Allowlisted sources**
| Source | Use |
|---|---|
| PubMed E-utilities (NCBI) | Exercise / nutrition research |
| Wikipedia API | General background |
| Open Food Facts API | Barcode / product nutrition |
| USDA FoodData Central API | Whole-food nutrition |
| Brave Search API (optional, user key) | General web search |
| Claude API (optional, user key, off by default) | Deep research on anonymized queries only |

**Network hardening**
- Single `ResearchClient` is the only code allowed to make network calls.
- Network Security Config: HTTPS only, cleartext disabled, domain allowlist enforced in code.
- No analytics, crash reporting, ads or tracking SDKs. Ever.
- A "Network log" screen shows every request ever sent.
- Global kill switch: Research Mode off = zero network traffic.

---

## 5. Features by phase

### v0.1 — Foundation + Fitness
- Onboarding: goals, body stats (metric), experience, training locations (Gym, Home, custom) with an equipment tick-list each, weekly schedule (location per day), injuries
- Encrypted storage, biometric lock
- Coach chat (Gemma 4), persistent memory
- Workout plan generator (rules engine + LLM explanation)
- Equipment adaptation (ADR 0008): "today I only have X and Y" → the Coach maps it to an inventory, the Kotlin engine swaps exercises (same movement pattern/muscles) and re-derives loads, the owner sees a diff and applies or undoes it; works for a session, a day, the week or the whole plan
- Workout logging: sets, reps, weight, RPE, rest timer
- Progressive overload + deload logic
- Bundled offline exercise database (free-exercise-db, public domain data)
- Steps from phone sensor (no wearable)

### v0.2 — Nutrition
- TDEE + macro targets (Mifflin-St Jeor, adjusted by weekly trend)
- Meal photo → estimated macros + critique (shown as estimate, ±20–40%)
- Barcode scanning (ML Kit) + Open Food Facts lookup (research mode) with local cache
- Fridge photo → meal ideas that fit remaining macros
- Grocery list

### v0.3 — Progress
- Progress photos with **ghost overlay** for consistent pose/distance
- Measurements + weight with moving-average trend
- Side-by-side and timeline views
- Weekly review (runs overnight while charging) that adjusts plans

### v0.4 — Live form check
- Camera workout mode: rep counting + form cues (pose landmarks)
- Voice cues during sets

### v0.5 — Grooming
- Skincare routine builder + tracker
- Face-shape-based haircut / beard suggestions
- Optional skin progress photos

### v0.6 — Style
- Wardrobe digitization (photo → auto-tagged items)
- Outfit of the day, outfit rating
- Color palette analysis (daylight photos)
- Wardrobe gap analysis

### Later
- Research mode expansions, magazine-style weekly "issue", habits & streaks, voice mode everywhere.

### Safety (all phases)
- Calorie floor and max weekly loss rate enforced in code
- Detects repeated meal skipping / very low intake → coach changes tone, suggests professional help
- Photo feedback focuses on trends, posture and style — never body ranking

---

## 6. Look & feel — minimal, with a magazine feel

> **Everything below is the default, not a fixed design.** The app is highly customizable — by the owner in Settings → Customize and by the Coach/specialists through validated, undoable tools. See [ADR 0007](adr/0007-customizable-ui-agent-editable.md).

- Palette: off-white `#F7F5F0`, ink black `#111111`, one accent (deep red `#A3122A`), grays for data. Dark mode = inverted.
- Typography (bundled, OFL-licensed, no runtime font downloads):
  - Headlines: **Bodoni Moda** (high-contrast fashion serif)
  - Body / numbers: **Instrument Sans**
- Live workout screen uses an inverted dark "focus mode" for readability mid-set.
- UI prototype (source of truth for design): https://claude.ai/artifact/RATouP8aDxkhKRNxgivVSJ
- Large headlines, generous whitespace, thin rules between sections, full-bleed photos.
- Coach messages styled like an editor's note.
- Weekly review presented as a "cover" with the best progress photo.

**Navigation**
```
[ Today ]  [ Train ]  [ Fuel ]  [ You ]  (+ Groom / Style tabs when built)
                floating Coach button on every screen
```

---

## 7. Tech stack (well-known libraries only)

| Layer | Library | Maintainer |
|---|---|---|
| Language | Kotlin | JetBrains |
| UI | Jetpack Compose + Material 3 | Google |
| Navigation | Navigation Compose | Google |
| DI | Hilt | Google |
| Async | Kotlin Coroutines + Flow | JetBrains |
| Serialization | kotlinx.serialization | JetBrains |
| Database | Room | Google |
| DB encryption | SQLCipher for Android | Zetetic |
| File/pref encryption | Tink + Android Keystore | Google |
| Preferences | DataStore (Tink-encrypted) | Google |
| Biometrics | androidx.biometric | Google |
| Camera | CameraX | Google |
| On-device LLM | LiteRT-LM (MediaPipe LLM API is maintenance-only) | Google |
| Vision models | MediaPipe Tasks | Google |
| Barcodes | ML Kit (bundled model) | Google |
| Background work | WorkManager | Google |
| Networking (research only) | OkHttp + Retrofit | Square |
| HTML text extraction | jsoup | jsoup (widely used) |
| Image loading | Coil (custom decrypting fetcher) | Coil (widely used, Kotlin-first) |
| Charts | Hand-drawn with Compose Canvas | — (no chart library) |
| Testing | JUnit, Turbine, Compose UI Test | — |

Note: `androidx.security:security-crypto` (EncryptedFile/EncryptedSharedPreferences) is deprecated — use Tink directly.

---

## 8. Security

- **Data at rest:** Room DB encrypted with SQLCipher; key wrapped by Android Keystore (hardware-backed).
- **Photos:** stored in app-private storage, encrypted with Tink (AES-GCM streaming). Never in the gallery.
- **App lock:** biometric/PIN on launch and after timeout.
- `FLAG_SECURE` on sensitive screens (hides from recents, blocks screenshots).
- `android:allowBackup="false"`; own encrypted, password-protected export/import instead.
- Model files verified by SHA-256 before loading.
- **Supply chain:** Gradle version catalog with pinned versions, Gradle dependency verification (checksums), no dynamic versions, minimal dependency count, R8 enabled for release.
- Reference checklist: OWASP MASVS.

---

## 9. Project structure (long-term sketch)

> Superseded for now by [ADR 0006](adr/0006-module-layout.md): start with a small module set and add modules per phase. The current layout lives in the README.

```
M3/
  app/                    # Android app shell, navigation, DI
  core/
    data/                 # Room, SQLCipher, repositories
    security/             # Keystore, Tink, biometric lock
    ui/                   # Theme, typography, shared components
  ai/
    llm/                  # LiteRT-LM wrapper, prompt templates, tool calling
    vision/               # MediaPipe pose/face/segmentation
    memory/               # Embeddings + retrieval
  feature/
    today/  train/  fuel/  progress/  groom/  style/  coach/
  research/               # The ONLY module with network access
```

---

## 10. Dev setup needed

- Android Studio (latest stable) — includes JDK and Android SDK
- Poco F5 with Developer Options + USB debugging enabled
- Model files (Gemma 4 E4B `.litertlm` etc.) downloaded once and pushed to the phone

---

## 11. Open questions

- ~~Imperial or metric units?~~ → metric only (ADR 0008)
- ~~Gym, home, or both?~~ → both, plus custom locations, each with its own equipment, adaptable by the Coach (ADR 0008)
- Should research results be auto-approved for trusted sources (PubMed, USDA), or always ask?
