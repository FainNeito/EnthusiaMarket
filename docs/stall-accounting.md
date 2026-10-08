# Guild stall contributor accounting

## Spec

G44 approved 8 October: FIFO stock attribution; existing/untraceable stock is
unattributed. Personal/guild payouts, tax, shop XP and shop ownership are unchanged.
Sales reporting labels gross customer payment, not profit or automatic payouts.
BUY-shop trades are separate inventory purchases, never SELL revenue.

Stock additions/removals and completed SELL sales are captured on the server
thread, with guild/stall/item identity at the time of the event. A durable local
outbox delivers ordered immutable observations off-thread. SQL applies each ID
once, atomically consuming stock lots and assigning revenue. Ledger mismatches
replace remaining attribution with unknown stock, never guess a contributor.
Concurrent/mixed/automated edits are conservative unattributed observations.
Reports are guild-manager-only, bounded, date-filtered and read-only; optional CSV
export preserves the same access rules and mitigates spreadsheet formula cells.

## SPEAR state

Canonical main fetched: 14351db. This review branch includes existing #205 date
filters and #202's maintenance dependencies; those open PRs remain prerequisites.
No EARS/state helper is present; manual requirements/tasks/test evidence apply.
FIFO, price conservation, uncertain stock, idempotent replay, failed transaction
rollback, restart delivery and report authorization require automated proof.
Java/Bedrock client acceptance and canonical integration release remain separate.
No production writes, migration, payout, merge or deployment is authorized.

## Prove / engine / architecture / refinement

The allocation contracts prove FIFO splitting, exact payment conservation even
at Long.MAX_VALUE, insufficient-stock rejection and unknown contributors. Real
SQLite and disposable loopback MariaDB verify idempotent replay, rollback of both
receipt/lots after an injected write failure, retry and guild/date boundaries.
An outbox restart test retains failed deliveries and acknowledges successful
ones once. Inventory tests cover manual additions, unexpected deltas and shared
same-item containers. Command tests verify registration, membership/authority
rejection before query and CSV formula/quote escaping.

No pre-existing test result is described as historical red/green. These are new
contracts for approved behavior. Detekt findings were resolved through smaller
query/capture/transaction helpers without broad suppressions. Domain allocation
has no Bukkit or framework dependencies; platform adapters stay in infrastructure.
The PostShopTransactionEvent constructor remains unchanged; added provenance and
integer-payment getters preserve existing consumers.

Accounting observations include only completed events captured by this plugin;
a crash before capture or a failed append cannot invent historical attribution.
Forced pending files survive acknowledged capture and SQL failure. Partial
uncommitted temporary files are not successful captures. Reports are top 100
contributor/material rows and distinguish unknown stock; no historic backfill or
profit distribution is performed. Accounting audit/receipt tables retain new
records until a separately reviewed retention policy; existing shop-history
pruning does not silently delete this ledger. Operators should include the new
tables and outbox in ordinary backups. Exact native and local-suite totals are
recorded below; hosted CI and live Java/Bedrock acceptance remain separate.

This branch targets canonical main and includes #202's durability source and
PR #205's reviewed date filters. Those existing PRs originate from a fork, so their
branches cannot be used as a canonical-repository PR base. Both dependencies must reach canonical main before release. No
production migration, build upload, role deletion, merge or activation occurred.

### Local verification, 8 October 2026

`clean test shadowJar` on Java 25 / Paper 26.2 with a frozen actual Guilds
community artifact: **894 tests, zero failures/errors, 12 unrelated skips**.
The new MariaDB accounting and existing loopback XP contracts ran with zero
skips; the 12 skips are Docker-only historical/moderation profiles and one remote
authentication test. Detekt passed under supported analyzer Java 22; production
compilation remains Java 25. The paired compatibility attempt with a simultaneously
replaced artifact was invalidated and rerun with fixed artifact copies.
No result from that failed harness run is presented as a successful check.


### Hosted review refinement

[PR #207](https://github.com/BadgersMC/EnthusiaMarket/pull/207) pairs with Guilds
#215. Initial Codacy findings prompted smaller capture/delivery helpers, explicit
`range` subcommands and authenticated native test credentials. The local
MariaDB test requires `MARKET_ACCOUNTING_TEST_MARIA_USER` and
`MARKET_ACCOUNTING_TEST_MARIA_PASSWORD` alongside the disposable port; no empty
credential default is permitted for this profile. Its schema grant is restricted
to the random accounting-test schema prefix on loopback.
The final refinement clean full build passes 894 tests with the same 12 unrelated
skips; native accounting runs authenticated with zero skips. Detekt passes;
wiki lint passes. Legacy root documentation has pre-existing lint findings outside
the wiki CI profile; none are described as passing validation. Exact-head hosted
Codacy and maintainer-approved fork workflows remain independent gates.
