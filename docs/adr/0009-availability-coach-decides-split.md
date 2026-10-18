# 0009 — The owner gives availability; the Coach decides the training/rest split

- **Date:** 2026-10-18
- **Status:** Accepted (amends the "weekly schedule" part of ADR 0008)

## Context
Phase 1b asked the owner to assign a location or "Rest" to each weekday. After trying it, the owner said: *"I want the coach to decide how the workout/rest split looks like based on your schedule."* The owner knows when they're free; choosing how many sessions to do, and which days, is coaching.

## Decision
- **Availability, not a schedule.** For each weekday the owner says where they could train that day (a location) or "Not free". This uses the existing `schedule_day` table, so no migration is needed. A row's `locationId` now means "available at", and null means not free. In code the field is `TrainingSetup.availability`.
- **The split is computed in deterministic Kotlin** (`:core:training`, ADR 0003: the model proposes, code decides):
  - **Sessions per week** come from experience and goal:
    - New: 3.
    - Some experience: 4 for build muscle or get stronger, otherwise 3.
    - Experienced: 5 for build muscle, otherwise 4.
    - Never more than the days available.
  - **Which days:** of all ways to pick that many available days, the one with the fewest back-to-back training days (counting Sunday→Monday), then the largest smallest gap, then the most equipment, then the earliest days.
  - **Session types by count:**
    - 1–3 sessions: full body A/B/C.
    - 4 sessions: upper/lower ×2.
    - 5 sessions: upper/lower/push/pull/legs.
  - **Exercises** fill each session's slots (movement pattern or target muscle) from the bundled catalog. Only exercises that the day's location can support are used; bodyweight always counts, and an adjustable bench also works as a flat bench.
  - **How exercises are chosen:**
    - Nothing above the owner's level when an easier option exists.
    - Not the easiest options for experienced lifters.
    - Heavy barbell lifts only in main slots, and preferred there when the goal is strength.
    - Explosive work (swings) never as a main lift.
    - Loadable options preferred for muscle and strength goals.
    - No repeats within a session, and fewer within the week.
  - **Session size:** beginners get 5 exercises per session, others 6. Fat-loss and general-fitness sessions end with a conditioning finisher.
  - **Sets, reps and rest** come from the goal, the slot's role (main lift, accessory, isolation) and the exercise itself:
    - Strength ranges (3–5) are only for heavy lifts. Bodyweight and single-side work stays in moderate ranges.
    - Beginners do 8–12 reps with fewer sets.
    - High-intensity conditioning is done as 20–30 s rounds, steady cardio as one block.
    - Slow negatives are always 3–5 reps.
  - **Safety in the catalog:** barbell work needs plates, and barbell squats, bench press and overhead press need a rack (or a Smith machine).
  - Weight loads arrive with logging and progressive overload (Phase 1c-3).
  - If a slot can't be filled at that location, the plan says so instead of inventing something.
- **The plan is a pure function** of profile + setup + catalog, so it's stable and recomputed whenever they change. When the Coach gets tools (Phase 1c-2), its changes become stored, versioned, undoable overrides on top of this base plan (ADR 0008). The Coach's chat explains the plan from the engine's facts; it never writes sets, reps or loads itself.
- **Injuries are free text** and aren't used by the engine yet. The Coach will read them and propose swaps through validated tools.

## Consequences
- The onboarding "Your week" step becomes "When can you train?", and its summary shows the Coach's split.
- Train shows the week; Today shows today's session or a rest day.
- The owner's existing 1b data still works: the days they picked become available days.
