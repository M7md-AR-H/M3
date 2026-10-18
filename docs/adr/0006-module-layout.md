# 0006 — Start with a small module layout

- **Date:** 2026-10-16
- **Status:** Accepted

## Context
The spec sketched ~15 Gradle modules. For a single-developer, single-device app, many modules slow builds and add boilerplate before they pay off. But two boundaries matter now: design-system reuse and network isolation (ADR 0005).

## Decision
Phase 0 starts with:
- `:app` — application, navigation, feature screens (one package per feature: `today`, `train`, `fuel`, `you`, `coach`)
- `:core:designsystem` — colors, typography, bundled fonts, shared components
- `:core:security` — Keystore/Tink key management, biometric lock (added in Phase 1)
- `:core:data` — Room + SQLCipher, repositories (added in Phase 1)
- `:ai` — LLM runtime wrapper, prompts, tool schemas, vision (added in Phase 1)
- `:research` — the only networked module (added when research mode starts)

Modules are created when their phase starts, not before. A feature moves into its own `:feature:*` module only if `:app` build times become a problem.

## Consequences
Fewer files and faster iteration now; a later split is a mechanical move because features are already separated by package.
