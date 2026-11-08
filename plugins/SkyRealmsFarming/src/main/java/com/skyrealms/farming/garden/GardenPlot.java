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

    // 16 plots arranged in 2 rows of 8 columns.
    // Each plot is at least 4x4 chunks (64x64 blocks = 4,096 blocks).
    // 8 columns span X in [-256, 255] (32 chunks).
    private static final int[] COL_OFFSETS_X = {-224, -160, -96, -32, 32, 96, 160, 224};
    // 2 rows: North row (plots 1-8) in Z [-80, -17], South row (plots 9-16) in Z [16, 79].
    // Grand Central Boulevard spans Z [-16, 15] (32 blocks wide, 2 chunks).
    private static final int NORTH_ROW_Z = -48;
    private static final int SOUTH_ROW_Z = 48;
    public static final int PLOT_WIDTH = 64;
    public static final int PLOT_DEPTH = 64;
    private static final int HALF_SIZE_NEG = 32;
    private static final int HALF_SIZE_POS = 31;

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
        return getCenterX(gardenCenterX) - HALF_SIZE_NEG;
    }

    public int getMaxX(int gardenCenterX) {
        return getCenterX(gardenCenterX) + HALF_SIZE_POS;
    }

    public int getMinZ(int gardenCenterZ) {
        return getCenterZ(gardenCenterZ) - HALF_SIZE_NEG;
    }

    public int getMaxZ(int gardenCenterZ) {
        return getCenterZ(gardenCenterZ) + HALF_SIZE_POS;
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
