# Production delivery and correctness

Review date: 2026-10-07. This is source review and release preparation, not production acceptance or authorization to merge/deploy.

## Source and build boundaries

- Authoritative Market main freshly fetched at `14351db4dc416138341a11d0e4e27602f207221b`.
- Existing PR #197 head before this review: `b9ec897a55c7ab105fc7a57e51c53463af52c1be`. Continued in an isolated worktree without modifying the original checkout or the separate guild-stall API worktree.
- PR #197 is open and mergeable. Existing-head build and PR quality diagnostics are `action_required`; no workflows were approved or rerun. Codacy's existing-head summary reports zero issues. CodeRabbit's success status accompanies a skipped-review comment, not completed manual review. No inline review threads were returned.
- PR #196 remains open/conflicting, but its DI-safe PlayerNameResolver and conditional Guild event registration are already present in main/#197. Do not merge its stale cumulative diff wholesale; canonical/main source inspection is the relevant provenance signal for these fixes.
- Fresh network main `559bfabc2187ab796a3be889f032383a8041f819` pins Market `b31fc322b9dfa900bb12da67e67f4c2665822275` and Guilds `a15b244e8a294bf18e6dedf722462edf9faa40ae`. Market still needs a pin update after canonical integration. Do not regress the newer Guilds pin to the earlier companion test release.
- The checksum-verified released LumaGuilds 3.0.23 reference artifact has SHA-256 `c2958842e976581743af510d57547d1d38542633eeff7dd27325a5b0d307eb19`. `javap` confirms the public bank methods on both GuildLookup and GuildLookupImpl. This is a downloaded release reference, not a measured production artifact. Production's filename/runtime-metadata discrepancy remains unresolved.

## Checklist delivery matrix

| Checklist item | Source/local proof | Remaining production gate |
| --- | --- | --- |
| Guild auction accounting | #197, GuildAuctionTest, AuctionRepositorySqlTest and lifecycle tests | V030 migration, escrow/restart/failure proof on exact deployed build |
| Guild sellback refunds | #197, GuildSellbackTest; this review adds fail-fast moderation reservation checks | Real bank/payment/persistence recovery and no duplicate refunds |
| Public Guild bank API | #197 LumaGuildsGuildProvider; released 3.0.23 API and implementation signatures inspected | Confirm exact installed companion binary and live bank semantics |
| Guild rank/membership protection | #197 GuildShopAccessTest, GuildStallProtectionTest and membership event adapter | Java/Bedrock, open-inventory revocation, offline roster and WorldGuard acceptance |
| Visitor anvils/lecterns | #197 StallVisitorProtectionTest and provisioned flags | Authorized region resync and player interaction/theft boundaries |
| Double-chest hopper/cart protection | #197 HopperControlListenerTest and indexed-listener tests | Both halves, carts, adjacent ownership and item conservation live |
| Creature/occupied-hive matching | #197 SpecialItemMatchTest | Real player item samples, metadata preservation and actual delivered stacks |
| Sign refresh and spear creation | #197 ContainerStockListenerTest and ShopCreateListenerTest | Actual unchanged-stock price redraw and Java/Bedrock aimed interactions |
| Bedrock editing/private input | #197 editor and ChatPriceListener tests | Real Cumulus clients, current permission recheck and chat bridge privacy |
| Item-frame policy | #197 EntityLimitListenerTest, bundled unlimited defaults | Explicit choice/update of existing operator config and live limits |
| Canonical release gates | Existing-head hosted gates inspected; local full-build evidence recorded separately | Maintainer workflow approval, human review/merge, then network pin PR and combined clean build |
| Provenance reconciliation | Current main, #196/#197 and network pins inspected | Exact source/build provenance for both installed production JARs |
| Ticket #406 | Existing repurchase/ownership regressions and V029 semantics retained | Targeted stall48/Quinn data, member intent and real former-member access verification |
| Production acceptance matrix | Relevant suites are part of the local full build | Java/Bedrock lease, renewal, expiry, auctions, trading, currency failures, concurrency and restart acceptance |

No production checkbox is closed by this source review. Missing pristine reset schematics remain separately tracked in the console-log backlog.

## DB-337 MariaDB release-check review

Spec: the V027 upgrade fixture must include the historical auctions table before V030 alters it, apply migrations 28 through 30, and preserve the funding identity and amount of existing personal bids. A guild bid must round-trip through the actual MariaDB repository and a repeated migration run must apply nothing. These are release evidence for REQ-326/311, not a new player behavior.

Source finding: the six Docker-only moderation cases create stalls and shops but omit auctions, and still expect only migrations 28 and 29. Local runs previously skipped them without Docker. Reproduce against a disposable loopback-only MariaDB 11.8.3, then repair the fixture; keep hosted Docker startup unchanged. No production database is involved.

Prove: a temporary native launcher ran the same six test bodies against checksum-verified MariaDB 11.8.3 on 127.0.0.1. All six failed in setup with `Table 'enthusia_market_test.auctions' doesn't exist`. The launcher only replaced the container endpoint/startup and renamed the test class; it did not change assertions or production code. It was removed from the source tree after execution.

Engine/architecture: include the pre-V030 auctions schema and an existing personal bid in the V027 fixture, drop auctions before stalls on reset, and expect migrations 28/29/30. Add a real-database regression for personal funding/amount preservation, guild bid save/reload, a second migration run applying nothing, and restoration to personal funding. No runtime, migration SQL, companion API, or hosted Docker policy changes.

Refine: all seven native MariaDB cases passed with zero failures/errors/skips after the fixture correction, including concurrency, durable fences and ownership restoration. This proves actual MariaDB SQL execution, not Docker startup or hosted Actions success. Before/after XML, logs and the native launcher are retained in the local `market-mariadb-validation-20261007` evidence directory beside the checkout. The published suite retains Testcontainers and must also pass the existing hosted no-skips gate.

## Post-merge network release procedure

1. A maintainer approves the build/quality workflows on the final PR head, checks the MariaDB no-skips step, and reviews/merges #197. CodeRabbit success with a skipped review is not human review. The connected account has upstream read-only permission and cannot approve those workflows or merge.
2. Refresh network main and create its pin-update branch from that base. Change only `plugins/enthusia-market` to the exact canonical merged Market commit after verifying its ancestry. Current network main is `559bfabc2187ab796a3be889f032383a8041f819`; preserve `plugins/luma-guilds` at `a15b244e8a294bf18e6dedf722462edf9faa40ae` or its then-current newer pin. No pre-merge Market pin PR is prepared against an unmerged head.
3. Use the network's documented `scripts/build-all.bat` on Windows or `scripts/build-all.sh` on Linux, plus required component checks. Root `buildAll` packages Market/Guilds via `shadowJar`; packaging alone does not execute their complete test suites. Verify Market against the actual pinned Guilds build, its public bank API, and the combined artifacts before the network PR is reviewable.
4. After the network PR merges, build from a fresh clean checkout of that exact network commit. Record submodule commits, artifact versions and SHA-256, local/hosted results, and installed companion provenance. A deployment or restart requires its own explicit authorization. On authorized staging/live acceptance, exercise guild accounting/refunds, Java/Bedrock permission revocation and menus, migration/restart recovery, sign redraw, private chat and region/item-transfer boundaries before closing production gates.

## SPEAR refinement: sellback moderation lock

Spec: REQ-335 rejects confirmation while an active moderation mutation reservation exists, before money or ownership/projection changes. Quote remains a pure read; confirmation rechecks the current lock.

Prove: five focused GuildSellbackTest cases ran with two failures before the guard. Both personal and guild tests quote an authorized owned stall, acquire a lock, then confirm. Before the explicit application guard, the mocked repository permitted the transition. This is not proof of a production moderation bypass: StallRepositorySql already rejects updates using the durable lock and optimistic revision. The new guard supplies the same fast local reservation boundary used by other ownership services; the durable SQL fence remains authoritative if a lock arrives later.

Engine/architecture: inject the existing MarketMutationGate port with its compatibility default; reject with ExecuteResult.Rejected and the existing localized rejection command path. No enum, schema, payment policy, WorldGuard mutation or companion API change is introduced. Unlocked sellback and failed-bank refund behavior retain existing tests.

Refine: record final local test counts, artifact hash and hosted head conclusions in verification.md. Project-local EARS/state helpers are absent; manual requirement/task/evidence records are maintained, with no validator success claim.
