package com.skyrealms.farming.garden.mastery;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The 10 agricultural crops supported by SkyRealms Crop Mastery.
 */
public enum CropType {
    WHEAT("Wheat", Material.WHEAT, "WHEAT", "🌾", 10),
    CARROT("Carrot", Material.CARROT, "CARROTS", "🥕", 12),
    POTATO("Potato", Material.POTATO, "POTATOES", "🥔", 12),
    BEETROOT("Beetroot", Material.BEETROOT, "BEETROOTS", "🏮", 15),
    MELON("Melon", Material.MELON_SLICE, "MELON", "🍉", 18),
    PUMPKIN("Pumpkin", Material.PUMPKIN, "PUMPKIN", "🎃", 20),
    SUGAR_CANE("Sugar Cane", Material.SUGAR_CANE, "SUGAR_CANE", "🎋", 14),
    CACTUS("Cactus", Material.CACTUS, "CACTUS", "🌵", 16),
    COCOA("Cocoa", Material.COCOA_BEANS, "COCOA", "🍫", 15),
    NETHER_WART("Nether Wart", Material.NETHER_WART, "NETHER_WART", "🍄", 25);

    private final String displayName;
    private final Material icon;
    private final String naturalCropId;
    private final String symbol;
    private final int baseMasteryXp;

    CropType(
            @NotNull String displayName,
            @NotNull Material icon,
            @NotNull String naturalCropId,
            @NotNull String symbol,
            int baseMasteryXp
    ) {
        this.displayName = displayName;
        this.icon = icon;
        this.naturalCropId = naturalCropId;
        this.symbol = symbol;
        this.baseMasteryXp = baseMasteryXp;
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

    public int baseMasteryXp() {
        return baseMasteryXp;
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
}
