# Private finder glow, pulses and walkway routing

The existing search result guide now renders actual glowing, full-bright glass display beams around the selected stall and a separate 1.2-block box around its exact sign. Displays have no collision, are hidden by default before world insertion, are nonpersistent and are shown only through Player.showEntity to the recipient. Other players keep their normal view. No scoreboard teams or existing entities are modified. Java clients use glow; Floodgate Bedrock clients retain private particles. GPU appearance and two-client privacy require native acceptance.

## Settings

All settings live under finderTrail in enthusiamarket.yaml; restart the plugin/server through the normal update path. Existing values are preserved and absent keys gain defaults.

- intensity: LOW (6), NORMAL (12), HIGH (24) direction samples per render; shared maxParticlesPerRender remains the ceiling.
- pulseSpeed: 2.0 blocks/sec by default, normalized to 0..8; zero keeps a static trail. Pulses follow route segments, including turns.
- arrivalSound / arrivalSoundName / arrivalVolume / arrivalPitch: enabled, BLOCK_NOTE_BLOCK_PLING, 0.4, 1.5. Unknown sound falls back to pling; volume 0..1 and pitch 0.5..2.
- outline.glow: true; false restores particle boundaries.
- outline.beamWidth: 0.04, bounded 0.01..0.2 blocks.
- outline.maxDisplaysPerPlayer: 48, bounded 0..128.
- outline.maxDisplays: 512 globally, bounded 0..1024. A complete shape must fit; otherwise use particles. Zero disables displays.
- Existing outline settings remain: enabled, revealDistance (24), arrivalSeconds (10), height (3), color (#FFC857), showShopMarker and particle fallback spacing/size/cap.

The sign box remains available if region geometry is missing. Arrival sound/status now occurs even without an outline. Without valid region geometry, arrival feedback lasts one render, preserving the prior no-outline termination boundary. Locator and particles point at the next route point while the action bar shows remaining route distance to the shop.

## Explicit walkways

No terrain is searched, no chunks are loaded, and no safe route is inferred. Operators must mark walkable node coordinates. Default nodes is empty, retaining direct guidance. Each node has id, world, x, y, z and links. Links are bidirectional; unknown links are ignored. Maximum 128 nodes per world; duplicate IDs, oversized/disconnected graphs and endpoints outside attachmentDistance fall back to direct guidance. Attachment distance defaults to 12 blocks, bounded 1..32. Choose endpoint nodes and attachment distances that do not attach across walls.

Example only (these are not installed market coordinates):

```yaml
finderTrail:
  routing:
    enabled: true
    attachmentDistance: 6.0
    nodes:
      - id: entrance
        world: market
        x: 0.5
        y: 65.0
        z: 0.5
        links: [corner]
      - id: corner
        world: market
        x: 0.5
        y: 65.0
        z: 20.5
        links: [shops]
      - id: shops
        world: market
        x: 20.5
        y: 65.0
        z: 20.5
        links: []
```

At selection, the adapter captures a shortest path on that explicit graph. A route point advances within 2.5 blocks; getting close to the shop through a wall does not bypass outstanding route points. Re-select the shop to replan after leaving the route. These nodes are operator configuration, not a new permission or management GUI.

## Lifecycle and costs

Existing 60-second duration, range/player caps, shared rotating particle budget and private locator mode preferences remain. Unloaded geometry, cancellation, replacement, expiry, quit, death, world change, master disable and shutdown remove owned displays. Every traversed beam chunk is checked without loading it. Displays are cached instead of spawned every render and excluded by their owned UUIDs from stall entity-limit rescans.

No schema, rent, trading, guild build, resource-pack, production data or permission changes. The existing Nexo locator add-on remains valid. TEST builds are unmerged and do not establish production readiness.
