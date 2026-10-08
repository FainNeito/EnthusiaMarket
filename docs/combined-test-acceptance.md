# Combined Market and Guilds TEST acceptance

Prepared 2026-10-08. **Plan only: no scenario below has been executed on TEST.**

This checklist covers the approved search, stock feedback, rent warnings, stall access, effect protection and shop-finder changes. Ratings/reviews, stall resets and new community requests are excluded. Creating this document does not authorize uploading plugins, restarting TEST, changing live data, merging PRs or production activation.

## Source and delivery gates

| Input | Verified snapshot | Current gate |
| --- | --- | --- |
| Market main | `14351db4dc416138341a11d0e4e27602f207221b` | Documentation branch starts here |
| [Market #214](https://github.com/BadgersMC/EnthusiaMarket/pull/214) | `d2090cebdb06f1a1061261d9c32a1efcebbfa2df` | Open; Actions need maintainer approval; Codacy passed |
| [Guilds #216](https://github.com/BadgersMC/LumaGuilds/pull/216) | `faeae6b7881f61c21ae81766c913fa434025f36f` | Open; hosted build run `37842682382` passed |
| Guilds current main | `e60d99e` after fetching 2026-10-08 | Newer than the tested companion base; contains runtime-only AxKoth integration `27ce69c` and Discord role cleanup #207 |

- [ ] G01 — Refresh both default branches, PR heads, reviews and exact-head checks immediately before the session. Reconcile Guilds' newer main safely, inspect CI dependency preparation after the runtime-only AxKoth change, and repeat affected build/API checks. Prior checks do not prove this newer combined source.
- [ ] G02 — Obtain maintainer approval for Market's build, quality and wiki Actions; inspect the resulting exact-head checks and actionable review findings. Do not mark skipped automated review as completed review.
- [ ] G03 — Record which source pair is approved for isolated TEST, Java/Paper profile, companion versions and owning network pins. Any unmerged TEST artifact must be labelled as such. Production requires merged source, reviewed network pins and the canonical clean release build.
- [ ] G04 — Before any separately authorized TEST operation, record existing loaded versions/configuration and backups of TEST plugin data, world fixtures and private pack configuration. Record artifact version, source SHA and SHA-256; verify uploaded bytes and startup logs separately from local build results.

Previous local evidence: Market 915 cases, zero failures/errors, eight external skips; Guilds 1714 cases, zero failures/errors, 19 external skips. Four private artwork checks passed separately, and Market's final Guilds alliance-service contract passed with zero skips. These are regression/API results, not native database or Minecraft client acceptance. See the PR descriptions for artifact hashes and execution details.

## Fixture and evidence sheet

Use dedicated TEST stalls and disposable test items, not production stalls or valuable player stock. Record actual identifiers before execution; angle-bracket command values below are placeholders.

| Fixture | Record before execution |
| --- | --- |
| Personal stall and guild stall | IDs, ownership epoch, state, rent terms, deadlines, moderation state |
| Guild A, selected ally B, unselected ally C | Guild IDs, current active alliances, ranks and permissions |
| Actors | Personal owner/member; guild owner; authorized manager; member without management permission; outsider; B ally; C ally; blacklisted B ally; staff admin |
| Clients | Java and Bedrock/Floodgate accounts, versions, translated player names; second nearby observer for privacy checks |
| Shop fixtures | Stocked SELL, empty SELL, SELL below per-trade quantity, BUY with empty storage, opted-out shop, matching nested shulker/bundle, more than 36 matches |
| Materials | Stone/cobblestone/stone sword, flower/dye/flower pot, redstone/repeater/redstone ore, axe, helmet/chestplate/leggings/boots, colored shulker |
| Interaction fixture | Anvil, lectern, door/trapdoor, fence gate, button, lever, container and safe restocking drop area |
| Warning fixtures | Personal and guild stalls inside/outside 24-hour window, grace stall, enough/insufficient responsible-payer funds |
| Spatial fixtures | Nearby destination, distant destination beyond 256 blocks, another world, wall obstruction and known unloaded terrain |

For every case record: ID; PASS/FAIL/BLOCKED/NOT RUN; tester/client; UTC time; exact source/artifact/configuration; setup; action; expected versus actual result; screenshot/video/log reference; balances, item counts or persisted rows where relevant; linked issue and cleanup. Blank results mean NOT RUN. Record reconnect cooldowns and configuration overrides so a suppressed warning is not misclassified.

## Search and stock feedback — REQ-377 through REQ-380

Run applicable menu cases on both Java and Bedrock. Use non-admin accounts for stock rejection; admin availability is deliberately different.

| ID | Action | Required result |
| --- | --- | --- |
| S01 | Run `/finditem category:redstone` and `/shop search category:redstone`; then `item:redstone` | Category finds dust and circuitry such as repeaters/pistons/hoppers/buttons; strict category excludes redstone ore. Exact item finds only dust. Both entrypoints agree. |
| S02 | Search `category:armor`, `category:combat`, `category:weapons`, `category:tools` | Armor covers all four armor slots, not only helmets; combat includes armor/weapons/supplies. Axe intentionally overlaps tools/weapons/combat. |
| S03 | Search `category:stone`, `item:stone`, `category:flowers`, `category:wood` | Stone family versus exact Stone is distinct; stone swords are excluded from strict Stone. Flowers exclude dyes/pots; strict Wood excludes wooden tools. |
| S04 | Search all 25 canonical groups; test `armour`, singular aliases, bare `diamond`, `item:diamond`, `item:minecraft:stone` and an unknown explicit category | Completion/query forms work unquoted. Bare diamond retains prefix lookup; exact diamond excludes equipment. Unknown explicit category does not silently fall back to item names. Custom display names do not change membership. |
| S05 | Search matching contents in each supported colored shulker and bundle; compare opted-out shop | Container contents match within existing traversal bounds; configured box/bundle remains the traded unit. Opted-out shops stay absent. |
| S06 | Search an empty SELL item, then a SELL with less stock than its configured per-trade amount; repeat with another inventory already open | Player receives `<query> is currently not in stock.`; no empty results inventory opens and the existing inventory is preserved. |
| S07 | Search an item with no matching shops; switch a SELL-only result to BUY direction | No-match feedback is distinct from stock shortage. Rejected direction change preserves the current inventory and its usable controls. |
| S08 | Mix stocked/empty SELL and BUY shops; use direction, stock toggle, sort and pagination | Stocked results remain usable. BUY shops can appear with empty containers. Explicit out-of-stock display and admin exceptions retain existing semantics. Direction persists across sort/stock/page changes; accepted direction changes return to page one. |
| S09 | Drain/restock a matching shop after opening results; refresh and attempt a trade | Refreshed search reflects new recorded stock; transaction rechecks live stock/funds/capacity. No negative stock, duplicate items or charge for rejected trade. Category searches do not show a misleading single-material ticker. |

Canonical groups: tools, combat, weapons, armor, foliage, flowers, wood, stone, redstone, building, decoration, food, farming, ores, ore_blocks, materials, brewing, potions, enchanting, storage, shulker, transport, lighting, workstations, drops. This is command lookup; no category-browser menu is introduced.

## Login warnings — REQ-367 through REQ-369

Defaults in source: enabled; 24-hour warning window; insufficient-funds warnings enabled; at most 10 messages; 300-second reconnect cooldown. Confirm the actual loaded TEST configuration. Deadlines are existing prepaid-renewal/grace terms; warnings do not auto-charge rent.

| ID | Action | Required result |
| --- | --- | --- |
| W01 | Join as personal owner/member and authorized guild manager with due fixtures within 24 hours; compare outside-window fixture | Relevant deadlines and manual purchase-sign renewal instruction appear. Outside-window OWNED stall is not warned. Unauthorized guild member/outsider receives no stall notice. |
| W02 | Join with a GRACE stall; compare inactive/unowned stall | Relevant grace deadline is reported; inactive/unowned stalls do not receive renewal notices. |
| W03 | Compare insufficient versus sufficient personal balance, then insufficient versus sufficient guild bank | Personal warning uses the joining responsible payer; guild warning uses the guild bank. No other player's balance is disclosed; balance and deadline do not change. Zero renewal amount does not generate insufficient-funds feedback. |
| W04 | Disable warning/funds settings in an authorized test profile; exercise unavailable economy/guild balance | Disabled notices stay disabled. Unknown balance is not asserted to be insufficient; plugin remains functional. Record actual failure injection or mark BLOCKED. |
| W05 | Rapid reconnect, disconnect while discovery is pending, then join with more than 10 relevant stalls | Cooldown/coalescing bound messages; stale session gets no notice. Message cap/overflow summary works. No main-thread repository query, accumulating pending queue or funds mutation is observed. |

Use natural waiting or a reviewed isolated fixture tool for exact 24-hour boundary and failure-path setup. Do not edit live database deadlines or shorten global rent policy merely to accelerate acceptance. Thread/queue claims require instrumentation/log evidence; a screenshot alone does not prove them.

## Stall policy, blacklists and allied grants — REQ-373, REQ-374, REQ-376; Guilds REQ-143/144

| ID | Action | Required result |
| --- | --- | --- |
| A01 | Guild owner and manager open guild stall details then flags/access; compare unprivileged member; test `/stallaccess settings <stall>` directly | Java and Bedrock shortcut follows EDIT_SHOP_STOCK / Market MANAGE_SHOPS authority. Direct command does not bypass authority. Current permission is rechecked on click/save. |
| A02 | Open settings, revoke manager rank, then submit old UI; repeat while stall is durably moderation-reserved | Stale/unauthorized/reserved mutation is rejected without changing stored policy. Ordinary permitted management remains functional. |
| A03 | Add `/stallaccess blacklist <stall> <player> true`; attempt entry, teleport, trade, pickup and inventory use; remove using menu or `false` | Target is denied across entry/trade/access paths; removing entry restores ordinary policy. Test a blacklisted selected ally: blacklist overrides grants. Already-inside denial uses a loaded allowed exit when available; if none is available, trade/pickup remain denied and player can leave normally. |
| A04 | Try to blacklist an authorized stall manager; resolve a Bedrock player name | Authorized manager cannot be blacklisted. Correct UUID is stored for the resolved player; no unrelated account is blocked. Unknown name/invalid boolean does not mutate policy. |
| A05 | Toggle visitor ENTRY/TRADE/ANVIL/LECTERN/DOORS/BUTTONS/LEVERS/ITEM_PICKUP individually, then restore | Actual interaction follows each flag; chest/stock/price privilege is not silently granted to ordinary visitors. Exercise gate/trapdoor, damaged anvil and stale open inventory/click/drag paths. Existing region restrictions remain effective. |
| A06 | Start with active allies B/C and no selected grants; restrict ordinary visitors | Allies have no special grants by default. Merely being allied does not bypass restricted entry or trade. |
| A07 | Grant B ENTRY only, then TRADE; leave C unselected; selectively test B ANVIL/LECTERN/CHESTS/STOCK/PRICES grants | Only the named guild and chosen capabilities gain access. Owner choice controls shop/container/stock/price rights; grants never imply building, ownership, policy management, shop deletion or trust delegation. Market and WorldGuard protections both apply. |
| A08 | Remove B's grant, end the alliance, change B membership; separately exercise absent companion in an isolated profile | Access is revoked using current relation/membership. Pending/enemy/self/absent alliances cannot authorize access. Missing optional service denies allied grants while ordinary policy still works. |
| A09 | Restart authorized isolated TEST after changing flags/blacklist/effects/grants; transfer only the disposable fixture to a successor through the supported workflow | Settings persist for the same ownership. Former owner's settings do not apply to successor; stale save from old UI fails. No stalled cache grants old authority. No stall reset is used. |
| A10 | Exercise policy save failure/uncertain commit using a reviewed isolated harness, on the actual supported TEST database | Confirmed rollback preserves prior policy; uncertain state denies access until supported durable reconciliation. Record adapter/database evidence, not a mocked unit-test pass. Never manually edit production locks or rows. |

## Potion and restocking protection — REQ-375

Defaults include blocked INVISIBILITY and incoming potions disabled. Effect protection does not guarantee all dropped stock is safe: after suppression a visible visitor can still collect drops if ITEM_PICKUP is allowed. Verify pickup-deny configuration separately.

| ID | Action | Required result |
| --- | --- | --- |
| P01 | Enter while already invisible; stand inside when invisibility blocking is enabled; drink/reapply a blocked effect inside | Existing invisibility is suppressed and incoming blocked application is rejected. Nearby observer can see the visitor after suppression. Unblocked selected effects remain unchanged. |
| P02 | Exit after measurable time; repeat after the original duration expires | Only remaining unexpired duration returns; exit never renews the original full duration. Expired effect does not return. |
| P03 | Quit/rejoin while suppressed; die while suppressed; separately perform authorized orderly plugin/server shutdown | Quit/orderly disable restores only valid remaining duration. Death discards the held effect. Abrupt process kill is not an effect-restoration acceptance claim. |
| P04 | Test splash, lingering/cloud and tipped-arrow delivery from inside/outside; vary selected effects and incoming setting | Existing global market potion protections remain enforced; stall settings cannot be assumed to override a stricter global listener. Blocked effects do not sneak through boundary/race paths. Record actual configured listener outcome. |
| P05 | Visitor camps during manager restocking; use disposable shulker/drops with visitor ITEM_PICKUP disabled; repeat as blacklisted ally | Suppression prevents invisible camping; pickup restriction/blacklist prevents visitor theft. Manager restocking remains authorized and no items duplicate/disappear. Capture both clients and before/after item counts. |

## Private shop direction trails — REQ-370 through REQ-372

Defaults: enabled, 60 seconds, 256-block range, 64 active trails, global 200 particles per render. It is a straight direction guide, not obstacle-aware routing.

| ID | Action | Required result |
| --- | --- | --- |
| T01 | Normal player selects result in `/finditem`, repeat `/shop search`; place observer nearby | Selecting player sees direction particles; observer does not. Normal player is not teleported. Verify actual Java and Bedrock rendering separately. |
| T02 | Select another result, use sort/direction/page controls, then `/shop trail stop` | Selection replaces previous trail; controls retain the callback; cancellation stops it without affecting another player's trail. |
| T03 | Arrive, wait 60 seconds, disconnect, die, change world; separately authorized plugin disable | Trail terminates on each lifecycle boundary and does not return after reconnect. Record each boundary independently. |
| T04 | Select too-distant/other-world destination; approach unloaded terrain; place wall between start/destination | Range/world rejection is safe. No terrain is force-loaded and particles stay on loaded terrain. Guide does not claim to navigate the wall or teleport the player. Verify chunk state with approved instrumentation. |
| T05 | Staff normal-click then shift-click; disable trails in isolated profile | Existing normal-click admin teleport remains; shift-click guides. Disabled guide makes no particles and does not grant normal players teleport authority. |
| T06 | Exercise configured active/render limits using reviewed isolated load harness | Caps and rotating budget hold without per-tick SQL or leaked tasks. Record measurements; mark BLOCKED if no harness exists rather than declaring a player smoke test a performance pass. |

## Persistence, cleanup and acceptance decision

- [ ] D01 — Record V034 stall policy migration on the actual isolated SQLite/MariaDB profile. Confirm valid persisted flags, blacklist UUIDs, ally IDs/capabilities and ownership/revision fencing after restart. Native MariaDB acceptance is separate from SQLite and mocked tests.
- [ ] D02 — Compare relevant balances, rent deadlines, shop quantities and unrelated ownership/permissions before and after the run. Unexpected mutation or access grant fails acceptance.
- [ ] D03 — Restore disposable fixtures, ranks, guild relations, flags/effects, test funds and configuration through supported workflows; stop trails and remove test drops. Do not reset stalls. For a migration rollback, restore the paired TEST binary/configuration/database backup through a separately authorized operation rather than downgrading a migrated database in place.
- [ ] D04 — Attach evidence and issue links for every failure. Mark source tests, hosted checks, uploaded files, loaded server versions, native database acceptance and Java/Bedrock client acceptance separately. Any BLOCKED or NOT RUN critical case remains open.
- [ ] D05 — Maintainer approves the final source pair and unresolved findings. Production release remains a later reviewed merge/pin/build and specifically authorized deployment decision.

## SPEAR documentation verification

Spec: acceptance boundaries above derive from approved requirements and current source adapters. Prove: trace scenarios to the pinned implementation and previous regression records; no historical red/green or TEST result is fabricated. Engine: not applicable to a documentation-only checklist; no runtime change. Arch: includes optional service, permissions, WorldGuard, database/cache, player-session and client boundaries. Refine: verify commands/defaults against source, links, case IDs, fixture cleanup and `git diff --check`. Project EARS/state helpers are absent; manual record maintained. No compile/test rerun is needed for this document-only change.

The existing [mobile interactive preview](https://enthusiamarket-mobile-menu.awareyak.chatgpt.site) is illustrative: its shops, stock, location and trails are simulated. This checklist adds no GUI or visual behavior and does not treat preview clicks as Minecraft acceptance.
