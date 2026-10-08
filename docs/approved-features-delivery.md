# Approved Market feature delivery - 8 October 2026

## Scope and requirements

The owner approved stone/flower search categories, expiry and insufficient-renewal-funds login warnings, configurable stall access and private shop-finder trails. Ratings/reviews and stall resets are excluded.

Allied access starts disabled. Authorized managers select individual currently allied guilds and each guild's capabilities; blacklists override grants. Access never implies building, ownership, policy management, shop deletion or trust delegation. Existing guild field permissions remain authoritative. The default blocked effect is invisibility, including effects already active inside the stall. Pickup checks close the gap before periodic suppression.

Warnings default to 24 hours before expiry and check the responsible personal payer or guild bank. Existing rent is prepaid manual renewal; no automatic charge is introduced. Trails default to 60 seconds, remain private to the finder and stop on arrival/cancellation; they are direction guides rather than obstacle pathfinding.

## Merge path

Stock-feedback reconciliation update: #213 now includes #215 and preserves shop-direction/navigation controls. Review and merge #215, then #213, then #214. The reconciled #213 clean build passed 899 cases with zero failures/errors and eight unchanged skips; architecture and Java 21 Detekt passed. Detailed tested commits, regression proof, hashes and preview are in search-stock-integration.md. Earlier narrow-build evidence below is historical for the original bundle.

This bundle starts from authoritative main `14351db4dc416138341a11d0e4e27602f207221b` and reconciles the reviewed sources of #197 (correctness), #198 (guild read API), #199 (search), #201 (storage), and the four feature PRs #209-212. It preserves the warning listener's repository registration order, both direction filters and navigation callbacks, the shared guild-authority rule, and all shutdown callbacks. Guilds #216 supplies the optional public alliance service and Java/Bedrock guild-detail shortcut; its prerequisite is Guilds #215. Older Guilds runtimes deny allied access safely.

The other pending Market PRs remain separate. A broader compatibility rehearsal including the earlier maintenance stack passed 972 cases with no failures/errors and 14 external/integration skips; that rehearsal is compatibility evidence, not a new merge or deployment instruction.

## SPEAR evidence and acceptance boundaries

Spec: requirements and defaults above, with area-specific requirements in community-search-verification.md, login-warning-verification.md, finder-trail-verification.md and stall-access-verification.md.

Prove: category exclusions/aliases; real search-menu clicks; payer/deadline/failure and asynchronous warning boundaries; trail budgets/lifecycle; permission and transaction denial; double-chest/open-inventory revocation; rollback and uncertain-commit reconciliation; existing-effect duration restoration; actual companion JAR alliance-service registration and revocation. Existing-effect and database failures have targeted regression evidence. No historical production red/green claim is fabricated.

Engine/architecture: bounded workers and caches, optional public Bukkit service contracts, ownership/revision/moderation-fenced SQL and selected capabilities. Event paths avoid policy SQL and particle paths avoid terrain loading. No private companion-container reflection.

Refine: locally resolved overlapping branches and static findings; final supported build, architecture and hosted review evidence is recorded below. Project-local EARS validator/state helpers were not found; manual requirement/task/evidence records are maintained.

- [x] Implement and reconcile approved sources.
- [x] Establish focused regression and broader pending-stack compatibility proof.
- [x] Record final narrow-bundle clean build and inspect hosted approval gates.
- [ ] Maintainer review, hosted workflow approval and canonical merge.
- [ ] Network pin/build reconciliation, separately authorized staging and player acceptance.

Native MariaDB access-policy execution, running-server startup, WorldGuard behavior and real Java/Bedrock client acceptance are not claimed. Artifacts are unmerged local test builds. No production mutation or reset occurred.

Final narrow build: Java 25 / Paper 26.2 `clean test shadowJar jacocoTestReport`, 890 cases, zero failures/errors and eight external/integration skips. Java 21 Detekt passed. Source tested: `3eae69d719c5be78385c854e14016b6299a2dcd5`; subsequent delivery-record changes are documentation only. Test artifact `EnthusiaMarket-1.0.0-approved-review.1.jar`, SHA-256 `7ada80ba02c91852a15e23f72ad9dd6beb4fa8e3e48f3de701480dd45bc5ec8f`. Companion Guilds #216 source `694bb5ff53b8d68c5cb644b6100b7d87f0ce0e2d`, JAR SHA-256 `890ec938fdaef552a46334b4673be689e3e7c6f0349cfa7e299cb3abf26d0aa6`; the actual public service contract case executed with zero skips. Guilds hosted build, Codacy and four wiki checks pass at that head. Market fork workflows require maintainer approval; that is a separate gate from local proof.
