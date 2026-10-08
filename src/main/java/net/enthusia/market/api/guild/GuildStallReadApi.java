package net.enthusia.market.api.guild;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Optional, read-only companion contract; never exposes moderation mutations. */
public interface GuildStallReadApi {
    /** Exact supported contract version. */
    int API_VERSION = 1;

    /** Implemented version; consumers reject incompatible versions. */
    int apiVersion();

    /** Fresh guild inventory; denied or failed reads complete exceptionally. */
    CompletionStage<List<GuildStallSnapshot>> guildStalls(UUID guildId, UUID viewerId);
}
