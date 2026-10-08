# Login rent warnings

## Spec and scope

REQ-367: WHEN an owner or authorized rent-renewing member joins THE SYSTEM SHALL show relevant renewal/grace deadlines within a configurable warning window (default 24 hours) and explain manual purchase-sign renewal.

REQ-368: WHEN renewal is relevant and its responsible payer lacks the renewal amount THE SYSTEM SHALL warn without charging, changing deadlines or exposing another player's balance. Guild renewals check the guild bank; personal renewals check the joining payer.

REQ-369: WHEN warnings are loaded THE SYSTEM SHALL isolate repository reads from the server thread, perform guild/economy checks and player delivery on the server thread, coalesce pending requests, reject stale sessions and bound queue/message volume.

Base: current main 14351db plus dependency #201 a96d27c. Owner approved warning/trail defaults in this turn. No automatic rent charge, economy policy change, stall reset or production operation. Local EARS/state helpers absent; manual SPEAR record. New behavior has no historical red/green claim.

## Tasks

- [x] Inspect actual prepaid rent, grace and actor/guild payment semantics.
- [x] Implement warnings and bounded asynchronous discovery with explicit lifecycle.
- [x] Prove deadlines, eligibility, affordability, failure and queue/thread boundaries.
- [ ] Full supported build/architecture/Detekt and exact-head PR findings.

Ten new policy/worker cases pass: inclusive 24-hour window, grace/legacy deadlines, disabled/inactive paths, authorized personal members, guild management/payer, unknown balance/free rent, deferred storage/server delivery, coalescing, retry, rejection without inline SQL and shutdown suppression. No funds or deadlines mutate. Player delivery checks exact current session and a five-second request-age ceiling; reconnect cooldown and bounded output are explicit adapter guards. Canonical Java-25/Paper-26.2 clean test/shadowJar/jacoco build passes 800 cases, zero failures/errors, seven external-resource skips. Detekt passes after extracting session/freshness conditions. Refined focused cases and shadowJar pass after this behavior-preserving refinement. Real client acceptance is separate.
