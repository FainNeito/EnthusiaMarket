# Market and Currency workload investigation

## Spec and boundaries

REQ-363: WHEN repeated sales or auction settlement are profiled THE SYSTEM SHALL distinguish scheduler/repository operation counts and synthetic adapter timing from current server measurements, while preserving money/item conservation and server-thread financial/world operations.

Current main is 14351db4dc416138341a11d0e4e27602f207221b. This investigation starts from current main independently of the pending fixes. Currency maintained provider source is wsg138/EnthusiaCurrency 3a5a2f5 (1.4.4); BadgersMC provider main remains 3d32bb5 (1.3.0), and network main 559bfabc pins 9696501 (1.4.4). Exact installed provider provenance and contemporary server latency remain unverified. Historical watchdog reports do not establish present causality.

Project-local EARS/state helpers are absent; REQ-363 and the phase/evidence record here are manual. No production operations, stall resets or community feature changes are included.

## Prove

Four characterization cases pass on current main: real ContainerTradeService and Vault adapter transfer 100 items for 100 currency; individual sales perform 100 balance reads/withdrawals/deposits and the existing batch path performs one of each. Both conserve the same buyer/owner balances and inventories. The actual scheduler callback performs findExpired/allOpen on its calling thread. Injected 20 ms per read demonstrates 40 ms of synchronous discovery/reminder work; 100 no-bid auctions perform 100 synchronous stall reads and 100 close writes on the calling thread. These are synthetic adapters, not actual server SQL timing or a historical watchdog reproduction. No failing behavior test is claimed for this characterization.

## Engine and architecture

No Market execution change is selected from a synthetic timing result alone. Auction settlement includes synchronous persistence and Bukkit/economy/world projections; moving the complete lifecycle asynchronously would violate these contracts. Its scheduler comment currently misstates its actual synchronous scheduling and will be corrected. The independently supported Currency fix coalesces pending stale balance-read refreshes without changing transaction validation.

## Refine and delivery

Clean Java-25/Paper-26.2 test/shadowJar/jacoco build passes 778 cases, zero failures/errors, seven external-resource skips (six Docker MariaDB and one remote authentication). LayerRules architecture checks are included. Java-21 Detekt passes. The initial untouched main test setup failed JUnit engine discovery; align its JUnit 6.0.3 BOM/launcher with existing pending #197/#205 rather than altering product execution. Local unmerged test JAR SHA-256: b8d92ba9304f1d79f4ae8875177bd336b1eb2a30bd59afd7e0ef036e8fff98d1. Currency fix is submitted as [provider #20](https://github.com/wsg138/EnthusiaCurrency/pull/20), with 23 passing tests and zero skips. No artifact was uploaded. Isolated synthetic adapter timings do not prove server throughput, tick latency or resolution of the historical watchdog. Current workload traces and actual Java/Bedrock client acceptance remain separate.

## Tasks and remaining work

Review refinement: Codacy flagged the scoped test-fixture initializer; use explicit per-test setup/teardown instead. The refined clean build still passes 778 cases with zero failures/errors and seven external-resource skips; Detekt passes. Refined local review JAR SHA-256: 7c618a83f7104e486ffd88af06334efae40c4cdca1a19765f27747d73bf5f2b9. Initial integration rehearsal with pending #197-#206 passes all six focused workload, architecture and Guilds-runtime cases at adfd4719261829245522af1cdee078d73abae41d. Market delivery is [#208](https://github.com/BadgersMC/EnthusiaMarket/pull/208); hosted fork workflows require maintainer approval and do not constitute passing CI.

- [x] Specify and characterize sale/batch adapter counts and conservation.
- [x] Characterize auction discovery/reminder/per-auction calling-thread work and correct the misleading scheduler comment.
- [x] Prove and submit the independently supported Currency stale-read coalescing fix.
- [ ] Complete exact-head hosted review; canonical/provider source reconciliation and network pin/release checks remain external gates.
- [ ] Obtain a representative current server profile before selecting further settlement architecture changes. A safe design must revalidate bids/end times and mutation reservations, serialize settlement and preserve charge/refund/recovery ordering; async stale auction snapshots are not sufficient.
