# Accepted transaction-history date filters

## Spec

User accepted checklist feature 8 on 8 October 2026. Canonical BadgersMC/EnthusiaMarket main was fetched at `14351db4dc416138341a11d0e4e27602f207221b`; clean isolated branch `codex/market-history-filters` starts from it. REQ-356/357/358 preserve `/shop history [page]` and add `all`, `today` and `range YYYY-MM-DD YYYY-MM-DD`, each with optional page. Both range dates are inclusive in the server timezone used for displayed timestamps. All means retained rows. Retention, unread state, owner-or-buyer visibility and permissions stay unchanged. No export/contributor feature, stall reset or production action.

## Prove

After aligning JUnit to the existing maintenance branches' BOM and registering the unrelated website argument in the scanner fixture, the original command scanner test compiled and executed one case, failing specifically with `Missing today filter`. Earlier discovery/fixture setup failures are not behavioral regression evidence. The focused green suite ran 16 cases without failures/errors/skips: command registration, command routing/validation/paging, calendar boundaries and existing/new SQLite repository cases.

## Engine and architecture

ShopHistoryWindow is a domain value; application ShopHistoryDates computes strict ISO calendar boundaries with an injectable clock/zone. Infrastructure owns SQL and player messages. SQL applies `(owner = ? OR buyer = ?)` and timestamp predicates before LIMIT/OFFSET, and orders timestamp/ID descending for stable ties. Existing repository methods/signatures remain; the new overload rejects unsupported adapters rather than silently ignoring the filter. No migration or data rewrite is needed. The read command keeps its existing synchronous query contract; this feature does not claim new outage/performance guarantees. Main-thread Bukkit name lookup and messaging are preserved.

At most eleven rows are queried; the page range remains 1 through 1000. Links preserve explicit all/today/range command arguments. Invalid, impossible or reversed dates are rejected before SQL. Calendar conversion handles 23/25-hour DST dates and leap days. No history read marks notifications delivered or resurrects pruned rows. Existing header/transaction locale overrides are preserved; new filter/status keys use bundled fallback behavior.

## Refine

Clean Java-25/Paper-26.2 `test shadowJar` after the adapter refinement passed **788 cases, zero failures/errors, eight external-resource skips**. Architecture checks are in the suite. Seven skipped cases require container-backed MariaDB; one is remote authentication. The new MariaDB filter case was executed separately against disposable native MariaDB 11.8.3 with one case, zero failures/errors/skips; it verifies real SQL privacy, start/end boundaries, stable ties and paging. Its fixture tests the query table contract, not a complete release migration. Temporary native launcher was removed after use. Java-21 Detekt passed after extracting the history adapter, rather than suppressing the command-class method-count finding. Wiki frontmatter checks, 31-page Markdown lint, strict MkDocs and language YAML parsing passed. No project-local EARS/state helpers exist; these requirements/tasks/evidence are manual records, not a validator pass.

Local unmerged review JAR SHA-256: `241a73731e4a377c065549741a6db9a5c8c204d6142025842c78b08c4c953970`. This is not a production release. Documentation, import placement and test placement were adjusted to avoid conflicts with pending maintenance work; engine behavior is unchanged by that relocation. Hosted checks/review, canonical/network release, activation and real Java/Bedrock acceptance remain separate.

## Publication and integration rehearsal

Published as [Market #205](https://github.com/BadgersMC/EnthusiaMarket/pull/205), independent of the pending maintenance stack. A local source merge rehearsal with #197/#198/#199/#200/#201/#202/#203/#204 merges without remaining conflicts and passes **899 cases, zero failures/errors, thirteen external-resource skips**. The prepared Guilds runtime contract executes without skips. New requirements/tasks, imports, test placement and wiki additions were placed to avoid overlapping pending changes rather than adding those dependencies to this PR. This is component integration rehearsal, not a network release or production/client proof.

Hosted build/quality/wiki workflows require maintainer fork approval. CodeRabbit skipped automatic review. Fresh exact-head Codacy and human/inline findings are inspected separately and recorded in the PR description. No deployment, migration, database write to production, world edit, reset or Discord post occurred. The disposable native test database was shut down.
