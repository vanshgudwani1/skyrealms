package com.skyrealms.farming.garden;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable model representing an island's dedicated Garden region in skyrealms_garden.
 */
public final class Garden {

    private final UUID gardenId;
    private final UUID islandId;
    private final int regionIndex;
    private final String worldName;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int size;
    private final boolean unlocked;
    private final Instant unlockedAt;
    private final Instant createdAt;

    public Garden(
            @NotNull UUID gardenId,
            @NotNull UUID islandId,
            int regionIndex,
            @NotNull String worldName,
            int centerX,
            int centerY,
            int centerZ,
            int size,
            boolean unlocked,
            @Nullable Instant unlockedAt,
            @NotNull Instant createdAt
    ) {
        this.gardenId = Objects.requireNonNull(gardenId, "gardenId cannot be null");
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.regionIndex = regionIndex;
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.size = size;
        this.unlocked = unlocked;
        this.unlockedAt = unlockedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public UUID gardenId() {
        return gardenId;
    }

    public UUID islandId() {
        return islandId;
    }

    public int regionIndex() {
        return regionIndex;
    }

    public String worldName() {
        return worldName;
    }

    public int centerX() {
        return centerX;
    }

    public int centerY() {
        return centerY;
    }

    public int centerZ() {
        return centerZ;
    }

    public int size() {
        return size;
    }

    public boolean unlocked() {
        return unlocked;
    }

    @Nullable
    public Instant unlockedAt() {
        return unlockedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public int minX() {
        return centerX - (size / 2);
    }

    public int maxX() {
        return centerX + (size / 2);
    }

    public int minZ() {
        return centerZ - (size / 2);
    }

    public int maxZ() {
        return centerZ + (size / 2);
    }

    public boolean isInRegion(int x, int z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }

    public boolean isInRegion(@NotNull Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        return isInRegion(location.getBlockX(), location.getBlockZ());
    }

    @NotNull
    public Location getCenterLocation() {
        World world = Bukkit.getWorld(worldName);
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }

    /**
     * Entrance arrival spawn point at the West Grand Torii Gate (facing East down the Sakura promenade).
     */
    @NotNull
    public Location getSpawnLocation() {
        World world = Bukkit.getWorld(worldName);
        return new Location(world, centerX - 248.5, centerY + 1.0, centerZ + 0.5, -90.0f, 0.0f);
    }

    /**
     * Elarion the Garden Keeper's location at the East Sacred Sakura Shrine.
     */
    @NotNull
    public Location getKeeperLocation() {
        World world = Bukkit.getWorld(worldName);
        return new Location(world, centerX + 248.5, centerY + 1.0, centerZ + 0.5, 90.0f, 0.0f);
    }
}
