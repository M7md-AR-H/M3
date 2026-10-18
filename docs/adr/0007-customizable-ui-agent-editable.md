# 0007 — Highly customizable UI that the Coach and specialists can change

- **Date:** 2026-10-16
- **Status:** Accepted

## Context
The owner wants M³ to be highly customizable, and wants the Coach and the other specialists to be able to customize it themselves (e.g. the Nutritionist adds a protein card to Today; the Coach switches to dark mode at night or reorders the home screen around this week's focus). Letting a model rewrite the UI freely would be unsafe and fragile (ADR 0003: model output is untrusted).

## Decision
1. **The UI is driven by a `UiConfig` document, not hard-coded.** Everything customizable is data:
   - **Theme tokens:** colors (ground, ink, accent, muted…), light/dark/auto, font pairing (from a bundled set), type scale, density, corner radius, motion on/off.
   - **Layout:** tab set and order; Today screen as an ordered list of **cards** from a registry (check-in, workout, fuel, coach note, habits, weight trend, photo reminder, …), each with show/hide, order and per-card options.
   - **Content settings:** units, coach tone, language style, which metrics show, notification times, habit list.
2. **Screens render from the config.** The design system reads tokens from `UiConfig`; Today renders its card list from the registry. New customizable surfaces register themselves rather than hard-coding layout.
3. **Agents change it only through tools.** e.g. `ui.set_theme_token`, `ui.reorder_cards`, `ui.toggle_card`, `ui.set_card_option`, `ui.add_habit`. Each is schema-validated, and each specialist may only touch its own scope (the Nutritionist can add/remove nutrition cards, not change the theme).
4. **Guardrails enforced in code**, not by the model: minimum text contrast (WCAG 4.5:1), minimum font size, 44 dp touch targets, at least one way back to Settings and Coach, no hiding safety messages.
5. **The owner stays in control:**
   - Every change is versioned (`UiConfig` history in the encrypted DB) with a one-tap **Undo** and full history to roll back.
   - Agent changes show a small "Coach changed X — Undo" notice. A setting picks: *apply automatically* / *ask me first* (default: ask first for theme and layout, auto for card options).
   - Owner settings can be **locked** so no agent can change them.
   - "Reset to default" always exists.
6. The owner can make every same change by hand in **Settings → Customize** (the agents use the same tools the UI uses).

## Consequences
- Phase 0's design system must be token-based from day one (no hard-coded colors/fonts in screens).
- More upfront structure (card registry, config schema, migrations for config versions).
- In exchange, customization is safe, undoable and testable, and agents get a clear, limited set of levers.
