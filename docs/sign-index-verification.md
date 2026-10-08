# Shop sign lookup maintenance

SPEAR base: freshly fetched canonical main `14351db`.

REQ-353: WHEN a shop sign is clicked THE SYSTEM SHALL resolve the indexed shop without querying SQL on the server thread.

REQ-354: WHEN persisted shop metadata, stock, location, ownership or deletion changes THE SYSTEM SHALL reconcile sign/container indices only after successful persistence, preserving current transaction inputs.

REQ-355: WHEN moderation reserves a stall THE SYSTEM SHALL project the live lock onto cached sign results without a SQL lookup.

Scope is read-path maintenance. Economy, item transfer, trade settlement, auction settlement and game events retain their current threading and behavior. No speculative off-thread Bukkit or currency calls, community feature or policy change. No EARS/state helper exists; maintain manual requirement/task/evidence.

Prove: the original `IndexedShopRepository.findBySign` test compiled and executed, then failed with `SQL unavailable`. Both indexed hits and misses now execute without touching the SQL delegate. Stock/batches, moved sign/container coordinates, metadata, deletion, bulk freeze and immediate moderation overlays are covered by regressions. Mutation failure leaves prior cached stock intact. Startup still rebuilds from persisted shops; all normal mutations pass through the repository decorator. Direct database edits are outside this cache contract and require a normal rebuild/restart rather than an invisible live SQL edit.

Engine: synchronize container/sign/ID maps and reconcile immutable Shop copies by stable ID. A moved record removes both old coordinate entries. Legacy duplicate sign coordinates resolve deterministically to lowest stable ID, not reindex order; no duplicate cleanup or ownership policy is introduced. Successful stock and freeze writes update the cache without a follow-up SQL read. Moderator locking overlays a copy, so releasing a lock immediately restores the underlying state.

Architecture: application code depends only on domain and standard library. Platform adapters and the existing event threading are unchanged. Existing custom index implementations must implement the new operations; defaults reject unsupported indexing instead of silently reintroducing synchronous SQL. ShopRepository signatures are unchanged.

Refine: clean full Java 25/Paper 26.2 `test shadowJar` passed 779 cases, zero failures/errors, seven existing external-resource skips. Java-21 Detekt and architecture checks passed. Local unmerged review JAR SHA-256: `cec4d594a28c119f8b30f857821bd5a3854bd281b9f14d993be4f6e788e7df94`. JUnit alignment matches the other prepared maintenance PRs.

This fixes shop-sign resolution, not purchase-stall sign/stall SQL reads or auction/currency settlement. A complete storage-outage settlement design must preserve item/money conservation and durable reservations; no Bukkit/economy call is moved to a worker by this change. Production profiling and Java/Bedrock acceptance remain separate gates.

Published as #204. Head `1522bbe18a3babc50096951ea5b1a5103670f082` passed Codacy with zero annotations; no human/inline findings returned. CodeRabbit skipped automatic review. Hosted build/quality workflows require maintainer approval. This documentation update does not change the tested engine.
