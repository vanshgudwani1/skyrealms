package com.skyrealms.farming.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks rare crop mutations discovered by players.
 */
public final class MutationDiscovery {

    private final UUID discoveryId;
    private final UUID playerUuid;
    private final String mutationType;
    private final String cropType;
    private final Instant discoveredAt;
    private long count;

    public MutationDiscovery(
            @NotNull UUID discoveryId,
            @NotNull UUID playerUuid,
            @NotNull String mutationType,
            @NotNull String cropType,
            @NotNull Instant discoveredAt,
            long count
    ) {
        this.discoveryId = Objects.requireNonNull(discoveryId, "discoveryId cannot be null");
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.mutationType = Objects.requireNonNull(mutationType, "mutationType cannot be null").toUpperCase();
        this.cropType = Objects.requireNonNull(cropType, "cropType cannot be null").toUpperCase();
        this.discoveredAt = Objects.requireNonNull(discoveredAt, "discoveredAt cannot be null");
        this.count = Math.max(1, count);
    }

    @NotNull
    public UUID discoveryId() {
        return discoveryId;
    }

    @NotNull
    public UUID playerUuid() {
        return playerUuid;
    }

    @NotNull
    public String mutationType() {
        return mutationType;
    }

    @NotNull
    public String cropType() {
        return cropType;
    }

    @NotNull
    public Instant discoveredAt() {
        return discoveredAt;
    }

    public long count() {
        return count;
    }

    public void incrementCount() {
        this.count++;
    }
}
