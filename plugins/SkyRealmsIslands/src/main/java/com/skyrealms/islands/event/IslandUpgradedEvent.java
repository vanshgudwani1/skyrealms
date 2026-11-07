package com.skyrealms.islands.event;

import com.skyrealms.islands.model.IslandUpgrade;
import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.island.upgraded.v1
 */
public final class IslandUpgradedEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.island.upgraded.v1";

    private final UUID islandId;
    private final IslandUpgrade upgrade;
    private final Instant timestamp;

    public IslandUpgradedEvent(@NotNull UUID islandId, @NotNull IslandUpgrade upgrade) {
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.upgrade = Objects.requireNonNull(upgrade, "upgrade cannot be null");
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
    public IslandUpgrade upgrade() {
        return upgrade;
    }
}
