# Market release acceptance reconciliation

Evidence reviewed on 2026-10-30. This ledger separates automated, historical
native and current native evidence. It does not approve a production rollout.
Canonical main was freshly fetched as57704f1acfaf8aacfdd65cb4ae74c0f36b55712d.
The acceptance reconciliation itself changes documentation only. The separately
proved REQ-394 command correction is tracked in history-command-verification.md.
Project-local EARS/state helpers are absent, so SPEAR records are maintained here.

## Candidate and activation

- PR226 behavior source:615fff9eccfe58bf80398bfc1c9a1732ee44d0e4.
- Canonical run41115116315, artifact13004399521; required build, security,
  diagnostics and Codacy checks passed. CodeRabbit skipped the draft; this is
  not an independent review. Human review and merge remain separate gates.
- CI synthetic checkout934279ee1ec157e826aed43b0775598237b6b170 has the same
  tree80ea7c942e92e9f084ed733f9a7d8faad49bbfdf as that PR head.
- TEST enabled version:1.0.53-market-ui.934279ee1ec1-UNMERGED-TEST.
- JAR SHA-256:2f9582f3c57fbc9041556dfbbbe850aa1f0740539d6523ed73d78a1ab21f20ff.
- Activation2556 used a verified offline backup, upload/readback, exact old-JAR
  retirement and one TEST stop/start. A fresh runtime receipt confirms the hash.
  Guilds PR19, Staff and TestBridge hashes were unchanged; startup completed
  without linkage errors. The existing nonfatal Nexus loader warning remains.
- Original database bytes and all 33 table fingerprints matched after activation
  and the canceled client check. Configuration remained unchanged, with stall
  resets disabled. Backup and detailed receipts remain in the private local
  bridge and cloud evidence; raw databases/player records are not committed.

## Completed native slices

| Slice | Observed result | Boundary |
| --- | --- | --- |
| Management corrections2529 | Bare delete opens; empty BUY filter closes prior SELL results; Selected/Not selected labels display | Menus canceled; no deletion or trust confirmation |
| Pagination2549 | 91 entries, 45/45/1 pages; editor/delete/trust forward and backward navigation; trust drafts retained across pages | Temporary metadata fixture only; all 90 added rows removed and all original table fingerprints restored |
| Empty search2549 | Matching zero-stock raw-gold query and absent netherite query send feedback without opening empty menus | Distinct from empty direction filtering |
| Trust shorthand2556 | Bare player-name route opens the existing trust menu on FainNeito | Canceled; no trust write, ordinary-player or confirmation-persistence claim |
| Finder2562 | Mode-only BOTH leaves client idle; bare stop while idle is accepted; user Shift-click from distant location reports too far/other world and displays coordinates | Does not establish an active guide, player-marker suppression, timeout or arrival |

Earlier native sessions also demonstrated ownership revocation/reconnect,
single/bulk trading, personal rent warnings, invisibility suppression/restoration,
allied-entry default/config persistence and guild-bank/XP commerce. Those passes
remain historical evidence of their specific builds and companion pairings,
not current PR226/PR19 acceptance. Their limitations were reconciled in the
private bridge acceptance audit of 2026-10-29.

## Automated evidence on the activated behavior source

The verified canonical manifest reports 1,147 cases: 1,146 executed, zero
failures/errors and one unchanged opt-in remote-authentication skip. Relevant
suites below all executed without skips. Counts are not additional test runs.

| Boundary | Verified suite counts | Native limit |
| --- | --- | --- |
| Container transactions | ContainerTradeServiceTest26; ContainerTradeServiceTradeTest7; GuildSaleTradeBoundaryTest7 | Mocks do not establish live inventory/currency settlement |
| Reward recovery | GuildSaleRewardDeliveryTest11, including lost acknowledgement, provider failure, frozen chapter and ordered replay | Does not establish installed companion failure injection or a current live restart |
| Installed API shape | GuildShopXpRuntimeContractTest1 | CI companion profile is not proof of every installed companion behavior |
| Sale accounting | StallSaleAllocationTest2; SQL accounting2; native MariaDB accounting1 | Nonzero tax/cap/concurrency live cases remain separate |
| Ownership/access | Region access6; access resync5; legacy reconciliation2; guild-shop access6 | Two-client current-build enforcement remains separate |
| Finder privacy/rendering | Waypoint privacy7; locator7; glow6; HUD5; tracker5 | Real appearance, two-client privacy and active lifecycle need native checks |
| Management/search | Pagination11; stock feedback9; finder-menu3; trust registrar4 | Automated Java/Bedrock adapters are not a Bedrock client pass |
| Rent warnings | RentLoginWarningServiceTest6 | Guild-bank insufficient-rent and provider edge cases need native acceptance |

## Remaining gates

1. Current paired transaction checks: last-item/bulk delivery, stock refresh,
   insufficient funds, guild accounting/XP and applicable tax/cap/failure/replay
   cases. Keep FainNoir-dependent actions on hold pending Staff-session resolution;
   FainNeito's separate Staff inventory also needs an agreed fixture and preimage.
2. Current active finder lifecycle, observer privacy, glow appearance, wrong-world
   refusal and 60-second expiry. Configured route nodes are empty; no terrain
   survey or route configuration is claimed. The available client API cannot hold
   Shift while clicking, which the admin result-selection route requires.
3. Selected/unselected ally, blacklist, capability and potion lifecycle edge
   enforcement. Current owned-stall fixtures must be confirmed before changes.
4. Ordinary-player permission acceptance remains deferred. A native Bedrock
   client is still needed for its acceptance; automated forms are not a substitute.
5. Independent review, maintainer merge, owning network pins and a canonical clean
   merged release build precede separately authorized production deployment.

No reviews/ratings, stall resets, production mutation, Staff recovery, or automatic
permission/session changes are included in this reconciliation.
