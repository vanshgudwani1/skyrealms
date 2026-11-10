package com.skyrealms.farming.garden.journal;

import org.jetbrains.annotations.NotNull;

/**
 * Permanent botanical achievements tracked in the Botanical Journal.
 */
public enum BotanicalAchievement {
    FIRST_BOUNTY("First Bounty", "Harvest 100 total crops across your Garden"),
    HARVEST_STREAK_25("Steady Rhythm", "Achieve a 25-crop continuous harvest streak"),
    HARVEST_STREAK_100("Botanical Flow", "Achieve a 100-crop continuous harvest streak"),
    SURGE_HARVESTER("Sakura Sweeper", "Collect 5+ items in a single harvest action"),
    DIVERSE_GARDENER("Tenfold Botanist", "Harvest all 10 Garden crop species at least once"),
    STRAIN_DISCOVERER("Strain Researcher", "Unlock any crop's Tier III discovery strain"),
    GRANDMASTER_BOTANIST("Grandmaster Botanist", "Reach Tier VII Grandmaster Mastery on any crop"),
    MYTHIC_COLLECTOR("Living Archive", "Reach 10,000+ lifetime collection on any crop");

    private final String title;
    private final String description;

    BotanicalAchievement(@NotNull String title, @NotNull String description) {
        this.title = title;
        this.description = description;
    }

    @NotNull
    public String title() {
        return title;
    }

    @NotNull
    public String description() {
        return description;
    }
}

