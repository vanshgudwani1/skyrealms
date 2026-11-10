package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.mastery.CropType;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configurable Crop Bed tiers inside customizable Garden plots.
 */
public enum BedType {
    BASIC("Basic Bed", 1, 1, 0.00, 0.00, Material.FARMLAND, "Standard farmland for normal crop growth and Farming XP"),
    RICH_SOIL("Rich Soil Bed", 10, 2, 0.08, 0.10, Material.FARMLAND, "+8% bonus crop chance and +10% Farming XP"),
    SPECIALIZED("Specialized Bed", 25, 3, 0.12, 0.15, Material.FARMLAND, "+12% bonus crop chance and +15% Farming XP for its assigned crop");

    private final String displayName;
    private final int requiredFarmingLevel;
    private final int requiredPlotUpgradeLevel;
    private final double bonusDropModifier;
    private final double xpMultiplierModifier;
    private final Material baseSoilMaterial;
    private final String description;

    BedType(
            @NotNull String displayName,
            int requiredFarmingLevel,
            int requiredPlotUpgradeLevel,
            double bonusDropModifier,
            double xpMultiplierModifier,
            @NotNull Material baseSoilMaterial,
            @NotNull String description
    ) {
        this.displayName = displayName;
        this.requiredFarmingLevel = requiredFarmingLevel;
        this.requiredPlotUpgradeLevel = requiredPlotUpgradeLevel;
        this.bonusDropModifier = bonusDropModifier;
        this.xpMultiplierModifier = xpMultiplierModifier;
        this.baseSoilMaterial = baseSoilMaterial;
        this.description = description;
    }

    @NotNull public String displayName() { return displayName; }
    public int requiredFarmingLevel() { return requiredFarmingLevel; }
    public int requiredPlotUpgradeLevel() { return requiredPlotUpgradeLevel; }
    public double bonusDropModifier() { return bonusDropModifier; }
    public double xpMultiplierModifier() { return xpMultiplierModifier; }
    @NotNull public Material baseSoilMaterial() { return baseSoilMaterial; }
    @NotNull public String description() { return description; }

    public boolean appliesBonusTo(@Nullable CropType assignedCrop, @NotNull CropType harvestedCrop) {
        if (this == SPECIALIZED) {
            return assignedCrop == null || assignedCrop == harvestedCrop;
        }
        return true;
    }

    @NotNull
    public static BedType fromName(@Nullable String name) {
        if (name == null) return BASIC;
        for (BedType type : values()) {
            if (type.name().equalsIgnoreCase(name)) return type;
        }
        return BASIC;
    }
}

