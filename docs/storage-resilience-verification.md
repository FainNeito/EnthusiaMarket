# Storage resilience maintenance

Base: canonical main `14351db4dc416138341a11d0e4e27602f207221b`.

## Spec

REQ-345: WHEN a player joins THE SYSTEM SHALL read missed-sale history outside the server thread and send the summary only to the same still-connected player session.

REQ-346: WHEN a missed-sale summary is delivered THE SYSTEM SHALL acknowledge only the rows included in that summary without acknowledging subsequently recorded sales.

REQ-347: IF history storage fails or its bounded notification queue is full THE SYSTEM SHALL leave unread rows recoverable and return from the join handler without a synchronous storage fallback.

No permission, trade, guild-XP event, rent, history retention, or community feature policy changes. Notification delivery is at least once: an acknowledgement failure can repeat a delivered summary, which is preferable to suppressing unread history. Reads/acknowledgements use one daemon worker and a bounded queue; Bukkit player lookup, language rendering and message delivery stay on the server thread. Disable stops admission and cancels pending work before database shutdown.

## Prove / engine / architecture / refine

The join regression executed against the original implementation and failed with `IllegalStateException: Storage unavailable` (one executed case, one failure). Initial missing companion and ambiguous test-overload setup failures were corrected before this behavioral red result and are not counted as regression proof.

Final local Java 25/Paper 26.2 test/shadowJar: 790 cases, zero failures/errors, seven existing external-resource skips. Java-21 Detekt passed. Eighteen notification/repository cases cover separate IO/server queues, consistent SQL watermarks with identical timestamps, storage and scheduling rejection, read/acknowledgement failures, empty results, config changes, shutdown, repeated joins, rejoin during read/acknowledgement, online event priority and unread offline/disabled sales. No local EARS/state helper exists in this repository; this requirement/task/evidence record is maintained manually. JUnit dependency alignment matches the independently prepared fixes in PRs #197/#199 so current Gradle can discover tests.

Local unmerged review artifact: `build/libs/EnthusiaMarket-1.0.0-storage-review.1.jar`. SHA-256 and exact-head hosted checks are recorded at publication. This is not a production release.

## Remaining checklist boundaries

This slice addresses join and live-sale notification SQL, not the completed-trade history writer, sign/auction database paths, or production/client acceptance. Completed-trade durable recording requires a separate journal design that composes with the ongoing guild-XP settlement journal; do not replace it with an unbounded in-memory asynchronous queue.
