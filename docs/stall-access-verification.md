# Owner-controlled stall access

## Spec

REQ-373: WHEN an owner or authorized stall manager edits stall settings THE SYSTEM SHALL persist bounded visitor flags, per-player blacklists, selected potion-effect blocks and per-allied-guild capability grants, while rejecting locked or stale ownership mutations.

REQ-374: WHILE an access policy is active THE SYSTEM SHALL enforce it at entry, shop transactions, safe interactions, inventory/stock/price access and item pickup; blacklist denials SHALL override allied grants. Grants SHALL require a current alliance and SHALL be off by default. Allied access SHALL NOT grant building, stall ownership, policy management, shop deletion or trust delegation.

REQ-375: WHILE a selected effect such as invisibility is blocked inside a stall THE SYSTEM SHALL suppress an existing effect and reject incoming blocked effects, restoring only unexpired remaining duration on exit and preserving other effects. Death SHALL discard suppressed effects; disconnect/disable SHALL restore appropriately without extending duration.

REQ-376: WHEN ownership changes THE SYSTEM SHALL prevent the prior owner's flags/blacklists/allied grants from applying to the successor, and SHALL maintain ordinary/moderation authorization and persistence failure boundaries. Event hot paths SHALL use a mutation-maintained cache rather than repeated SQL.

User authorized owners/managers to blacklist and edit flags through guild menus; specifically called out invisible visitors stealing drops during restocking. Specific allied guilds/capabilities are selected by managers and off by default. Reviews are explicitly deferred; resets remain excluded. Base main 14351db plus prerequisite #197 7d7e54a. Companion Guilds menu/API delivery is a separate reviewed PR. Local EARS/state helpers absent; this manual requirement/task record uses SPEAR.

## Tasks

- [x] Inspect ownership, moderation, WorldGuard, trade and Guilds contracts.
- [x] Implement ownership-bound policy storage, cached enforcement and authorized UI/commands.
- [x] Implement selected-effect suppression with duration/lifecycle conservation.
- [x] Add opt-in per-ally capability configuration and optional current-alliance companion API.
- [x] Prove permissions, blacklists, ownership transfer, stale saves, persistence rollback, effects and actual GUI/transaction paths.
- [x] Complete supported local builds/architecture, inspect hosted reviews and prove pending-stack compatibility.

Local proof: five initial persisted policy cases plus existing listener/management contracts passed. Existing invisibility suppression/restoration and expiry tests passed against MockBukkit. The initial full Java 25/Paper 26.2 suite passed 828 cases with eight environment skips. The expanded full suite exposed a trustAll fixture/contract regression; retaining its existing no-refetch behavior while filtering current deletion authority corrected it. Expanded focused policy, rollback/uncertain-commit, trade, allied-management, effect and existing management cases pass. Final combined source rehearsal follows before handoff.

Settings use a separate portable SQLite/MariaDB table (V034; V031-033 reserved by pending history/XP/accounting work). Writes fence ownership, settings revision and durable moderation reservation. Failed insert rollback retains the previous policy; uncertain commits deny access until durable command reconciliation succeeds. Ordinary writes and moderation release refresh the ownership cache; event checks perform no SQL. Rare authorized settings commands write synchronously. No native MariaDB settings test or real player acceptance is claimed.

Use `/stallaccess settings <stall>` or the Guilds detail shortcut (#216). Add a blacklist with `/stallaccess blacklist <stall> <player> true`; the menu removes existing entries. Managers select flags, blocked effects and per-guild capability grants. Invisibility blocks include already active effects; incoming splash/lingering potions remain off by default. Entry denial evicts toward a loaded, allowed prior location or world spawn; if neither is allowed/loaded, trading/pickup remain denied and the player can leave normally. No terrain or items are deleted.

Selected effects are restored on exit, quit and plugin disable only for unexpired remaining duration; death discards the held effect. Abrupt process termination cannot run restoration callbacks and is not claimed as tested. Trail/search/warning work and this access work remain unmerged local review artifacts; no production change or stall reset.

Final delivery: reconciled Market #213 passed the clean 890-case suite with zero failures/errors and eight external skips, shadowJar/JaCoCo, architecture and Java-21 Detekt. Broader pending-stack rehearsal passed 972 cases, zero failures/errors, 14 skips. Current review threads are resolved/absent; Market hosted workflow approval is pending, while Guilds #216 hosted checks pass. See approved-features-delivery.md for tested commits and final artifact evidence. These task checks establish source implementation and local proof, not hosted build approval or production/client acceptance.
