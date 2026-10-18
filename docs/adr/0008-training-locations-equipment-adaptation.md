# 0008 — Training locations, equipment inventories and Coach-driven plan adaptation

- **Date:** 2026-10-18
- **Status:** Accepted

## Context
The owner trains in more than one place and wants the plan to fit whatever is available that day. In the owner's words: *"if i ask the coach … that i only have x and y tools, i want him to revise my schedule or maybe my day to just using those tools."* The owner is "all for customization" (ADR 0007), so this has to be first-class, not a setting buried in onboarding.

Two constraints from earlier decisions apply:
- **The model proposes, code decides** (ADR 0003). A 4B on-device model can't be trusted to invent safe, balanced substitutions or loads.
- **Every change is versioned and undoable, and owner locks are respected**, the same as UI changes (ADR 0007).

## Decision

### Data model (stored in the encrypted DB, ADR 0004)
- **Equipment catalog:** a fixed enum of equipment kinds, about 40 of them:
  - Free weights: barbell, dumbbells, kettlebell, EZ bar, weight plates.
  - Stations: bench (flat/adjustable), squat rack, pull-up bar, dip bars, cable station, Smith machine, leg press, rowing machine, treadmill, bike.
  - Small gear: resistance bands, TRX/suspension, jump rope, foam roller, mat, plyo box.
  - Bodyweight-only.
  - Plus **custom items** with a free-text name the owner adds, which the engine treats as "unknown" unless mapped to a kind.
  - Items can carry **details** where they matter for loading: dumbbells/kettlebells as a list of available weights in kg (e.g. 2–24 kg in 2 kg steps, or fixed pairs), the band count, and whether the bench is adjustable.
- **Training locations:** named profiles, each with an inventory. Defaults are **Gym** and **Home**, and the owner can add more ("Hotel", "Park", "Office"). Onboarding asks which locations exist and shows a tick-list per location.
- **Weekly schedule:** each training day points to a location (e.g. Mon/Thu Gym, Sat Home). This can be changed any time.
- **Day override:** "today I only have X and Y" creates a temporary inventory for one date or one session. It never edits the saved location.

### Exercise database
Every exercise in the bundled database is tagged with:
- The **equipment it needs**, as alternatives: for example, "dumbbells AND bench" or "barbell AND rack".
- Its **movement pattern**: squat, hinge, horizontal/vertical push, horizontal/vertical pull, lunge, carry, core, conditioning.
- Its **primary and secondary muscles**, and a **difficulty**.

### Adaptation engine (deterministic Kotlin, in `:core:training` or similar)
- **Input:** a plan scope (one session, one day, the rest of the week, or the whole plan) plus an inventory.
- **For each exercise the inventory can't support,** the engine picks the best substitute:
  - **Same movement pattern**, then the best primary-muscle overlap, then similar difficulty, then whatever the owner did recently (for variety).
- **Loading is re-derived in code.** If only light dumbbells are available, the engine adjusts the scheme: more reps, slower tempo, unilateral variants or added sets, so effort stays similar. Progressive-overload history keeps a separate track per exercise.
- **Output:** a **proposed diff** (old exercise → new exercise, old scheme → new scheme). The diff is shown to the owner and applied as a versioned, undoable change.
- **Invariants checked in code before applying:**
  - Every exercise is supported by the inventory.
  - Weekly volume per muscle group stays within a set band of the original.
  - Movement-pattern balance is kept.
  - Safety rules hold (e.g. no heavy axial loading without a rack).

### Coach integration
- The Coach turns natural language into **schema-validated tool calls**:
  - `training.setDayInventory(date | sessionId, items[])`
  - `training.adaptPlan(scope, inventorySource)`
  - `training.setLocationInventory(locationId, items[])`
  - `training.assignLocation(day, locationId)`
- Equipment names are mapped to catalog enums. If something is ambiguous ("some weights"), the Coach asks a clarifying question instead of guessing.
- The Trainer specialist owns these tools (ADR 0003 scoped write access). The **owner can lock** "Agents may change my plan" and "Agents may change my locations", just like the UI locks.
- The Coach's reply explains the swap using the engine's diff. The model never writes the sets, reps or loads itself.
- **Default is "ask first":** the Coach shows the proposed diff and the owner taps Apply. The owner can switch this to "apply and tell me" for day overrides.

### Units
**Metric only:** kilograms, centimetres/metres, kilometres, kcal, 24-hour time, and day-month dates. There is no imperial option, at the owner's request ("not the American stuff"). Units live in one formatting layer, so nothing else hard-codes them.

## Consequences
- Every exercise in the bundled database needs equipment and pattern tags. Where the source data lacks them, they're added at import time, which is a one-off curation task in Phase 1c.
- Some plans can't be fully adapted, for example heavy squats with only bands. The engine then says so and offers the closest alternative instead of silently weakening the plan.
- Separate overload tracks per variant mean a "home" bench-press substitute doesn't reset the gym bench-press progression.
- The engine is pure Kotlin and is unit-tested for the invariants above, independently of the model.
