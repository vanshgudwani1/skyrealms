package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.mastery.CropType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player-configurable crop bed inside a Garden plot's 62x62 interior.
 * Coordinates (localX, localZ) are relative to the plot center (pcx, pcz) in [-31, 30].
 */
public record CropBed(
        @NotNull UUID bedId,
        @NotNull UUID islandId,
        int plotIndex,
        @NotNull BedType bedType,
        @Nullable CropType targetCrop,
        int minLocalX,
        int minLocalZ,
        int maxLocalX,
        int maxLocalZ
) {
    public static final int INTERIOR_MIN = -31;
    public static final int INTERIOR_MAX = 30;

    public CropBed {
        Objects.requireNonNull(bedId, "bedId cannot be null");
        Objects.requireNonNull(islandId, "islandId cannot be null");
        Objects.requireNonNull(bedType, "bedType cannot be null");
        if (plotIndex < 1 || plotIndex > 16) {
            throw new IllegalArgumentException("plotIndex must be between 1 and 16");
        }
        int minX = Math.min(minLocalX, maxLocalX);
        int maxX = Math.max(minLocalX, maxLocalX);
        int minZ = Math.min(minLocalZ, maxLocalZ);
        int maxZ = Math.max(minLocalZ, maxLocalZ);
        if (minX < INTERIOR_MIN || maxX > INTERIOR_MAX || minZ < INTERIOR_MIN || maxZ > INTERIOR_MAX) {
            throw new IllegalArgumentException("Crop bed coordinates must stay inside plot interior [-31, 30]");
        }
        minLocalX = minX;
        maxLocalX = maxX;
        minLocalZ = minZ;
        maxLocalZ = maxZ;
    }

    public boolean containsLocal(int localX, int localZ) {
        return localX >= minLocalX && localX <= maxLocalX && localZ >= minLocalZ && localZ <= maxLocalZ;
    }

    public boolean overlaps(@NotNull CropBed other) {
        return this.minLocalX <= other.maxLocalX && this.maxLocalX >= other.minLocalX
                && this.minLocalZ <= other.maxLocalZ && this.maxLocalZ >= other.minLocalZ;
    }

    public int width() {
        return (maxLocalX - minLocalX) + 1;
    }

    public int depth() {
        return (maxLocalZ - minLocalZ) + 1;
    }

    public int area() {
        return width() * depth();
    }

    public boolean isWithinRadius(int expandedRadius) {
        int minAllowed = Math.max(INTERIOR_MIN, -expandedRadius);
        int maxAllowed = Math.min(INTERIOR_MAX, expandedRadius - 1);
        return minLocalX >= minAllowed && maxLocalX <= maxAllowed
                && minLocalZ >= minAllowed && maxLocalZ <= maxAllowed;
    }

    public double getBonusDropModifier(@NotNull CropType harvestedCrop) {
        return bedType.appliesBonusTo(targetCrop, harvestedCrop) ? bedType.bonusDropModifier() : 0.0;
    }

    public double getXpMultiplierModifier(@NotNull CropType harvestedCrop) {
        return bedType.appliesBonusTo(targetCrop, harvestedCrop) ? bedType.xpMultiplierModifier() : 0.0;
    }

    @NotNull
    public CropBed withBedType(@NotNull BedType newType) {
        return new CropBed(bedId, islandId, plotIndex, newType, targetCrop, minLocalX, minLocalZ, maxLocalX, maxLocalZ);
    }

    @NotNull
    public CropBed withTargetCrop(@Nullable CropType newTarget) {
        return new CropBed(bedId, islandId, plotIndex, bedType, newTarget, minLocalX, minLocalZ, maxLocalX, maxLocalZ);
    }
}

