package com.skyrealms.farming.garden.ability;

import com.skyrealms.farming.garden.harvest.GardenBalanceConfig;
import com.skyrealms.farming.garden.mastery.CropType;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages scaling math, activation timers, cooldown persistence, and daily payout caps
 * for custom Garden-only abilities driven by mcMMO Farming (Herbalism) level.
 */
public final class GardenAbilityService {

    private final GardenBalanceConfig config;
    private final Map<UUID, Long> surgeActiveUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> surgeCooldownUntilMs = new ConcurrentHashMap<>();

    private record DailyCoinAccumulator(LocalDate utcDate, double totalCoins) {}
    private final Map<UUID, DailyCoinAccumulator> dailySproutCoins = new ConcurrentHashMap<>();

    public GardenAbilityService() {
        this(GardenBalanceConfig.defaults());
    }

    public GardenAbilityService(@NotNull GardenBalanceConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    @NotNull
    public GardenBalanceConfig config() {
        return config;
    }

    public boolean isUnlocked(@NotNull GardenAbility ability, int farmingLevel) {
        return switch (ability) {
            case BOUNTIFUL_HARVEST -> config.bountifulEnabled() && farmingLevel >= config.bountifulUnlockLevel();
            case VERDANT_AURA -> config.verdantEnabled() && farmingLevel >= config.verdantUnlockLevel();
            case SAKURA_HARVEST_SURGE -> config.surgeEnabled() && farmingLevel >= config.surgeUnlockLevel();
            case GOLDEN_SPROUT -> config.goldenSproutEnabled() && farmingLevel >= config.goldenSproutUnlockLevel();
        };
    }

    /**
     * Smooth linear progression from configured minChance at unlockLevel up to maxChance at maxScaleLevel.
     */
    public double getBountifulHarvestChance(int farmingLevel) {
        if (!config.bountifulEnabled() || farmingLevel < config.bountifulUnlockLevel()) {
            return 0.0;
        }
        int start = config.bountifulUnlockLevel();
        int end = config.bountifulMaxScaleLevel();
        double t = Math.min(1.0, Math.max(0.0, (double) (farmingLevel - start) / Math.max(1, end - start)));
        return config.bountifulMinChance() + t * (config.bountifulMaxChance() - config.bountifulMinChance());
    }

    /**
     * Smooth linear progression from configured minChance at unlockLevel up to maxChance at maxScaleLevel.
     */
    public double getGoldenSproutChance(int farmingLevel) {
        if (!config.goldenSproutEnabled() || farmingLevel < config.goldenSproutUnlockLevel()) {
            return 0.0;
        }
        int start = config.goldenSproutUnlockLevel();
        int end = config.goldenSproutMaxScaleLevel();
        double t = Math.min(1.0, Math.max(0.0, (double) (farmingLevel - start) / Math.max(1, end - start)));
        return config.goldenSproutMinChance() + t * (config.goldenSproutMaxChance() - config.goldenSproutMinChance());
    }

    /**
     * Computes controlled Golden Sprout coin payout from crop base value, Farming level, and Crop Mastery tier,
     * clamped to the configured per-harvest maximum.
     */
    public double calculateGoldenSproutCoins(@NotNull CropType crop, int farmingLevel, int masteryTier) {
        if (!config.goldenSproutEnabled() || farmingLevel < config.goldenSproutUnlockLevel()) {
            return 0.0;
        }
        double base = config.getCropBaseCoins(crop);
        int levelsAboveUnlock = Math.max(0, farmingLevel - config.goldenSproutUnlockLevel());
        double raw = base + (levelsAboveUnlock * config.goldenSproutLevelCoinBonus())
                + (Math.max(0, masteryTier) * config.goldenSproutMasteryTierCoinBonus());
        return Math.min(config.goldenSproutMaxCoinsPerHarvest(), Math.max(0.0, Math.round(raw * 100.0) / 100.0));
    }

    /**
     * Applies the per-player daily Golden Sprout coin cap and returns the approved payout (0 if capped).
     */
    public double reserveDailyGoldenSproutCoins(@NotNull UUID playerUuid, double requestedCoins) {
        if (requestedCoins <= 0.0) return 0.0;
        double cap = config.goldenSproutDailyCoinCap();
        if (cap <= 0.0) return requestedCoins;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        final double[] granted = {0.0};
        dailySproutCoins.compute(playerUuid, (id, current) -> {
            double usedToday = (current != null && current.utcDate().equals(today)) ? current.totalCoins() : 0.0;
            double remaining = Math.max(0.0, cap - usedToday);
            double approved = Math.min(requestedCoins, remaining);
            granted[0] = approved;
            return new DailyCoinAccumulator(today, usedToday + approved);
        });
        return granted[0];
    }

    public double getRemainingDailyGoldenSproutCap(@NotNull UUID playerUuid) {
        double cap = config.goldenSproutDailyCoinCap();
        if (cap <= 0.0) return Double.MAX_VALUE;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        DailyCoinAccumulator acc = dailySproutCoins.get(playerUuid);
        double used = (acc != null && acc.utcDate().equals(today)) ? acc.totalCoins() : 0.0;
        return Math.max(0.0, cap - used);
    }

    public int getSurgeDurationSeconds(int farmingLevel) {
        if (!config.surgeEnabled() || farmingLevel < config.surgeUnlockLevel()) {
            return 0;
        }
        int delta = farmingLevel - config.surgeUnlockLevel();
        int span = config.surgeMaxDurationSeconds() - config.surgeMinDurationSeconds();
        int bonus = Math.min(span, delta / 8);
        return config.surgeMinDurationSeconds() + bonus;
    }

    public int getSurgeCooldownSeconds(int farmingLevel) {
        if (!config.surgeEnabled() || farmingLevel < config.surgeUnlockLevel()) {
            return config.surgeBaseCooldownSeconds();
        }
        int delta = farmingLevel - config.surgeUnlockLevel();
        return Math.max(config.surgeMinCooldownSeconds(), config.surgeBaseCooldownSeconds() - (delta / 3));
    }

    public boolean isSurgeActive(@NotNull UUID playerUuid) {
        return System.currentTimeMillis() < surgeActiveUntilMs.getOrDefault(playerUuid, 0L);
    }

    public long getSurgeRemainingActiveSeconds(@NotNull UUID playerUuid) {
        long remMs = surgeActiveUntilMs.getOrDefault(playerUuid, 0L) - System.currentTimeMillis();
        return remMs <= 0 ? 0 : (remMs + 999) / 1000;
    }

    public long getSurgeRemainingCooldownSeconds(@NotNull UUID playerUuid) {
        long remMs = surgeCooldownUntilMs.getOrDefault(playerUuid, 0L) - System.currentTimeMillis();
        return remMs <= 0 ? 0 : (remMs + 999) / 1000;
    }

    public boolean tryActivateSurge(@NotNull UUID playerUuid, int farmingLevel) {
        if (!isUnlocked(GardenAbility.SAKURA_HARVEST_SURGE, farmingLevel)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < surgeActiveUntilMs.getOrDefault(playerUuid, 0L)) {
            return false;
        }
        if (now < surgeCooldownUntilMs.getOrDefault(playerUuid, 0L)) {
            return false;
        }

        int durationSec = getSurgeDurationSeconds(farmingLevel);
        int cooldownSec = getSurgeCooldownSeconds(farmingLevel);
        surgeActiveUntilMs.put(playerUuid, now + (durationSec * 1000L));
        surgeCooldownUntilMs.put(playerUuid, now + ((durationSec + cooldownSec) * 1000L));
        return true;
    }

    /**
     * Ends active Surge window on disconnect/world-leave WITHOUT clearing cooldown,
     * preventing reconnect/world-swap cooldown bypasses.
     */
    public void deactivateSurgeWindowOnly(@NotNull UUID playerUuid) {
        surgeActiveUntilMs.remove(playerUuid);
    }
}
