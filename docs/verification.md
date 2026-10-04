# Market check refinement — 2026-10-04

## Spec

Preserve REQ-323, REQ-326 and REQ-329 while reducing the remaining Codacy complexity findings to the configured limit of five. Bid rejection must retain IP reservation rollback, guild edit checks must retain field-specific authorization, and creature normalization must preserve component boundaries and custom data.

Delivery is through Market PR #197. Production, merging, JAR uploads and server operations are outside this authorization. A future deployment requires canonical merged source, a clean build, artifact provenance and any owning monorepo pin update.

## Prove

The exact published head `cf9695a` has three actionable Codacy findings: `placeBidWithPermit` (6), `maySaveEdits` (7), and a normalization lambda (7). Existing GuildAuctionTest, GuildShopAccessTest and SpecialItemMatchTest provide brownfield regression coverage; no historical test-first claim is made. Current Market main remains `14351db`.

## Engine and architecture

Extract reservation finalization, field-edit decisions and list normalization into small named helpers without changing persistence, permission or normalization semantics. Keep these helpers in their existing application services. Validate against a released LumaGuilds runtime containing the merged public bank API, rather than treating API-only compilation as runtime acceptance.

## Refine and tooling

Focused guild auction, guild access and item matching regressions passed against LumaGuilds 3.0.17. The clean full suite passed: 806 tests, zero failures/errors, seven skipped (including all six MariaDB integration cases). Architecture tests are included. Detekt passed on Java 21; compilation and tests use Java 25 / Paper 26.2. Local Maven consumption uses the previously built pinned Nexus source; an initial Detekt invocation without that flag could not resolve the unpublished remote dependency and was corrected.

The shaded local review artifact `build/libs/EnthusiaMarket-1.0.0-spear-review.2.jar` built successfully after these checks. SHA-256: `7377253bf8b07f14f3d30d75fdbf1df34e61ecf3f218189372e9080c2e4608a7`. It is an unmerged local test artifact and was not uploaded.

LumaGuilds PR #197 is merged at `6b13f3900f84cfff45ed08377684ea65e2029895`. The actual v3.0.17 runtime comes from tag/source `6c9f5a521cca783fbe518f5ad2d749f6e2d211c7`; its SHA-256 is `de18a4672ac37f456ed72742706ae0ea7ff96dfd0f6878bc6a1d5511b9efb34a`. `javap` confirmed the bank methods on both GuildLookup and GuildLookupImpl. CI now downloads this runtime and verifies its checksum. This proves class/API compatibility, not live guild-bank or player acceptance.

Hosted checks remain a separate gate. The preceding head had three Codacy complexity findings and GitHub workflows awaiting repository administrator approval. Inspect the refinement's exact published head before closing REFINE-331; local success does not replace hosted or MariaDB integration results.

Project-local SPEAR EARS/state helpers and a project SPEAR skill are absent; the existing SPEAR Paper Brownfield skill is used. No validator or state-tool success is claimed. This behavior-preserving refinement reuses existing EARS requirements and tests; documentation and CI dependency changes do not introduce new player behavior requiring a separate behavioral proof.

## REQ-332 configurable item frames — SPEAR evidence

Spec: unlimited normal/glow item frames by default; `-1` bypasses their placement's shared total, while finite/zero caps and other entity limits remain enforced. Operator configuration is preserved.

Prove: before implementation, nine focused listener cases ran with two failures and zero errors/skips: bundled defaults were finite, and explicitly unlimited frames were still rejected by `_total`. New cases also cover zero/finite frame caps, finite frames at the total boundary and other entities retaining their total cap.

Engine/architecture: change bundled defaults and put the frame-specific total decision in the existing domain policy; retain the infrastructure event adapter and counter. No companion API or database migration changes.

Refine: clean full suite passed on Java 25/Paper 26.2 against LumaGuilds 3.0.17: 811 tests, zero failures/errors, seven skipped (including six MariaDB cases). The nine listener tests are green; architecture checks are included. Detekt passed on Java 21. The earlier Market head `da2b960` passed Codacy without annotations; hosted checks for this frame-change head remain a separate pending gate. Project-local EARS/state tooling remains absent.

The shaded unmerged local review artifact `build/libs/EnthusiaMarket-1.0.0-spear-review.3.jar` built successfully after these checks; SHA-256 `b251e2d6d59eebe040d36db334514aa44d1816c007836e5c927a218116391439`. It was not uploaded or activated.

For an existing installation, edit each desired group in `plugins/EnthusiaMarket/entitylimits.yml` to set `item_frame: -1` and `glow_item_frame: -1`, then restart through an authorized operational action. Limits are loaded when the listener is constructed; the existing `/em reload` command only reloads the main configuration and translations. This PR neither overwrites that file nor changes production. Nonnegative values remain configurable per type; per-stall extra allowances retain their existing additive meaning.

## Future release gate

Read-only inspection of `BadgersMC/enthusia-network` found Market pinned to `b31fc322b9dfa900bb12da67e67f4c2665822275` and LumaGuilds to `c427d5dbc4838c95bcde45d57be14a6b6980ff8e`. Before a future deployment, merge the Market fixes, update the owning monorepo pins through its normal PR, and validate the combined clean canonical build and artifact provenance. No monorepo update, merge, upload, restart or production change was performed here. Live Java/Bedrock, WorldGuard and real creature-item acceptance remains open as documented in reported-bug-fixes.md.
