# Guild shop counterparty attribution

## Requirements

- WHEN Market settles a guild shop sale or purchase, THE SYSTEM SHALL retain Guilds system accounting and record a readable sale-time buyer or seller name in the existing reason field.
- WHEN the settlement is reversed, THE SYSTEM SHALL identify the reversal without claiming a completed trade.
- IF a player name is invalid for safe display, THEN THE SYSTEM SHALL omit it without blocking or changing settlement.
- WHEN an older or automatic record has no counterparty, THE SYSTEM SHALL retain its existing System presentation.

## Evidence and boundaries

Fork Market main 0e0c771 and Guilds main cf9f29a; GuildLookup system bank methods accept a reason, preserve zero-UUID system actor, and write the reason into bank history. Gold ledger limits descriptions to 255 characters. Player-facing reason must stay short and avoid control/formatting characters. Do not change bank permissions, debit the player through the guild API, change XP, infer historical buyers, or expose UUIDs. The compatibility-history write is secondary to canonical gold, so a successful bank mutation does not guarantee a history entry. Bank entries occur during settlement and may be followed by compensation; identify them as payments, not proof of completed item delivery. Native TEST and mobile interactive preview remain separate from local test evidence.

## Fork merge release guard

The fork release workflow publishes on main pushes. Its job now skips only when the push head commit message contains `[skip release]`; other main pushes retain normal release behavior. This allows a merged-commit CI build for TEST without automatically publishing a release. Verify both the merge commit message and the triggered release/build workflow outcomes after merge.
