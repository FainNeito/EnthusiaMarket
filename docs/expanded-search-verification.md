# Expanded search categories

REQ-377: WHEN a player searches a supported category THE SYSTEM SHALL find its defined materials, including relevant nested shulker/bundle contents, while respecting opt-outs and existing direction/stock controls. Categories SHALL allow intentional overlap.

REQ-378: WHEN a query uses `item:<material>` THE SYSTEM SHALL perform exact material lookup, and WHEN it uses `category:<name>` THE SYSTEM SHALL perform strict category lookup. Bare material/prefix queries and existing category aliases SHALL retain their established coverage; ambiguous stone/flower searches SHALL retain their category meaning.

REQ-379: WHEN completing either `/shop search` or `/finditem` THE SYSTEM SHALL suggest supported categories and explicit query forms alongside ordinary materials. Both entrypoints SHALL use the same exact-item ticker interpretation and search semantics.

Accepted taxonomy: tools, combat, weapons, armor, foliage, flowers, wood, stone, redstone, building, decoration, food, farming, ores, ore_blocks, materials, brewing, potions, enchanting, storage, shulker, transport, lighting, workstations and drops. Existing singular/armour aliases remain supported. No enchantment/custom-item filters, shop permission changes, persistence changes, resets or reviews are introduced.

SPEAR base: current authoritative main 14351db; isolated branch integrates approved #213 head 8f32e0c. EARS/state helpers absent; this manual requirements/task record is maintained. New work is scoped to matcher/query/completion/ticker adapters and player documentation. The interactive preview is a local lookup simulation with sample items, not a live inventory or production acceptance claim.

- [x] Specify categories, query precedence and compatibility boundaries.
- [x] Prove newly missing categories and exact/category lookup before engine changes.
- [x] Implement shared catalog/query/completion and both adapters.
- [x] Prove category inclusions/exclusions, aliases, overlaps, containers and lookup boundaries.
- [x] Complete canonical build, architecture, Detekt and interactive preview.
- [x] Publish source PR and inspect exact-head hosted review/approval state.

Rare crafted variants and new materials require deliberate family definitions; arbitrary item display names do not determine category membership. Strict category searches deliberately avoid legacy prefix accidents. Bare legacy aliases keep their old prefix coverage in addition to expanded curated membership.

Proof: three initial tests failed before engine changes (new categories, strict categories and exact lookup). After engine they passed. Expanded tests cover every canonical category, aliases, exclusions, overlaps, real container matching, opt-outs, ticker interpretation, namespace normalization and Turkish locale. A completion test's expected result omitted STORAGE for the shared `sto` prefix; correcting that expectation preserved correct prefix behavior.

Architecture refinement reproduced a real command boundary failure: one of two Brigadier execution cases failed because Nexus's ordinary String word parser stopped at the colon. MarketSearchArgument provides a dedicated greedy resolver registered before command scanning; unrelated String arguments retain their old grammar. /finditem uses the same argument-type factory. Actual scanned Nexus search metadata and Brigadier dispatch now accept unquoted category/item/namespaced selectors, prefixes and spaced names. Scanner fixtures register the new type independently.

Final canonical Java-25/Paper-26.2 clean test/shadowJar/JaCoCo passed 906 cases, zero failures/errors and eight environment/integration skips; architecture tests are included. Java-21 Detekt passed. The catalog export case executed against supported item materials; private container and menu/direction-trail regressions remain green. Artifact `EnthusiaMarket-1.0.0-categories-review.4.jar` is an unmerged local review build; its final SHA-256 and source commit are recorded at PR delivery. No MariaDB schema, economy, permissions, server state or client acceptance is changed/claimed.

Interactive preview uses the exported runtime membership, with all 25 categories and individual exact/prefix lookups. Local interaction verification exercised each category, exact/prefix/namespace/unknown selector behavior, pagination and both command choices. It reports matching materials rather than inventing live shop listings. EARS/state tooling remains unavailable.

Review artifact SHA-256: 65043d796df0210115eb414520e49b0a24e86289062103164b85e3aaa038c8dc. Final standalone scanner fixture/actual command execution checks pass after registering the new argument type independently. All runtime source was exercised by the clean full suite; later fixture and delivery-record refinements do not change runtime behavior.

Delivery: Market #214 follows #213. Tested runtime source bd07ad0941558d35075ae315597ecb6c46e3d5a4 is mergeable with no unresolved current review threads or changes-requested findings. Hosted build/quality/wiki and Codacy concluded action_required at that head; maintainer approval is required and no hosted Market build/static pass is claimed. Delivery-record-only commits do not change tested runtime source. Canonical merge, any network pin/build reconciliation, separately authorized staging and real Java/Bedrock acceptance remain external gates.
