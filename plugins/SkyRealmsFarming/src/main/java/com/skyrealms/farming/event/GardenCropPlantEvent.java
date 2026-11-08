package com.skyrealms.farming.event;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.platform.api.event.CancellableEvent;
import com.skyrealms.platform.api.event.SkyEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Event: skyrealms.garden.crop.plant.v1
 * Fired when a player plants a crop inside their Garden plot.
 */
public final class GardenCropPlantEvent implements SkyEvent, CancellableEvent {

    public static final String EVENT_NAME = "skyrealms.garden.crop.plant.v1";

    private final Player player;
    private final Garden garden;
    private final GardenPlot plot;
    private final String cropType;
    private final Location location;
    private final Instant timestamp;
    private boolean cancelled = false;

    public GardenCropPlantEvent(
            @NotNull Player player,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull String cropType,
            @NotNull Location location
    ) {
        this.player = Objects.requireNonNull(player, "player cannot be null");
        this.garden = Objects.requireNonNull(garden, "garden cannot be null");
        this.plot = Objects.requireNonNull(plot, "plot cannot be null");
        this.cropType = Objects.requireNonNull(cropType, "cropType cannot be null");
        this.location = Objects.requireNonNull(location, "location cannot be null");
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
    public Player player() {
        return player;
    }

    @NotNull
    public Garden garden() {
        return garden;
    }

    @NotNull
    public GardenPlot plot() {
        return plot;
    }

    @NotNull
    public String cropType() {
        return cropType;
    }

    @NotNull
    public Location location() {
        return location;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
