# Search stock and approved-feature integration - 8 October 2026

## Spec

Reconcile #215 with #213, then #214. The current authoritative main is `14351db4dc416138341a11d0e4e27602f207221b`. Inputs are #213 `8f32e0c`, #214 `1efb1e6` and #215 `7001fd1`.

Keep direction filters, pagination, price controls, stock display, search categories, permission checks and finder callbacks. Reject empty results before inventory creation and stall lookups. Report stock shortage only when shops match the selected direction; a direction with no matching shops uses the existing no-match message. Both rejection paths preserve an already open inventory. BUY/admin availability and explicit out-of-stock display remain supported.

## Prove and engine

The local #213/#215 merge conflicted in SearchResultsMenu, en_US and requirements. Both requirement sections and the feature no-match wording were preserved.

After resolving the initial overlap, the nine focused stock-feedback cases produced one real failure: BUY selection against a stocked SELL-only result reported out-of-stock instead of no-match. The menu now checks direction matches independently of stock to select the correct feedback. All nine focused cases then passed, with zero failures, errors or skips. This is new integration regression evidence; the standalone #215 evidence remains in finditem-stock-verification.md.

## Architecture and refine

The guard stays at the shared menu boundary before inventory replacement and repository lookups. No permission, persistence, companion API or direction semantics changed. Project-local EARS validator/state helpers are unavailable; this manual record supplements the existing requirement/task evidence.

Approved-feature bundle tested at `48958b1ce9a86f1c999b39d0a06c6b0fb9ecbc72`: canonical Java 25/Paper 26.2 `clean test shadowJar jacocoTestReport` passed 899 cases, zero failures/errors and eight unchanged external/integration skips. Architecture cases and Java 21 Detekt passed. Test artifact `EnthusiaMarket-1.0.0-approved-stock-review.1.jar`, SHA-256 `6251f8426250f06af18264c277b250b706cbd7cba2e5d1a8639f1fd938b93395`.

The supplied Guilds #216 review JAR retained SHA-256 `890ec938fdaef552a46334b4673be689e3e7c6f0349cfa7e299cb3abf26d0aa6`; its optional alliance-service contract case executed. The initial expanded-category integration tested at `3e876758d251ffcf8bc543cd1ad77ada0ba57607` passed 915 cases with zero failures/errors and eight unchanged skips, plus architecture and Java 21 Detekt. Runtime source is unchanged by subsequent evidence-only commits. See expanded-search-verification.md for its final artifact record.

Merge order for maintainers is #215 -> #213 -> #214. The bug head is an ancestor of the feature bundle, and that bundle is an ancestor of expanded categories. This avoids the observed overlapping-source conflicts in this order. Both bundles remain based on current main `14351db4`. Existing historical records remain valid for their earlier builds.

The private mobile preview is https://enthusiamarket-mobile-menu.awareyak.chatgpt.site. The Out of stock and No matching shops samples exercise initial rejection. Selling shops only followed by changing Shop Type from SELL to BUY demonstrates no-match feedback while the current menu remains open. Sample shops and navigation are simulated. Local touch/browser checks passed stock/no-match feedback, inventory preservation, category membership, sorting, paging, controls, image loading and 320px layout. Real Minecraft acceptance remains pending.

All outputs are unmerged local test artifacts. Hosted workflow approval, maintainer review/merge, network pin/build reconciliation and separately authorized server/client acceptance remain delivery gates. No production operations, reviews/ratings or stall resets are included.
