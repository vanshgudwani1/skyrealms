package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.mastery.CropType;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Built-in customizable plot layout presets across full 4x4 chunks (62x62 interior):
 * includes optimal farming layouts for each of the 10 crops plus Clean Canvas & single Custom slot.
 */
public enum PlotLayoutPreset {
    WHEAT_OPTIMAL("Optimal Wheat", Material.WHEAT, "Full 4x4 chunk hydrated farmland strips tuned for Wheat", CropType.WHEAT),
    CARROT_OPTIMAL("Optimal Carrot", Material.CARROT, "Full 4x4 chunk hydrated farmland strips tuned for Carrots", CropType.CARROT),
    POTATO_OPTIMAL("Optimal Potato", Material.POTATO, "Full 4x4 chunk hydrated farmland strips tuned for Potatoes", CropType.POTATO),
    BEETROOT_OPTIMAL("Optimal Beetroot", Material.BEETROOT, "Full 4x4 chunk hydrated farmland strips tuned for Beetroot", CropType.BEETROOT),
    MELON_OPTIMAL("Optimal Melon", Material.MELON_SLICE, "Alternating stem farmland & grass fruit lanes across 4x4 chunks", CropType.MELON),
    PUMPKIN_OPTIMAL("Optimal Pumpkin", Material.PUMPKIN, "Alternating stem farmland & grass fruit lanes across 4x4 chunks", CropType.PUMPKIN),
    SUGAR_CANE_OPTIMAL("Optimal Sugar Cane", Material.SUGAR_CANE, "2-row Sand channels bordering waterlogged canals across 4x4 chunks", CropType.SUGAR_CANE),
    CACTUS_OPTIMAL("Optimal Cactus", Material.CACTUS, "Checkerboard Sand pedestals with clear growth gaps across 4x4 chunks", CropType.CACTUS),
    COCOA_OPTIMAL("Optimal Cocoa", Material.COCOA_BEANS, "3-high Jungle Log walls with dual-side harvest alleys across 4x4 chunks", CropType.COCOA),
    NETHER_WART_OPTIMAL("Optimal Nether Wart", Material.NETHER_WART, "Full 4x4 chunk Soul Sand lanes with stone walkways", CropType.NETHER_WART),
    CLEAN_CANVAS("Clean Canvas", Material.GRASS_BLOCK, "Open lawn with entrance path and 9x9 starter bed", null),
    // Legacy scenic presets kept for backward DB compatibility
    TRADITIONAL_ROWS("Traditional Rows", Material.WHEAT, "Classic symmetrical farming rows with stone walkways", null),
    JAPANESE_GARDEN("Japanese Garden", Material.CHERRY_SAPLING, "Koi water channels, sakura accents, and organic framed beds", null),
    GREENHOUSE("Greenhouse", Material.GLASS, "Quartz-framed botanical planters with sea-lantern troughs", null),
    TERRACED_GARDEN("Terraced Garden", Material.STONE_BRICKS, "Stepped multi-elevation terraces with cascading irrigation", null);

    private static final List<PlotLayoutPreset> CROP_PRESETS = List.of(
            WHEAT_OPTIMAL, CARROT_OPTIMAL, POTATO_OPTIMAL, BEETROOT_OPTIMAL, MELON_OPTIMAL,
            PUMPKIN_OPTIMAL, SUGAR_CANE_OPTIMAL, CACTUS_OPTIMAL, COCOA_OPTIMAL, NETHER_WART_OPTIMAL
    );

    private final String displayName;
    private final Material icon;
    private final String description;
    private final CropType targetCrop;

    PlotLayoutPreset(
            @NotNull String displayName,
            @NotNull Material icon,
            @NotNull String description,
            @Nullable CropType targetCrop
    ) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.targetCrop = targetCrop;
    }

    @NotNull public String displayName() { return displayName; }
    @NotNull public Material icon() { return icon; }
    @NotNull public String description() { return description; }
    @Nullable public CropType targetCrop() { return targetCrop; }

    @NotNull
    public static List<PlotLayoutPreset> cropPresets() {
        return CROP_PRESETS;
    }

    @NotNull
    public static PlotLayoutPreset forCrop(@NotNull CropType crop) {
        for (PlotLayoutPreset preset : CROP_PRESETS) {
            if (preset.targetCrop == crop) return preset;
        }
        return CLEAN_CANVAS;
    }

    @NotNull
    public static PlotLayoutPreset fromName(@Nullable String name) {
        if (name == null) return CLEAN_CANVAS;
        for (PlotLayoutPreset preset : values()) {
            if (preset.name().equalsIgnoreCase(name)) return preset;
        }
        return CLEAN_CANVAS;
    }
}
