# Stall region editor verification

## Requirement

Staff need an in-game way to trace an irregular stall interior, see the selected volume, and explicitly approve its WorldGuard polygon before it is saved. The floor/build itself must remain outside the protected volume. No region writes should occur while collecting points or previewing.

## Implementation

- `/em region edit <stallId>` starts a private draft for an existing region ID or configured stall prefix and numeric ID.
- Left-click solid floor blocks to add polygon corners. Right-click removes the most recent corner; `/em region undo` does the same.
- `/em region addfloor` starts another horizontal outline once the current one is valid. `/em region removefloor` discards the active outline while keeping at least one.
- Each outline records its own floor Y and may extend beyond the other floors. `/em region height <topY>` sets the inclusive shared top; each outline starts at its floor Y + 1.
- `/em region preview` privately draws the selected polygon at its bottom and top, with vertical edges. The preview is valid for 30 seconds.
- `/em region confirm` writes only after a current preview and revalidation. Existing WorldGuard metadata is copied to replacement geometry; new regions are parented and provisioned through the configured market integration.
- `/em region cancel` discards the draft. The editor also clears on quit or world change, and cancels breaking/damaging blocks during selection.
- The writer checks polygon validity, configured parent containment for every occupied column, height, area, ID prefix, and WorldGuard save/provision failures.
- The writer unions and deduplicates air blocks scanned from all floor outlines. A single fresh preview and confirmation applies to the full multi-floor stall.

## Proof and verification

### Multi-floor editor update

- Scanner regressions cover two disjoint footprints at different elevations, including an upper overhang, and overlapping outlines whose shared air must be deduplicated.
- Full suite on final source: 1,277 tests, 0 failures, 0 errors, 17 skipped. `detekt` passes with Java 22. `shadowJar` passes with Java 25. `git diff --check` passes.
- Local unmerged artifact `EnthusiaMarket-1.0.0.jar`, SHA-256 `AA290D9D6B08CF9E7EC56D6DC6367FE989A0633A3B80EAC11D2E3360AC562C22`.
- This update is based on fork `main` SHA `0a521733dfc91e6eb7e57216fac1c67d72fae100` in branch `codex/multifloor-stall-interiors`. It has not been pushed, merged, installed, or activated.

- Focused polygon geometry tests: 2 passed, 0 failed.
- Full test suite on the final editor code: 1,192 tests, 0 failures, 0 errors, 14 skipped.
- `shadowJar` completed. Local unmerged artifact SHA-256: `0552947540B896820FA3C9DF226CDD02814A8D20B9DDDB0FCBE80D034076CF29`.
- `git diff --check` passed.
- Detekt reports eight pre-existing findings in unrelated files; no new editor-file findings.
- WorldGuard API compatibility was checked against the pinned API documentation; region replacement preserves existing metadata with `copyFrom`.

## Delivery boundary

This is a source change on `codex/stall-region-editor-20261127`, based on fork `main` SHA `39bc541afe27e52f440b7b0ceb6bcba18b4a0bbc`. It has not been pushed, merged, installed, or activated on Build/TEST. The preview behavior, WorldGuard persistence, command permissions, and the selected stall shape still need native Minecraft acceptance after a reviewed build is installed.

Project-local EARS/state helper scripts were not present; this file and `docs/tasks.md` are the manual requirement/evidence record.
