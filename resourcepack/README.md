# Stall locator icon

Merge `Nexo/pack/external_packs/EnthusiaMarketLocator` into the corresponding TEST Nexo folder, then use Nexo's supported pack rebuild/send workflow. This add-on changes only the `enthusiamarket` namespace. It does not override player dots or vanilla HUD sprites.

Set `finderTrail.locator.style: enthusiamarket:stall` only after the pack is installed and its generated output contains the style and texture. Keep `requireResourcePack: true`; missing/declined packs use the vanilla gold dot. Default plugin configuration uses `minecraft:default`, so servers without this add-on remain usable.

The icon is the existing TEST guild-stall artwork from `plugins/Nexo/pack/assets/lumaguilds/textures/enthusia/stall.png`, read on 2026-10-18, SHA-256 `68b3891b45659cab82eaa3d6f2e976353ff9d2d8afeffe8f428a4711346ce200`. It is copied unchanged, 16x16 RGBA. Minecraft renders it in a fixed 9x9 waypoint slot with white tint to preserve its colors. No new Nexo item, font glyph, entity or custom model is needed.

Java 26.2/26.3 pack formats are declared. Bedrock uses the existing particles and action bar; this add-on does not claim Geyser conversion support. Native appearance and Nexo obfuscation compatibility must be verified on TEST. Server operators should retain their existing player-location privacy settings.
