package com.skyrealms.farming.model;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Progression tiers for SkyRealms Farming Generators.
 */
public enum FarmingGeneratorTier {
    TIER_1_WHEAT(
            "Wheat Generator",
            1,
            15,
            Material.WHEAT,
            Set.of(Material.WHEAT),
            Set.of(Material.FARMLAND),
            Material.WHEAT_SEEDS
    ),
    TIER_2_ROOT(
            "Root Crop Generator",
            5,
            25,
            Material.CARROT,
            Set.of(Material.CARROTS, Material.POTATOES),
            Set.of(Material.FARMLAND),
            Material.CARROT
    ),
    TIER_3_FRUIT(
            "Fruit & Gourd Generator",
            12,
            45,
            Material.MELON,
            Set.of(Material.MELON, Material.PUMPKIN),
            Set.of(Material.FARMLAND, Material.DIRT, Material.GRASS_BLOCK),
            Material.MELON_SLICE
    ),
    TIER_4_TROPICAL(
            "Tropical Plant Generator",
            20,
            75,
            Material.SUGAR_CANE,
            Set.of(Material.SUGAR_CANE, Material.CACTUS),
            Set.of(Material.SAND, Material.RED_SAND, Material.DIRT, Material.GRASS_BLOCK),
            Material.SUGAR_CANE
    ),
    TIER_5_MYSTIC(
            "Mystic Arcane Generator",
            30,
            150,
            Material.NETHER_WART,
            Set.of(Material.NETHER_WART, Material.CHORUS_PLANT, Material.CHORUS_FLOWER),
            Set.of(Material.SOUL_SAND, Material.END_STONE),
            Material.NETHER_WART
    );

    private final String displayName;
    private final int requiredFarmingLevel;
    private final int baseFarmingXp;
    private final Material icon;
    private final Set<Material> cropMaterials;
    private final Set<Material> requiredSoilMaterials;
    private final Material primaryDropMaterial;

    FarmingGeneratorTier(
            @NotNull String displayName,
            int requiredFarmingLevel,
            int baseFarmingXp,
            @NotNull Material icon,
            @NotNull Set<Material> cropMaterials,
            @NotNull Set<Material> requiredSoilMaterials,
            @NotNull Material primaryDropMaterial
    ) {
        this.displayName = displayName;
        this.requiredFarmingLevel = requiredFarmingLevel;
        this.baseFarmingXp = baseFarmingXp;
        this.icon = icon;
        this.cropMaterials = cropMaterials;
        this.requiredSoilMaterials = requiredSoilMaterials;
        this.primaryDropMaterial = primaryDropMaterial;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }

    public int requiredFarmingLevel() {
        return requiredFarmingLevel;
    }

    public int baseFarmingXp() {
        return baseFarmingXp;
    }

    @NotNull
    public Material icon() {
        return icon;
    }

    @NotNull
    public Set<Material> cropMaterials() {
        return cropMaterials;
    }

    @NotNull
    public Set<Material> requiredSoilMaterials() {
        return requiredSoilMaterials;
    }

    @NotNull
    public Material primaryDropMaterial() {
        return primaryDropMaterial;
    }

    public boolean supportsCrop(@NotNull Material material) {
        return cropMaterials.contains(material);
    }
}
