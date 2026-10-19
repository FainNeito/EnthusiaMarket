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
