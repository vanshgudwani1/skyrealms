package com.skyrealms.farming.garden.journal;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Player-specific Botanical Journal state storing streaks, best single harvest,
 * unlocked botanical achievements, and discovered varieties.
 */
public final class BotanicalJournalEntry {

    private final UUID playerUuid;
    private int highestHarvestStreak;
    private int bestSingleHarvest;
    private int currentSessionStreak;
    private final Set<String> unlockedAchievements;
    private final Set<String> discoveredVarieties;

    public BotanicalJournalEntry(
            @NotNull UUID playerUuid,
            int highestHarvestStreak,
            int bestSingleHarvest,
            @NotNull Set<String> unlockedAchievements,
            @NotNull Set<String> discoveredVarieties
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.highestHarvestStreak = Math.max(0, highestHarvestStreak);
        this.bestSingleHarvest = Math.max(1, bestSingleHarvest);
        this.currentSessionStreak = 0;
        this.unlockedAchievements = new LinkedHashSet<>(unlockedAchievements);
        this.discoveredVarieties = new LinkedHashSet<>(discoveredVarieties);
    }

    @NotNull public UUID playerUuid() { return playerUuid; }
    public int highestHarvestStreak() { return highestHarvestStreak; }
    public int bestSingleHarvest() { return bestSingleHarvest; }
    public int currentSessionStreak() { return currentSessionStreak; }

    @NotNull public Set<String> unlockedAchievements() { return Collections.unmodifiableSet(unlockedAchievements); }
    @NotNull public Set<String> discoveredVarieties() { return Collections.unmodifiableSet(discoveredVarieties); }

    public boolean recordHarvest(int itemsHarvested) {
        boolean changed = false;
        currentSessionStreak++;
        if (currentSessionStreak > highestHarvestStreak) {
            highestHarvestStreak = currentSessionStreak;
            changed = true;
        }
        if (itemsHarvested > bestSingleHarvest) {
            bestSingleHarvest = itemsHarvested;
            changed = true;
        }
        return changed;
    }

    public void resetSessionStreak() {
        currentSessionStreak = 0;
    }

    public boolean unlockAchievement(@NotNull BotanicalAchievement achievement) {
        return unlockedAchievements.add(achievement.name());
    }

    public boolean hasAchievement(@NotNull BotanicalAchievement achievement) {
        return unlockedAchievements.contains(achievement.name());
    }

    public boolean addVariety(@NotNull String varietyId) {
        return discoveredVarieties.add(varietyId);
    }

    @NotNull
    public static String encodeSet(@NotNull Set<String> values) {
        if (values.isEmpty()) return "";
        return String.join(",", values);
    }

    @NotNull
    public static Set<String> decodeSet(@NotNull String encoded) {
        Set<String> set = new LinkedHashSet<>();
        if (encoded == null || encoded.isBlank()) return set;
        for (String s : encoded.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return set;
    }
}

