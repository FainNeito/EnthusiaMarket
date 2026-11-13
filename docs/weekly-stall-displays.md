# Weekly stall displays

WHEN an independently verified result for the last closed New York week is available, Market SHALL show that category's stall and owner in a hologram and use the verified player's skin for its NPC. For guilds the representative is the guild leader recorded for the award. IF no verified result exists, the category SHALL show a pending, unavailable, or empty state and remove any previous NPC.

The website reads separate player and guild results. Staff-reviewed finalized rows can override the automatic result. Capture the stall owner and representative when the sale happens so later ownership changes do not rewrite the week.

The automatic displays are opt-in and require separate positions. The existing PlaceholderAPI expansion remains compatible with the legacy solo endpoint. Use the bundled weekly-stall-displays.yml for positions and FancyNpcs 2.12.0 for skins. Retire the old manually placed TEST hologram before configuring an overlapping automatic display.

Verify a player winner, guild winner, missing result, verified no-sales result, missing skin, week rollover, and disable/re-enable in a native client before release. Project-local EARS/state tooling is absent.

## Automatic awards

WHEN a completed raw-gold SELL trade qualifies, Market SHALL capture the stall owner identity and the representative profile at sale time. Guild member purchases and self-trades do not qualify. Rankings use gross raw gold by stall and owner identity, with the latest sale providing the display name and skin. Equal totals use stall ID and then owner UUID as stable tie-breakers.

WHEN a generation-bound terminal poll has imported sales through the end of a closed New York week, the award endpoint SHALL rank player and guild stalls independently. A week that starts before the configured verified capture boundary remains pending. Generation changes, missing coverage, outstanding outbox work, and capture failures must prevent unsupported awards. Operators must rotate the generation after a restore, downgrade, or interruption of capture.

Before enabling automatic awards, verify capture on the intended server and set `award-capture-from` to that UTC instant and `award-generation` to the matching external UUID in `website-sales.yml`. After a capture interruption, clear any failure marker only after reviewing the outbox and set a new verified boundary on the reconciled generation. Install website migration 0004 and the updated poller before enabling awards. Check a real solo sale, a real outside-guild purchase and a guild-member purchase on TEST. Verify the first full closed week with both displays.
