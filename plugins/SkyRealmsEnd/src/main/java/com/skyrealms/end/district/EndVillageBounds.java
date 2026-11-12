package com.skyrealms.end.district;

import com.skyrealms.end.config.EndVillageConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Pure spatial boundary & district resolver for the SkyRealms End Village inside fullya_end.
 * Coordinates derive directly from the verified Litematic survey (+145, +40, -100 world anchor).
 */
public final class EndVillageBounds {

    private final EndVillageConfig config;

    public EndVillageBounds(@NotNull EndVillageConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public boolean isEndWorld(@Nullable String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(config.worldName());
    }

    public boolean isInSafeZone(@Nullable String worldName, double x, double y, double z) {
        if (!isEndWorld(worldName)) {
            return false;
        }
        return isLocalInSafeZone(x, y, z);
    }

    /**
     * Checks world coordinates within fullya_end against the village perimeter oval,
     * South Gate causeway, and Subterranean Void Crypt.
     */
    public boolean isLocalInSafeZone(double x, double y, double z) {
        EndVillageConfig.SafeZoneConfig sz = config.safeZone();
        if (sz.voidCryptBox().contains(x, y, z)) {
            return true;
        }
        if (sz.causewayBox().contains(x, y, z)) {
            return true;
        }
        return y >= config.offsetY() && sz.ellipse().contains(x, z);
    }

    public boolean isVoidFall(@Nullable String worldName, double y) {
        return isEndWorld(worldName) && y < config.voidRescueY();
    }

    /**
     * Resolves which village district owns the given world coordinate in fullya_end,
     * returning null when outside the village safe-zone.
     */
    @Nullable
    public EndDistrict resolveDistrict(double x, double y, double z) {
        if (!isLocalInSafeZone(x, y, z)) {
            return null;
        }

        if (config.safeZone().voidCryptBox().contains(x, y, z)) {
            return EndDistrict.DRAGON_SANCTUARY;
        }

        EndDistrict nearest = EndDistrict.CENTRAL_PLAZA;
        double bestDistSq = Double.MAX_VALUE;
        for (EndVillageConfig.DistrictSpec spec : config.districts().values()) {
            double dx = x - spec.spawn().x();
            double dz = z - spec.spawn().z();
            double dSq = dx * dx + dz * dz;
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                nearest = spec.district();
            }
        }
        return nearest;
    }
}

