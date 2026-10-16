# 0005 — Research mode is the only network path

- **Date:** 2026-10-16
- **Status:** Accepted

## Context
The owner wants a research mode (look up exercises, studies, foods) but everything else must stay on the phone. Android's INTERNET permission is app-wide, so the boundary has to be enforced in code and build config.

## Decision
- Only the `:research` module may depend on networking libraries (OkHttp, Retrofit, jsoup). A build check fails if any other module declares them.
- The INTERNET permission is **not** added until Phase 1's research work starts; Phase 0 ships with no network permission at all.
- `ResearchClient` enforces an allowlist of HTTPS hosts (PubMed/NCBI, Wikipedia, Open Food Facts, USDA FoodData Central; optional Brave Search and Claude API with the owner's own keys). Network Security Config disables cleartext.
- Every outgoing request is generated as a generic query, passed through a filter that strips anything matching profile data, and **shown to the user verbatim for approval** before sending.
- A local "Network log" lists every request ever sent. A global switch turns research off (zero traffic).
- Results are summarised on-device and stored locally with citations.

## Consequences
- Research is slower (one tap per request) — intended.
- Model downloads happen outside the app (adb/USB/file picker), so the app never needs to contact Hugging Face.
