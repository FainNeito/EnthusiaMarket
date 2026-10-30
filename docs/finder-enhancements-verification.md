# Finder enhancements — 18 October 2026

## Spec

REQ-388/389. Canonical main was fetched twice and remains 57704f1; ongoing isolated locator branch extends PR 221 without replacing existing work. Production unchanged. No local EARS/state helpers exist; this record tracks SPEAR manually. Operator configuration: finder-enhancements.md.

## Prove

The initial explicit-route run used an empty route planner: two cases, one failed (linked corners missing). After implementation, both passed. The real Nexus route-object save/load test failed with ClassCastException because nested entries reload as maps; the adapter now explicitly validates YAML maps. All four configuration tests passed after this fix. A tightened pulse test then reproduced the default 500ms cadence redrawing most positions in the same one-block lattice (two cases, one failure). Clustered moving pulses remove that alias. These are recorded new regressions; no historical red/green is claimed for pre-existing locator behavior.

## Engine and architecture

Bounded platform-free route graph and pulse/beam planners; a Paper adapter owns private nonpersistent display lifecycle. Displays are hidden before insertion, not collidable or saved, and only shown to the recipient. Complete shapes must fit both caps; loaded beam bounding chunks are checked without loading them. Spawn failures remove partial scenes; displays are cached and owned UUIDs are excluded from stall limit rescans. No scoreboard teams, other entities, permissions, schema, shops, rent or companion JAR changes. Optional Floodgate detection is null-safe; Bedrock retains particles.

## Refine and delivery boundaries

A clean Java 25 / Paper 26.2 build passed 1,043 cases, with zero failures/errors and 14 existing external skips. The final full Java 25 build, including route serialization and stronger pulse proof, passes 1,045 tests with zero failures/errors and 14 unchanged skips; shadowJar, JaCoCo and architecture checks pass. Analyzer Java 22 Detekt passes. TEST artifact version 1.0.53-finder-glow.2-UNMERGED-TEST, SHA-256 c29a81787639b6382ff82b703ca2928cf1fae1b1ebf9f42df4bd54627ef9963c. Architecture checks execute in the full suite; analyzer Java 22 Detekt is required. Build uses the retained installed Guilds integrated TEST artifact (source 580b1fb; SHA-256 6f8751d1222773628bf1287329c29281213f116aa645157e255d6f6879ceedd1), preserving the provider and prior TEST features.

The interactive schematic is mobile-tested at 390px, owner-private and accessible through https://enthusia-stall-locator-preview.awareyak.chatgpt.site with ChatGPT sign-in. It exposes guide stages, pulse stepping, intensity, glow/particle fallback, explicit routes and observer view; it is not a live client recording. The native locator artwork preview remains available.

Final exact-head hosted checks, review, source/JAR SHA, offline backup and TEST startup evidence are recorded in the PR handoff and private operations receipt. Native glow appearance, observer privacy, a real walkway-node survey and ordinary-player permission acceptance remain separate gates. Route nodes default empty; no terrain route was guessed or installed. No merge or production deployment is authorized or performed by this work.

Hosted refinement: six initial notices (Markdown heading spacing, two argument-count limits and a lambda complexity boundary) were corrected by formatting, grouped settings/search state and named function references. The refined `.2` source again passes all 1,045 tests, architecture, shadowJar/JaCoCo and Java 22 Detekt. The initial `.1` candidate was staged only, never activated; `.2` activated on TEST with offline backup archive-2026-10-19T031033Z.tar.gz, expected startup, preserved Guilds/Nexo and no Market errors.

Final helper refinement: an explicit spawn helper block and a separate scene-render helper correct Codacy grouping the function references with following code. All 42 focused finder tests and Java 22 Detekt pass afterward. Review artifact `.3` SHA-256 940e47fab61258cf5bacbccc10652d8dfec84108bdeac7aa69f2f5601850659c; final exact-head hosted build and TEST receipt are tracked in the PR handoff.
