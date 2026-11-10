package com.skyrealms.farming.garden.ability;

import org.jetbrains.annotations.NotNull;

/**
 * Custom Garden-only Farming abilities unlocked and scaled by mcMMO Farming (Herbalism) level.
 * Active exclusively inside the SkyRealms Garden world.
 */
public enum GardenAbility {
    BOUNTIFUL_HARVEST(
            "Bountiful Harvest",
            "Passive",
            1,
            "Chance for bonus crop drops on mature harvest inside unlocked Garden plots"
    ),
    VERDANT_AURA(
            "Verdant Aura",
            "Passive",
            10,
            "Grants Haste (and Speed at Lv.25+) while harvesting inside the Garden"
    ),
    SAKURA_HARVEST_SURGE(
            "Sakura Harvest Surge",
            "Active (Sneak + Right-Click Hoe)",
            15,
            "Harvests mature crops in a 3x3 area across your unlocked Garden plot"
    ),
    GOLDEN_SPROUT(
            "Golden Sprout",
            "Passive",
            30,
            "Chance on mature harvest to unearth bonus SkyRealms Coins"
    );

    private final String displayName;
    private final String triggerType;
    private final int unlockLevel;
    private final String shortSummary;

    GardenAbility(
            @NotNull String displayName,
            @NotNull String triggerType,
            int unlockLevel,
            @NotNull String shortSummary
    ) {
        this.displayName = displayName;
        this.triggerType = triggerType;
        this.unlockLevel = unlockLevel;
        this.shortSummary = shortSummary;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }

    @NotNull
    public String triggerType() {
        return triggerType;
    }

    public int unlockLevel() {
        return unlockLevel;
    }

    @NotNull
    public String shortSummary() {
        return shortSummary;
    }
}

