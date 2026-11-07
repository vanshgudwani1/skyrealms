package com.skyrealms.islands.world;

import org.jetbrains.annotations.NotNull;

/**
 * Deterministic collision-free spatial grid allocator.
 * Maps sequential island indexes to a 2D square spiral coordinate grid.
 */
public final class SpatialGridAllocator {

    private final int spacing;

    public SpatialGridAllocator(int spacing) {
        if (spacing <= 0) {
            throw new IllegalArgumentException("spacing must be greater than 0");
        }
        this.spacing = spacing;
    }

    public record GridCoord(int x, int z) {}

    /**
     * Converts an integer slot index (0, 1, 2...) into a unique 2D spiral grid coordinate.
     * Guaranteed 1-to-1 bijection: each index produces a distinct (gx, gz) pair.
     */
    @NotNull
    public static GridCoord indexToGrid(int n) {
        if (n <= 0) {
            return new GridCoord(0, 0);
        }

        // Determine which concentric square ring n belongs to
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
     * Computes real world center coordinates for island index n.
     */
    @NotNull
    public GridCoord getIslandCenter(int n) {
        GridCoord g = indexToGrid(n);
        return new GridCoord(g.x * spacing, g.z * spacing);
    }

    /**
     * Reverses world block coordinates to the nearest grid anchor coordinate.
     */
    @NotNull
    public GridCoord getNearestGridAnchor(int blockX, int blockZ) {
        int gx = Math.round((float) blockX / spacing);
        int gz = Math.round((float) blockZ / spacing);
        return new GridCoord(gx * spacing, gz * spacing);
    }

    /**
     * Unique 64-bit spatial hash combining grid coordinates for O(1) hash map lookups.
     */
    public static long gridKey(int anchorX, int anchorZ) {
        return (((long) anchorX) << 32) | (anchorZ & 0xFFFFFFFFL);
    }

    public int spacing() {
        return spacing;
    }
}
