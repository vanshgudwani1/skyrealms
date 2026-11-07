package com.skyrealms.islands.event;

import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.island.member.left.v1
 */
public final class IslandMemberLeftEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.island.member.left.v1";

    private final UUID islandId;
    private final UUID playerUuid;
    private final Instant timestamp;

    public IslandMemberLeftEvent(@NotNull UUID islandId, @NotNull UUID playerUuid) {
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.timestamp = Instant.now();
    }

    @Override
    @NotNull
    public String eventName() {
        return EVENT_NAME;
    }

    @Override
    @NotNull
    public Instant timestamp() {
        return timestamp;
    }

    @NotNull
    public UUID islandId() {
        return islandId;
    }

    @NotNull
    public UUID playerUuid() {
        return playerUuid;
    }
}
