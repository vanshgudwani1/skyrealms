package com.skyrealms.farming.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks player collection milestones per crop type.
 */
public final class FarmingCropCollection {

    private final UUID playerUuid;
    private final String cropType;
    private long amountCollected;
    private int tierUnlocked;
    private Instant updatedAt;

    public FarmingCropCollection(
            @NotNull UUID playerUuid,
            @NotNull String cropType,
            long amountCollected,
            int tierUnlocked,
            @NotNull Instant updatedAt
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.cropType = Objects.requireNonNull(cropType, "cropType cannot be null").toUpperCase();
        this.amountCollected = Math.max(0, amountCollected);
        this.tierUnlocked = Math.max(1, tierUnlocked);
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    @NotNull
    public UUID playerUuid() {
        return playerUuid;
    }

    @NotNull
    public String cropType() {
        return cropType;
    }

    public long amountCollected() {
        return amountCollected;
    }

    public void addAmount(long amount) {
        if (amount > 0) {
            this.amountCollected += amount;
            this.updatedAt = Instant.now();
        }
    }

    public int tierUnlocked() {
        return tierUnlocked;
    }

    public void setTierUnlocked(int tier) {
        this.tierUnlocked = tier;
        this.updatedAt = Instant.now();
    }

    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }
}
