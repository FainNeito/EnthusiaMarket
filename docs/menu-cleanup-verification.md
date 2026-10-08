# Shop menu cleanup

## Spec and boundaries

Approved scope: compact money-based buying/selling, unambiguous totals and unavailable states, non-overlapping bulk controls, clearer edit groups, safe save/back behavior and a separate deletion confirmation. Preserve permissions, guild access, existing trade execution/rollback, legacy barter placement and Bedrock form semantics. Stall resets and other community requests are excluded.

Canonical main fetched on 8 October 2026: `14351db4dc416138341a11d0e4e27602f207221b`. This branch was created from that base and fast-forwarded to dependency PR #197, exact head `7d7e54a7ea0d014fc0909dc5f43f99663b6f1c71`, because current guild save/delete authority and Bedrock edit routing belong to that pending fix. Review the menu-only delta against that head; merge #197 before this PR. No other feature branches are modified.

Project-local EARS/state helpers are absent. Requirements REQ-359..362 and manual phase/evidence tracking below replace unavailable tooling; no validator pass is claimed.

## Prove

The finalized MockBukkit inventory-click fixture was rerun against unchanged dependency source: 15 cases, 10 failures and two old-layout callback skips. It demonstrated the overlapping 32/Custom slot, five-row money menu, incorrect BUY receive/pay units, frozen maximum and immediate editor deletion. With the engine restored, all 15 pass with zero failures/errors/skips. The fixture explicitly registers the public InventoryFramework listener for each fresh server; initial listener-fixture failures are not claimed as product regressions. Four Cumulus submit/cancel/invalid/revoked-owner cases also pass.

## Engine and architecture

Three-row BUY/SELL menus show directional item/currency totals and disabled reasons; TRADE keeps its five-row placement slot and shulker preview. Bulk Custom moves to its own slot. The grouped editor saves through current application authority, prompts on Back with a changed draft, and confirms item/location before deletion. Confirmation is consumed once and checks current owner/admin authority again. GUI/form adapters continue to invoke existing application services. No economy calls, inventory mutations or persistence move to asynchronous threads. No database migration, companion API or production configuration change.

## Refine and delivery

Clean canonical Java 25/Paper 26.2 `clean test shadowJar jacocoTestReport -PuseMavenLocal=true -PreleaseVersion=1.0.0-menu-review.1` passes **840 cases, zero failures/errors, eight skips** (seven Docker MariaDB and one remote authentication). Existing conservation, rollback, guild access and LayerRules architecture tests are included. Detekt passes on Java 22; frontmatter lint (31 pages), player topic check (14 pages) and strict MkDocs build pass. Nexus comes from the clean pinned source `057836befb9e35aa252cf90104030ec86f28b33f`; companion is released LumaGuilds 3.0.23, SHA-256 `c2958842e976581743af510d57547d1d38542633eeff7dd27325a5b0d307eb19`. Unmerged local review JAR SHA-256: `18cf64ca3b7cf5344c65715c6ac11d683cdcfae37a005bd037d7cbb3a00f6cb1`. Logs and baseline XML are retained outside source in `market-menu-evidence-20261008`. Integration rehearsal and exact-head hosted review are pending. Native Java/Bedrock client acceptance is separate from MockBukkit/Cumulus proof. No merge, production upload, restart or activation is authorized.
