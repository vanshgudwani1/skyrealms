package com.skyrealms.islands.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable spatial anchor representing the center coordinates of an island.
 */
public record IslandLocation(
        @NotNull String worldName,
        int centerX,
        int centerY,
        int centerZ
) {
    public IslandLocation {
        Objects.requireNonNull(worldName, "worldName cannot be null");
    }

    /**
     * Checks if a world block position falls within the island's bounding box.
     */
    public boolean contains(@NotNull String targetWorld, int x, int z, int size) {
        if (!worldName.equalsIgnoreCase(targetWorld)) {
            return false;
        }
        int half = size / 2;
        return x >= (centerX - half) && x <= (centerX + half)
                && z >= (centerZ - half) && z <= (centerZ + half);
    }
}
