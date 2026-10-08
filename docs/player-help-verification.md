# Player help maintenance

SPEAR base: freshly fetched canonical main `14351db`. Scope is existing-command documentation and tutorial clarity; no community feature, rent/tax, permission, retention or transaction behavior is changed.

REQ-351: WHEN help advertises a command THE SYSTEM SHALL use a registered path with all required arguments and a useful prefill.

REQ-352: WHEN players request the existing shop tutorial THE SYSTEM SHALL explain actual creation, editing, bulk selection, finding shops, member renewal, expiry and vault recovery entry points without claiming unverified live acceptance.

Prove: validate in-game help against the actual command annotations and required parameter metadata consumed by Nexus. Engine changes are limited to help text and rendering additional tutorial lines; game behavior has no new engine slice. Architecture remains existing commands/language/help topics. No EARS/state helper exists; use this manual requirement/task/evidence record. Full client rendering, effective live permissions and posting instructions remain separate acceptance gates.

The contract executed against original help and failed on twelve entries: missing stall/auction arguments and the unregistered `/em vault` and `/em guildpolicy` paths. Corrected help points to `/shopvault open` and `/em guild policy`, distinguishes sell offers from administrator auctions, and retains the actual `/shophelp show` entry point and aliases. It does not introduce a root command route unsupported by the current command registry.

The quick tutorial uses actual sign creation and menu controls, explains positive whole-number typed price/cancellation, owner deletion, Java bulk selection, finding shops, personal member renewal, grace/auction consequences and the barter-payment vault. The current Bedrock purchase form provides one-trade confirmation, not the Java bulk/custom selector; this audit does not claim parity or implement the community-requested extension. The vault is for TRADE payments; no expired container inheritance or confiscation/recovery policy is invented.

Final clean Java 25/Paper 26.2 `test shadowJar`: 776 cases, zero failures/errors, seven existing external-resource skips. Contract and real-locale fallback cases executed. Architecture and Java-21 Detekt passed. Project wiki frontmatter and topic parity, all 31-page markdown lint, and strict MkDocs passed. JUnit alignment matches #197/#199/#201. Local unmerged review JAR SHA-256: `37876efc4a097fbaf04c04ebfe9d96fcbf018a6a65f4336e7bbe4952b1cfa3ff`.

Existing locale overrides remain authoritative; new keys fall back to bundled defaults. Updating existing overridden tutorial lines requires reviewing the locale as part of the separately authorized release. No live locale edit, policy update, wiki publication or Discord post occurred. Source/runtime verification cannot replace ordinary-player effective permission or Java/Bedrock acceptance checks.

Published as #203. Head `7f4881f8cc57262cfc19d6a14685fe4da2828dd8` passed Codacy with zero annotations; comments contain only skipped CodeRabbit review and a clean Codacy report. No human/inline findings returned. Hosted build, quality and wiki workflows require maintainer approval. This documentation-only evidence update does not alter the tested behavior.
