---
title: Shop search
audience: player
topic: shop-search
summary: Find shops buying or selling items, including colored shulkers and container contents.
keywords: [shop, search, find, lookup, query]
related: [buy-sell-trade, shop-creation]
updated: 2026-10-08
---

# Shop search

Find shops selling what you need — or buying what you have.

```text
/shop search <item>
/finditem <item>
```

## Basic search

Type any Minecraft item name or prefix. Tab-complete suggests material names and categories matching your typed prefix, case-insensitively. Existing individual searches continue to work.

```text
/shop search diamond
/shop search ender_pearl
/shop search oak_log
/shop search category:combat
/shop search item:stone
```

Results open in a GUI showing:

- The item and shop direction (BUY/SELL/TRADE).
- Per-trade amount, price, and remaining stock.
- Shop owner and location.

## Filter by direction

Click **Shop Type** (the hopper in the top row) to cycle through:

- **All shop types:** the default combined results.
- **Shop sells (you buy):** only shops selling the item to you.
- **Shop buys (you sell):** only shops buying the item from you.

The filter stays selected when you sort, change the out-of-stock toggle, or move between pages. Changing shop type returns to page one. Existing trade shops remain in the combined view; this search control does not enable barter.

## Categories and exact items

Use `category:<name>` to select a defined group, or `item:<material>` for one exact Minecraft material. For example, `category:stone` includes stone building families, while `item:stone` finds only Stone. `item:diamond` excludes diamond tools and armor; plain `diamond` keeps the existing prefix search. Namespace forms such as `item:minecraft:stone` also work. Unknown explicit categories do not fall back to item-name matching.

| Category | Includes |
| --- | --- |
| `tools` | Mining/farming/utility equipment; existing sword inclusion retained |
| `combat` | Weapons, armor, arrows, shields, totems and combat consumables |
| `weapons`, `armor` | Offensive equipment; wearable protection including horse/wolf armor and elytra |
| `foliage`, `flowers` | Plants/leaves/moss/fungi; a narrower flower group |
| `wood`, `stone` | Defined building families and their variants |
| `redstone` | Components, automation and input/output blocks |
| `building`, `decoration`, `lighting` | Building materials; decoration; light sources |
| `food`, `farming` | Edible items; crops/seeds/growing supplies |
| `ores`, `ore_blocks`, `materials` | Existing broad ore/resource group; ore blocks/ancient debris; crafting resources |
| `potions`, `brewing`, `enchanting` | Potion items; ingredients/equipment; books/enchanting supplies |
| `storage`, `shulker` | Containers including bundles; all shulker-box colors |
| `transport`, `workstations`, `drops` | Travel/rails; crafting/processing blocks; mob drops |

Groups overlap deliberately: axes are Tools, Weapons and Combat; flowers are Flowers, Foliage and Decoration. Classification uses the actual material, not an item's custom display name. Enchanting does not mean every enchanted tool; look up the tool's category or material.

Bare aliases still work (`tool`, `weapon`, `armour`, `potion`, `ore`, `flower`, `stones`, `shulker_box`). Legacy bare queries retain their old item-prefix coverage as well as expanded category membership. Use explicit `category:wood` to exclude incidental names such as wooden tools or mushroom stems, and `category:redstone` to exclude redstone ore. The legacy broad `ores` group is preserved; `ore_blocks` narrows it without removing old searches.

### Stone and flowers

Search `stone` or `stones` for stone building blocks and their slabs, stairs, walls and other building variants. This includes cobblestone, granite, diorite, andesite, deepslate, tuff, blackstone, basalt, calcite, dripstone blocks and end stone. Stone tools, stonecutters, ores, redstone and glowstone are excluded. Use `stone_sword` or another material name to search those items directly.

Search `flower` or `flowers` for flower items, including tall flowers, petals, eyeblossoms, wildflowers, cactus flowers, spore blossoms and flowering azalea. Seeds, dyes, flower pots and leaves are excluded. Both categories also find matching contents inside supported shulker boxes and bundles, while respecting shop search opt-outs.

```text
/shop search stone
/finditem flowers
```

## Pagination

Use the previous/next arrows at the bottom to change pages. The menu shows 36 results per page. The stock toggle shows or hides empty selling shops; buying shops can be listed with empty containers because you supply their items. Capacity and owner funds are checked when trading.

## Shulkers and container contents

`shulker` and `shulker_box` find uncolored boxes and every colored variant. A specific query such as `red_shulker_box` stays color-specific. Searches also inspect supported shulker and bundle contents, so searching for `gunpowder` can find a shop selling a box containing it. The result identifies the matched contents; the shop still trades its configured box or bundle.

## What shops are searchable

New shops are **searchable by default**. Shop owners can toggle this in the edit GUI (`/shop edit`). The server can change the default via the `shop.search.default` config key.

## Tips

Click a search result for a private particle direction guide to the shop. It lasts up to 60 seconds by default and stops when you arrive. Cancel it with `/shop trail stop`; selecting another shop replaces it. Guides are limited to nearby shops in your current world. They point toward the destination rather than finding a safe route around walls, so use normal paths. Staff retain normal-click teleport; shift-click starts a guide.

- **Sell search** means you're looking to BUY from shops that SELL. You're the customer.
- **Buy search** means you're looking to SELL to shops that BUY. You're the supplier.
- Results use the recorded matching stock count. Refreshing the search captures newer shop data; the trade checks current availability.
