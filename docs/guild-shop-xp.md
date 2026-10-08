# Guild-shop XP delivery

## Spec (REQ-342)

WHEN an outside customer completes a paid EnthusiaMarket SELL purchase from a guild-owned stall, THE SYSTEM SHALL award that stall's owning guild one configurable XP award (default 5), bounded by a guild UTC-day cap (500), buyer/guild UTC-day cap (50), and buyer/guild cooldown (300 seconds) shared across shops. Guild members, free trades, BUY shops, barter, and failed or compensated purchases SHALL award zero. Quantity and price SHALL NOT multiply XP. BUY-shop resale is a separate trade, not a refund.

WHEN a completed sale is redelivered, THE SYSTEM SHALL atomically consume its durable ID once with its caps and progression update. Eligibility and prestige identity SHALL be captured before payment. Pending sales from a previous prestige run SHALL NOT award XP to a new run. Purchased rewards SHALL remain untouched. Permanent consumption records SHALL outlive the ordinary XP-history retention policy.

## Architecture / acceptance

Guilds owns policy, membership snapshot, progression identity, caps and idempotence. Market owns the completed-sale journal and replay. A JDK-only ServicesManager API avoids a dependency cycle. Policy is snapshotted before payment; retries retain that quote. Guilds reuses its existing SQL XP engine in the same transaction. Progression cache refresh and level events follow committed awards.

Vault and inventories have no shared SQL transaction or durable payment receipts. A crash between item/payment effects and the Market COMPLETED journal write is ambiguous. PREPARED records never replay automatically; staff must investigate the trade evidence. Completed journal records retry after transient errors or acknowledgement loss. If preparation is unavailable, the purchase is refused before any side effect, rather than silently losing its reward. Personal/free trades remain unaffected.

## SPEAR state

- Spec: approved user policy above; Guilds base a15b244, Market main 14351db and stacked API dependency 322933c (PRs #197/#198).
- Prove: Market trade-boundary and persistent-journal tests cover preparation-before-payment, completion-after-delivery, withdrawal/delivery failure, own/personal/free/BUY boundaries, retry, acknowledgement loss, restart and ambiguous completion-write failure. Guilds owns the own-guild membership and prestige SQL proof.
- Engine: V031 journal, synchronous pre-payment quote, completion/abort boundary and ordered bounded replay implemented. Best-effort notification events remain separate from the durable reward boundary.
- Arch: domain ports isolate SQL and ServicesManager adapters. A JDK-only versioned API is resolved through the companion's own classloader on each call, without caching unavailable providers.
- Refine/local: clean Java 25/Paper 26.2 test/shadowJar passed 842 tests, zero failures/errors, eight skips against the actual unmerged Guilds review artifact; the isolated-loader API contract executed (not skipped). Detekt passed on JDK 22. Disposable loopback MariaDB 11.8.3 passed the new journal contract and all seven current migration/moderation fixture bodies, including V031. Docker-dependent hosted/runtime acceptance remains separate.
- The existing BedrockHeadStore fake-clock test advanced time before the full worker mutation/persist step completed. A queued executor fence now waits for that step before changing time; production head-store code is unchanged.
- Hosted checks/review for the final published head are pending. Local MariaDB proof does not establish staging/player acceptance.
- Project-local EARS/state helpers are absent; this file and docs/tasks.md are the manual state/evidence record.
- Hosted CI, merged companion build, staging and player acceptance remain separate; no production changes authorized.

## Companion and release gates

Companion: [LumaGuilds PR #212](https://github.com/BadgersMC/LumaGuilds/pull/212), native database follow-up e579f77. Market's inherited dependencies are [#197](https://github.com/BadgersMC/EnthusiaMarket/pull/197) and [#198](https://github.com/BadgersMC/EnthusiaMarket/pull/198). This branch remains a draft stack until those changes and the companion API are reviewed and merged. Canonical main was refreshed at 14351db; #197 advanced to 7d7e54a and its relevant authority/MariaDB-fixture changes were incorporated in merge 153dbd7 before delivery.

Market CI currently downloads the checksum-pinned released Guilds 3.0.17, which lacks GuildShopXpApi. The runtime contract explicitly skips with a diagnostic, and the workflow emits a warning; CI green on that release is not hosted proof of shop XP. Update the companion version/hash after #212 is merged and released, and require the contract to execute before production integration. Local paired-artifact validation must execute the contract. Gradle tracks configured companion path and contents so replacing the artifact invalidates results.

PREPARED journal entries mean unconfirmed or crash-ambiguous trades and never replay. ABORTED entries are known failures. Only COMPLETED records replay, ordered by sale time/ID, at most 100 every ten seconds. ACKNOWLEDGED stores the terminal Guilds outcome. Do not blindly promote PREPARED rows: inspect the actual payment and inventory evidence. The journal records owning guild, buyer, shop, ID and sale time; durable Guilds receipts survive ordinary XP audit cleanup.

An unavailable XP provider refuses paid guild SELL purchases before side effects with an explicit retry message. Deploy the merged Guilds API before the merged Market consumer; this draft is not authorized for production. Personal/free/BUY/barter trading is preserved. No item return/refund feature was added. Source fixes, canonical build/pins, hashes, CI and staging acceptance must be verified before any production upload/activation. No production changes, merge, restart or activation was performed.

Optional native checks use only loopback disposable test databases. Gradle tracks the configured port and reruns configured checks, so a skipped/stale external-database result cannot be reused.

Local unmerged review artifact: `build/libs/EnthusiaMarket-1.0.0-shop-xp-review.jar`; SHA-256 `8f4a973213280dbf09e8194c014627af07b304d28c4b9d317eb18ee3c5928fb8`. This is not a production artifact.

## Hosted review refinement

Paid SELL now separates stock collection, payment/compensation and actual inventory delivery. The inventory helper preserves the original collected stacks and exact delivered amounts for rollback. Trade boundary regressions passed; the real revised Guilds artifact runtime contract executed (one test, zero skips). Clean Java 25/Paper 26.2 test + shadowJar passed 842 tests, zero failures/errors, eight optional skips. Java 22 Detekt passed. Documentation spacing findings were corrected. Hosted results for this revised head remain separate; no production changes.
