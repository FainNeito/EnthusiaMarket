---
title: Shop search
audience: player
topic: shop-search
summary: Find shops buying or selling items, including colored shulkers and container contents.
keywords: [shop, search, find, lookup, query]
related: [buy-sell-trade, shop-creation]
updated: 2026-10-07
---

# Shop search

Find shops selling what you need — or buying what you have.

```text
/shop search <item>
/finditem <item>
```

## Basic search

Type any Minecraft item name or prefix. Tab-complete is supported — press Tab to see material names matching your typed prefix (case-insensitive). Categories such as `armor`, `tools`, `potions` and `food` also work.

```text
/shop search diamond
/shop search ender_pearl
/shop search oak_log
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

## Stone and flower categories

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

- **Sell search** means you're looking to BUY from shops that SELL. You're the customer.
- **Buy search** means you're looking to SELL to shops that BUY. You're the supplier.
- Results use the recorded matching stock count. Refreshing the search captures newer shop data; the trade checks current availability.
