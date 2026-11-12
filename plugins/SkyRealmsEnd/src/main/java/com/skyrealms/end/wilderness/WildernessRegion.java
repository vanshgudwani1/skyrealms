package com.skyrealms.end.wilderness;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The four End Wilderness hunting regions hosted in the separate wilderness map (`skyrealms_end_wilds`),
 * isolated from the protected End Village inside `fullya_end`.
 */
public enum WildernessRegion {
    SHATTERED_FIELDS(
            "shattered_fields",
            "Shattered Fields",
            1, 8,
            "#A8D5BA",
            0.5, 65.0, 0.5,
            150.0,
            "Twisted fractures where Void Mites and Shattered Drifters prowl."
    ),
    CRYSTAL_WILDS(
            "crystal_wilds",
            "Crystal Wilds",
            9, 15,
            "#72EFDD",
            320.5, 65.0, 0.5,
            150.0,
            "Luminous resonance plains patrolled by Crystal Sentinels."
    ),
    FORGOTTEN_RUINS(
            "forgotten_ruins",
            "Forgotten Ruins",
            16, 23,
            "#F4A261",
            0.5, 65.0, 320.5,
            150.0,
            "Sunken monoliths claimed by Ruin Wraiths and Obsidian Revenants."
    ),
    ABYSSAL_GROUNDS(
            "abyssal_grounds",
            "Abyssal Grounds",
            24, 30,
            "#C77DFF",
            -319.5, 65.0, -319.5,
            160.0,
            "The high-threat rim where Null Conjurers and Abyssal Ravagers roam."
    );

    private final String id;
    private final String displayName;
    private final int minLevel;
    private final int maxLevel;
    private final String accentHex;
    private final double anchorX;
    private final double anchorY;
    private final double anchorZ;
    private final double radius;
    private final String description;

    WildernessRegion(
            @NotNull String id,
            @NotNull String displayName,
            int minLevel,
            int maxLevel,
            @NotNull String accentHex,
            double anchorX,
            double anchorY,
            double anchorZ,
            double radius,
            @NotNull String description
    ) {
        this.id = id;
        this.displayName = displayName;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.accentHex = accentHex;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.radius = radius;
        this.description = description;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int minLevel() { return minLevel; }
    public int maxLevel() { return maxLevel; }
    public String accentHex() { return accentHex; }
    public double anchorX() { return anchorX; }
    public double anchorY() { return anchorY; }
    public double anchorZ() { return anchorZ; }
    public double radius() { return radius; }
    public String description() { return description; }

    public boolean contains(double x, double z) {
        double dx = x - anchorX;
        double dz = z - anchorZ;
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    @NotNull
    public static WildernessRegion resolveAt(double x, double z) {
        WildernessRegion closest = SHATTERED_FIELDS;
        double bestDistSq = Double.MAX_VALUE;
        for (WildernessRegion region : values()) {
            double dx = x - region.anchorX;
            double dz = z - region.anchorZ;
            double dSq = dx * dx + dz * dz;
            if (dSq <= region.radius * region.radius) {
                return region;
            }
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                closest = region;
            }
        }
        return closest;
    }

    @Nullable
    public static WildernessRegion byId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        for (WildernessRegion r : values()) {
            if (r.id.equals(norm) || r.name().equalsIgnoreCase(norm)) {
                return r;
            }
        }
        return null;
    }
}

