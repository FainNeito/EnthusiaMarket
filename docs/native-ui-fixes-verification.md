# Native UI corrections — 2026-10-30

## Spec and prove

REQ-390/391/392 refine the existing Market management flows. Current authoritative main was fetched as 57704f1acfaf8aacfdd65cb4ae74c0f36b55712d; ongoing PR226 started at 1db4f6346484e8f35812130f697f91d684caa11a. The cloud worker explicitly released source/build ownership because its executor was unavailable. Local Roundtable MARKET intent2508 records the isolated source-only continuation; no TEST/client resources are held.

Native TEST pass2504 observed an incomplete `/shop delete` command, previous SELL results remaining visible after an empty BUY transition, and the selected-shop tooltip showing `{true}`. This is behavioral failure evidence on the preceding TEST candidate, not an invented pre-patch unit-test run. The preserved pass report is https://drive.google.com/file/d/1n5W-kiy8SGJSZlcdwJjapnveldBAv0hE/view.

## Engine and architecture

- The actual Nexus registrar ignores Kotlin argument defaults. An explicit zero-argument delete route delegates to the existing menu path. The explicit mode route and its delete-all permission check remain intact.
- Search filter callbacks mark an in-menu transition. Only that path closes an empty results inventory; initial empty searches still preserve unrelated inventories. Direction, stock filtering, sorting, navigation and result actions retain their existing semantics.
- Bulk trust converts localized selection labels to plain text before passing the existing `sel` placeholder. This preserves custom icon templates without emitting a boolean or depending on component-placeholder support. Confirmation remains the only persistence action.

Changes remain within command/GUI adapters, language resources, focused tests and documentation. No schema, accounting, stock mutation, companion API, dependency or permission-policy changes. The repository has no local EARS validator or SPEAR state helper; manual requirement/task/evidence records are maintained.

## Refine and delivery

Authored regressions exercise the actual Nexus-built Brigadier tree (bare/explicit delete, additional delete-all permission, console/base-permission rejection), actual GUI clicks for empty direction and stock transitions, and both readable trust states without premature persistence. Existing coverage preserves initial empty-search inventories and cross-page confirmation.

Canonical GitHub CI is pending on the final PR head; no local JVM build is claimed. Interactive preview and exact-head results will be recorded after publication. New-build native acceptance remains pending. No merge, server operation or deployment is part of this task.
