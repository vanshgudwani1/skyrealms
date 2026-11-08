package com.skyrealms.farming.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a single crop position belonging to a farming generator structure.
 */
public final class CropNode {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final Material currentCropMaterial;
    private final AtomicBoolean regenerating = new AtomicBoolean(false);

    public CropNode(@NotNull String worldName, int x, int y, int z, @NotNull Material currentCropMaterial) {
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.x = x;
        this.y = y;
        this.z = z;
        this.currentCropMaterial = Objects.requireNonNull(currentCropMaterial, "currentCropMaterial cannot be null");
    }

    @NotNull
    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    @NotNull
    public Material currentCropMaterial() {
        return currentCropMaterial;
    }

    public boolean isRegenerating() {
        return regenerating.get();
    }

    public boolean tryLockForHarvest() {
        return regenerating.compareAndSet(false, true);
    }

    public void releaseLock() {
        regenerating.set(false);
    }

    public boolean matches(@NotNull Location loc) {
        if (loc.getWorld() == null) return false;
        return loc.getWorld().getName().equals(worldName)
                && loc.getBlockX() == x
                && loc.getBlockY() == y
                && loc.getBlockZ() == z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CropNode cropNode = (CropNode) o;
        return x == cropNode.x && y == cropNode.y && z == cropNode.z && worldName.equals(cropNode.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z);
    }
}
