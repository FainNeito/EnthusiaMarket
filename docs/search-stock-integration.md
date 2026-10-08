# Search stock and approved-feature integration - 8 October 2026

## Spec

Reconcile #215 with #213, then #214. The current authoritative main is `14351db4dc416138341a11d0e4e27602f207221b`. Inputs are #213 `8f32e0c`, #214 `1efb1e6` and #215 `7001fd1`.

Keep direction filters, pagination, price controls, stock display, search categories, permission checks and finder callbacks. Reject empty results before inventory creation and stall lookups. Report stock shortage only when shops match the selected direction; a direction with no matching shops uses the existing no-match message. Both rejection paths preserve an already open inventory. BUY/admin availability and explicit out-of-stock display remain supported.

## Prove and engine

The local #213/#215 merge conflicted in SearchResultsMenu, en_US and requirements. Both requirement sections and the feature no-match wording were preserved.

After resolving the initial overlap, the nine focused stock-feedback cases produced one real failure: BUY selection against a stocked SELL-only result reported out-of-stock instead of no-match. The menu now checks direction matches independently of stock to select the correct feedback. All nine focused cases then passed, with zero failures, errors or skips. This is new integration regression evidence; the standalone #215 evidence remains in finditem-stock-verification.md.

## Architecture and refine

The guard stays at the shared menu boundary before inventory replacement and repository lookups. No permission, persistence, companion API or direction semantics changed. Project-local EARS validator/state helpers are unavailable; this manual record supplements the existing requirement/task evidence.

Full build results and final tested commit will be appended after validation. All outputs are unmerged local test artifacts. Hosted workflow approval, maintainer review/merge, network pin/build reconciliation and separately authorized server/client acceptance remain delivery gates. No production operations, reviews/ratings or stall resets are included.
