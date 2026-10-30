# MARKET-14 first readable stall-access label patch

Base: fresh canonical main57704f1acfaf8aacfdd65cb4ae74c0f36b55712d. Isolated branchcodex/readable-stall-access-labels-20261029; pagination61513733 remains separate and clean.

## Spec
WHEN a player views stall settings THE SYSTEM SHALL show localized friendly sections, capabilities, effect names and Allowed/Blocked states instead of internal routes, enum identifiers or booleans. WHEN identity is unavailable THE SYSTEM SHALL show a neutral distinguishable unknown entry while retaining immutable action IDs. Display names SHALL be literal text, with control/legacy formatting removed. Unknown sections SHALL retain the existing visitor behavior and a friendly visitor title.
WHEN a setting operation fails THE SYSTEM SHALL show a safe localized uncertain-confirmation message and retain the complete failure/context in staff diagnostics. Constructors, command routing/options/permission annotations, policy checks, revision/durable mutations and projection behavior SHALL remain unchanged.
No new blocking offline/name-service lookups. The original Bukkit offline-name path is retained so known offline players remain identifiable; only actual name misses use numbered unknown fallback. Existing synchronous disk-I/O debt is explicitly deferred; no new lookup is added. Allied names are captured from the existing listing and used in selected-page titles, with a neutral fallback for unavailable direct routes. A current page may retain its last listed name until revisiting the list; identity/permissions never depend on that display name.

## Prove
Focused helper tests are authored before engine implementation: all11 capabilities, effects including unknown keys, booleans, unknown/known-offline/renamed/deleted identity inputs, unknown sections, literal hostile markup and legacy/control sanitization, localization substitution, and safe error text. Full JVM/GUI tests require a later bridge grant and hosted toolchain. No executable Gradle red/green is claimed.

## Engine / architecture / refine
Implemented plain-text labels with a pure StallAccessLabels helper and a small StallAccessUi adapter bound to the existing LangService/logger at startup and reset on disable. Constructor signatures and routing/permission annotation values remain exact. The startup binding avoids changing the existing command/menu constructor contract; it adds no background worker or identity cache. All dynamic names remain literal Component.text values, with legacy formatting/control characters removed.

Selected ally titles use names from the menu's existing guild listing snapshot; no extra guild lookup is performed for a title. Known offline blacklist names retain the original lookup path and lookup count. The pre-existing synchronous offline-name disk-I/O debt is deferred; this patch does not claim all lookups are nonblocking. Unknown entry numbers identify current list positions and may shift after removal; callbacks retain the actual UUID.

Setting failures log the Throwable plus stall/actor context at WARNING and return a localized uncertain-confirmation message to players. No claim of successful rollback or failed commit is invented. Original service edit, current ownership/rank checks, revision handling, blacklist mutation and WorldGuard projection calls remain unchanged.

Static checks: YAML parses; all11 actual StallCapability enum entries have explicit localized labels; constructors and exact routing/permission annotation values match base; offline lookup count remains one per blacklist entry; diff whitespace check passes. No application/domain/persistence/economy/build dependency source changed. Existing plain-text locale fallback handles missing keys.

Actual helper proof:68 assertions PASS. Cached Kotlin2.2.0 from verified Gradle9.1 distribution, verified JDK25, compiler heap192MiB/metaspace128MiB/30s and harness64MiB/metaspace64MiB/10s, one CPU/one JVM at a time. Compilation4.356s, assertions0.101s; exits0/0. Actual source SHA25621dd6639764237f9cb4e2465bcb5af8576b4cbbdf469d08d47a1920a7eb8918f unchanged during proof. This validates only the pure helper, not the repository's Kotlin2.3.20/JVM25 production build or framework integration.

Nine focused test methods are authored (seven label tests and two locale/diagnostic adapter tests); zero executed. GUI/native acceptance, full tests, executable architecture, Detekt/Codacy and shadowJar remain pending a later authorised hosted validation. No Gradle/Nexus/DB/CI/full-JAR operation started. Codacy MCP is unavailable in this environment; no analysis result claimed.

Browser review of the labelled synthetic artifact: known-offline name preserved; unknown entries numbered; hostile <red> name remains literal with zero interpreted red elements; friendly guild title retains stall context; all11 allied capabilities start Blocked; a policy toggles to Allowed; simulated save failure shows safe uncertain-confirmation text; revoked manage authority closes the simulated menu. Native acceptance remains pending. Representative effect fixtures are explicitly not a live registry snapshot. SPEAR tools/EARS validator absent; manual evidence records used. No requirement file or dependency pins changed. No auction/accounting/CSV/trade/compensation changes; no PR220 routing annotation change. No shared build/TEST/client/GitHub publication operation authorised.

## Visual artifact and handoff

Private responsive descriptive simulation: https://enthusia-stall-access-labels-review.awareyak.chatgpt.site
Source: docs/previews/stall-access-labels.html. Existing six-row inventory and sequential controls are preserved; a labelled descriptive mirror is added only in the simulation for touch/keyboard review. A390px content-layout toggle is not a device emulator. Previous/Next remain the existing always-present clamped controls; this patch does not redesign pagination.

Source review specifically retains which-stall context in titles, known-offline name behavior and staff diagnostics. Other MARKET-14 audit findings remain deferred. TrustManageMenu captured-row concern is legacy/source-level pending reachability proof, not a confirmed current exploitable player flow; no trust mutation change here. MARKET-15 pagination source/export remains at61513733 separately.

No GitHub branch push, PR creation, hosted job, TEST/client action, plugin deployment, merge, reload or restart. Draft PR metadata/export is prepared solely for review and a later matching bridge grant.
