# Market check refinement — 2026-10-04

## Production correctness continuation — 2026-10-07

Fresh canonical main remains `14351db`; the existing #197 head was `b9ec897`. Continued its work in an isolated branch/worktree. See production-delivery-correctness.md for the fourteen-item release/acceptance matrix and current network pins.

REQ-335 / LOCK-336: five GuildSellbackTest cases ran with two genuine pre-guard failures. The new personal/guild cases acquire a moderation reservation between quote and confirmation. The implementation injects the registered MarketMutationGate port and rejects before sellback mutations. StallRepositorySql's existing durable lock and optimistic revision fences are preserved; these mocked application regressions do not demonstrate a production SQL-fence bypass. Default constructor compatibility and the existing rejection command path remain intact.

Clean Java-25 / Paper-26.2 `test shadowJar` against the checksum-verified released LumaGuilds 3.0.23 runtime passed 820 tests, zero failures/errors, seven skips. Six skips are JdbcMarketModerationMariaDbTest, and one is MarketRemoteAuthenticationTest. GuildSellbackTest's five cases and LayerRulesTest passed. Java-21 Detekt and `git diff --check` passed. A first invocation split an unquoted Gradle property in PowerShell and failed before running tasks; the quoted clean invocation above is the successful validation.

The companion SHA-256 is `c2958842e976581743af510d57547d1d38542633eeff7dd27325a5b0d307eb19`; javap confirmed systemBankWithdraw/systemBankDeposit on both API and implementation. This release reference does not prove the exact installed production binary. CI's existing 3.0.17 companion remains the declared minimum profile; its hosted execution still needs fork workflow approval.

Unmerged local review artifact: `build/libs/EnthusiaMarket-1.0.0-production-review.1.jar`, SHA-256 `8b2fb334a58c3a36dfda996da0d17a4fa7b8474f20b9ff990db328fc4136964e`. This is a local test artifact, not a production release, and was not uploaded.

Existing-head inspection: open/mergeable #197; build and quality workflows are action_required. Codacy's summary reported zero issues. CodeRabbit's success status accompanies an explicitly skipped review, so no completed automated review is claimed. Final published-head checks must be inspected separately. Project-local EARS/state helpers remain absent; manual requirements/tasks/evidence were maintained without a validator success claim. No canonical merge, network pin mutation, JAR upload, WorldGuard resync, production data repair or restart occurred.

## Spec

Preserve REQ-323, REQ-326 and REQ-329 while reducing the remaining Codacy complexity findings to the configured limit of five. Bid rejection must retain IP reservation rollback, guild edit checks must retain field-specific authorization, and creature normalization must preserve component boundaries and custom data.

Delivery is through Market PR #197. Production, merging, JAR uploads and server operations are outside this authorization. A future deployment requires canonical merged source, a clean build, artifact provenance and any owning monorepo pin update.

## Prove

The exact published head `cf9695a` has three actionable Codacy findings: `placeBidWithPermit` (6), `maySaveEdits` (7), and a normalization lambda (7). Existing GuildAuctionTest, GuildShopAccessTest and SpecialItemMatchTest provide brownfield regression coverage; no historical test-first claim is made. Current Market main remains `14351db`.

## Engine and architecture

Extract reservation finalization, field-edit decisions and list normalization into small named helpers without changing persistence, permission or normalization semantics. Keep these helpers in their existing application services. Validate against a released LumaGuilds runtime containing the merged public bank API, rather than treating API-only compilation as runtime acceptance.

## Refine and tooling

Focused guild auction, guild access and item matching regressions passed against LumaGuilds 3.0.17. The clean full suite passed: 806 tests, zero failures/errors, seven skipped (including all six MariaDB integration cases). Architecture tests are included. Detekt passed on Java 21; compilation and tests use Java 25 / Paper 26.2. Local Maven consumption uses the previously built pinned Nexus source; an initial Detekt invocation without that flag could not resolve the unpublished remote dependency and was corrected.

The shaded local review artifact `build/libs/EnthusiaMarket-1.0.0-spear-review.2.jar` built successfully after these checks. SHA-256: `7377253bf8b07f14f3d30d75fdbf1df34e61ecf3f218189372e9080c2e4608a7`. It is an unmerged local test artifact and was not uploaded.

LumaGuilds PR #197 is merged at `6b13f3900f84cfff45ed08377684ea65e2029895`. The actual v3.0.17 runtime comes from tag/source `6c9f5a521cca783fbe518f5ad2d749f6e2d211c7`; its SHA-256 is `de18a4672ac37f456ed72742706ae0ea7ff96dfd0f6878bc6a1d5511b9efb34a`. `javap` confirmed the bank methods on both GuildLookup and GuildLookupImpl. CI now downloads this runtime and verifies its checksum. This proves class/API compatibility, not live guild-bank or player acceptance.

Hosted checks remain a separate gate. The preceding head had three Codacy complexity findings and GitHub workflows awaiting repository administrator approval. Inspect the refinement's exact published head before closing REFINE-331; local success does not replace hosted or MariaDB integration results.

Project-local SPEAR EARS/state helpers and a project SPEAR skill are absent; the existing SPEAR Paper Brownfield skill is used. No validator or state-tool success is claimed. This behavior-preserving refinement reuses existing EARS requirements and tests; documentation and CI dependency changes do not introduce new player behavior requiring a separate behavioral proof.

## REQ-332 configurable item frames — SPEAR evidence

Spec: unlimited normal/glow item frames by default; `-1` bypasses their placement's shared total, while finite/zero caps and other entity limits remain enforced. Operator configuration is preserved.

Prove: before implementation, nine focused listener cases ran with two failures and zero errors/skips: bundled defaults were finite, and explicitly unlimited frames were still rejected by `_total`. New cases also cover zero/finite frame caps, finite frames at the total boundary and other entities retaining their total cap.

Engine/architecture: change bundled defaults and put the frame-specific total decision in the existing domain policy; retain the infrastructure event adapter and counter. No companion API or database migration changes.

Refine: clean full suite passed on Java 25/Paper 26.2 against LumaGuilds 3.0.17: 811 tests, zero failures/errors, seven skipped (including six MariaDB cases). The nine listener tests are green; architecture checks are included. Detekt passed on Java 21. The earlier Market head `da2b960` passed Codacy without annotations; hosted checks for this frame-change head remain a separate pending gate. Project-local EARS/state tooling remains absent.

The shaded unmerged local review artifact `build/libs/EnthusiaMarket-1.0.0-spear-review.3.jar` built successfully after these checks; SHA-256 `b251e2d6d59eebe040d36db334514aa44d1816c007836e5c927a218116391439`. It was not uploaded or activated.

For an existing installation, edit each desired group in `plugins/EnthusiaMarket/entitylimits.yml` to set `item_frame: -1` and `glow_item_frame: -1`, then restart through an authorized operational action. Limits are loaded when the listener is constructed; the existing `/em reload` command only reloads the main configuration and translations. This PR neither overwrites that file nor changes production. Nonnegative values remain configurable per type; per-stall extra allowances retain their existing additive meaning.

## SIGN-333 price-only edit report — SPEAR evidence

Spec: REQ-327 includes a price-only `/shop edit` change from 3 to 5 with unchanged inventory. Persisted shop data and rendered sign text must agree after the loaded-container timer cycle.

Prove: current canonical main `14351db` returns early solely on unchanged raw stock; its sign updater only writes stock/header lines. The pending PR already changes both conditions: it tracks the rendered shop fields and renders the full sign. This is brownfield verification of an existing pending fix, not a new historical red/green claim.

Engine/architecture: no additional production source change was needed. A regression now saves the edited price through ShopManagementService and invokes the same `refreshBatch` timer entry point wired in EnthusiaMarket. It asserts actual price components serialize to `["3", "5"]`, rather than only counting redraw calls. All 24 focused management/listener tests passed without failures/errors/skips. The preceding implementation's full suite had 811 tests (seven skipped); no fresh full-suite claim is made for this test-only addition. Hosted checks remain distinct from local proof. No server version/configuration or player acceptance was verified, and nothing was uploaded or activated.

Refine: Detekt passed on Java 21. No project-local EARS/state tooling is present; no tooling success is claimed.

## Future release gate

Read-only inspection of `BadgersMC/enthusia-network` found Market pinned to `b31fc322b9dfa900bb12da67e67f4c2665822275` and LumaGuilds to `c427d5dbc4838c95bcde45d57be14a6b6980ff8e`. Before a future deployment, merge the Market fixes, update the owning monorepo pins through its normal PR, and validate the combined clean canonical build and artifact provenance. No monorepo update, merge, upload, restart or production change was performed here. Live Java/Bedrock, WorldGuard and real creature-item acceptance remains open as documented in reported-bug-fixes.md.

## CHAT-334 custom-price input privacy — SPEAR evidence

Spec: REQ-334 claims private prompt input before chat broadcasters, schedules existing menu processing on the main thread, supports Paper fallback and avoids duplicate legacy/Paper callbacks. Other chat must remain public normally.

Prove: the new event-dispatch suite ran before implementation with five tests, two failures and no errors/skips. Price and bulk messages were not cancelled ahead of a LOW-priority legacy broadcaster. The checked local RoseChat source uses AsyncPlayerChatEvent at LOW with ignoreCancelled=true, whereas Market previously cancelled only AsyncChatEvent. [Paper ChatProcessor](https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/io/papermc/paper/adventure/ChatProcessor.java) dispatches legacy chat first and propagates its cancellation into the modern event. These are source/adapter findings; the active server's exact installed chat plugin was not inspected.

Engine/architecture: add a LOWEST legacy handler and put both event adapters ahead of broadcasters. Preserve the existing prompt maps, parsing and callbacks in their existing classes; do not move Bukkit scheduling into domain code. Both handlers ignore cancelled messages and cancel before enqueueing. Modern fallback remains supported; no companion binary/API changes or migration are required.

Refine: all five new tests passed inside the clean full suite: 817 tests, zero failures/errors, seven skips (including six MariaDB cases). Tests cover private legacy input, callback deferral, Paper-only input, propagated cancellation without duplicate callbacks, ordinary chat and bulk quantity input. Architecture tests are included; Detekt passed on Java 21, compile/tests on Java 25/Paper 26.2 with LumaGuilds 3.0.17. Project-local EARS/state helpers remain absent. Exact published-head hosted checks and live two-player/chat-bridge acceptance remain separate gates.

The shaded unmerged local review artifact `build/libs/EnthusiaMarket-1.0.0-spear-review.4.jar` built after these checks; SHA-256 `aff265473b525bae7a0740e207068827ba268359ecd85b707bb6d56d986d12a7`. No upload, activation, merge or production change was performed.

## DB-337 MariaDB V030 release verification (2026-10-07)

Spec: the V027 test baseline must permit the actual V028/V029/V030 upgrade, preserve existing personal auction funding and support guild funding persistence. This is testing/release infrastructure under REQ-326/311; no new runtime engine or behavioral policy is required. Project-local EARS/state tooling remains absent.

Prove: the same six MariaDB test bodies executed through a temporary native loopback launcher on checksum-verified MariaDB 11.8.3; all six failed with the missing `auctions` table during setup. The original Docker annotation had hidden this failure in earlier local runs. The native launcher changed only startup/endpoint and class name, not test bodies or assertions. MariaDB archive SHA-256: `debd9643db9b3d35276fb782789564484c681b1bb264d03de0b3a2e8e739f493`, verified against the publisher's checksum file.

Engine/architecture: correct only the integration fixture, include a historical personal bid before migration, expect versions 28/29/30, and add a seventh test exercising real AuctionRepositorySql save/reload, unchanged personal bid identity/amount/time, guild funding, migration rerun and return to personal funding. Production SQL and adapters are unchanged; hosted Testcontainers startup remains intact.

Refine: all seven native cases pass, zero failures/errors/skips. The temporary launcher was removed from source and the isolated database was shut down. Clean canonical Java-25/Paper-26.2 `clean test shadowJar jacocoTestReport` with released LumaGuilds 3.0.23 passed: 821 cases, zero failures/errors, eight skips. Seven are the Docker MariaDB class exercised separately natively; one is remote authentication. LayerRulesTest, GuildSellbackTest and AuctionRepositorySqlTest are included. Java-21 Detekt and diff whitespace checks passed. An initial PowerShell invocation split an unquoted dotted release property into a task name; the corrected quoted canonical command above passed, with no product test failure implied.

Local unmerged review artifact: `build/libs/EnthusiaMarket-1.0.0-production-review.2.jar`, SHA-256 `5daef9c86e88a17fbb0f90890115f049df65d5c6f50c59d83df93f2e8fc081c9`. It was not uploaded or activated. Local evidence is retained beside the checkout in `market-mariadb-validation-20261007` (before/after XML, launcher, full-build and Detekt logs). This evidence establishes actual MariaDB SQL behavior, not hosted Docker startup or real-server/client acceptance.

Fresh network main remains `559bfabc2187ab796a3be889f032383a8041f819`, with Market `b31fc322` and Guilds `a15b244e`. Its documented combined build is `scripts/build-all.bat` / `scripts/build-all.sh`; root shadowJar packaging does not replace component tests. Post-merge steps are recorded in production-delivery-correctness.md. Current upstream permissions are read-only for Market and network, so maintainer workflow approval/review/merge remains external. No network pin or production state was changed. Exact new-head hosted findings must be inspected after publication.

## STALE-335 ticket bug-0406 — ownership investigation

Spec: REQ-321 requires fresh delegated access after forfeiture/ownership transfer. The supplied screenshots show a member list for stall48 containing two unfamiliar names and a reported expired-stall purchase retaining old members; the reporter explicitly did not verify those players' actual access. A website screenshot shows another roster/owner view but does not establish a synchronized live access snapshot.

Prove/source: refreshed canonical main remains `14351db` (PR #195). RentCollectionService clears members on emergency forfeiture; eviction and sellback clear them on release. Both buyout and auction settlement call Stall.awardTo, which clears delegated members. WorldGuardRegionMemberSync.setOwner clears former owners and members before adding the successor. The member-list command reads Stall.members, and a SOLO stall's canManage permits those members, so stale data is potentially an authority issue rather than merely cosmetic. The current production build and WorldGuard projection were not inspected.

Engine/architecture: no additional production code change is supported by this report. The historical V029 repair deliberately does not purge members from currently OWNED/GRACE stalls because the row cannot prove whether the current owner added them. Blanket clearing would remove legitimate members. A test now buys a dirty vacant stall with a former delegated member and verifies the purchase result has no inherited members, belongs to the buyer, and denies the former member management.

Refine: 92 focused Stall/Buyout/Auction/Rent/Eviction tests passed with zero failures/errors/skips; Detekt passed on Java 21. This is verification of an existing canonical fix, not a claimed new red/green implementation. Hosted checks for the evidence/test follow-up remain distinct. Project-local EARS/state tooling remains absent. A future targeted repair needs exact installed source/version evidence, a read-only database/WorldGuard comparison and current-owner confirmation of intended members. No database change, WorldGuard mutation, sellback, deployment or production operation was performed.

## Guild-shop XP (REQ-342)

See [guild-shop-xp.md](guild-shop-xp.md) for policy, payment boundary, journal states and SPEAR evidence.

## Approved guild stock accounting (8 October 2026)

See [stall-accounting.md](stall-accounting.md): 895-test paired clean build passes
with 12 unrelated optional skips; exact integer FIFO, receipt/lot rollback,
native MariaDB retry, unknown stock, inventory interference, access denial,
command registration and CSV escaping are covered. Java-22 Detekt passes.
The actual Guilds JAR was frozen during compilation/testing. Hosted CI and live
Java/Bedrock acceptance are independent gates; no production changes occurred.
