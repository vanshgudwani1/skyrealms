package com.skyrealms.farming.garden;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Model representing one of the 16 individual crop plots inside an island's Garden.
 * Plots are arranged in 2 rows of 8 columns.
 */
public final class GardenPlot {

    private static final int[] COL_OFFSETS_X = {-24, -17, -10, -3, 4, 11, 18, 25};
    private static final int NORTH_ROW_Z = -8;
    private static final int SOUTH_ROW_Z = 8;
    private static final int HALF_PLOT_SIZE = 3; // 7x7 total footprint (5x5 farmland inside)

    private final UUID islandId;
    private final int plotIndex;
    private final boolean unlocked;
    private final String cropType;
    private final String soilType;
    private final double fertility;
    private final double moisture;
    private final double nutrientLevel;

    public GardenPlot(
            @NotNull UUID islandId,
            int plotIndex,
            boolean unlocked,
            @Nullable String cropType,
            @NotNull String soilType,
            double fertility,
            double moisture,
            double nutrientLevel
    ) {
        if (plotIndex < 1 || plotIndex > 16) {
            throw new IllegalArgumentException("plotIndex must be between 1 and 16");
        }
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.plotIndex = plotIndex;
        this.unlocked = unlocked;
        this.cropType = cropType;
        this.soilType = Objects.requireNonNull(soilType, "soilType cannot be null");
        this.fertility = fertility;
        this.moisture = moisture;
        this.nutrientLevel = nutrientLevel;
    }

    public UUID islandId() {
        return islandId;
    }

    public int plotIndex() {
        return plotIndex;
    }

    public boolean unlocked() {
        return unlocked;
    }

    @Nullable
    public String cropType() {
        return cropType;
    }

    public String soilType() {
        return soilType;
    }

    public double fertility() {
        return fertility;
    }

    public double moisture() {
        return moisture;
    }

    public double nutrientLevel() {
        return nutrientLevel;
    }

    /**
     * Relative X offset from the Garden center.
     */
    public int getRelativeCenterX() {
        int col = (plotIndex <= 8) ? (plotIndex - 1) : (plotIndex - 9);
        return COL_OFFSETS_X[col];
    }

    /**
     * Relative Z offset from the Garden center.
     */
    public int getRelativeCenterZ() {
        return (plotIndex <= 8) ? NORTH_ROW_Z : SOUTH_ROW_Z;
    }

    /**
     * World coordinates for plot center.
     */
    public int getCenterX(int gardenCenterX) {
        return gardenCenterX + getRelativeCenterX();
    }

    public int getCenterZ(int gardenCenterZ) {
        return gardenCenterZ + getRelativeCenterZ();
    }

    public int getMinX(int gardenCenterX) {
        return getCenterX(gardenCenterX) - HALF_PLOT_SIZE;
    }

    public int getMaxX(int gardenCenterX) {
        return getCenterX(gardenCenterX) + HALF_PLOT_SIZE;
    }

    public int getMinZ(int gardenCenterZ) {
        return getCenterZ(gardenCenterZ) - HALF_PLOT_SIZE;
    }

    public int getMaxZ(int gardenCenterZ) {
        return getCenterZ(gardenCenterZ) + HALF_PLOT_SIZE;
    }

    public boolean contains(int gardenCenterX, int gardenCenterZ, int blockX, int blockZ) {
        return blockX >= getMinX(gardenCenterX) && blockX <= getMaxX(gardenCenterX)
                && blockZ >= getMinZ(gardenCenterZ) && blockZ <= getMaxZ(gardenCenterZ);
    }

    @NotNull
    public Location getCenterLocation(@NotNull Garden garden) {
        World world = garden.getCenterLocation().getWorld();
        return new Location(world, getCenterX(garden.centerX()) + 0.5, garden.centerY() + 1.0, getCenterZ(garden.centerZ()) + 0.5);
    }
}
