package com.skyrealms.islands.event;

import com.skyrealms.islands.model.Island;
import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Event: skyrealms.island.created.v1
 */
public final class IslandCreatedEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.island.created.v1";

    private final Island island;
    private final Instant timestamp;

    public IslandCreatedEvent(@NotNull Island island) {
        this.island = Objects.requireNonNull(island, "island cannot be null");
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
    public Island island() {
        return island;
    }
}
