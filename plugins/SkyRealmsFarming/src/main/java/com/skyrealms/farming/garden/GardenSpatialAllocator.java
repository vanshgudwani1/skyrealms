package com.skyrealms.farming.garden;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Deterministic collision-free spatial grid allocator for SkyRealms Garden.
 * Maps sequential island indexes to a 2D square spiral coordinate grid
 * with configurable spacing (default 512 blocks) and region size (default 256x256).
 */
public final class GardenSpatialAllocator {

    private final int spacing;
    private final int regionSize;

    public GardenSpatialAllocator(int spacing, int regionSize) {
        if (spacing <= 0) {
            throw new IllegalArgumentException("spacing must be greater than 0");
        }
        if (regionSize <= 0 || regionSize > spacing) {
            throw new IllegalArgumentException("regionSize must be positive and <= spacing");
        }
        this.spacing = spacing;
        this.regionSize = regionSize;
    }

    public GardenSpatialAllocator() {
        this(512, 256);
    }

    public record GridCoord(int x, int z) {}

    public record RegionBounds(int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    /**
     * Converts a sequential region index (0, 1, 2, ...) into a 2D spiral grid coordinate.
     */
    @NotNull
    public static GridCoord indexToGrid(int n) {
        if (n <= 0) {
            return new GridCoord(0, 0);
        }

        int k = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2.0);
        int maxInRing = (2 * k + 1) * (2 * k + 1) - 1;
        int d = maxInRing - n;

        if (d < 2 * k) {
            return new GridCoord(k - d, -k);
        }
        d -= 2 * k;

        if (d < 2 * k) {
            return new GridCoord(-k, -k + d);
        }
        d -= 2 * k;

        if (d < 2 * k) {
            return new GridCoord(-k + d, k);
        }
        d -= 2 * k;

        return new GridCoord(k, k - d);
    }

    /**
     * Computes world center coordinates for garden region index n.
     */
    @NotNull
    public GridCoord getGardenCenter(int n) {
        GridCoord g = indexToGrid(n);
        return new GridCoord(g.x * spacing, g.z * spacing);
    }

    /**
     * Computes the bounding box for garden region index n.
     */
    @NotNull
    public RegionBounds getRegionBounds(int n) {
        GridCoord center = getGardenCenter(n);
        int half = regionSize / 2;
        return new RegionBounds(
                center.x - half,
                center.z - half,
                center.x + half,
                center.z + half
        );
    }

    /**
     * Determines the nearest region anchor for given world block coordinates.
     */
    @NotNull
    public GridCoord getNearestGridAnchor(int blockX, int blockZ) {
        int gx = Math.round((float) blockX / spacing);
        int gz = Math.round((float) blockZ / spacing);
        return new GridCoord(gx * spacing, gz * spacing);
    }

    public int spacing() {
        return spacing;
    }

    public int regionSize() {
        return regionSize;
    }
}
