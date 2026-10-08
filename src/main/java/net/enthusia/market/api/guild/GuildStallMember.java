package net.enthusia.market.api.guild;

import java.util.Set;
import java.util.UUID;
import java.util.Objects;

/** Immutable current-member shop capabilities, not an authority to perform actions. */
public record GuildStallMember(UUID playerId, Set<String> permissions) {
    /** Defensively copy the capability set across the plugin boundary. */
    public GuildStallMember {
        Objects.requireNonNull(playerId, "playerId");
        permissions = Set.copyOf(permissions);
    }
}
