# Completed-trade history durability

SPEAR base: current canonical main `14351db` plus pending #197 (`7d7e54a7`), #198 (`864ab6b`), #200 (`37a6390`) and #201 (`a96d27c`). Dependency branches were merged into this review source; no production or canonical merge was performed.

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

Before the guild-stack merge, clean Java 25/Paper 26.2 `test shadowJar`: 848 cases, zero failures/errors, eleven external-resource skips (ten container-backed database cases and one remote authentication case). The equivalent isolated native MariaDB 11.8.3 runs executed all three new history cases and seven moderation upgrade cases without failures or skips. SQLite tests prove receipt/history rollback and replay after pruning. Shutdown proof interrupts blocked delivery, retains the pending record and refuses new appends. Java-21 Detekt passed; architecture checks are included in the full suite. All local artifacts are unmerged review builds, not production releases.

Publication depends on #197, #198, #200 and #201; review and merge those sources first. The #200 companion API depends on Guilds #212 and its released artifact; the inherited guild branches remain draft. This history PR does not grant approval for those dependencies. V032 reserves a distinct migration number from pending guild auction V030 and XP V031. Preserve local pending/corrupt records during upgrades; do not delete receipt rows as part of normal history pruning. A receipt acknowledges only this history projection, never an economy or XP operation. Snapshot, performance and real-client acceptance gates remain open.

Published as #202. Exact-head Codacy identified one anonymous-method complexity finding; force/write and read/deliver responsibilities were extracted and all focused history/SQLite cases plus Detekt passed. Head `631f9e8e3c5cb1dfa0b7e5e57627f22efc98d23b` then passed Codacy with zero annotations. No human/inline review findings were returned; CodeRabbit skipped automatic review. Hosted build and quality workflows need maintainer approval. Local final review JAR SHA-256: `f4ab7de7ec120bb0229be09b1213415e73afcdc1c759ddf82246db0cc0fb94ea`. This is not a production release. That documentation-only evidence update did not alter the tested engine.

## Combined dependency verification (7 October 2026)

Merge `7b4130d` incorporates prepared #200 head `37a6390` (including #198 and current #197) alongside #201. Both maintenance tasks and the guild API task were preserved. The moderation upgrade fixture now executes and asserts every migration 28 through 32; no migration is filtered or skipped. This resolves the source conflict before maintainer review. No changes were made to the other task's branch.

Clean Java-25/Paper-26.2 `test shadowJar` on this combined source passed 869 cases, zero failures/errors, twelve external-resource skips. The paired Guilds runtime contract executed successfully against unmerged `LumaGuilds-3.0.17-shop-xp-review.jar`, SHA-256 `0f7d906272a12230d5807d2fd5b44a3bd4189a164b22497ced5d4346c34df361`. The eleven skipped database cases executed separately on disposable native MariaDB 11.8.3: seven moderation/upgrades, three history durability, one guild journal; all zero failures/errors/skips. The runtime contract executed again without skips. Remote authentication remains unverified without external credentials. Native launchers were removed after use.

A local merge rehearsal of #197/#198/#199/#200/#201/#202/#203/#204 merged without remaining conflicts and passed 885 cases with zero failures/errors and the same twelve external-resource skips. Architecture checks are in this suite; combined Java-21 Detekt passed. This rehearsal is component-source integration, not the network monorepo release build, hosted CI or production acceptance.

Combined history review JAR SHA-256: `a6260da05aa2f0f16921352dd6d64470f6a02bd1a6b7d2241e789da063ff707f`. Local rehearsal JAR SHA-256: `54c7afb8f6c63d3c684359699ce28040adf6b32ab1127ac2bf20d60a318040aa`. Both are unmerged test artifacts. Local full-suite XML/log evidence is retained outside source in the Codex review workspace. Keep the earlier 848-case record as history-specific pre-stack evidence, not the final combined result.
