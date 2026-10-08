package net.enthusia.market.api.guild;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Persisted ownership/rent data plus optional live region coordinates. */
public record GuildStallSnapshot(
        String id, String region, String world, String state,
        long rent, long intervalSeconds, Instant nextRentAt, Instant graceEndsAt,
        String coordinates, List<GuildStallMember> members
) {
    /** Defensively copy roster data; missing coordinates/deadlines remain null. */
    public GuildStallSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(state, "state");
        members = List.copyOf(members);
    }
}
