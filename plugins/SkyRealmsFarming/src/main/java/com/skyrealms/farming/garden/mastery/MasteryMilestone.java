package com.skyrealms.farming.garden.mastery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Defines the 7 mcMMO-driven Crop Mastery tiers (I through VII) for each crop.
 * Separates reward categories (drop bonuses, XP multipliers, coins, discoveries, cosmetics, equipment hooks)
 * and gives each crop a distinct botanical progression identity.
 */
public record MasteryMilestone(
        int tier,
        int requiredMcMmoLevel,
        long requiredHarvests,
        @NotNull String title,
        @NotNull String description,
        double doubleDropBonus,
        double mcMmoXpBonusMultiplier,
        int coinReward,
        @Nullable String discoveryUnlock,
        @Nullable String cosmeticUnlock,
        @Nullable String equipmentHook
) {
    public static final int MAX_TIER = 7;

    @NotNull
    public static List<MasteryMilestone> getMilestonesFor(@NotNull CropType crop) {
        return switch (crop) {
            case WHEAT -> buildTiers(crop,
                    "Thresher Initiate", "Golden Sheaf", "Millwright", "Amber Harvester",
                    "Sunlit Cultivator", "Grand Reaper", "Sovereign of Golden Fields",
                    "Golden Wheat Strain", "Wheat Blossom Trail", "Golden Scythe Attunement");
            case CARROT -> buildTiers(crop,
                    "Root Gatherer", "Crisp Seeker", "Enriched Cultivator", "Burrow Harvester",
                    "Solar Botanist", "Master Forager", "Keeper of Enriched Roots",
                    "Enriched Carrot Variety", "Amber Root Sparkle", "Root-Tiller Charm");
            case POTATO -> buildTiers(crop,
                    "Tuber Digger", "Earth Sifter", "Highland Cultivator", "Starch Harvester",
                    "Deep-Soil Botanist", "Master Agronomist", "Lord of Rare Tubers",
                    "Rare Potato Mutation", "Earthy Spore Mist", "Deep-Spade Resonance");
            case BEETROOT -> buildTiers(crop,
                    "Crimson Planter", "Ruby Tapper", "Dye Distiller", "Velvet Harvester",
                    "Botanical Alchemist", "Grand Vintner", "Master of Crimson Essence",
                    "Crimson Botanical Extract", "Ruby Petal Aura", "Infuser Vessel Slot");
            case MELON -> buildTiers(crop,
                    "Melon Slicer", "Vine Tender", "Emerald Cultivator", "Gourd Harvester",
                    "Canopy Botanist", "Grand Vinekeeper", "Sovereign of Giant Melons",
                    "Giant Melon Strain", "Emerald Mist Burst", "Broad-Cleaver Edge");
            case PUMPKIN -> buildTiers(crop,
                    "Gourd Carver", "Patch Keeper", "Autumn Cultivator", "Lantern Harvester",
                    "Harvest Botanist", "Grand Gourdkeeper", "Monarch of Giant Pumpkins",
                    "Giant Pumpkin Strain", "Autumn Leaf Swirl", "Gourd-Carver Crest");
            case SUGAR_CANE -> buildTiers(crop,
                    "Reed Cutter", "Bank Tender", "Hydro-Cultivator", "Sweet Harvester",
                    "Irrigated Botanist", "Grand Crystallizer", "Architect of Sweet Reeds",
                    "Refined Cane Strain", "Crystal Sugar Glint", "Hydro-Sickle Alignment");
            case CACTUS -> buildTiers(crop,
                    "Desert Pruner", "Spine Tender", "Arid Cultivator", "Dune Harvester",
                    "Xerophyte Botanist", "Grand Oasis Master", "Titan of Sunlit Flora",
                    "Prickly Blossom Variety", "Desert Sun Flare", "Thorn-Guard Grip");
            case COCOA -> buildTiers(crop,
                    "Bean Picker", "Pod Tender", "Canopy Cultivator", "Roast Harvester",
                    "Aromatic Botanist", "Grand Chocolatier", "Emperor of Canopy Pods",
                    "Artisan Brewing Bean", "Velvet Roast Shimmer", "Canopy-Hook Blade");
            case NETHER_WART -> buildTiers(crop,
                    "Spore Tender", "Soul Planter", "Crimson Cultivator", "Ember Harvester",
                    "Alchemical Botanist", "Grand Arcanist", "Overlord of Nether Spores",
                    "Arcane Alchemical Wart", "Crimson Soul Embers", "Alembic Infusion Core");
        };
    }

    private static List<MasteryMilestone> buildTiers(
            @NotNull CropType crop,
            String t1, String t2, String t3, String t4, String t5, String t6, String t7,
            String discovery, String cosmetic, String equipmentHook
    ) {
        String name = crop.displayName();
        return List.of(
                new MasteryMilestone(1, 1, 25L, t1,
                        "+5% " + name + " bonus drop, +5% Farming XP",
                        0.05, 0.05, 100, null, null, null),
                new MasteryMilestone(2, 5, 100L, t2,
                        "+5% " + name + " bonus drop, +5% Farming XP",
                        0.05, 0.05, 250, null, null, null),
                new MasteryMilestone(3, 10, 250L, t3,
                        "+5% " + name + " bonus drop, +5% Farming XP, unlocks " + discovery,
                        0.05, 0.05, 500, discovery, null, null),
                new MasteryMilestone(4, 20, 600L, t4,
                        "+5% " + name + " bonus drop, +5% Farming XP",
                        0.05, 0.05, 1000, null, null, null),
                new MasteryMilestone(5, 35, 1500L, t5,
                        "+5% " + name + " bonus drop, +5% Farming XP, unlocks " + cosmetic,
                        0.05, 0.05, 2500, null, cosmetic, null),
                new MasteryMilestone(6, 50, 3500L, t6,
                        "+5% " + name + " bonus drop, +5% Farming XP, unlocks " + equipmentHook,
                        0.05, 0.05, 5000, null, null, equipmentHook),
                new MasteryMilestone(7, 75, 7500L, t7,
                        "+10% " + name + " bonus drop, +5% Farming XP, Grandmaster " + crop.masteryIdentity(),
                        0.10, 0.05, 15000, "Grandmaster " + discovery, "Grandmaster " + cosmetic, "Grandmaster " + equipmentHook)
        );
    }
}
