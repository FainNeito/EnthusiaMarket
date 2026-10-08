# Search stock feedback (REQ-380)

## Spec

WHEN a Market item search has matching shops but no entries visible under the
default stock filter THE SYSTEM SHALL send a localized message naming the query
as not in stock and SHALL NOT open or replace the player's inventory.

The shared SearchResultsMenu is the presentation boundary for both /finditem
and /shop search. Availability means at least one complete configured trade.
BUY shops and admin shops retain their existing unlimited display semantics;
explicitly including out-of-stock entries continues to work. No matches retain
the existing no-results message. No permissions, persistence, or trade changes.

## Workflow

Authoritative current main: 14351db4dc416138341a11d0e4e27602f207221b.
Isolated branch: codex/finditem-out-of-stock. Existing feature work is preserved.
No project-local EARS/state helpers or installed SPEAR skill were found; manual
requirements, tasks and verification evidence are maintained here.

- [x] Specify stock feedback and compatibility boundaries.
- [x] Prove the empty-menu regression before engine changes.
- [x] Add the shared presentation guard and localized feedback.
- [x] Verify complete trades, mixed results, BUY/admin and explicit stock display.
- [x] Complete full tests, architecture, build and mobile preview.
- [ ] Publish PR and inspect exact-head hosted checks and review findings.

## Proof and validation

Current main's JUnit 5.8.1 engine conflicts with the resolved platform:
`ReflectionUtils.returnsVoid` fails during test discovery. Aligned tests with
JUnit BOM 6.0.3 and explicit platform launcher, matching the supported pending
feature branch's already validated build setup. This changes test dependencies
only. MockBukkit requires an explicit starting inventory for replacement checks;
the fixture was corrected before final behavioral proof.

Before the menu guard: seven focused cases ran, four failed on inventory
replacement. After the guard: all seven pass in the canonical clean full suite.
Java 25/Paper 26.2 clean test/shadowJar/JaCoCo: 781 cases, zero failures/errors,
seven unchanged integration/environment skips. LayerRulesTest passes.
Java 21 Detekt and git diff --check pass.

Released LumaGuilds 3.0.23 reference SHA-256:
`c2958842e976581743af510d57547d1d38542633eeff7dd27325a5b0d307eb19`.
No companion API or plugin-runtime contract changes.

Local, unmerged test artifact: EnthusiaMarket-1.0.0-stock-feedback-review.1.jar.
SHA-256: `f76d915804d9a2ac480e1b489dc0225e3e66ff6aa7c9af07cc942be2efda68de`.

Private mobile preview: https://enthusiamarket-mobile-menu.awareyak.chatgpt.site.
Sample availability selects stocked, empty, or no-match fixtures. Browser checks
cover query-specific chat without a search menu, no-match messaging, stocked
results, touch navigation, and 320px layout. Shops and trails are simulated;
the category examples represent pending category work, not current production.

## Acceptance limits

Source-level reproduction is separate from the user's production report.
No production mutation is authorized or performed. PR merge, network pin/build
reconciliation and Java/Bedrock live acceptance remain separate delivery gates.
