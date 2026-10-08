# Completed-trade history durability

SPEAR base: current canonical main `14351db` plus pending correctness #197 (`7d7e54a7`) and notification #201 (`a96d27c`). Both dependency heads were merged locally before final validation; no production or canonical merge was performed.

## Spec

REQ-348: WHEN a completed trade reaches the history listener THE SYSTEM SHALL persist an immutable local recovery record before returning and perform SQL delivery on a dedicated bounded worker without moving Bukkit or economy operations off the server thread.

REQ-349: WHEN a pending history record is retried after storage failure or restart THE SYSTEM SHALL insert its SQL history at most once using a durable recording receipt in the same SQL transaction.

REQ-350: IF history delivery, acknowledgement or shutdown is interrupted THE SYSTEM SHALL retain pending recovery records and never replay inventory, currency or guild-XP events.

Local file persistence can fail or stall with an unavailable disk; no hard wall-clock promise is made for filesystem I/O. This closes the synchronous SQL watchdog path, not the crash window between the completed trade and its event or the game's inventory persistence. A local append failure is reported without undoing an already completed trade. History remains an audit projection, not a money/item recovery ledger. Disk records remain on outage/shutdown; SQL receipts deliberately survive normal history retention so old replay cannot resurrect a pruned sale. Corrupt records are retained and reported, never guessed or deleted.

## Tasks and evidence

The original completed-trade listener regression compiled and executed, then failed its assertion that SQL must not run on the caller thread. The replacement test checks a real storage component while its SQL dependency is unavailable. Notification recording tests still exercise real Bukkit listener priority.

Infrastructure owns file encoding/checksum, atomic rename, worker scheduling and SQL delivery; the domain exposes only the replay-safe repository operation. A single periodic task processes at most 100 records per five-second pass and keeps no per-sale executor queue. SQL failures retain pending files, failed file acknowledgements replay the same receipt, and malformed records are quarantined without starving other sales. Temporary incomplete files are never accepted as completed records. Receipt rows deliberately have no history foreign key and are not pruned with history. Monitor pending-file growth during long outages; no silent drop, rent/tax/retention change or automatic corrupt-record repair is introduced.

File contents are forced before atomic rename. This is tested process-restart recovery, not a guarantee against filesystem loss or sudden host power failure; directory metadata durability depends on the host filesystem. Atomic moves must be supported by the plugin data filesystem. Receipts and the file journal are private infrastructure, not new player reporting/export features.

Validation evidence is recorded below before publication. No project-local EARS/state helpers exist. Community reporting/export/filter requests and guild-XP policies remain separate. The immutable completed event retains its original thread and is never replayed, so the paired XP listener remains the separate owner of XP settlement.

Final clean Java 25/Paper 26.2 `test shadowJar`: 848 cases, zero failures/errors, eleven external-resource skips (ten container-backed database cases and one remote authentication case). The equivalent isolated native MariaDB 11.8.3 runs executed all three new history cases and seven moderation upgrade cases without failures or skips. SQLite tests prove receipt/history rollback and replay after pruning. Shutdown proof interrupts blocked delivery, retains the pending record and refuses new appends. Java-21 Detekt passed; architecture checks are included in the full suite. All local artifacts are unmerged review builds, not production releases.

Publication depends on #197 and #201; merge those reviewed sources first. V032 reserves a distinct migration number from pending guild auction V030 and XP V031. Preserve local pending/corrupt records during upgrades; do not delete receipt rows as part of normal history pruning. A receipt acknowledges only this history projection, never an economy or XP operation. Snapshot, performance and real-client acceptance gates remain open.
