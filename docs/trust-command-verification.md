# Trust command shorthand correction

## Spec and prove

REQ-393 restores the advertised `/shop trust <player>` route. Native TEST pass2549 on
PR226 head967a30c reproduced `Unknown or incomplete command` for
`/shop trust FainNoir`; the explicit `menu` suffix opened the existing menu and
passed three-page navigation and canceled selection checks. This is observed
pre-fix failure evidence, not a claimed historical unit-test run.

Before editing, the isolated worktree was clean at967a30c and canonical main was
fetched again as57704f1acfaf8aacfdd65cb4ae74c0f36b55712d, unchanged from the
ongoing candidate's last base inspection. The existing TEST candidate remains
the integration scope; none of its accepted changes are replaced with an older
main-only JAR. Roundtable source intent2552 records the bounded continuation.

## Engine and architecture

The pinned Nexus registrar does not dispatch Kotlin default arguments. An
explicit player-name-only handler delegates to the same `menu` path as the
existing mode handler. The mode is required only on that second overload.
Name resolution, owner scope, base permission, console rejection, explicit
`all`, menu selection and confirmation-only persistence remain in their
existing adapters/services. No GUI layout, language, schema, companion API,
inventory or economy behavior changes.

## Refine and delivery

ShopTrustCommandRegistrationTest scans the real command class and invokes the
actual pinned Nexus-built Brigadier tree. It covers the shorthand and explicit
menu reaching the same unknown-player validation without persistence, explicit
all with the resolved target, permission denial, console rejection, missing
player and excess arguments. Existing GUI tests cover opening, selection and
confirmation behavior; native acceptance of the new build is a separate gate.

Local and canonical CI results, source/artifact pins, review and TEST deployment
receipts are recorded on PR226 and in the local bridge evidence. Pending checks
are not implied to have passed. No repository-local EARS validator or SPEAR
state helper exists; these requirement/task/evidence files track the workflow.
Production and merging remain outside the authorized TEST deployment.

The unchanged menu can be explored in the existing
[interactive pagination simulation](https://enthusia-market-pagination-review.awareyak.chatgpt.site/).
It is a simulation and does not prove command registration or native rendering.
