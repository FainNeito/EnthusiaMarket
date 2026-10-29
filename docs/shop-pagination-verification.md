# MARKET-15 — shop menu pagination

Base: canonical main 57704f1acfaf8aacfdd65cb4ae74c0f36b55712d, freshly queried 2026-10-29.

## Spec
WHEN an authorised player opens owned/edit, delete, bulk trust, or Bedrock owned shops, THE SYSTEM SHALL expose all eligible shops in ascending immutable shop-ID order in pages of 45, with Previous/Next and a current/total page indicator. Empty lists have page 1/1. Previous/Next are absent at boundaries.
WHEN the eligible list changes, THE SYSTEM SHALL refresh it on navigation/actions and clamp the requested page to its valid range. Captured shop buttons SHALL resolve by ID rather than a new list index.
WHILE choosing bulk trust targets, THE SYSTEM SHALL retain selected IDs across pages and remove IDs no longer eligible. Confirmation SHALL use the actual player as actor and recheck current authority per selected ID. Delete confirmations SHALL report success only on successful deletion.
WHEN use permission or shop authority is revoked, THE SYSTEM SHALL refuse the stale action. Java menus and Bedrock callbacks SHALL recheck use permission; owner-scoped menus SHALL reject a different player.

## Prove
Tests are authored before engine edits. Boundary matrix: 0/45/46/90/91; unordered inputs; negative/oversized page; all IDs reachable exactly once; changing lists; deletion clamping; selection across pages; permission revocation; stale snapshot buttons.
Gradle execution is BLOCKED before test discovery: wrapper download raises Network is unreachable, Gradle 9.1 unavailable, installed JDK 17 versus required 25. No red or green JVM result is claimed.

## Engine / architecture / refine
Implemented all four adapters with shared immutable-ID ordering and page calculation in interaction/ShopMenuPage, plus a shared Java footer. Bulk selected/armed IDs are retained only while currently eligible; final trust/delete still use the service's fresh per-ID authority checks. Added localized deletion failure feedback; success is emitted only on true. Bedrock stale deleted/unauthorized selections refresh and clamp.

Static architecture review: only interaction adapters/helpers, tests, one language key and docs change. No application/domain/storage/build files changed. Existing command permission enthusiamarket.shop.use is rechecked and existing canEdit/canDelete policies are retained. Adapter navigation performs fresh listing. Deferred shop actions use captured IDs and current rows. Framework API usage follows existing GUI patterns. Cumulus SimpleForm content/buttons, SimpleFormImpl.callResultHandler and ValidFormResponseResult.of were checked against upstream source at 22e1a25d9e326dcdfc1bc17403942a330213dd23; actual dependency compile remains pending.

Refine: split Bedrock response handling and Java icon rendering for bounded method complexity. Reviewed stale IDs, missing entries, owner mismatch, permission loss, failed deletion, bulk cross-page selection, boundary footer overlap and clamp behavior. Added 18 Kotlin tests (2 page-model, 11 Java, 5 Bedrock); these are authored, NOT executed. No independent reviewer or executable architecture result is claimed. No schemas, economy, stock accounting, dependencies, build configuration or existing PR223 changes permitted.
SPEAR command plugin, ~/.claude/CLAUDE.md and EARS validator are absent in this cloud environment. Manual spec/proof records are used without claiming command execution or validated requirements entries.
No AGENTS.md found in the cloud checkout or its workspace ancestors. Read docs/README.md, tech-stack.md, implementation.md, dev-setup.md and repository Codacy instructions. Codacy MCP is unavailable; no Codacy result claimed.

## Coordination
Local bridge owns restored Roundtable MARKET-15 (01a188e5-9ce2-772a-864e-1d0dcecd29a7), intent 2235, shop-pagination. Cloud checkout is isolated; no local source/build, shared TEST, clients, provider operations, merge, deployment or restart. Policy file remains local and must be relayed by bridge before shared capacity is requested.

## Current executable evidence and blockers

- git diff --check passes; language YAML parses; simulation JavaScript syntax check passes.
- Browser interaction observed cross-page bulk selection (IDs 1 and 46), successful simulated bulk confirmation, deletion of ID46 clamping 46->45 entries, and Bedrock navigation to ID91 on page3/3. These are simulation checks, not Kotlin or client tests. A 390px content-width inspection is available; no native-device acceptance claimed.
- Initial wrapper could not connect directly. Fixed cloud execution by deriving HTTP/HTTPS JVM proxy properties from inherited proxy variables and using the installed system Java CA trust store. No TLS bypass or dependency pin change.
- Installed official checksum-verified Gradle9.1 and Temurin25.0.5+8 in /tmp/market-toolchain. The Gradle plugin resolves with the proxy/CA configuration.
- Market configuration then fails before test discovery because com.github.BadgersMC.Nexus:nexus-permissions-gradle:057836b is absent from JitPack.
- Followed CI's supported local-Maven path: checked out Nexus057836befb9e35aa252cf90104030ec86f28b33f; publishToMavenLocal fails before compilation because its core module requires JDK21, which is not installed. This is the CURRENT build blocker. BUILD released to bridge; no next build without resource coordination.
- Downloaded approved LumaGuilds3.0.39 runtime; SHA256 verified against CI's 3CA71CC1ADC2B3B69565755D25F0A5A2241B52163E41A08B005F7C50ACBB65F1.
- Full tests, regression red/green execution, executable architecture checks, Detekt and shadowJar remain pending. Codacy unavailable; bridge may reset/review its MCP configuration if desired. No successful build or usable JAR produced.

## Visual review

Private HTTPS simulation: https://enthusia-market-pagination-review.awareyak.chatgpt.site
Source: [previews/shop-pagination.html](previews/shop-pagination.html). Synthetic shop item icons and fixtures; not live data/screenshots. Responsive Java grid, Bedrock list, current/total pages, selection persistence, delete confirmation/clamp, external access changes and use permission refusal. 390px toggle is a content-width simulation, not a device emulator.

## Remaining bounded handoff

1. Obtain matching current bridge ACK before shared GitHub push/PR/hosted CI or another heavy build.
2. Add verified cloud JDK21 without modifying repository pins; make Gradle discover both21/25; publish exact Nexus to cloud MavenLocal.
3. Run focused18 tests, full test suite (report skips), executable architecture, Java21/22 Detekt and shadowJar using -PuseMavenLocal=true and approved Guilds runtime. Fix real failures, then pin exact source.
4. Push a dedicated branch to existing FainNeito/EnthusiaMarket fork, create cross-repository PR against BadgersMC/EnthusiaMarket main, and inspect comments/reviews/checks on its exact head. Preserve draftPR223 (CHAPTER_FROZEN); this patch is pagination only. Fork write permission verified; canonical push permission is absent.
5. No merge, plugin deployment, production reload/restart, TEST or client operation is authorised or performed.
