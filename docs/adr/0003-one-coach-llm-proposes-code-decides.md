# 0003 — One Coach chat; the model proposes, code decides

- **Date:** 2026-10-16
- **Status:** Accepted

## Context
The product has several "specialists" (Trainer, Nutritionist, Analyst, later Groomer and Stylist). Running a separate model per specialist is impossible within phone memory, and separate chats confused the owner during prototyping ("what's the difference between the coach and the trainer?"). Small on-device models also make factual and arithmetic mistakes.

## Decision
1. **One chat with one Coach.** The same LLM plays every specialist via a different system prompt, tool set and data scope. The Coach routes each message to a specialist; replies are labelled with the specialist's name.
2. **Specialists have scoped write access.** E.g. only the Trainer can modify the workout plan; only the Nutritionist can modify nutrition targets.
3. **The model proposes, code decides.** The model acts only through tool calls. Each tool call is JSON, parsed with kotlinx.serialization against a fixed schema, range/enum-validated, then executed by deterministic Kotlin. Invalid calls are rejected and the model is told why.
4. **Numbers come from code.** TDEE, macros, progression, deloads and safety floors are Kotlin functions. The model explains and motivates; it does not invent numbers.

## Consequences
- More upfront work defining tool schemas.
- Behaviour is testable with plain unit tests, and a model mistake cannot corrupt data or bypass safety limits.
