package com.skyrealms.farming.event;

import com.skyrealms.farming.model.CropNode;
import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.platform.api.event.CancellableEvent;
import com.skyrealms.platform.api.event.SkyEvent;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Event: skyrealms.farming.crop.generated.v1
 */
public final class FarmingCropGenerateEvent implements SkyEvent, CancellableEvent {

    public static final String EVENT_NAME = "skyrealms.farming.crop.generated.v1";

    private final FarmingGenerator generator;
    private final CropNode cropNode;
    private Material cropMaterial;
    private final Instant timestamp;
    private boolean cancelled = false;

    public FarmingCropGenerateEvent(
            @NotNull FarmingGenerator generator,
            @NotNull CropNode cropNode,
            @NotNull Material cropMaterial
    ) {
        this.generator = Objects.requireNonNull(generator, "generator cannot be null");
        this.cropNode = Objects.requireNonNull(cropNode, "cropNode cannot be null");
        this.cropMaterial = Objects.requireNonNull(cropMaterial, "cropMaterial cannot be null");
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
    public Material cropMaterial() {
        return cropMaterial;
    }

    public void setCropMaterial(@NotNull Material cropMaterial) {
        this.cropMaterial = Objects.requireNonNull(cropMaterial);
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
