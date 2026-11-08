package com.skyrealms.farming.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Tracks player farming level, cumulative XP, and aggregate production statistics.
 */
public final class FarmingPlayerProgress {

    private final java.util.UUID playerUuid;
    private long farmingXp;
    private int farmingLevel;
    private long totalHarvests;
    private Instant updatedAt;

    public FarmingPlayerProgress(
            @NotNull java.util.UUID playerUuid,
            long farmingXp,
            int farmingLevel,
            long totalHarvests,
            @NotNull Instant updatedAt
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.farmingXp = Math.max(0, farmingXp);
        this.farmingLevel = Math.max(1, farmingLevel);
        this.totalHarvests = Math.max(0, totalHarvests);
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    @NotNull
    public java.util.UUID playerUuid() {
        return playerUuid;
    }

    public long farmingXp() {
        return farmingXp;
    }

    public void addXp(long amount) {
        if (amount > 0) {
            this.farmingXp += amount;
            this.updatedAt = Instant.now();
        }
    }

    public int farmingLevel() {
        return farmingLevel;
    }

    public void setFarmingLevel(int level) {
        this.farmingLevel = Math.max(1, level);
        this.updatedAt = Instant.now();
    }

    public long totalHarvests() {
        return totalHarvests;
    }

    public void incrementHarvests() {
        this.totalHarvests++;
        this.updatedAt = Instant.now();
    }

    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }

    @NotNull
    public static FarmingPlayerProgress createDefault(@NotNull java.util.UUID playerUuid) {
        return new FarmingPlayerProgress(playerUuid, 0L, 1, 0L, Instant.now());
    }
}
