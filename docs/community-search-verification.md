# Approved stone and flower search categories

## Spec

REQ-364: WHEN a player searches stone or stones THE SYSTEM SHALL match stone building families and their building variants, excluding ores, redstone devices and stone tools.

REQ-365: WHEN a player searches flower or flowers THE SYSTEM SHALL match flower items including tall flowers, petals, eyeblossoms and newer flower items, excluding seeds, dyes, pots and unrelated foliage.

REQ-366: WHEN either category is searched THE SYSTEM SHALL retain opt-out visibility, case-insensitive matching, supported nested-container limits and exact/prefix material queries outside category keywords.

Base: authoritative main 14351db4dc416138341a11d0e4e27602f207221b. Owner approved these categories on 8 October 2026. Independent branch; no other community policy or production changes. Local EARS/state helpers are absent; this manual record tracks SPEAR.

## Tasks and evidence

- [x] Specify category membership and ambiguity boundaries.
- [x] Prove missing matches and category exclusions on current main.
- [x] Implement in the shared search matcher and document player queries.
- [x] Verify focused/full tests, architecture and Detekt.
- [ ] Inspect exact-head hosted findings and deliver a reviewable PR.

Java 25 / Paper 26.2; clean supported Nexus local profile. Main's old JUnit dependency needs the existing pending PRs' 6.0.3 BOM/launcher alignment for MockBukkit test discovery. No historical failure is attributed to production from this test dependency mismatch.

Prove: all four new category cases failed before engine changes (assertion failures, no errors). Engine: bounded explicit material families avoid substring matches such as redstone and avoid stone-tool prefix collisions. Existing categories and material prefixes outside the four new category keywords are unchanged. Refine: four category cases plus ten existing shared matcher cases pass on the first focused green run.

Canonical clean test/shadowJar/jacoco build: 778 cases, zero failures/errors, seven external-resource skips. Java-21 Detekt passes. Local unmerged review JAR SHA-256: aa6865c1432fba1b1891eb116f8285645287071a557312418f5d885f8e984013. Pending #199 integration and hosted checks remain separate gates; no artifact uploaded.
