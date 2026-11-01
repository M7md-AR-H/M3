# ADR 0012: Reminders

**Status:** accepted (2026-11-01)

## Context
The owner asked for meaningful notifications: to work out, to log things, to eat, to drink water. M³ runs on their daily phone, so a noisy app gets its notifications turned off. Three things shape the design:

- A timer can't know what the owner already did. A reminder for something done is noise.
- The shade can be read on an unlocked phone without M³'s own unlock, so text there sidesteps the app lock. Notification listeners can read it too, and CLAUDE.md says personal data never leaves the device.
- Xiaomi phones stop background apps hard, and a force-stop clears alarms.

## Decision
**One alarm, decided when due.** M³ holds one alarm for the next due reminder (and one for the end of a rest). When it fires, the next alarm is set first, so a killed process can't end the chain. Then Kotlin reads the logs and decides whether to send anything. A failed send retries after 5 minutes. `ReminderRules` and `Reminders` are pure and unit tested; the model is never involved (ADR 0003).

| Reminder | Default | Silent when |
|---|---|---|
| Morning | 07:30 | checked in today and weighed in within 3 days (the You tab says a few mornings a week) |
| Breakfast, Lunch, Dinner | 09:00, 13:00, 19:00 | food logged in the last 2 hours |
| Workout | 17:30 | rest day, already trained, or a workout is open |
| Water | from 10:30, every 3 h, not after 20:30 | 2.5 L reached or the habit ticked |
| Log your day | 21:00 | nothing is open |
| Lights out | 22:30, off by default | habit ticked |
| Weekly review | Mondays 09:30 | last week has no logs |
| Rest over | when a rest ends | M³ is on screen |

**Private by default.** Text has no numbers or plan details unless the owner turns on "Show my numbers". The lock screen only ever shows "M³". Notifications are local-only, with no system-suggested actions.

**Water became a real log.** One boolean habit can't say when to stop, so `water_day` holds millilitres. The habit ticks itself at 2.5 L. "+250 ml" works from the notification.

**Exact alarms with `USE_EXACT_ALARM`.** "Workout at 17:30" should arrive at 17:30. Inexact alarms can be up to an hour late on Android 12+. The Play policy limiting this permission doesn't apply: M³ isn't on any store. It's a platform API, so WorkManager (a new dependency, 15-minute minimum and Doze delays) isn't needed. If the system ever refuses exact alarms, the code falls back to inexact ones.

**Alarms are set again** after boot, an app update, a clock or time-zone change, any settings change, and every start of M³. A late alarm more than 45 minutes after its time is dropped.

**The Coach can change reminders** (ADR 0007). Plain requests ("turn off water reminders", "remind me to train at 18:00") are parsed in Kotlin and go through the normal proposal card with Apply, Undo and a new owner lock, `REMINDERS`. The model is skipped: this needs no prompt space or eval, and it can't state the wrong time.

## Consequences
- Three new permissions: `POST_NOTIFICATIONS` (asked in context), `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. No new dependency, no network.
- The background path opens the encrypted database without M³'s unlock. That's the existing design (the key isn't tied to the app lock), and it's why the text is private by default.
- On Xiaomi, reminders depend on Autostart and battery settings only the owner can change (OWNER_TASKS #7).
- Steps can't be part of a reminder: Android gives background apps no sensor events.
