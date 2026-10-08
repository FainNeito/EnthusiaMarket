# Config — EnthusiaMarket

**Date:** 2026-05-24
**Status:** Spec (canonical; the `@ConfigFile("enthusiamarket")` annotated `EnthusiaMarketConfig` class in `src/main/kotlin/net/badgersmc/em/config/` generates `enthusiamarket.yaml` with documented defaults)
**Owner:** BadgersMC

Defines every config key the plugin reads, its type, default, source REQ, and which component consumes it. The Nexus `ConfigLoader` generates the YAML on first run; values are edited in `plugins/EnthusiaMarket/enthusiamarket.yaml`.

**Note:** The old `config.yml` (Bukkit Configuration API) has been replaced by the Nexus `@ConfigFile` system. The schema below is authoritative — the Nexus `ConfigLoader` uses reflection on the config class fields, so field names map directly to YAML keys. See `src/main/kotlin/net/badgersmc/em/config/EnthusiaMarketConfig.kt` for the canonical field layout.

## Conventions

- All durations are ISO-8601 strings (`PT5M`, `P1D`) unless the key suffix names a unit (`*-sec`, `*-ticks`).
- Money values are integer minor units in the Vault economy (typically whole-coin).
- Percentages are decimals (`0.05` = 5%).
- Booleans use lowercase `true`/`false`.

## 1. `market` — region binding

| Key | Type | Default | REQ | Used by |
|---|---|---|---|---|
| `market.world` | string | `world` | REQ-002 | `EnthusiaMarket.onEnable`, `ImportStallsService` |
| `market.region-prefix` | string | `stall_` | REQ-002 | `ImportStallsService` |

## 2. `rent` — periodic charge

| Key | Type | Default | REQ | Used by |
|---|---|---|---|---|
| `rent.mode` | enum `formula\|flat` | `formula` | REQ-003 | `RentTerms` factory |
| `rent.formula-pct` | decimal | `0.01` (1% of winning-bid per period) | REQ-003 | `RentTerms.formula(pct)` |
| `rent.flat-amount` | integer | `0` | REQ-003 | `RentTerms.flat(amount)` |
| `rent.collection-interval` | duration | `P1D` | REQ-003 | `RentCollectionService` scheduler |
| `rent.grace-period` | duration | `P3D` | REQ-004 | default/eviction in `RentCollectionService` |

## 3. `auction` — timed sales

| Key | Type | Default | REQ | Used by |
|---|---|---|---|---|
| `auction.default-duration` | duration | `PT24H` | REQ-007 | `AuctionLifecycleService.start` |
| `auction.min-duration` | duration | `PT15M` | REQ-007 | validation |
| `auction.max-duration` | duration | `P7D` | REQ-007 | validation |
| `auction.anti-snipe-sec` | integer (seconds) | `30` | Trigger window — bid within this of endAt | `Auction.antiSnipeWindow` |
| `auction.anti-snipe-extend-sec` | integer (seconds) | `30` | Extension duration added when triggered | `Auction.antiSnipeExtension` |
| `auction.fee-pct` | decimal | `0.05` (5% to system) | REQ-009 | settlement payout calc |
| `auction.min-starting-bid` | integer | `1` | REQ-007 | validation |

## 4. `shop` — sign trading

| Key | Type | Default | REQ | Used by |
|---|---|---|---|---|
| `shop.tax-pct` | decimal | `0.02` (2% to system) | REQ-006 | `ShopTradeService` |
| `shop.allow-bedrock-edit` | bool | `true` | REQ-011 | sign-edit form trigger |

## 5. `lumaguilds` — guild integration

| Key | Type | Default | REQ | Used by |
|---|---|---|---|---|
| `lumaguilds.enabled` | bool | `true` | REQ-010 | DI module wiring |
| `lumaguilds.manage-rank` | string (rank id) | `officer` | REQ-010 | `Stall.canManage(actor)` authorization |
| `lumaguilds.pay-from` | enum `bank\|leader` | `bank` | REQ-003 | rent debit source for guild stalls |

## 6. `database`

| Key | Type | Default | REQ | Used by |
|---|---|---|---|---|
| `database.type` | enum `sqlite\|mariadb` | `sqlite` | REQ-020 | `Database.open` |
| `database.sqlite-file` | path (relative to data folder) | `enthusiamarket.db` | REQ-020 | sqlite branch |
| `database.mariadb.host` | string | `localhost` | REQ-020 | mariadb branch |
| `database.mariadb.port` | integer | `3306` | REQ-020 | mariadb branch |
| `database.mariadb.database` | string | `enthusiamarket` | REQ-020 | mariadb branch |
| `database.mariadb.username` | string | `em` | REQ-020 | mariadb branch |
| `database.mariadb.password` | string | `""` | REQ-020 | mariadb branch |
| `database.pool.max-size` | integer | `10` | REQ-020 | Hikari config (not yet wired) |

## 7. `bedrock` — Floodgate

| Key | Type | Default | REQ | Used by |
|---|---|---|---|---|
| `bedrock.force-forms` | bool | `false` | REQ-011 | force Cumulus even when Floodgate absent (testing) |
| `bedrock.form-timeout-sec` | integer | `60` | REQ-011 | Cumulus form expiry |

## 8. `debug`

| Key | Type | Default | Used by |
|---|---|---|---|
| `debug.log-economy` | bool | `false` | `VaultEconomyProvider` |
| `debug.log-migrations` | bool | `true` | `Migrations.runAll` |

## Validation rules

- `rent.mode == flat` ⇒ `rent.flat-amount > 0`
- `rent.mode == formula` ⇒ `rent.formula-pct >= 0`
- `auction.min-duration <= auction.default-duration <= auction.max-duration`
- `0 <= shop.tax-pct + auction.fee-pct <= 1`
- `lumaguilds.enabled == false` ⇒ guild-owned stalls cannot be created (REQ-010 inactive)

Invalid config ⇒ disable plugin + log explicit error (parallels REQ-041).

## Private finder outline — REQ-381/382

Edit `plugins/EnthusiaMarket/enthusiamarket.yaml`, preserving existing keys. These keys use their actual camelCase Nexus property names. `/em reload` reloads the existing configuration object; appearance/limits are consumed on each render, while region geometry is captured on selection. Reselect a shop after enabling an outline that was disabled at selection. Disabling the finder clears the destination; disabling only the outline preserves direction behavior and ends an active arrival phase.

```yaml
finderTrail:
  enabled: true
  durationSeconds: 60
  maxRange: 256.0
  maxActive: 64
  maxParticlesPerRender: 200
  outline:
    enabled: true
    revealDistance: 24.0
    arrivalSeconds: 10
    height: 3.0
    spacing: 1.0
    maxParticlesPerPlayer: 64
    color: "#FFC857"
    particleSize: 1.0
    showShopMarker: true
```

| Outline key | Default | Effective bounds / behavior |
| --- | --- | --- |
| `enabled` | true | False retains the existing direction-only finder |
| `revealDistance` | 24 | 3–256 blocks from the selected shop; finder range still applies |
| `arrivalSeconds` | 10 | 0–60 seconds; zero disables arrival retention |
| `height` | 3 | 0.5–16 blocks, clipped to the region's vertical span near the shop |
| `spacing` | 1 | 0.25–8 blocks; widened automatically to meet particle caps |
| `maxParticlesPerPlayer` | 64 | 0–128 outline particles per render; also constrained by the shared finder budget |
| `color` | #FFC857 | Six-digit RGB hex with leading #; malformed values use gold |
| `particleSize` | 1 | 0.25–4; non-finite values use 1 |
| `showShopMarker` | true | Prioritizes four particles above the selected shop |

Non-finite geometry/configuration is rejected or normalized without an unbounded planner. Non-finite distance, height and spacing use their defaults. One destination remains active per player, including arrival. Direction expiry is still 60 seconds by default; arriving before expiry permits up to ten additional seconds of outline, without restarting that arrival timer. Disablement/cancel/world change/death/disconnect/shutdown clears the applicable phase. No outline entities or blocks are created; particles are sent only to the finding player and only in loaded chunks.
