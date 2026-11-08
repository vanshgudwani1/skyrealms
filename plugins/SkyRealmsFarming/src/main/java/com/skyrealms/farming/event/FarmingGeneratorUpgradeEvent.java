package com.skyrealms.farming.event;

import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.GeneratorUpgrade;
import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.farming.generator.upgraded.v1
 */
public final class FarmingGeneratorUpgradeEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.farming.generator.upgraded.v1";

    private final FarmingGenerator generator;
    private final GeneratorUpgrade upgrade;
    private final UUID actorUuid;
    private final Instant timestamp;

    public FarmingGeneratorUpgradeEvent(
            @NotNull FarmingGenerator generator,
            @NotNull GeneratorUpgrade upgrade,
            @NotNull UUID actorUuid
    ) {
        this.generator = Objects.requireNonNull(generator, "generator cannot be null");
        this.upgrade = Objects.requireNonNull(upgrade, "upgrade cannot be null");
        this.actorUuid = Objects.requireNonNull(actorUuid, "actorUuid cannot be null");
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
    public GeneratorUpgrade upgrade() {
        return upgrade;
    }

    @NotNull
    public UUID actorUuid() {
        return actorUuid;
    }
}
