package com.skyrealms.islands.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted upgrade state for an island tier (e.g. size, generators, minions).
 */
public record IslandUpgrade(
        @NotNull UUID islandId,
        @NotNull String upgradeKey,
        int upgradeLevel,
        @NotNull Instant updatedAt
) {
    public IslandUpgrade {
        Objects.requireNonNull(islandId, "islandId cannot be null");
        Objects.requireNonNull(upgradeKey, "upgradeKey cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
