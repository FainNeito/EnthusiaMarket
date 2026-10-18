# Stall locator navigation — REQ-386/387

## Spec

Authoritative base: BadgersMC/EnthusiaMarket main 57704f1, fetched 2026-10-18. Isolated branch agent/market-stall-locator. TEST currently runs Market 1.0.53, Paper 26.2 build 128 / Java 25, PacketEvents 2.14.0 and Guilds 3.0.0-shop-api-integrated-UNMERGED-TEST. Preserve that companion build and all TEST data. User authorized building and deploying this feature to TEST; production operations and merging remain outside scope.

SPEAR spec -> prove -> engine -> arch -> refine. No project-local EARS validator or state helper exists; maintain this manual requirement/task/evidence record. New-feature tests establish current behavior, not historical production failures.

Use installed optional PacketEvents through its supported API, isolated from platform-free tracking. No synthetic entities, global locator gamerule changes, attributes, player-location broadcasts or forced chunk loads. Fixed shop position is transmitted only to the selected recipient, with plugin-owned identifier and explicit cleanup. Unsupported/missing packet support or Bedrock falls back to particles. Use the exact existing TEST stall artwork in a separate namespaced Nexo external-pack add-on. The client uses a fixed 9x9 waypoint slot; pack and appearance need live verification.

## Tasks and evidence

- [x] Inspect canonical main, existing tracking/outline architecture and actual companion/runtime versions.
- [x] Specify privacy, optional dependencies, resource-pack fallback, preferences and lifecycle boundaries.
- [x] Prove tracker, private transport, session cleanup, config roundtrip and preference behavior.
- [x] Implement and run canonical full tests, shadowJar, architecture and static checks.
- [x] Publish source PR #221 and inspect exact-head checks/comments; refine actionable quality findings.
- [x] Back up TEST, deploy explicitly labeled unmerged test artifact plus pack add-on, activate TEST and verify startup and downloaded artwork.
- [ ] Observe selected waypoint appearance/privacy in the native client; ordinary-player permissions remain deferred.
- [x] Update interactive preview to match implemented behavior and provide mobile link.

Existing preview: https://enthusia-stall-locator-preview.awareyak.chatgpt.site (private; ChatGPT login). A simulated HUD with actual TEST artwork and inspected Java 26.3 geometry; this is not native client acceptance.

## Local proof and architecture

Final Java 25 / Paper 26.2 test, shadowJar and JaCoCo run: 1,032 tests, zero failures/errors, 14 existing external-environment skips. Architecture tests execute in that suite. Analyzer Java 22 Detekt passes. The final build uses the exact locally retained installed Guilds integration artifact (source 580b1fb, SHA-256 6f8751d1222773628bf1287329c29281213f116aa645157e255d6f6879ceedd1), preserving its API-registration and prior TEST features. Initial compilation and focused checks also used the approved release 3.0.39 classpath; hosted CI retains its independently pinned release profile.

Focused evidence covers private recipient/identifier ownership, replacement/untrack/no repeat packets, negative block-coordinate flooring, white custom tint/vanilla gold, real client-version gate and injected Bedrock identity, disconnected recipients, loaded-pack/invalid-style fallbacks, preference PDC semantics, Nexus config persistence, arrival sound once, expiry/disable and quit/death/world/cancel/shutdown cleanup, no attribute access, existing menu callback behavior and loaded-chunk particle constraints. Six initial fixture failures were relaxed MockK generic PDC return values, resolved with explicit null stubs; one added Floodgate fixture could not proxy a legacy Cumulus type, so its identity input is injected while the real version gate remains exercised. These were fixture limitations, not historical production regression claims.

Tracker frames expose destination/arrival state without platform imports. Optional packet types remain in the infrastructure adapter. Labels are captured once on selection. Existing direction/outline budgets remain bounded; locator-only sessions may reserve unused direction samples within the same conservative global cap. No per-render SQL or forced chunk loads. Bedrock uses existing particle/action-bar fallback. Mode preferences are a namespaced player PDC value and do not alter permissions.

TEST candidate: `1.0.53-stall-locator-UNMERGED-TEST`, SHA-256 `493cf85551e337b05b6ddee89f59bfe3ae67b596ab3e9a7c578a842300b358ae`. Unmerged/local testing artifact, not a production release. Hosted checks, TEST activation and native client appearance are separate evidence gates.

## Hosted review and TEST refinement

PR #221 source 0cf5b41 passed hosted build, diagnostics and security. Codacy reported three maintainability findings in HUD rendering (method length and two complexity limits). Extracting transport failure handling, arrival feedback and distance status resolves those findings without changing guidance behavior. The refined full Java 25 suite again passes 1,032 tests, zero failures/errors and 14 unchanged environment skips; Java 22 Detekt passes. Refined TEST artifact: `1.0.53-stall-locator.2-UNMERGED-TEST`, SHA-256 `3d82d7ed36c795bde8af9176b3b508c8daddb1c80eacddb1c3d1707ed26aa813`.

The first candidate activated successfully on TEST with the Guilds integration preserved and no Market locator startup errors. Offline backup was saved before activation. Full remote JAR download is blocked by the download host's TLS failure; upload/remote size and all 85 changed archive entries were verified via authenticated TLS readback. The actual Java client downloaded the generated 5,228,786-byte pack: the custom style points to `enthusiamarket:stall`, and the icon bytes match the original artwork hash exactly after Nexo SIMPLE obfuscation. Native `/shop trail mode both` and `default` commands succeeded; the temporary mode override was removed. FainNeito reconnected to TEST. FainNoir was offline; the temporary proxy reconnect grant was removed.

Native marker appearance/privacy through an actual selected search-result guide remains a separate acceptance gate; packet/session tests, pack download and successful startup do not claim that observation. Ordinary-player permission testing remains deferred. The interactive mobile preview now matches the implemented gold/white/yellow destination text and 9x9 icon geometry; it remains a simulation and requires ChatGPT sign-in.

Final TEST activation: source ab167a6, artifact `.2` / SHA-256 `3d82d7ed36c795bde8af9176b3b508c8daddb1c80eacddb1c3d1707ed26aa813`; offline rollback archive `archive-2026-10-18T205221Z.tar.gz`. Startup on 2026-10-18 20:53 UTC confirmed the expected version, server ready, no pending update, unchanged Nexo settings, preserved Guilds integration build, regenerated pack and no Market locator errors. FainNeito was returned to TEST. Production was not changed.

Codacy's remaining function-length notice included the following helper in an expression-bodied try function. An explicit function block fixes the boundary. The full `javap -c -p` executable disassembly of FinderNavigationHud is byte-for-byte identical to the activated `.2` artifact; the final source change affects syntax/debug metadata only. Focused finder tests pass again with no failures/skips. TEST retains the fully tested `.2` artifact and its recorded source/hash; this syntax-only refinement does not require another activation. Final hosted status is recorded in the PR handoff separately.
