package com.skyrealms.farming.event;

import com.skyrealms.farming.model.CropNode;
import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.platform.api.event.CancellableEvent;
import com.skyrealms.platform.api.event.SkyEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Event: skyrealms.farming.generator.harvest.v1
 */
public final class FarmingGeneratorHarvestEvent implements SkyEvent, CancellableEvent {

    public static final String EVENT_NAME = "skyrealms.farming.generator.harvest.v1";

    private final FarmingGenerator generator;
    private final CropNode cropNode;
    private final Player player;
    private long farmingXpAwarded;
    private final Instant timestamp;
    private boolean cancelled = false;

    public FarmingGeneratorHarvestEvent(
            @NotNull FarmingGenerator generator,
            @NotNull CropNode cropNode,
            @NotNull Player player,
            long farmingXpAwarded
    ) {
        this.generator = Objects.requireNonNull(generator, "generator cannot be null");
        this.cropNode = Objects.requireNonNull(cropNode, "cropNode cannot be null");
        this.player = Objects.requireNonNull(player, "player cannot be null");
        this.farmingXpAwarded = farmingXpAwarded;
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
    public FarmingGenerator generator() {
        return generator;
    }

    @NotNull
    public CropNode cropNode() {
        return cropNode;
    }

    @NotNull
    public Player player() {
        return player;
    }

    public long farmingXpAwarded() {
        return farmingXpAwarded;
    }

    public void setFarmingXpAwarded(long farmingXpAwarded) {
        this.farmingXpAwarded = farmingXpAwarded;
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
