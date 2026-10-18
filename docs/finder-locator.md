# Finding a shop with the locator bar

Run `/finditem redstone` (or another item/category), then select a shop. Staff use Shift-click to start guidance; their ordinary teleport click is preserved.

- `/shop trail stop` cancels guidance.
- `/shop trail mode locator` uses the private locator marker, with particle fallback when unsupported.
- `/shop trail mode particles` uses the original direction particles.
- `/shop trail mode both` combines both.
- `/shop trail mode default` removes the personal override and follows server configuration.

Preferences persist with the player's data. The action bar names the stall, shop item and distance; arrival feedback replaces it while the existing arrival outline finishes. The locator marker is removed on arrival, cancellation, expiry, world change, death, disconnect and plugin shutdown. Selecting another shop replaces the old destination.

## Configuration

These keys live in the existing `enthusiamarket.yaml` under `finderTrail`:

```yaml
finderTrail:
  enabled: true
  mode: BOTH
  showDistance: true
  arrivalSound: true
  durationSeconds: 60
  maxRange: 256.0
  maxActive: 64
  maxParticlesPerRender: 200
  locator:
    enabled: true
    style: minecraft:default
    requireResourcePack: true
```

The existing `finderTrail.outline` settings remain independent of the navigation mode. Use `enthusiamarket:stall` only after installing the supplied [Nexo add-on](../resourcepack/README.md). Missing/declined packs use a vanilla gold marker. The supplied multicolor artwork uses white waypoint tint. The bar has a fixed 9x9 icon slot and shows destinations within its normal forward-facing viewing range; direction indicators are not obstacle-aware paths.

The optional `packetevents` plugin must support waypoint packets (tested with 2.14.0). Older Java clients, Bedrock/Floodgate, disabled locator support and unavailable transports fall back to particles. No vanilla player waypoints, player attributes, gamerules, permissions or entities are changed.

Native Java acceptance, Geyser limitations and TEST artifact provenance are recorded separately in [verification](finder-locator-verification.md).
