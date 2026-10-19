# Stall access recovery — bug-0406 follow-up

## Spec and evidence

Ticket 406 describes inherited members after expired-stall purchases, with `/em sellback` clearing them. Its reporter did not verify effective container/build access. The second screenshot shows an ownership start of September 27, before the September 29 ownership-integrity release. This does not establish which binary handled any individual transition.

Current canonical main at investigation was `57704f1acfaf8aacfdd65cb4ae74c0f36b55712d`. `Stall.awardTo`, SQL persistence and the WorldGuard award adapter already clear prior members. This change does not claim to introduce that existing transfer fix.

Read-only production evidence on October 19 found an installed `EnthusiaMarket-1.0.52.jar` filename, a database backup captured October 18 04:12 UTC with V029 recorded, and saved WorldGuard regions captured October 19. The main database and current log exceed the panel editor limit; signed downloads failed certificate validation and were not bypassed. Therefore the database comparison is a dated backup comparison, not an atomic/live permissions snapshot or running-JAR provenance proof. Private copies remain outside the repository.

In that backup, stall48 has no delegated members; the second screenshot's coordinates map to stall59, which has forfeited emergency-auction state and an empty roster. Comparing eligible SOLO/NONE backup rows against the later saved WorldGuard file showed 17 differences: 14 forfeited/vacant stalls retained former ACL entries; two active stalls lacked intended members; one active stall had an extra WorldGuard member. Different capture times and possible manual changes limit causal conclusions. No current owner is accused of adding stale members.

The supported source gap is deterministic: startup only reconciled guild stalls, and the administrative recovery command skipped forfeited emergency auctions. Consequently saved solo ACLs could survive restarts and the documented recovery path could leave forfeited access untouched.

Two further source-level regressions were reproduced separately (8 member-service tests, 2 expected failures): the former SOLO owner retained `canManage` authority in EMERGENCY_AUCTIONING and could add members, and a rejected database save still left a new WorldGuard grant. These paths can reintroduce external access independently of an ownership award; they are fixed in this PR.

## Engine and architecture

`StallRegionAccessService` owns the shared lifecycle policy using only domain ports. The existing startup listener invokes it after ServerLoad and for later WorldLoad events, on the server thread. Administrative `/em rg resync` uses the same policy before provisioning flags; failures are isolated per stall.

| State | Access projection |
| --- | --- |
| OWNED / GRACE, SOLO | Replace owner and both old domains, then replay current DB members |
| OWNED / GRACE, GUILD | Replace both domains with current guild membership, including an empty roster |
| OWNED / GRACE, NONE | Clear inconsistent ACLs |
| UNOWNED / EMERGENCY_AUCTIONING | Clear owners and members; former owner is only provenance |
| AUCTIONING / RE_AUCTIONING, NONE | Clear system-auction ACLs |
| Seller-owned auction | Preserve pending settlement |
| MODERATION_HOLD or live moderation reservation | Preserve provider-owned access and flags |

Automatic recovery changes only WorldGuard access, never DB membership, shops, rent, region geometry, flags or stall contents. It does not infer which members of an active persisted roster were added by a former owner. Such ambiguous legacy members require current-owner confirmation and targeted removal; blanket purges and stall resets are excluded.

`Stall.canManage` now rejects UNOWNED, EMERGENCY_AUCTIONING and MODERATION_HOLD identifiers, including legacy member fields. Seller-owned auctions retain their prior authority until settlement. Member mutations check the durable moderation gate, and additions persist successfully before granting WorldGuard membership. Repeating an already-persisted member addition retries its projection after a provider failure without a second DB write. Existing member revocation remains revoke-before-save, so a failed removal write cannot introduce a grant.

WorldGuard lookup now throws on missing worlds/managers/regions so recovery does not falsely report success. A later world load retries that world's roster. Persistent provider failures remain visible and require operator attention; there is no claim of transactional SQL/WorldGuard atomicity.

The WorldGuard adapter retains platform lookup in infrastructure. Core-only WorldGuard test dependencies exercise real UUID/name/group domains without loading the Bukkit plugin or its unrelated SQL migrations. No new runtime dependency, schema, command, permission or visual element is introduced.

## Prove and refine

Before implementation, both new tests against the actual delayed startup hook failed because SOLO owner replacement and emergency access clearing were never called (2 tests, 2 expected failures). Additional coverage checks moderation preservation, per-stall failure isolation, later world loads, empty guild rosters, seller/system auctions, SQL reload after transfer, repeated reconciliation and real WorldGuard domain cleanup. Final check results are recorded in verification.md.

No project-local EARS/state helpers were present; requirements, tasks and this evidence record implement the SPEAR trace manually. Live Java/Bedrock interaction, the currently loaded binary, current DB contents and production repair remain separate acceptance gates. No remote write, resync, restart, deployment or Discord message was performed.
