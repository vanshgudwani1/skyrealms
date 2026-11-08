package com.skyrealms.farming.event;

import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.farming.generator.created.v1
 */
public final class FarmingGeneratorCreateEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.farming.generator.created.v1";

    private final FarmingGenerator generator;
    private final UUID creatorUuid;
    private final Instant timestamp;

    public FarmingGeneratorCreateEvent(@NotNull FarmingGenerator generator, @NotNull UUID creatorUuid) {
        this.generator = Objects.requireNonNull(generator, "generator cannot be null");
        this.creatorUuid = Objects.requireNonNull(creatorUuid, "creatorUuid cannot be null");
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
    public UUID creatorUuid() {
        return creatorUuid;
    }
}
