# 0010 — Coach chat: on-device turns, one validated proposal per reply, apply/undo

- **Date:** 2026-10-18
- **Status:** Accepted (implements the chat parts of ADR 0003, ADR 0007 and ADR 0008)

## Context
The Coach tab is a chat with Gemma 4 E4B, running on the phone (ADR 0002). The owner wants to say things like "today I only have dumbbells" or "my knee hurts on lunges" and have the plan adapt (ADR 0008), and the Coach may also change the app's look (ADR 0007).

A 4B model:
- follows simple formats most of the time, but not always;
- invents numbers easily;
- has a 4096-token window.

## Decision

### Turns
- **Stateless turns.** Each turn sends two things:
  - a system instruction holding the rules, the tools and the app's current facts (profile, places and equipment, free days, this week's plan with exercise ids, avoided exercises, theme, Today cards, owner locks);
  - the recent conversation (about 3,600 characters) plus the new message.
- The model doesn't keep a conversation between turns. This costs a little prefill time each turn, but the facts are always current and the prompt always fits.
- The prompt text is in `:coach` (`CoachPrompt`). It is changed only with an eval run (`app/src/androidTest/.../CoachPromptEval.kt`, emulator-only, test data only).

### Tools
- **One proposal per reply.** The model may end its reply with `<tool>{…}</tool>`. Kotlin parses that against fixed schemas (`CoachTools`, kotlinx.serialization) and checks every value against real data:
  - known equipment kinds, including everyday names and synonyms;
  - exercises in the catalog;
  - the owner's own places;
  - dates from today to this Sunday.
- **The tools:**
  - `day_equipment`: equipment for one day this week.
  - `avoid_exercise` and `allow_exercise`.
  - `set_day`: change where the owner is free on a day (ADR 0009).
  - `theme`.
  - `card`: show, hide, or move to the top.
- Changing a place's saved equipment stays an owner action in You → Training setup for now.

### Proposals
- **Ask first.** A valid tool call becomes a proposal card. It shows what would change in words and the plan changes it causes (`PlanDiff`). The owner taps Apply or Not now.
- On Apply, the tool JSON is validated again against the current state, and the owner's locks are checked again:
  - `TRAINING_PLAN` covers day equipment and avoided exercises.
  - `AVAILABILITY` covers free days.
  - The UI locks apply to the look.
- UI changes go through `UiConfigStore.apply` with the Coach as actor, so ADR 0007's guardrails apply.
- **Undo.** Each applied proposal stores how to reverse itself: the previous day equipment, the previous exclusion, the previous free-day place, or the inverse UI change. Undo is the owner's own action.
- **Overrides on top of the plan.** Day equipment and avoided exercises are stored in `day_override` and `excluded_exercise`. The planner applies them (`PlanOverrides`):
  - Day overrides never move training days, and only change their own day.
  - Day overrides older than this week are deleted at startup.
- Invalid or locked tool calls are shown as "couldn't use this suggestion" or "locked by you", never applied.

### Storage and model lifecycle
- **Chat history** is stored in the encrypted DB (`chat_message`, DB v2 via Room auto-migration) and is never logged.
- **Model lifecycle:**
  - The model loads on the first message: GPU with multi-token prediction, falling back to CPU without it. Emulators skip the GPU.
  - One answer runs at a time.
  - When the app goes to the background, the running answer is stopped and the model is unloaded. An answer that was still waiting doesn't load the model afterwards.
- **Model cache.** The GPU's compiled cache lives in `no_backup/llm-cache`, never in `cacheDir`, because Android can purge `cacheDir` mid-write. It is only used with at least 1 GB free. The CPU fallback uses no weight cache: that cache grows to about the model's size, and a failed write aborts the native runtime.
- **Stopping safely.** Stop calls `cancelProcess()` and waits for the runtime to confirm before freeing the conversation. Freeing it while the runtime was still reading the prompt crashed the app on the emulator.
- **Whole turns.** The owner's message and the Coach's reply are saved together. A failed or stopped answer saves nothing and gives the text back to the input box.
- **One action at a time.** Apply, Not now and Undo are serialised. Only the most recent applied change can be undone.
- **Day window.** Day changes cover the next 7 days, so "tomorrow" works on a Sunday.

## Consequences
- Every turn re-reads the facts, so the Coach never works from a stale plan.
- If the model skips the tool format, the owner still gets the advice text, just with no Apply button.
- Specialist labels (Trainer, Nutritionist…) aren't shown yet; all replies are "Coach" until there is more than one domain.
