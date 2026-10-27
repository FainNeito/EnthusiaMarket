# Direct stall snapshot follow-up

## Requirement and proof

When schematic capture is enabled, direct personal and guild stall purchases must capture the unowned region before payment or ownership changes. Capture failure must reject the purchase without charging, release the IP reservation and notify operators. When disabled, purchase behavior remains unchanged. Existing snapshots must not be overwritten.

The deployed TEST Market reported a missing stall21 snapshot during eviction. Its direct buyout source has no capture call. This supports a missing capture path; it does not establish why this specific snapshot was absent. The existing snapshot adapter is idempotent.

## Scope and acceptance

Test both owner paths and failure before payment. No server data, artwork, deployment, or geometry cleanup is changed here. Restore ordering and recovery for stalls with already-missing snapshots are separate unresolved work.

## Verification and limitations

The candidate was rebased onto BadgersMC main `57704f1acfaf8aacfdd65cb4ae74c0f36b55712d`. On JDK 25, the final candidate passed the full Gradle test task with 1,024 tests, zero failures and 14 skips, using the locally built unshaded Guilds API dependency. The focused buyout tests cover personal and guild capture failures before charging, capture before payment, the disabled setting, and rejecting an unavailable provider before payment. Detekt passed separately on JDK 22; its JDK 25 run failed on its unsupported Java version. An earlier compilation-only failure was caused by a missing Guilds dependency and does not establish a behavioral red.

Nexus core component registry indexes component interfaces, but actual server startup injection of the schematic adapter is not verified here. The candidate rejects a Disabled provider while snapshots are configured, before charging; actual server injection still needs validation. Verify runtime wiring and enabled-provider behavior before deployment. Existing missing snapshots, eviction restore ordering, asynchronous restore completion, and cleanup of previously altered geometry remain separate issues. Project-local SPEAR helpers were not present; this file records the requirement and evidence manually.
