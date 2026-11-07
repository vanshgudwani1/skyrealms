package com.skyrealms.islands.event;

import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.island.visited.v1
 */
public final class IslandVisitedEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.island.visited.v1";

    private final UUID islandId;
    private final UUID visitorUuid;
    private final Instant timestamp;

    public IslandVisitedEvent(@NotNull UUID islandId, @NotNull UUID visitorUuid) {
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.visitorUuid = Objects.requireNonNull(visitorUuid, "visitorUuid cannot be null");
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
    public UUID visitorUuid() {
        return visitorUuid;
    }
}
