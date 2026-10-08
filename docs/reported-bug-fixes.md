# Discord bug fixes — pull request review

Scope: unresolved reports from the Market Discord audit and the LumaGuilds cross-check.
Source changes are published for pull request review. No JAR upload, deployment, production change or restart is part of this work.

## Acceptance requirements

- Guild rank permissions govern chest viewing, inventory changes, shop creation, price changes and deletion, including shops created by another member. Guild shop creator ownership must not bypass current guild permissions.
- Membership changes refresh WorldGuard projections immediately; runtime checks deny removed members even before projection repair. Empty rosters clear stale access. Restart repair includes offline members.
- Guild sellback requires management permission and sends any refund to the owning guild bank; failed payment preserves ownership.
- Guild auction bids persist their guild payment/ownership target, use the guild bank for escrow and refunds, and award ownership to that guild. Permission/membership/limits are revalidated at settlement. Solo bidding stays compatible.
- Java and Bedrock shop edits recheck authority when submitted and refresh the complete sign, even if stock did not change.
- Sneak-left-click shop creation works with a spear that emits an air interaction, using a bounded sign ray trace.
- Bucketed animals and occupied beehives match by meaningful contents without confusing incidental entity identity with item identity. Distinct variants, occupancy, enchantments and custom item metadata remain distinct. Actual inventory items are delivered.
- Visitors can use anvils and read lecterns without taking or replacing a stall's book. Shop containers and hopper transfers remain protected, including both halves of double chests.

## Evidence boundaries

Repository baselines: Market `14351db4`, LumaGuilds `611d7bb0`.
Previously merged ownership and item-delivery fixes are retained and receive regression coverage.
Local tests/builds do not establish deployed version or Java/Bedrock player acceptance.

## Fixes ready for review

| Report or gap | Changed behavior | Primary source |
|---|---|---|
| Guild rank controls do not govern stall contents | Container viewing requires ACCESS_SHOP_CHESTS; stock changes and building require EDIT_SHOP_STOCK; price changes require MODIFY_SHOP_PRICES. Current guild rank takes precedence over shop creator/trusted identity. | ShopAccessPolicy.kt, GuildStallProtectionListener.kt, ShopManagementService.kt |
| Members join/leave without access refresh | Market listens for both membership events, refreshes on the server thread, and replaces empty rosters. Runtime checks also revoke access to inventories that were already open. Offline restart reconciliation remains supported. | GuildStallAccessSync.kt, LumaGuildsListenerRegistration.kt, GuildStallResyncOnStartup.kt |
| Manual signs bypass shop-creation rank gate | Stall management now requires current guild membership plus management permission. Java and Bedrock creation confirmations recheck the current stall and authority. | Stall.kt, ShopCreateListener.kt, CreateShopMenu.kt, BedrockCreateShopForm.kt |
| Guild owner cannot manage shops another member created | Guild stock managers can delete those shops and their linked containers; price editors can edit their prices. Shop creator identity remains available for history, without becoming a permission bypass. | ShopManagementService.kt, BlockProtectionListener.kt |
| Guild sellback unavailable | Authorised guild members can sell back stalls. Prepaid-rent refunds go to the owning guild bank. A failed refund restores the stall without clearing access or releasing its IP reservation. Solo delegated actors also refund the actual owner. | StallSellbackService.kt |
| Auction ownership always becomes personal | The Java bid menu offers personal/guild targets. `/em bidguild <auction> <amount> [guild-name-or-id]` is available to both clients. V030 persists the guild escrow target; outbids, cancellation, failed saves and denied awards refund that payer. Another member bidding for the same guild pays only the increase. Settlement checks current guild authority and awards guild ownership. Guild sellers receive guild-bank proceeds. | Bid.kt, AuctionLifecycleService.kt, AuctionRepositorySql.kt, AuctionBidMenu.kt, AdminCommands.kt |
| Market relies on private LumaGuilds bank internals | Added public systemBankWithdraw/systemBankDeposit methods using guild funds directly, with positive Int-bounded amount validation and existing bank safeguards. Market uses this API instead of reflecting the private BankService field or inventing a personal actor. | LumaGuilds api/GuildLookup.kt and GuildLookupImpl.kt; Market LumaGuildsGuildProvider.kt |
| Edited price/quantity leaves stale sign text | The stock refresher tracks sign-affecting shop changes as well as stock and redraws the item, quantity, price, header and stock. It retries when sign chunks become available. | ContainerStockListener.kt |
| Spear-held creation fails | Sneaking main-hand spear air interactions resolve a target within six blocks and follow normal sign/container/authority checks. | ShopCreateListener.kt |
| Axolotl/hive stock rejects equivalent creature contents | Special-container comparison ignores incidental entity identity and timers in serialized NBT, preserving creature variants, baby/adult distinctions, occupancy and unrelated custom metadata. Actual container stacks remain the delivered items. Malformed NBT fails closed. | ItemStackMatch.kt, SpecialItemMatch.kt |
| Visitors cannot read lecterns/use public workstations | Provisioned stall interaction flags allow public workstation use. Visitors read a copy of a written lectern book; taking/replacing books and manipulating decorations remain protected. Existing regions require an authorised flag resync when this is eventually deployed. | WorldGuardRegionProvisioner.kt, StallVisitorProtectionListener.kt |
| Hopper/cart access through opposite double-chest half | Hopper transfers check both chest halves, and reject extraction/insertion if any associated shop forbids it. | HopperControlListener.kt |
| Bedrock editing and creation gaps | `/shop edit` routes Bedrock users to forms, including guild-managed shops. Quantity and price inputs use the same permission checks as Java. BUY/SELL creation no longer passes a zero barter-cost override. | ShopCommands.kt, BedrockOwnedShopsForm.kt, BedrockShopEditForm.kt, BedrockCreateShopForm.kt |

## Validation and review boundary

- Market full test suite: 806 tests, 0 failures, 0 errors, 7 skipped. Architecture checks (included), Detekt and shaded local build passed.
- LumaGuilds GuildLookupImpl and BankServiceBukkitDelegation tests: 41 tests, zero failures/errors/skips; shaded local build passed.
- The fresh-build blockers were resolved using the repository-pinned Nexus source (`057836b`) published only to the local Maven cache, plus existing local RoseChat/CombatLogX compile dependencies. Market JUnit modules were aligned to fix its test-engine discovery mismatch.
- Detekt runs under the available Java 21 runtime because its bundled analyzer cannot parse Java 25's runtime version; production/test compilation stays on Java 25 and Paper 26.2.
- These are local code/build results. Real player testing of creature-item samples, spears, anvils, WorldGuard interaction precedence and Java/Bedrock menus remains unperformed.
- Both plugin updates are a coordinated pair: this Market build requires the new LumaGuilds bank API. V030 is an additive migration; legacy personal auction bids remain personal.
- Earlier merged ownership reconciliation and real-item delivery fixes were preserved. No production database repair, WorldGuard resync, server restart or player experiment was performed.
- No JAR was uploaded and no production changes were made.
- Custom price/bulk input privacy: intercept the legacy chat event before RoseChat's LOW-priority broadcast; retain Paper fallback and main-thread input handling, avoiding duplicate callbacks. Five event-dispatch regressions cover privacy, normal chat, Paper fallback and duplicate prevention. Full local suite: 817 tests, 0 failures/errors, 7 skipped; Detekt passed. Live two-player/chat-bridge acceptance remains unperformed.
- Item frames are now configurable with unlimited (`-1`) defaults for both normal/glow frames in all bundled categories. Unlimited frame placement bypasses the shared entity total; zero/positive caps remain enforced. Existing operator files are preserved. The frame refinement passed 811 tests (7 skipped) and Detekt; production adoption requires an authorized configuration change/restart after canonical delivery.
- The 2026-10-04 SPEAR check refinement revalidated all 806 tests and Detekt against the released LumaGuilds 3.0.17 runtime containing the merged bank API. See [verification.md](verification.md) for artifact provenance, hosted-check gates, MariaDB skips and owning monorepo pins.
