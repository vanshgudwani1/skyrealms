package com.skyrealms.farming.event;

import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.farming.generator.activated.v1
 */
public final class FarmingGeneratorActivateEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.farming.generator.activated.v1";

    private final FarmingGenerator generator;
    private final UUID activatorUuid;
    private final Instant timestamp;

    public FarmingGeneratorActivateEvent(@NotNull FarmingGenerator generator, @NotNull UUID activatorUuid) {
        this.generator = Objects.requireNonNull(generator, "generator cannot be null");
        this.activatorUuid = Objects.requireNonNull(activatorUuid, "activatorUuid cannot be null");
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
    public UUID activatorUuid() {
        return activatorUuid;
    }
}
