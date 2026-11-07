package com.skyrealms.islands.event;

import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.island.deleted.v1
 */
public final class IslandDeletedEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.island.deleted.v1";

    private final UUID islandId;
    private final UUID ownerUuid;
    private final Instant timestamp;

    public IslandDeletedEvent(@NotNull UUID islandId, @NotNull UUID ownerUuid) {
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");
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
    public UUID ownerUuid() {
        return ownerUuid;
    }
}
