package com.skyrealms.farming.event;

import com.skyrealms.platform.api.event.SkyEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.garden.plot.unlock.v1
 * Fired when an island unlocks a new plot in their Garden.
 */
public final class GardenPlotUnlockEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.garden.plot.unlock.v1";

    private final UUID islandId;
    private final int plotIndex;
    private final Player player;
    private final Instant timestamp;

    public GardenPlotUnlockEvent(
            @NotNull UUID islandId,
            int plotIndex,
            @Nullable Player player
    ) {
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.plotIndex = plotIndex;
        this.player = player;
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

    public int plotIndex() {
        return plotIndex;
    }

    @Nullable
    public Player player() {
        return player;
    }
}
