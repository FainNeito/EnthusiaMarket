# Hanging purchase sign verification

## Requirement

Registered ceiling and wall hanging purchase signs must route clicks through Market and must be unregistered by the existing authorized break flow, just like standing and wall signs. Unregistered signs retain vanilla behavior.

## Evidence

- The click and break listeners previously checked `Tag.SIGNS` and `Tag.WALL_SIGNS`. Paper's `Tag.ALL_SIGNS` is documented as the all-sign material tag; it covers the sign material family including hanging variants.
- `PurchaseSignClickListenerTest` routes right-click events for `OAK_HANGING_SIGN` and `OAK_WALL_HANGING_SIGN` through the actual listener, verifies the interaction is cancelled, and confirms both open the purchase method flow. Existing standing-sign interaction tests continue to pass.
- Full suite: 1,276 tests, 0 failures, 0 errors, 17 skipped.
- `detekt` passes with Java 22. `shadowJar` passes with Java 25. `git diff --check` passes.
- Paper API compile/test dependency is pinned to `26.2.build.129-stable`; current official Paper Tag docs were consulted through Context7.

## Delivery boundary

Source is based on fork `main` `0a521733dfc91e6eb7e57216fac1c67d72fae100` in branch `codex/hanging-purchase-signs`. The local artifact is for validation only. No server upload, restart, activation, or native Minecraft client acceptance has occurred.

Project-local EARS/state helper scripts were not present. This file and the REQ-414 entry in `docs/requirements.md` are the manual requirement and evidence record.
