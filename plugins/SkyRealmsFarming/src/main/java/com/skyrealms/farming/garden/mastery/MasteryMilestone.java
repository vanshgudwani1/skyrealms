package com.skyrealms.farming.garden.mastery;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Defines milestone progression perks at levels 1, 5, 10, 15, 20, 25, and 30 for each crop.
 */
public record MasteryMilestone(
        int level,
        String title,
        String description,
        double doubleDropBonus,
        int bonusCoinsPerHarvest
) {
    public static final List<Integer> MILESTONE_LEVELS = List.of(1, 5, 10, 15, 20, 25, 30);

    @NotNull
    public static List<MasteryMilestone> getMilestonesFor(@NotNull CropType crop) {
        return switch (crop) {
            case WHEAT -> List.of(
                    new MasteryMilestone(1, "Thresher Initiate", "+5% double wheat drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Golden Sheaf", "+10% double drop chance & rare golden wheat", 0.10, 25),
                    new MasteryMilestone(10, "Flour Millwright", "+15% double drop chance & bread nutrition bonus", 0.15, 50),
                    new MasteryMilestone(15, "Abundant Grain", "+20% double drop chance & fast auto-replant", 0.20, 100),
                    new MasteryMilestone(20, "Celestial Harvest", "+25% double drop chance & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Grand Harvester", "+30% double drop chance & triple drop trigger", 0.30, 350),
                    new MasteryMilestone(30, "God of the Golden Fields", "+40% double drop chance & permanent haste aura", 0.40, 500)
            );
            case CARROT -> List.of(
                    new MasteryMilestone(1, "Root Gatherer", "+5% double carrot drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Night Sight", "+10% double drop & Night Vision on harvest", 0.10, 25),
                    new MasteryMilestone(10, "Golden Essence", "+15% double drop & bonus Golden Carrot alchemy", 0.15, 50),
                    new MasteryMilestone(15, "Crisp Crunch", "+20% double drop & speed surge on harvest", 0.20, 100),
                    new MasteryMilestone(20, "Solar Roots", "+25% double drop & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Master Forager", "+30% double drop chance & triple harvest", 0.30, 350),
                    new MasteryMilestone(30, "Keeper of the Golden Root", "+40% double drop chance & divine luck", 0.40, 500)
            );
            case POTATO -> List.of(
                    new MasteryMilestone(1, "Tuber Digger", "+5% double potato drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Toxin Filter", "+10% double drop & immunity to poisonous potatoes", 0.10, 25),
                    new MasteryMilestone(10, "Hot Spud", "+15% double drop & baked potato instant cook chance", 0.15, 50),
                    new MasteryMilestone(15, "Heavy Starch", "+20% double drop & resistance burst on harvest", 0.20, 100),
                    new MasteryMilestone(20, "Earthbound Vitality", "+25% double drop & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Highland Cultivator", "+30% double drop chance & multi-drop", 0.30, 350),
                    new MasteryMilestone(30, "Lord of the Earth Tuber", "+40% double drop chance & saturation pulse", 0.40, 500)
            );
            case BEETROOT -> List.of(
                    new MasteryMilestone(1, "Crimson Planter", "+5% double beetroot drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Ruby Dye", "+10% double drop & extra red dye drops", 0.10, 25),
                    new MasteryMilestone(10, "Vital Soup", "+15% double drop & instant regeneration tick", 0.15, 50),
                    new MasteryMilestone(15, "Deep Taproot", "+20% double drop & soil moisture preservation", 0.20, 100),
                    new MasteryMilestone(20, "Crimson Blessing", "+25% double drop & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Grand Vintner", "+30% double drop & extra seed return", 0.30, 350),
                    new MasteryMilestone(30, "Master of Crimson Velvet", "+40% double drop & heart pulse aura", 0.40, 500)
            );
            case MELON -> List.of(
                    new MasteryMilestone(1, "Melon Slicer", "+5% bonus melon slice yield", 0.05, 10),
                    new MasteryMilestone(5, "Glistering Slice", "+10% bonus yield & glistering melon drop chance", 0.10, 25),
                    new MasteryMilestone(10, "Thirst Quencher", "+15% bonus yield & swiftness burst", 0.15, 50),
                    new MasteryMilestone(15, "Dense Rind", "+20% bonus yield & stem preservation", 0.20, 100),
                    new MasteryMilestone(20, "Emerald Melons", "+25% bonus yield & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Vine Master", "+30% bonus yield & multi-slice cascade", 0.30, 350),
                    new MasteryMilestone(30, "Sovereign of the Emerald Vine", "+40% bonus yield & instant regrowth", 0.40, 500)
            );
            case PUMPKIN -> List.of(
                    new MasteryMilestone(1, "Gourd Carver", "+5% double pumpkin drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Jack's Lantern", "+10% double drop & night illumination aura", 0.10, 25),
                    new MasteryMilestone(10, "Autumn Pastry", "+15% double drop & pumpkin pie bonus yield", 0.15, 50),
                    new MasteryMilestone(15, "Thick Shell", "+20% double drop & stem preservation", 0.20, 100),
                    new MasteryMilestone(20, "Golden Gourd", "+25% double drop & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Halloween Herald", "+30% double drop & fear ward aura", 0.30, 350),
                    new MasteryMilestone(30, "King of the Autumn Harvest", "+40% double drop & harvest burst", 0.40, 500)
            );
            case SUGAR_CANE -> List.of(
                    new MasteryMilestone(1, "Reed Cutter", "+5% double sugar cane drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Sugar Rush", "+10% double drop & Speed I for 5s on harvest", 0.10, 25),
                    new MasteryMilestone(10, "Refined Crystallizer", "+15% double drop & paper efficiency", 0.15, 50),
                    new MasteryMilestone(15, "Riverbank Vitality", "+20% double drop & rapid vertical growth", 0.20, 100),
                    new MasteryMilestone(20, "Sweet Symphony", "+25% double drop & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Hyper-Speed Scythe", "+30% double drop & Speed II surge", 0.30, 350),
                    new MasteryMilestone(30, "Architect of Sweet Waters", "+40% double drop & perpetual haste", 0.40, 500)
            );
            case CACTUS -> List.of(
                    new MasteryMilestone(1, "Desert Pruner", "+5% double cactus drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Thorn Armor", "+10% double drop & thorns feedback shield", 0.10, 25),
                    new MasteryMilestone(10, "Green Pigment", "+15% double drop & extra green dye drops", 0.15, 50),
                    new MasteryMilestone(15, "Desert Survivor", "+20% double drop & heat stamina", 0.20, 100),
                    new MasteryMilestone(20, "Spike Barrage", "+25% double drop & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Oasis Lord", "+30% double drop & multi-segment harvest", 0.30, 350),
                    new MasteryMilestone(30, "Titan of the Sunlit Dunes", "+40% double drop & untouchable aura", 0.40, 500)
            );
            case COCOA -> List.of(
                    new MasteryMilestone(1, "Bean Picker", "+5% double cocoa bean drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Chocolatier", "+10% double drop & cookie crafting nutrition boost", 0.10, 25),
                    new MasteryMilestone(10, "Jungle Aroma", "+15% double drop & Haste I on harvest", 0.15, 50),
                    new MasteryMilestone(15, "Dense Pod", "+20% double drop & rapid pod maturity", 0.20, 100),
                    new MasteryMilestone(20, "Confectioner", "+25% double drop & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "Jungle Canopy Lord", "+30% double drop & tree blessing", 0.30, 350),
                    new MasteryMilestone(30, "Emperor of Dark Chocolate", "+40% double drop & sweet frenzy", 0.40, 500)
            );
            case NETHER_WART -> List.of(
                    new MasteryMilestone(1, "Fungus Tender", "+5% double nether wart drop chance", 0.05, 10),
                    new MasteryMilestone(5, "Alchemical Spark", "+10% double drop & brewing stand speed boost", 0.10, 25),
                    new MasteryMilestone(10, "Crimson Spores", "+15% double drop & soul sand affinity", 0.15, 50),
                    new MasteryMilestone(15, "Nether Resistance", "+20% double drop & Fire Resistance on harvest", 0.20, 100),
                    new MasteryMilestone(20, "Soul Flame Harvester", "+25% double drop & +25% Farming XP", 0.25, 200),
                    new MasteryMilestone(25, "High Alchemist", "+30% double drop & potion potency blessing", 0.30, 350),
                    new MasteryMilestone(30, "Overlord of the Nether Garden", "+40% double drop & nether immunity", 0.40, 500)
            );
        };
    }
}
