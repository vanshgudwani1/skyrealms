package com.skyrealms.pets.model;

import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable definition of a Cubees 3D companion pet.
 */
public record PetDefinition(
        @NotNull String id,
        @NotNull String blueprintId,
        @NotNull String displayName,
        @NotNull TextColor color,
        @NotNull Material iconMaterial,
        @NotNull String rarityLabel,
        @NotNull String role,
        @NotNull String element,
        @NotNull String flavor,
        long unlockCostCoins,
        double baseFortune,
        double maxFortune,
        double baseXpBonus,
        double maxXpBonus,
        double baseSpeedBonus,
        double maxSpeedBonus
) {
    public static final int MAX_LEVEL = 10;

    public PetDefinition {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(blueprintId, "blueprintId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(color, "color cannot be null");
        Objects.requireNonNull(iconMaterial, "iconMaterial cannot be null");
        Objects.requireNonNull(rarityLabel, "rarityLabel cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(element, "element cannot be null");
        Objects.requireNonNull(flavor, "flavor cannot be null");
    }

    public boolean isStarterPet() {
        return unlockCostCoins <= 0L;
    }

    @NotNull
    public SkyItemStats statsForLevel(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        double progress = (MAX_LEVEL <= 1) ? 1.0 : (double) (clamped - 1) / (MAX_LEVEL - 1);
        double fortune = lerp(baseFortune, maxFortune, progress);
        double xp = lerp(baseXpBonus, maxXpBonus, progress);
        double speed = lerp(baseSpeedBonus, maxSpeedBonus, progress);
        return SkyItemStats.builder()
                .farmingFortune(fortune)
                .farmingXpBonus(xp)
                .speedBonus(speed)
                .build();
    }

    private static double lerp(double min, double max, double t) {
        return min + (max - min) * t;
    }
}

