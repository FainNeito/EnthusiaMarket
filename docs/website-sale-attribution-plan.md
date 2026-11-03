# Website sale attribution: implementation boundaries

Base: `57704f1acfaf8aacfdd65cb4ae74c0f36b55712d` (BadgersMC/EnthusiaMarket main). The separate earlier `site-feed-market` checkout has uncommitted exporter work and must remain untouched.

## Requirements

- When an eligible, completed, non-admin raw-gold SELL transaction is recorded, the system shall capture the canonical stall identifier and any verified public guild attribution at the transaction boundary.
- When an old transaction or version-1 outbox record lacks attribution, the system shall retain it without inventing attribution from current shop or guild ownership.
- When an outbox record is retried, the system shall preserve the same transaction and attribution through durable, idempotent delivery; version-1 pending records shall remain readable.
- When the website requests a bounded page of eligible public sales, the system shall expose only the verified sale fields through an authenticated, opt-in read-only endpoint, with stable pagination and no buyer or seller identities.
- If currency or transaction eligibility cannot be verified, the system shall withhold the unsupported amount or transaction rather than label it raw gold.

## Existing behavior and boundaries

`ContainerTradeService` has `shop.stallId` and an optional sale-time guild UUID. `PostShopTransactionEvent` currently exposes the guild UUID but not the stall ID. `ShopTransactionRecorder` drops guild attribution; `ShopHistoryOutbox` writes version-1 records; `shop_transactions` lacks immutable stall/guild attribution. The existing historical ledger cannot safely be backfilled using current shop ownership. Shop removal and history pruning also need explicit preservation semantics before using the existing ledger as an authoritative public history.

Before implementation, confirm how the public guild name is resolved at the time of sale and which transaction types count, including admin shops, self-trades, modified items and non-cash barter. A nullable or unavailable guild label is preferable to a guessed historical name. Examine the previous local exporter and recent upstream changes without overwriting any checkout.

## Verification and delivery

Local work in progress adds optional sale-time IDs to trade events and transaction history, a nullable migration, and version-3 outbox writes with version-1 and version-2 reads. A separate public archive and authenticated loopback reader are wired for a conservative subset of solo cash sales only. Archiving requires the operator to set `ENTHUSIA_MARKET_SALES_CURRENCY=RAW_GOLD` after verifying the actual server currency; the HTTP listener additionally requires opt-in configuration and a dedicated token. No production settings are changed. The full local test task passed after merging fork main 0e0c771fdbf65ce7cf88711f6cb3f3084eb2e715 into the topic branch: 1,172 tests, zero failures or errors and 14 skipped, using a locally published Nexus and archived LumaGuilds 3.0.39; the dependencies are not proof of the live runtime. An archived 1.0.52 JAR confirmed that changing `ShopTransaction` would alter existing constructor and copy signatures; attribution was moved into a separate type so its constructor is unchanged. The new repository interface method and event accessors still require binary compatibility checks against real companion runtimes before delivery.

Guild eligibility remains unresolved: the current `GuildProvider.isMember` adapter returns `false` when its lookup is unavailable. That result cannot prove a buyer is not a guild member, so guild sales are excluded until a verified tri-state or equivalent complete membership source is available. The endpoint marks its coverage incomplete; a separate local website candidate propagates that flag and withholds official winners, but that coordinated change is not deployed. Do not activate this partial feed as a definitive Stall of the Week or guild leaderboard. Currency verification is captured at the sale boundary, not retroactively inferred from the current configuration. A cursor beyond the retained archive now fails closed instead of silently hiding new sales; source-generation tracking and a reviewed recovery procedure are still required for restores that do not lower the maximum ID. True end-to-end tests remain required.

Rollback boundary: an older plugin that understands only version-1 pending records cannot safely consume newer records. Before any future activation, establish and test a rollback plan that preserves and drains or translates pending newer records without quarantine, loss, duplicate receipts, or replay of an ambiguous transaction. The migrations and new columns must likewise remain compatible with rollback. The local candidate is not deployment-ready.

Add remaining coverage for idempotent migration, sale eligibility, stable cursors and empty/mixed pages, privacy, guild changes, stall reassignment, deletion and retention, and compatible HTTP authentication. Validate the actual companion runtime and website contract. No TEST or production changes have occurred. Existing SPEAR requirements, tasks and verification records remain authoritative; project-local EARS/state tooling was not found.
