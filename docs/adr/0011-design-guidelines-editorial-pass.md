# ADR 0011: Owner's design guidelines, applied to the editorial system

**Status:** accepted (2026-10-30)

## Context
The owner supplied UI guidelines ("DESIGN_GUIDELINES", v1.0, 2026-10-30) and asked for them to be used to improve the app. They offer two visual styles (Glassy Dashboard, Gradient Hero) built on blur, rounded cards and pills. Their own first rule says that where they conflict with an existing design system, the project's system wins and they fill the gaps.

CLAUDE.md fixes M³'s system: off-white ground, ink, one accent, Bodoni Moda + Instrument Sans, square corners, thin rules. The prototype is the UI source of truth. The built app had drifted from it: nearly everything was a bordered box, Bodoni never set its optical size, and numbers weren't tabular.

## Decision
Keep the editorial look. Neither style in section 2 of the guidelines is used. Everything else is applied.

| Guideline | In M³ |
|---|---|
| 2 + 1 colours, accent used sparingly | Already the system. Accent only on the primary action, kickers and marks |
| Max 2 families, tight headlines, balanced lines | `LineBreak.Heading`, negative tracking on display and headline |
| Tabular numbers | `figure` and `numeric` styles; kickers and labels use `tnum` |
| Spacing scale | 4 / 8 / 12 / 16 / 24 / 32, plus the prototype's 20 dp page margin as `gutter` |
| Depth, no borders on everything, no cards in cards | Depth comes from rules and one inverted ink panel. One boxed item per page |
| Skeletons, no spinners or "Loading…" | `Skeleton`, shown only after 200 ms so fast loads don't flicker |
| Undo instead of "Are you sure?" | Coach Clear hides the chat, then deletes after the Undo window |
| Hit targets ≥ 44 | Kept; moved to `M3SquareButton` and `TextAction` |
| Empty states are designed | Fuel lists what's coming; unbuilt Today cards are named once, not shown as boxes |
| Motion, reduced motion | Existing `Motion` tokens; Compose scales them with the system setting |

Not applicable on native Android: `⌘K`, hover, URL state, web focus rings, BlurHash until photos exist. Squircles conflict with square corners, though the corner token still lets the owner round everything.

Customization (ADR 0007) still holds: every new component reads tokens, and no `UiConfig` field changed, so saved configs load as they are.

## Consequences
- Bodoni needs one `FontFamily` per optical size, because Compose doesn't set `opsz` itself. Four are defined.
- `M3Colors.accentOnInk` is new: the opposite palette's accent, for text on the ink panel.
- If the owner later prefers a glass look, it's a new theme direction, not a tweak: blur also needs care with `FLAG_SECURE` screenshots and contrast.
