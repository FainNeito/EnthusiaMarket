# Login rent warnings

Integration refinement: warning listener construction now follows manual StallRepository registration. The source-order contract and ten policy/worker cases pass after moving the eager request. Earlier ordering could fail startup before any join event. This is source-derived failure evidence; no historical red build is claimed.

## Spec and scope

REQ-367: WHEN an owner or authorized rent-renewing member joins THE SYSTEM SHALL show relevant renewal/grace deadlines within a configurable warning window (default 24 hours) and explain manual purchase-sign renewal.

REQ-368: WHEN renewal is relevant and its responsible payer lacks the renewal amount THE SYSTEM SHALL warn without charging, changing deadlines or exposing another player's balance. Guild renewals check the guild bank; personal renewals check the joining payer.

REQ-369: WHEN warnings are loaded THE SYSTEM SHALL isolate repository reads from the server thread, perform guild/economy checks and player delivery on the server thread, coalesce pending requests, reject stale sessions and bound queue/message volume.

Base: current main 14351db plus dependency #201 a96d27c. Owner approved warning/trail defaults in this turn. No automatic rent charge, economy policy change, stall reset or production operation. Local EARS/state helpers absent; manual SPEAR record. New behavior has no historical red/green claim.

## Tasks

- [x] Inspect actual prepaid rent, grace and actor/guild payment semantics.
- [x] Implement warnings and bounded asynchronous discovery with explicit lifecycle.
- [x] Prove deadlines, eligibility, affordability, failure and queue/thread boundaries.
- [x] Full supported local build/architecture/Detekt and inspect exact-head PR findings.

Ten new policy/worker cases pass: inclusive 24-hour window, grace/legacy deadlines, disabled/inactive paths, authorized personal members, guild management/payer, unknown balance/free rent, deferred storage/server delivery, coalescing, retry, rejection without inline SQL and shutdown suppression. No funds or deadlines mutate. Player delivery checks exact current session and a five-second request-age ceiling; reconnect cooldown and bounded output are explicit adapter guards. Canonical Java-25/Paper-26.2 clean test/shadowJar/jacoco build passes 800 cases, zero failures/errors, seven external-resource skips. Detekt passes after extracting session/freshness conditions. Refined focused cases and shadowJar pass after this behavior-preserving refinement. Real client acceptance is separate.

Final delivery: reconciled Market #213 passed the clean 890-case suite with zero failures/errors and eight external skips, shadowJar/JaCoCo, architecture and Java-21 Detekt. Broader pending-stack rehearsal passed 972 cases, zero failures/errors, 14 skips. Current review threads are resolved/absent; Market hosted workflow approval is pending, while Guilds #216 hosted checks pass. See approved-features-delivery.md for tested commits and final artifact evidence. These task checks establish source implementation and local proof, not hosted build approval or production/client acceptance.
