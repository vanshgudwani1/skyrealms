package com.skyrealms.farming.event;

import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.mutation.CropMutationType;
import com.skyrealms.platform.api.event.SkyEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Event: skyrealms.farming.crop.mutated.v1
 */
public final class FarmingCropMutationEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.farming.crop.mutated.v1";

    private final FarmingGenerator generator;
    private final Player player;
    private final Material cropMaterial;
    private final CropMutationType mutationType;
    private final Instant timestamp;

    public FarmingCropMutationEvent(
            @NotNull FarmingGenerator generator,
            @NotNull Player player,
            @NotNull Material cropMaterial,
            @NotNull CropMutationType mutationType
    ) {
        this.generator = Objects.requireNonNull(generator, "generator cannot be null");
        this.player = Objects.requireNonNull(player, "player cannot be null");
        this.cropMaterial = Objects.requireNonNull(cropMaterial, "cropMaterial cannot be null");
        this.mutationType = Objects.requireNonNull(mutationType, "mutationType cannot be null");
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
    public Player player() {
        return player;
    }

    @NotNull
    public Material cropMaterial() {
        return cropMaterial;
    }

    @NotNull
    public CropMutationType mutationType() {
        return mutationType;
    }
}
