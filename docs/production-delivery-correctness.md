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

## SPEAR refinement: sellback moderation lock

Spec: REQ-335 rejects confirmation while an active moderation mutation reservation exists, before money or ownership/projection changes. Quote remains a pure read; confirmation rechecks the current lock.

Prove: five focused GuildSellbackTest cases ran with two failures before the guard. Both personal and guild tests quote an authorized owned stall, acquire a lock, then confirm. Before the explicit application guard, the mocked repository permitted the transition. This is not proof of a production moderation bypass: StallRepositorySql already rejects updates using the durable lock and optimistic revision. The new guard supplies the same fast local reservation boundary used by other ownership services; the durable SQL fence remains authoritative if a lock arrives later.

Engine/architecture: inject the existing MarketMutationGate port with its compatibility default; reject with ExecuteResult.Rejected and the existing localized rejection command path. No enum, schema, payment policy, WorldGuard mutation or companion API change is introduced. Unlocked sellback and failed-bank refund behavior retain existing tests.

Refine: record final local test counts, artifact hash and hosted head conclusions in verification.md. Project-local EARS/state helpers are absent; manual requirement/task/evidence records are maintained, with no validator success claim.
