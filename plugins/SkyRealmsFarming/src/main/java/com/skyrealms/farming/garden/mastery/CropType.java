package com.skyrealms.farming.garden.mastery;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The 10 agricultural crops supported by SkyRealms mcMMO-driven Crop Mastery and Botanical Journal.
 */
public enum CropType {
    WHEAT("Wheat", Material.WHEAT, "WHEAT", "◆", 10, "Golden Wheat Varieties", "Grain & Cereal"),
    CARROT("Carrot", Material.CARROT, "CARROTS", "◆", 12, "Enriched Carrot Varieties", "Root Cultivation"),
    POTATO("Potato", Material.POTATO, "POTATOES", "◆", 12, "Rare Tuber Mutations", "Subterranean Root"),
    BEETROOT("Beetroot", Material.BEETROOT, "BEETROOTS", "◆", 15, "Botanical Pigment Extracts", "Botanical Extract"),
    MELON("Melon", Material.MELON_SLICE, "MELON", "◆", 18, "Giant Melon Discoveries", "Vine Gourd"),
    PUMPKIN("Pumpkin", Material.PUMPKIN, "PUMPKIN", "◆", 20, "Giant Pumpkin Discoveries", "Autumn Gourd"),
    SUGAR_CANE("Sugar Cane", Material.SUGAR_CANE, "SUGAR_CANE", "◆", 14, "Advanced Reed Cultivation", "Hydro-Reed"),
    CACTUS("Cactus", Material.CACTUS, "CACTUS", "◆", 16, "Rare Desert Varieties", "Arid Flora"),
    COCOA("Cocoa", Material.COCOA_BEANS, "COCOA", "◆", 15, "Specialized Brewing Beans", "Canopy Pod"),
    NETHER_WART("Nether Wart", Material.NETHER_WART, "NETHER_WART", "◆", 25, "Rare Alchemical Spores", "Arcane Mycelium");

    private final String displayName;
    private final Material icon;
    private final String naturalCropId;
    private final String symbol;
    private final int baseMcMmoXp;
    private final String masteryIdentity;
    private final String botanicalCategory;

    CropType(
            @NotNull String displayName,
            @NotNull Material icon,
            @NotNull String naturalCropId,
            @NotNull String symbol,
            int baseMcMmoXp,
            @NotNull String masteryIdentity,
            @NotNull String botanicalCategory
    ) {
        this.displayName = displayName;
        this.icon = icon;
        this.naturalCropId = naturalCropId;
        this.symbol = symbol;
        this.baseMcMmoXp = baseMcMmoXp;
        this.masteryIdentity = masteryIdentity;
        this.botanicalCategory = botanicalCategory;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }

    @NotNull
    public Material icon() {
        return icon;
    }

    @NotNull
    public String naturalCropId() {
        return naturalCropId;
    }

    @NotNull
    public String symbol() {
        return symbol;
    }

    public int baseMcMmoXp() {
        return baseMcMmoXp;
    }

    @NotNull
    public String masteryIdentity() {
        return masteryIdentity;
    }

    @NotNull
    public String botanicalCategory() {
        return botanicalCategory;
    }

    @Nullable
    public static CropType fromCropId(@NotNull String cropId) {
        for (CropType type : values()) {
            if (type.name().equalsIgnoreCase(cropId) || type.naturalCropId().equalsIgnoreCase(cropId)) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    public static CropType fromBlockMaterial(@NotNull Material material) {
        return switch (material) {
            case WHEAT -> WHEAT;
            case CARROTS -> CARROT;
            case POTATOES -> POTATO;
            case BEETROOTS -> BEETROOT;
            case MELON -> MELON;
            case PUMPKIN -> PUMPKIN;
            case SUGAR_CANE -> SUGAR_CANE;
            case CACTUS -> CACTUS;
            case COCOA -> COCOA;
            case NETHER_WART -> NETHER_WART;
            default -> null;
        };
    }

    /**
     * Item material placed in a player's inventory when planting this crop.
     */
    @NotNull
    public Material seedItemMaterial() {
        return switch (this) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROT -> Material.CARROT;
            case POTATO -> Material.POTATO;
            case BEETROOT -> Material.BEETROOT_SEEDS;
            case MELON -> Material.MELON_SEEDS;
            case PUMPKIN -> Material.PUMPKIN_SEEDS;
            case SUGAR_CANE -> Material.SUGAR_CANE;
            case CACTUS -> Material.CACTUS;
            case COCOA -> Material.COCOA_BEANS;
            case NETHER_WART -> Material.NETHER_WART;
        };
    }

    /**
     * Block material placed in-world when planted on farmland or crop soil.
     */
    @NotNull
    public Material plantedBlockMaterial() {
        return switch (this) {
            case WHEAT -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT -> Material.BEETROOTS;
            case MELON -> Material.MELON_STEM;
            case PUMPKIN -> Material.PUMPKIN_STEM;
            case SUGAR_CANE -> Material.SUGAR_CANE;
            case CACTUS -> Material.CACTUS;
            case COCOA -> Material.COCOA;
            case NETHER_WART -> Material.NETHER_WART;
        };
    }

    /**
     * Maps a held seed/planting item in a player's hand to its CropType.
     */
    @Nullable
    public static CropType fromSeedItemMaterial(@NotNull Material itemMaterial) {
        for (CropType type : values()) {
            if (type.seedItemMaterial() == itemMaterial) {
                return type;
            }
        }
        return null;
    }
}
