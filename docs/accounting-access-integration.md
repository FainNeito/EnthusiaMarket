# Stall accounting and access integration

## Spec

Integrate reviewed Market #207 (e1df70a) and #213 (8f32e0c) from refreshed canonical main 14351db. Preserve existing approved policies, payout amounts, own-guild XP exclusion, FIFO attribution and current access checks. A denied transaction must not transfer items or money, publish a sale, award XP or consume contributor stock. Preserve independent history, notification, accounting, access-refresh and trail lifecycle wiring.

## SPEAR state

Project-local EARS/state helpers are absent. This manual requirement/task/evidence record applies. A read-only merge-tree probe reproduced five conflicts: tasks, plugin lifecycle fields, trade constructor, shop commands and language keys. Resolve by retaining both features; tasks retain the newer accounting history evidence. No new visual behavior or community policy is introduced. Production remains untouched; client acceptance deferred.

- [x] Inspect current main and exact open PR heads; preserve unrelated dirty worktree.
- [x] Reproduce integration conflicts in isolated worktree.
- [x] Prove permission denial plus accounting/reward composition, complete combined build and architecture/static checks.
- [x] Publish reviewable combined source as Market #216 and inspect exact-head hosted checks/review findings.
- [ ] Maintainer review/merge, network pin reconciliation and separately authorized client acceptance.

## Verification and architecture

Combined Java 25/Paper 26.2 build: 942 cases, zero failures/errors, 14 external skips; test, shadowJar and JaCoCo pass. Both actual Guilds XP and alliance-runtime contract cases execute without skips against the pending Guilds #216 artifact (SHA-256 46210f7e4590b5470deed9da86fc3a44accbd59a4c198cae4fd98a0c69537c01). LayerRulesTest passes. Initial post-merge static analysis detects 24 shop-command functions; retain all command routes, inline two single-use/delegating helpers and move shared target resolution to a private infrastructure helper. The project enforces strictly fewer than 22 class functions. Java-22 Detekt passes after refinement. First refinement suite: 31 cases, zero failures/errors, four database skips. Final command regression suite: {'tests': 19, 'failures': 0, 'errors': 0, 'skipped': 0}. No new behavioral policy, payout, permission, menu layout or icon change is introduced.

Separate notification/history/accounting startup and shutdown fields are retained; both access policy and reward ports remain in trade DI. The positional test harness argument is replaced with explicit named ports. Access denial precedes persistence/payment/inventory/reward interactions; allowed sales retain reward prepare/payment/delivery/complete order. Accounting observation requirement is renumbered REQ-381 to avoid collision with notification REQ-345. Migrations V031-034 remain additive and distinct. Existing SQLite/outbox/accounting failure contracts execute in the combined suite; native MariaDB and server startup are not repeated or claimed in this rehearsal. Codacy MCP tools are unavailable here; local Detekt and exact-head hosted Codacy review remain distinct.

Final unmerged local review artifact SHA-256: 36a6ba690d4a8733afa064afe9b48890a99f67a58061d41af716c954e4cfbda1. Production unchanged; in-game Java/Bedrock acceptance deferred. Hosted workflows still pin the released older Guilds runtime; paired new-API release/pin reconciliation remains a required release gate, even if those hosted builds pass.

Published: https://github.com/BadgersMC/EnthusiaMarket/pull/216. Initial head f798bf2 is mergeable; build, Wiki Checks and PR quality diagnostics require maintainer approval (`action_required`); Codacy is running. No human review or inline finding at publication. A documentation-only follow-up records this status; recheck its exact head before reporting final hosted results.
