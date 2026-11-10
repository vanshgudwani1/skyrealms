package com.skyrealms.farming.garden.plot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plot expansion & upgrade tiers connecting mcMMO Farming level and EconomyService.
 */
public enum PlotUpgradeTier {
    TIER_1(1, "Starter Plot", 31, 8, 1, 0.0, "Full 4x4 chunks (62x62 interior), up to 8 Crop Beds, Basic Beds"),
    TIER_2(2, "Expanded Terraces", 31, 10, 10, 5000.0, "Full 4x4 chunks, up to 10 Crop Beds, unlocks Rich Soil Beds"),
    TIER_3(3, "Botanical Estate", 31, 12, 25, 25000.0, "Full 4x4 chunks, up to 12 Crop Beds, unlocks Specialized Beds"),
    TIER_4(4, "Grand Conservatory Plot", 31, 14, 50, 100000.0, "Full 4x4 chunks, up to 14 Crop Beds");

    public static final int FULL_INTERIOR_RADIUS = 31;

    private final int level;
    private final String title;
    private final int expandedRadius;
    private final int maxBeds;
    private final int requiredFarmingLevel;
    private final double coinCost;
    private final String summary;

    PlotUpgradeTier(
            int level,
            @NotNull String title,
            int expandedRadius,
            int maxBeds,
            int requiredFarmingLevel,
            double coinCost,
            @NotNull String summary
    ) {
        this.level = level;
        this.title = title;
        this.expandedRadius = expandedRadius;
        this.maxBeds = maxBeds;
        this.requiredFarmingLevel = requiredFarmingLevel;
        this.coinCost = coinCost;
        this.summary = summary;
    }

    public int level() { return level; }
    @NotNull public String title() { return title; }
    public int expandedRadius() { return expandedRadius; }
    public int maxBeds() { return maxBeds; }
    public int requiredFarmingLevel() { return requiredFarmingLevel; }
    public double coinCost() { return coinCost; }
    @NotNull public String summary() { return summary; }

    @NotNull
    public static PlotUpgradeTier forLevel(int level) {
        for (PlotUpgradeTier tier : values()) {
            if (tier.level == level) return tier;
        }
        return level >= 4 ? TIER_4 : TIER_1;
    }

    @Nullable
    public PlotUpgradeTier nextTier() {
        return switch (this) {
            case TIER_1 -> TIER_2;
            case TIER_2 -> TIER_3;
            case TIER_3 -> TIER_4;
            case TIER_4 -> null;
        };
    }
}

