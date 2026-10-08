# Private shop-finder trail

REQ-370: WHEN a player selects a search result THE SYSTEM SHALL offer a private particle direction trail to that shop, replacing the player's prior trail without changing existing admin teleport behavior.

REQ-371: WHILE a trail is active THE SYSTEM SHALL bound lifetime (default 60 seconds), range, active trails and render budget, and SHALL stop on arrival, cancellation, disconnect, death, world change or plugin shutdown.

REQ-372: WHEN trail particles are rendered THE SYSTEM SHALL use only loaded chunks, never force-load terrain, teleport a normal player, perform per-tick SQL or advertise a straight direction guide as obstacle-aware pathfinding.

Owner approved default lifetime/arrival/cancel behavior. Base current main 14351db. Manual SPEAR spec/task/evidence; EARS/state helpers absent. Reviews deferred; resets excluded. New behavior has no historical failing production test claim.

- [x] Specify direction-guide and lifecycle boundaries.
- [x] Implement bounded planner, Bukkit adapter and search/cancel controls.
- [x] Prove planner/lifecycle/actual menu integration and full supported build.
- [x] Inspect exact-head review/checks and prove #199 integration in #213.

Four planner cases and three actual InventoryFramework click cases pass: replacement/cap, rotating global budget, arrival/timeout/world/missing-player/range termination, zero budget/cancel/clear; ordinary clicks do not teleport, controls preserve callback, and staff shift-click guides while normal-click teleports. MockBukkit fixtures explicitly register InventoryFramework's public listener for each server instance because its one-time registration persists across test instances. This is fixture setup, not a production registration change.

Final local Java 25 / Paper 26.2 clean test, shadowJar and JaCoCo passed: 781 cases, zero failures/errors, seven external-environment skips. Java 21 Detekt passed after named click/validation helper extraction. Particle privacy/no-chunk-load adapter contract is source verified; actual player visibility remains a client acceptance gate. Artifact is an unmerged local review build.

Final delivery: reconciled Market #213 passed the clean 890-case suite with zero failures/errors and eight external skips, shadowJar/JaCoCo, architecture and Java-21 Detekt. Broader pending-stack rehearsal passed 972 cases, zero failures/errors, 14 skips. Current review threads are resolved/absent; Market hosted workflow approval is pending, while Guilds #216 hosted checks pass. See approved-features-delivery.md for tested commits and final artifact evidence. These task checks establish source implementation and local proof, not hosted build approval or production/client acceptance.
