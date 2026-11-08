package com.skyrealms.farming.garden.mastery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Model representing an individual player's mastery progression for a specific crop across 30 levels.
 */
public final class CropMastery {

    public static final int MAX_LEVEL = 30;

    private final UUID playerUuid;
    private final CropType cropType;
    private long masteryXp;
    private int level;
    private final Set<Integer> claimedMilestones;

    public CropMastery(
            @NotNull UUID playerUuid,
            @NotNull CropType cropType,
            long masteryXp,
            int level,
            @NotNull Set<Integer> claimedMilestones
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.cropType = Objects.requireNonNull(cropType, "cropType cannot be null");
        this.masteryXp = Math.max(0, masteryXp);
        this.level = Math.min(MAX_LEVEL, Math.max(0, level));
        this.claimedMilestones = new HashSet<>(claimedMilestones);
    }

    @NotNull
    public UUID playerUuid() {
        return playerUuid;
    }

    @NotNull
    public CropType cropType() {
        return cropType;
    }

    public long masteryXp() {
        return masteryXp;
    }

    public int level() {
        return level;
    }

    @NotNull
    public Set<Integer> claimedMilestones() {
        return Collections.unmodifiableSet(claimedMilestones);
    }

    public void addXp(long amount) {
        if (amount <= 0) return;
        this.masteryXp += amount;
        recalculateLevel();
    }

    public boolean claimMilestone(int milestoneLevel) {
        if (this.level >= milestoneLevel && !claimedMilestones.contains(milestoneLevel)) {
            claimedMilestones.add(milestoneLevel);
            return true;
        }
        return false;
    }

    public boolean isMilestoneClaimed(int milestoneLevel) {
        return claimedMilestones.contains(milestoneLevel);
    }

    /**
     * Calculates the cumulative XP required to achieve level L using the power curve:
     * XP(L) = floor(50 * L^2.1)
     */
    public static long calculateXpForLevel(int targetLevel) {
        if (targetLevel <= 0) return 0;
        return (long) Math.floor(50.0 * Math.pow(targetLevel, 2.1));
    }

    private void recalculateLevel() {
        int newLevel = 0;
        for (int l = 1; l <= MAX_LEVEL; l++) {
            if (this.masteryXp >= calculateXpForLevel(l)) {
                newLevel = l;
            } else {
                break;
            }
        }
        this.level = Math.min(MAX_LEVEL, newLevel);
    }

    public long xpForCurrentLevel() {
        return calculateXpForLevel(level);
    }

    public long xpForNextLevel() {
        if (level >= MAX_LEVEL) {
            return calculateXpForLevel(MAX_LEVEL);
        }
        return calculateXpForLevel(level + 1);
    }

    public double progressToNextLevel() {
        if (level >= MAX_LEVEL) return 1.0;
        long currentBase = xpForCurrentLevel();
        long nextBase = xpForNextLevel();
        long range = nextBase - currentBase;
        if (range <= 0) return 1.0;

        long currentProgress = masteryXp - currentBase;
        return Math.min(1.0, Math.max(0.0, (double) currentProgress / range));
    }

    @NotNull
    public String getProgressBar(int barLength) {
        double progress = progressToNextLevel();
        int filled = (int) Math.round(progress * barLength);
        StringBuilder sb = new StringBuilder();
        sb.append("§a");
        for (int i = 0; i < filled; i++) {
            sb.append("■");
        }
        sb.append("§7");
        for (int i = filled; i < barLength; i++) {
            sb.append("□");
        }
        return sb.toString();
    }

    @Nullable
    public MasteryMilestone getNextMilestone() {
        List<MasteryMilestone> milestones = MasteryMilestone.getMilestonesFor(cropType);
        for (MasteryMilestone m : milestones) {
            if (this.level < m.level()) {
                return m;
            }
        }
        return null;
    }

    /**
     * Total double drop chance accumulated from all achieved milestones.
     */
    public double getTotalDoubleDropBonus() {
        double total = 0.0;
        List<MasteryMilestone> milestones = MasteryMilestone.getMilestonesFor(cropType);
        for (MasteryMilestone m : milestones) {
            if (this.level >= m.level()) {
                total += m.doubleDropBonus();
            }
        }
        return total;
    }

    @NotNull
    public String encodeClaimedMilestones() {
        if (claimedMilestones.isEmpty()) return "";
        List<String> list = claimedMilestones.stream().sorted().map(String::valueOf).toList();
        return String.join(",", list);
    }

    @NotNull
    public static Set<Integer> decodeClaimedMilestones(@NotNull String str) {
        Set<Integer> set = new HashSet<>();
        if (str.isBlank()) return set;
        String[] parts = str.split(",");
        for (String p : parts) {
            try {
                set.add(Integer.parseInt(p.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return set;
    }
}
