package com.skyrealms.farming.garden.harvest;

import com.skyrealms.farming.garden.mastery.CropType;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Authoritative configuration values for Garden ability scaling, Crop Mastery stacking,
 * Golden Sprout economy payouts, and reward caps.
 */
public final class GardenBalanceConfig {

    private final double maxCombinedBonusDropChance;
    private final int maxBonusDropsPerHarvest;
    private final double maxXpMultiplier;
    private final double goldenSproutMaxCoinsPerHarvest;
    private final double goldenSproutDailyCoinCap;
    private final double goldenSproutLogThreshold;

    private final boolean bountifulEnabled;
    private final int bountifulUnlockLevel;
    private final double bountifulMinChance;
    private final double bountifulMaxChance;
    private final int bountifulMaxScaleLevel;
    private final Set<CropType> bountifulEligibleCrops;

    private final boolean verdantEnabled;
    private final int verdantUnlockLevel;
    private final int verdantHaste1Level;
    private final int verdantSpeed1Level;
    private final int verdantHaste2Level;
    private final int verdantDurationTicks;

    private final boolean surgeEnabled;
    private final int surgeUnlockLevel;
    private final int surgeMinDurationSeconds;
    private final int surgeMaxDurationSeconds;
    private final int surgeMinCooldownSeconds;
    private final int surgeBaseCooldownSeconds;

    private final boolean goldenSproutEnabled;
    private final int goldenSproutUnlockLevel;
    private final double goldenSproutMinChance;
    private final double goldenSproutMaxChance;
    private final int goldenSproutMaxScaleLevel;
    private final double goldenSproutLevelCoinBonus;
    private final double goldenSproutMasteryTierCoinBonus;
    private final Map<CropType, Double> cropBaseCoins;

    public GardenBalanceConfig(
            double maxCombinedBonusDropChance,
            int maxBonusDropsPerHarvest,
            double maxXpMultiplier,
            double goldenSproutMaxCoinsPerHarvest,
            double goldenSproutDailyCoinCap,
            double goldenSproutLogThreshold,
            boolean bountifulEnabled,
            int bountifulUnlockLevel,
            double bountifulMinChance,
            double bountifulMaxChance,
            int bountifulMaxScaleLevel,
            @NotNull Set<CropType> bountifulEligibleCrops,
            boolean verdantEnabled,
            int verdantUnlockLevel,
            int verdantHaste1Level,
            int verdantSpeed1Level,
            int verdantHaste2Level,
            int verdantDurationTicks,
            boolean surgeEnabled,
            int surgeUnlockLevel,
            int surgeMinDurationSeconds,
            int surgeMaxDurationSeconds,
            int surgeMinCooldownSeconds,
            int surgeBaseCooldownSeconds,
            boolean goldenSproutEnabled,
            int goldenSproutUnlockLevel,
            double goldenSproutMinChance,
            double goldenSproutMaxChance,
            int goldenSproutMaxScaleLevel,
            double goldenSproutLevelCoinBonus,
            double goldenSproutMasteryTierCoinBonus,
            @NotNull Map<CropType, Double> cropBaseCoins
    ) {
        this.maxCombinedBonusDropChance = Math.max(0.0, Math.min(1.0, maxCombinedBonusDropChance));
        this.maxBonusDropsPerHarvest = Math.max(0, maxBonusDropsPerHarvest);
        this.maxXpMultiplier = Math.max(1.0, maxXpMultiplier);
        this.goldenSproutMaxCoinsPerHarvest = Math.max(0.0, goldenSproutMaxCoinsPerHarvest);
        this.goldenSproutDailyCoinCap = Math.max(0.0, goldenSproutDailyCoinCap);
        this.goldenSproutLogThreshold = Math.max(0.0, goldenSproutLogThreshold);

        this.bountifulEnabled = bountifulEnabled;
        this.bountifulUnlockLevel = Math.max(1, bountifulUnlockLevel);
        this.bountifulMinChance = Math.max(0.0, Math.min(1.0, bountifulMinChance));
        this.bountifulMaxChance = Math.max(this.bountifulMinChance, Math.min(1.0, bountifulMaxChance));
        this.bountifulMaxScaleLevel = Math.max(this.bountifulUnlockLevel + 1, bountifulMaxScaleLevel);
        this.bountifulEligibleCrops = EnumSet.copyOf(bountifulEligibleCrops);

        this.verdantEnabled = verdantEnabled;
        this.verdantUnlockLevel = Math.max(1, verdantUnlockLevel);
        this.verdantHaste1Level = Math.max(1, verdantHaste1Level);
        this.verdantSpeed1Level = Math.max(1, verdantSpeed1Level);
        this.verdantHaste2Level = Math.max(this.verdantHaste1Level, verdantHaste2Level);
        this.verdantDurationTicks = Math.max(40, verdantDurationTicks);

        this.surgeEnabled = surgeEnabled;
        this.surgeUnlockLevel = Math.max(1, surgeUnlockLevel);
        this.surgeMinDurationSeconds = Math.max(1, surgeMinDurationSeconds);
        this.surgeMaxDurationSeconds = Math.max(this.surgeMinDurationSeconds, surgeMaxDurationSeconds);
        this.surgeMinCooldownSeconds = Math.max(5, surgeMinCooldownSeconds);
        this.surgeBaseCooldownSeconds = Math.max(this.surgeMinCooldownSeconds, surgeBaseCooldownSeconds);

        this.goldenSproutEnabled = goldenSproutEnabled;
        this.goldenSproutUnlockLevel = Math.max(1, goldenSproutUnlockLevel);
        this.goldenSproutMinChance = Math.max(0.0, Math.min(1.0, goldenSproutMinChance));
        this.goldenSproutMaxChance = Math.max(this.goldenSproutMinChance, Math.min(1.0, goldenSproutMaxChance));
        this.goldenSproutMaxScaleLevel = Math.max(this.goldenSproutUnlockLevel + 1, goldenSproutMaxScaleLevel);
        this.goldenSproutLevelCoinBonus = Math.max(0.0, goldenSproutLevelCoinBonus);
        this.goldenSproutMasteryTierCoinBonus = Math.max(0.0, goldenSproutMasteryTierCoinBonus);
        this.cropBaseCoins = new EnumMap<>(cropBaseCoins);
    }

    @NotNull
    public static GardenBalanceConfig defaults() {
        EnumMap<CropType, Double> baseCoins = new EnumMap<>(CropType.class);
        baseCoins.put(CropType.WHEAT, 12.0);
        baseCoins.put(CropType.CARROT, 14.0);
        baseCoins.put(CropType.POTATO, 14.0);
        baseCoins.put(CropType.BEETROOT, 16.0);
        baseCoins.put(CropType.MELON, 15.0);
        baseCoins.put(CropType.PUMPKIN, 18.0);
        baseCoins.put(CropType.SUGAR_CANE, 15.0);
        baseCoins.put(CropType.CACTUS, 16.0);
        baseCoins.put(CropType.COCOA, 18.0);
        baseCoins.put(CropType.NETHER_WART, 22.0);

        return new GardenBalanceConfig(
                0.75, 3, 2.0, 250.0, 15000.0, 500.0,
                true, 1, 0.05, 0.50, 100, EnumSet.allOf(CropType.class),
                true, 10, 10, 25, 50, 200,
                true, 15, 10, 20, 30, 60,
                true, 30, 0.01, 0.15, 100, 1.25, 5.0, baseCoins
        );
    }

    @NotNull
    public static GardenBalanceConfig fromConfig(@NotNull FileConfiguration config) {
        GardenBalanceConfig def = defaults();

        List<String> eligibleNames = config.getStringList("garden.abilities.bountiful-harvest.eligible-crops");
        Set<CropType> eligible = EnumSet.noneOf(CropType.class);
        if (eligibleNames.isEmpty()) {
            eligible.addAll(EnumSet.allOf(CropType.class));
        } else {
            for (String s : eligibleNames) {
                CropType t = CropType.fromCropId(s);
                if (t != null) eligible.add(t);
            }
            if (eligible.isEmpty()) eligible.addAll(EnumSet.allOf(CropType.class));
        }

        EnumMap<CropType, Double> cropCoins = new EnumMap<>(CropType.class);
        for (CropType crop : CropType.values()) {
            double fallback = def.getCropBaseCoins(crop);
            cropCoins.put(crop, config.getDouble("garden.abilities.golden-sprout.crop-base-coins." + crop.name(), fallback));
        }

        return new GardenBalanceConfig(
                config.getDouble("garden.reward-caps.max-combined-bonus-drop-chance", def.maxCombinedBonusDropChance),
                config.getInt("garden.reward-caps.max-bonus-drops-per-harvest", def.maxBonusDropsPerHarvest),
                config.getDouble("garden.reward-caps.max-xp-multiplier", def.maxXpMultiplier),
                config.getDouble("garden.reward-caps.golden-sprout-max-coins-per-harvest", def.goldenSproutMaxCoinsPerHarvest),
                config.getDouble("garden.reward-caps.golden-sprout-daily-coin-cap", def.goldenSproutDailyCoinCap),
                config.getDouble("garden.reward-caps.golden-sprout-log-threshold", def.goldenSproutLogThreshold),
                config.getBoolean("garden.abilities.bountiful-harvest.enabled", def.bountifulEnabled),
                config.getInt("garden.abilities.bountiful-harvest.unlock-level", def.bountifulUnlockLevel),
                config.getDouble("garden.abilities.bountiful-harvest.min-chance", def.bountifulMinChance),
                config.getDouble("garden.abilities.bountiful-harvest.max-chance", def.bountifulMaxChance),
                config.getInt("garden.abilities.bountiful-harvest.max-scale-level", def.bountifulMaxScaleLevel),
                eligible,
                config.getBoolean("garden.abilities.verdant-aura.enabled", def.verdantEnabled),
                config.getInt("garden.abilities.verdant-aura.unlock-level", def.verdantUnlockLevel),
                config.getInt("garden.abilities.verdant-aura.haste-1-level", def.verdantHaste1Level),
                config.getInt("garden.abilities.verdant-aura.speed-1-level", def.verdantSpeed1Level),
                config.getInt("garden.abilities.verdant-aura.haste-2-level", def.verdantHaste2Level),
                config.getInt("garden.abilities.verdant-aura.duration-ticks", def.verdantDurationTicks),
                config.getBoolean("garden.abilities.sakura-surge.enabled", def.surgeEnabled),
                config.getInt("garden.abilities.sakura-surge.unlock-level", def.surgeUnlockLevel),
                config.getInt("garden.abilities.sakura-surge.min-duration-seconds", def.surgeMinDurationSeconds),
                config.getInt("garden.abilities.sakura-surge.max-duration-seconds", def.surgeMaxDurationSeconds),
                config.getInt("garden.abilities.sakura-surge.min-cooldown-seconds", def.surgeMinCooldownSeconds),
                config.getInt("garden.abilities.sakura-surge.base-cooldown-seconds", def.surgeBaseCooldownSeconds),
                config.getBoolean("garden.abilities.golden-sprout.enabled", def.goldenSproutEnabled),
                config.getInt("garden.abilities.golden-sprout.unlock-level", def.goldenSproutUnlockLevel),
                config.getDouble("garden.abilities.golden-sprout.min-chance", def.goldenSproutMinChance),
                config.getDouble("garden.abilities.golden-sprout.max-chance", def.goldenSproutMaxChance),
                config.getInt("garden.abilities.golden-sprout.max-scale-level", def.goldenSproutMaxScaleLevel),
                config.getDouble("garden.abilities.golden-sprout.level-coin-bonus", def.goldenSproutLevelCoinBonus),
                config.getDouble("garden.abilities.golden-sprout.mastery-tier-coin-bonus", def.goldenSproutMasteryTierCoinBonus),
                cropCoins
        );
    }

    public double maxCombinedBonusDropChance() { return maxCombinedBonusDropChance; }
    public int maxBonusDropsPerHarvest() { return maxBonusDropsPerHarvest; }
    public double maxXpMultiplier() { return maxXpMultiplier; }
    public double goldenSproutMaxCoinsPerHarvest() { return goldenSproutMaxCoinsPerHarvest; }
    public double goldenSproutDailyCoinCap() { return goldenSproutDailyCoinCap; }
    public double goldenSproutLogThreshold() { return goldenSproutLogThreshold; }

    public boolean bountifulEnabled() { return bountifulEnabled; }
    public int bountifulUnlockLevel() { return bountifulUnlockLevel; }
    public double bountifulMinChance() { return bountifulMinChance; }
    public double bountifulMaxChance() { return bountifulMaxChance; }
    public int bountifulMaxScaleLevel() { return bountifulMaxScaleLevel; }
    public boolean isBountifulEligible(@NotNull CropType crop) { return bountifulEligibleCrops.contains(crop); }

    public boolean verdantEnabled() { return verdantEnabled; }
    public int verdantUnlockLevel() { return verdantUnlockLevel; }
    public int verdantHaste1Level() { return verdantHaste1Level; }
    public int verdantSpeed1Level() { return verdantSpeed1Level; }
    public int verdantHaste2Level() { return verdantHaste2Level; }
    public int verdantDurationTicks() { return verdantDurationTicks; }

    public boolean surgeEnabled() { return surgeEnabled; }
    public int surgeUnlockLevel() { return surgeUnlockLevel; }
    public int surgeMinDurationSeconds() { return surgeMinDurationSeconds; }
    public int surgeMaxDurationSeconds() { return surgeMaxDurationSeconds; }
    public int surgeMinCooldownSeconds() { return surgeMinCooldownSeconds; }
    public int surgeBaseCooldownSeconds() { return surgeBaseCooldownSeconds; }

    public boolean goldenSproutEnabled() { return goldenSproutEnabled; }
    public int goldenSproutUnlockLevel() { return goldenSproutUnlockLevel; }
    public double goldenSproutMinChance() { return goldenSproutMinChance; }
    public double goldenSproutMaxChance() { return goldenSproutMaxChance; }
    public int goldenSproutMaxScaleLevel() { return goldenSproutMaxScaleLevel; }
    public double goldenSproutLevelCoinBonus() { return goldenSproutLevelCoinBonus; }
    public double goldenSproutMasteryTierCoinBonus() { return goldenSproutMasteryTierCoinBonus; }

    public double getCropBaseCoins(@NotNull CropType crop) {
        return cropBaseCoins.getOrDefault(crop, 12.0);
    }
}

