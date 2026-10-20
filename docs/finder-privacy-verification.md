# Stall-only locator privacy

## Spec

2026-10-19: user reports an indefinitely visible player locator. Idle clients must receive no tracking waypoints. Only an explicitly selected Market destination may be tracked; player markers must remain hidden during and outside navigation. Existing arrival, timeout, stop, replacement, world/death/quit and shutdown cleanup remains required. Mode selection alone must not start guidance.

Current canonical main 57704f1 was fetched and inspected; isolated worktree fast-forwarded to ongoing PR 221 head 7dd600a. Preserve separate access repair PR 222 and TEST integration. No production change or merge authorized. No project-local EARS/state helper exists; this is the manual SPEAR record.

## Prove

The existing transport sends only its own waypoint but never filters native tracking packets. Its absence of a privacy listener leaves native player tracking independent from guide cleanup. Four new listener-contract tests failed with a deliberately pass-through initial implementation (four assertion failures, no errors); this is new behavioral proof, not a claim of historical red tests. Regression tests exercise real outgoing listener decisions for idle, active, cross-viewer, update and cancellation states.

## Engine / architecture / refine

FinderWaypointPrivacy owns a concurrent per-viewer destination allowlist at the optional PacketEvents infrastructure boundary. It permits removals, blocks all nonselected tracking and fails closed on unreadable waypoint packets. Bukkit/session state stays off the packet thread. Startup clears online-player identifiers without sending locations; shutdown removes owned destinations before unregistering. Failed sends remain eligible for cleanup and retry. No world gamerules, attributes, permissions, database, companion API or world entities change. PacketEvents 2.14.0 is the installed/compiled transport; missing-adapter fallback cannot enforce native waypoint suppression and is explicitly documented.

Java 25 test/shadowJar/JaCoCo: 1,052 tests, zero failures/errors, 14 unchanged external-environment skips; architecture checks pass. Final focused finder/search suite after send-retry and shutdown refinements passes; Java 22 Detekt and git diff whitespace checks pass. Compiled against the retained TEST Guilds b401e3d6 artifact. No local EARS/state tooling exists.

Updated private HTTPS preview: https://enthusia-stall-locator-preview.awareyak.chatgpt.site (owner sign-in). Start/cancel/expiry/observer interactions were checked at a 390px mobile viewport with no horizontal overflow. The preview defaults to idle, uses the ordinary XP bar outside navigation and includes a selected-stall-only native HUD composite. It remains a simulation. The standard source helper pushed commit 554f2c0; its shell packager requires unavailable Bash, so the same static build preparation helper and a validated dist tar archive were used to finish publication. Native deployment reports success.

This source refinement is for review in ongoing PR 221, not merged production. Updated TEST activation and actual native client acceptance remain separate from these automated and preview checks. Production remains unchanged.

## REQ-387 cancellation command dispatch (2026-10-20)

Spec: `/shop trail stop` must cancel the selected guide without a second argument. The existing mode commands, permission gate and player-only effects must remain intact. This is a command adapter correction, with no visual, persistence or navigation-policy change.

Prove: the coordinator's native TEST acceptance on version `1.0.53-stall-only-locator-UNMERGED-TEST` recorded `/shop trail stop` rejected as unknown/incomplete at 09:18:28, `/shop trail mode both` accepted at 09:18:45, and `/shop trail stop unused` accepted at 09:19:53 with the selected marker and outline removed. These reported client observations isolate argument dispatch from the existing cleanup handler. Source review of pinned Nexus `057836befb9e35aa252cf90104030ec86f28b33f` confirms its Paper registrar attaches execution only to the final declared argument and extracts every argument; Kotlin default values and optional metadata do not produce optional Brigadier branches.

Engine/architecture: replace the generic `trail <action> <mode>` adapter with explicit `trail stop` (no arguments) and `trail mode <mode>` leaves. Both retain `enthusiamarket.shop.use`, the player guard and the existing finder service. No Nexus dependency, command root, menu, schema, privacy filter or lifecycle implementation changes.

Refine: `ShopTrailCommandRegistrationTest` scans the actual `ShopCommands` definition and invokes the pinned registrar's private tree builder before executing Brigadier commands. It covers the advertised stop syntax, all four preference values without guidance start, required mode values, rejection of extra stop arguments, permission denial and console isolation. Reflection is confined to tests because the real registrar does not expose its builder; no duplicate command-tree implementation is used.

The coordinator ran the unchanged regression on Entity in the isolated `EnthusiaMarket-trail-stop-20261020` checkout. Baseline PR 221 head `6a6974729c541dd095d7d0fd36f23492ea6d9de2` plus the test produced five tests, three expected failures, zero errors and zero skips (XML timestamp `2026-10-20T13:27:58.987Z`, 125.9 seconds). Both advertised and console stop commands were incomplete at position 15; the extra-argument test failed because the old parser accepted `stop unused`. Applying the adapter correction produced five tests, zero failures/errors/skips (`2026-10-20T13:28:46.783Z`, 45.0 seconds).

Both runs used Java 25.0.3, the supported `-PuseMavenLocal=true` profile and Nexus source `057836befb9e35aa252cf90104030ec86f28b33f`. The existing Guilds compile/test companion was built from `694bb5ff53b8d68c5cb644b6100b7d87f0ce0e2d`, SHA-256 `890ec938fdaef552a46334b4673be689e3e7c6f0349cfa7e299cb3abf26d0aa6`. This is focused regression evidence, not a repeated full suite or live companion parity claim. Independent source review reported no blocker. Final published-head CI and native client acceptance of a rebuilt TEST artifact remain open; no merge or deployment is claimed here. Project-local EARS/state tooling remains absent; no validator success is claimed.
