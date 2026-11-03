# Auction region size — 2026-11-01

## Spec and evidence

The user approved showing stall dimensions and volume in auction listings. The
example 5 × 5 × 10 has a bounding volume of 250 blocks. REQ-397 labels this as
the enclosing region bounds, not a guarantee that every block is empty,
accessible, buildable or inside a nonrectangular region.

Authoritative parent main 57704f1 and fork main 2870eb9 were fetched; this
isolated branch is based on fork main and preserves the separate finder work.
The existing RegionProvider documents inclusive bounds. WorldGuard's adapter
can return polygon bounding boxes; it calls Bukkit.getWorld, so the auction
menu resolves visible-region sizes on its synchronous render path rather than
the async database prefetch. Absent, invalid, overflowing or failed bounds return
an unavailable label; no database, bid, auction or region state is mutated.

Four tests covering inclusive coordinates, one block, unavailable/inverted
bounds and arithmetic overflow are authored but not yet run. Locale YAML and
`git diff --check` pass. There is no project-local EARS validator or SPEAR
state helper; this requirement, task and evidence record are maintained
manually. A clean historical red run is not claimed for this new feature.

## Preview and remaining verification

[Interactive auction preview](https://enthusia-market-native-fixes-review.xxnatetheprogamerxx.chatgpt.site/auction-size.html)
shows three schematic listings including 5 × 5 × 10 and unavailable bounds.
A JavaScript syntax and three-selection logic check passed locally. The owner-
private hosted Site deployed successfully, but this session's browser surface
is unavailable, so actual browser and authenticated mobile access remain
unverified. Icons and data are illustrative; no bids can be placed in preview.

Local Gradle and hosted CI need current capacity dispositions. Native Java and
Bedrock acceptance, final review, PR, merge and TEST activation are not claimed.
