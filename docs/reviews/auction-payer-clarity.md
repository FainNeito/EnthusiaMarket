# MARKET13 — auction payer clarity

## Spec

User-authorized presentation scope: explicit Personal/Guild selection, selected guild name, funding source, full bid, live current/minimum and readable time. Every preset/custom amount opens a review naming the payer and stall ownership destination before dispatch. Invalid/stale guild selection fails closed; it must never be labelled Personal. Confirmation delegates to the existing authoritative bid service. No delta/anti-snipe/refund/settlement policy changes.

Existing requirements consulted: REQ-007/008/009/010/011 and REQ-029. Existing requirement prose describes older item escrow; actual current stall-auction implementation is the source contract. No requirements validator/SPEAR command plugin available; this is manual spec/prove/engine/arch/refine evidence, not an EARS validation claim.

## Prove

Focused regression tests are authored before engine edits. They cover full-bid amounts/overflow, readable deferred/expired time and literal/unknown identities. Adapter tests will cover review-before-dispatch, payer capture, stale permissions/minimum and custom input. No compiler, Gradle or test execution is authorized for this source-only lane; no test pass claimed.

## Architecture and scope

Base canonical main57704f1, verified through approved GitHub API on2026-10-29. Constructors and public pendingCustomBids remain compatible. Existing eligibleGuilds and placeBid own authority/economy. Main-thread UI continuation is required for custom chat; no async Bukkit inventory mutations. Pagination and readable-stall-access branches are separate and untouched.

## Engine / architecture / refine evidence

Implemented explicit Personal action and a named-guild picker (45 choices per page, Previous/Next/page indicator/Back; stable guild IDs). Bid/review Java screens use3 rows with summary slot4 and actions starting9; guild picker uses6 rows with choices0..44 and footer45/49/51/53. Bedrock displays the same content and actions as Cumulus forms, using the existing MenuFactory selection policy including forceForms. Native browser remains the existing Java auction browser; this patch routes the selected bid flow, not a new Bedrock auction browser.

The service adds only auctionForBid, a fresh read delegating to existing private findAuction (findById then findOpenByStall fallback). All mutation/economy/anti-snipe/refund/settlement code remains untouched. Confirmation revalidates the current row, OPEN/not-ended status (deferred timer exempt), minimum, guild eligibility and displayed snapshot identity/name/high-bid/timer. Changed details require a new review; no automatic adjustment or dispatch. Single-use confirmation sends the full bid and captured guild ID to existing placeBid, which rechecks authoritative policy and computes actual charge.

Main startup binds existing NexusScheduler and MenuFactory through a narrow UI runtime object; menu/listener constructors and public pendingCustomBids signature remain unchanged. The pinned Nexus057836b source provides runOnMain(action: () -> Unit): AutoCloseable. The original AuctionChatListener had no handoff; it now atomically claims chat on the event thread, cancels before broadcast (LOWEST/ignoreCancelled), and schedules the continuation. Tokens, original Player session identity, online/timeout and replacement guards are checked on main. Quit clears that player's prompt/token/guild state; disable resets runtime and all prompts. Volatile scheduler/selector and generation fences prevent old queued/form actions after disable/reconfigure. No economy call occurs before confirmation.

First pure/session regression tests were authored before engine code; adapter and race/boundary cases were added during refinement.23 focused Kotlin test methods are authored, NONE executed:3 pure presentation,8 review/session,9 Java/chat integration and3 Bedrock form adapter tests. No compiler, Gradle, DB, plugin JAR, hosted CI, TEST or clients ran. Static checks only: language YAML parses, referenced literal UI keys exist, preview JavaScript syntax passes, git diff --check passes. These checks do not prove compilation or runtime behavior.

Standalone preview: docs/previews/auction-payer-clarity.html. Clearly labelled synthetic simulation; ordinary fixture amounts only (JavaScript safe integers), no native client acceptance. It mirrors explicit payer selection,46 guild picker pages, preset/custom review, stale guild/minimum/name, authority loss, closed/deferred auction, timeout and quit cleanup. Native custom input is chat; the preview labels its inline field as simulated chat. Root owns the single private Site appgprj_6ae3de2562308191a187dde17d247319 / enthusia-market-auction-review; no duplicate Site registered here. HTTPS publication is pending root.

Manual SPEAR spec/prove/engine/architecture/refine record only. AGENTS.md/SPEAR CLI/CLAUDE.md/EARS helpers and Codacy MCP remain unavailable as previously recorded. No suppression or analyzer rule weakening added. No requirements identifiers, source-wide policy or task completion claims fabricated.

Preview refinement check:23 assertions passed against the actual inline JavaScript under a lightweight synthetic DOM using Node's built-in VM. This covers explicit actions,46th-guild paging, custom review, single dispatch, changed minimum/name, revoked guild/permission, closed auction, timeout and quit fixtures. It is not a browser rendering test, Kotlin execution or native acceptance. The standalone harness is retained in the cloud delivery package.

Read-only source comparison confirms AuctionLifecycleService is byte-for-byte unchanged after removing the one added read-query declaration/comment. No payment logic change. Root review requested explicit payer actions, volatile lifecycle binding and Player-session/quit cleanup; all are implemented with authored cases. The native auction browser itself still uses its existing Java UI; no unsupported claim of a new Bedrock browser.
