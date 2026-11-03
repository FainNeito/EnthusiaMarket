# Owner-shop display correction — 2026-10-31

## Spec and prove

REQ-395 follows real FainNeito TEST acceptance recorded in Roundtable2977.
The owned-shop tooltip showed `TextComponentImpl` for Active/Frozen and
`2147483647 trades` for a normal BUY shop. The saved native failure is
[screenshot34](https://drive.google.com/file/d/1ANRPNtHaXwL-aF6Qn-XnuKi9vdex0RuT/view).

Fetched authoritative BadgersMC/EnthusiaMarket main57704f1; no new base commits
since the ongoing PR226 candidate60031a6. Created an isolated worktree from
current main, deepened shallow history to establish ancestry, then fast-forwarded
to the ongoing candidate. Existing checkout preserved. Roundtable2980 records
source-only ownership; no TEST or client resources were claimed.

Four actual MockBukkit GUI tests with real Nexus LangService and bundled locale
failed before implementation: default and overridden statuses exposed component
internals, and BUY/admin availability exposed the sentinel. Red XML retained in
local `build/owner-display-evidence/red.xml`. After implementation all four pass,
plus existing pagination and locale tests (16 focused cases, no skips).

## Engine and architecture

OwnedShopsMenu serializes localized status components to MiniMessage before
supplying the existing parsed `frozen` placeholder. This retains configured
status styles and outer templates; it avoids the pinned LangService calling
Object.toString on a component. Verified that contract against its installed
bytecode and real locale regression. Existing partial language overrides retain
their values and receive the new bundled availability defaults.

Admin shops display localized Unlimited stock. Normal BUY entries explain that
capacity and funds are checked when trading; normal SELL/TRADE entries retain
complete-stock division. Internal sentinel values remain unchanged for existing
search filtering and ordering. Changes are confined to the GUI adapter, locale,
regressions and documentation: no schema, economy, provider, companion API,
permissions or menu-navigation changes.

Repository-local EARS validator and SPEAR state helpers are absent. Requirement,
task and this manual evidence record retain spec/prove/engine/arch/refine scope.

## Interactive preview

[Tap through corrected owner tooltips](https://enthusia-market-native-fixes-review.xxnatetheprogamerxx.chatgpt.site/owner-display.html).
Select SELL, BUY, empty TRADE or admin entries, switch Active/Frozen and compare
the reported defect. This is a simulation with schematic icons; clicking a shop
in Minecraft still opens its existing editor. No server data is changed.

Local browser interaction verified BUY text, both statuses, admin Unlimited,
TRADE zero stock and before/after comparison. At390px and320px there was no
horizontal overflow; alternative entry buttons are46px high. Saved preview
source46612138e176cfb6303c07a6c4621e2222a1ed45 was published successfully to the
existing owner-private Site. The hosted link reaches its ChatGPT sign-in gate;
authenticated mobile-device rendering is unverified. Native corrected-build
acceptance remains pending. The screenshot is preview evidence, not a live
Minecraft capture.

## Delivery boundaries

Local Java25/Paper26.2 full suite: 1,154 cases, 1,140 executed, zero failures
or errors, 14 skips (13 Docker MariaDB cases and one opt-in authentication
case). All four new tests, the 11 pagination cases and architecture rules ran.
Checksum-verified released Guilds3.0.39 SHA256
3ca71cc1adc2b3b69565755d25f0a5a2241b52163e41a08b005f7c50acbb65f1
backs the unchanged companion runtime contract. The local Maven Nexus cache
profile is separate from the exact-ref canonical CI build. Local shadowJar
1.0.53-owner-display-UNMERGED-LOCAL-TEST SHA256
1c53ef3abc335d0f42c42c01ab4066f70e8c69d9e05157528ecefa05bff3069b
is a dirty-source review artifact, not a deployable release.

Java22 Detekt completed with three existing findings in untouched files:
ContainerTradeService LargeClass607/600, ShopCommands TooManyFunctions27/22,
and PurchaseMenu render LongMethod60/60. All three files are byte-identical to
pre-fix60031a6; no finding names a changed GUI or test file. This is a failed
whole-project check, not a clean static-analysis claim. Required exact-head
hosted checks/review remain separate and are recorded in the PR handoff. A local labelled test build is not a canonical release. The guild-ledger
system-name/debit-sign observation is being handled by the existing Guilds owner
under separate source/build acknowledgment2987. No Guilds source was edited here.
No merge, deployment, restart, production operation or additional client input.
