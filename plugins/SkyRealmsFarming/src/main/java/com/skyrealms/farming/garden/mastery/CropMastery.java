package com.skyrealms.farming.garden.mastery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Model representing an individual player's mcMMO-driven Crop Mastery for a specific crop across 7 tiers.
 * Tier advancement requires both crop harvest count and the player's mcMMO Farming (Herbalism) level.
 */
public final class CropMastery {

    public static final int MAX_TIER = MasteryMilestone.MAX_TIER;

    private final UUID playerUuid;
    private final CropType cropType;
    private long harvestCount;
    private int tier;
    private final Set<Integer> claimedMilestones;

    public CropMastery(
            @NotNull UUID playerUuid,
            @NotNull CropType cropType,
            long harvestCount,
            int tier,
            @NotNull Set<Integer> claimedMilestones
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.cropType = Objects.requireNonNull(cropType, "cropType cannot be null");
        this.harvestCount = Math.max(0L, harvestCount);
        this.tier = Math.min(MAX_TIER, Math.max(0, tier));
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

    public long harvestCount() {
        return harvestCount;
    }

    public int tier() {
        return tier;
    }

    @NotNull
    public Set<Integer> claimedMilestones() {
        return Collections.unmodifiableSet(claimedMilestones);
    }

    /**
     * Records crop harvests and re-evaluates tier advancement against the player's mcMMO Farming level.
     */
    public void recordHarvest(long amount, int mcMmoFarmingLevel) {
        if (amount > 0) {
            this.harvestCount += amount;
        }
        recalculateTier(mcMmoFarmingLevel);
    }

    /**
     * Re-evaluates unlocked tier when mcMMO Farming level increases.
     */
    public void recalculateTier(int mcMmoFarmingLevel) {
        int newTier = 0;
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (this.harvestCount >= m.requiredHarvests() && mcMmoFarmingLevel >= m.requiredMcMmoLevel()) {
                newTier = m.tier();
            } else {
                break;
            }
        }
        this.tier = Math.max(this.tier, Math.min(MAX_TIER, newTier));
    }

    public boolean claimMilestone(int milestoneTier) {
        if (this.tier >= milestoneTier && !claimedMilestones.contains(milestoneTier)) {
            claimedMilestones.add(milestoneTier);
            return true;
        }
        return false;
    }

    public boolean isMilestoneClaimed(int milestoneTier) {
        return claimedMilestones.contains(milestoneTier);
    }

    @Nullable
    public MasteryMilestone getNextMilestone() {
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (this.tier < m.tier()) {
                return m;
            }
        }
        return null;
    }

    public double progressToNextTier() {
        MasteryMilestone next = getNextMilestone();
        if (next == null) return 1.0;

        long prevReq = 0L;
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (m.tier() == this.tier) {
                prevReq = m.requiredHarvests();
                break;
            }
        }

        long range = next.requiredHarvests() - prevReq;
        if (range <= 0) return 1.0;
        long progress = Math.max(0L, this.harvestCount - prevReq);
        return Math.min(1.0, (double) progress / range);
    }

    @NotNull
    public String getProgressBar(int barLength) {
        double progress = progressToNextTier();
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

    /**
     * Cumulative double drop bonus from all unlocked tiers (up to +40% at Tier VII).
     */
    public double getTotalDoubleDropBonus() {
        double total = 0.0;
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (this.tier >= m.tier()) {
                total += m.doubleDropBonus();
            }
        }
        return total;
    }

    /**
     * Cumulative bonus mcMMO Farming XP multiplier from all unlocked tiers (up to +35% at Tier VII).
     */
    public double getTotalMcMmoXpMultiplier() {
        double total = 0.0;
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (this.tier >= m.tier()) {
                total += m.mcMmoXpBonusMultiplier();
            }
        }
        return total;
    }

    public long getRemainingHarvestsForNextTier() {
        MasteryMilestone next = getNextMilestone();
        if (next == null) return 0L;
        return Math.max(0L, next.requiredHarvests() - this.harvestCount);
    }

    @NotNull
    public String getCurrentTierTitle() {
        if (this.tier <= 0) return "Unranked";
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (m.tier() == this.tier) {
                return m.title();
            }
        }
        return "Tier " + this.tier;
    }

    @NotNull
    public List<String> getUnlockedDiscoveries() {
        List<String> list = new ArrayList<>();
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (this.tier >= m.tier() && m.discoveryUnlock() != null) {
                list.add(m.discoveryUnlock());
            }
        }
        return list;
    }

    @NotNull
    public List<String> getUnlockedCosmetics() {
        List<String> list = new ArrayList<>();
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (this.tier >= m.tier() && m.cosmeticUnlock() != null) {
                list.add(m.cosmeticUnlock());
            }
        }
        return list;
    }

    @NotNull
    public List<String> getUnlockedEquipmentHooks() {
        List<String> list = new ArrayList<>();
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (this.tier >= m.tier() && m.equipmentHook() != null) {
                list.add(m.equipmentHook());
            }
        }
        return list;
    }

    @NotNull
    public String encodeClaimedMilestones() {
        if (claimedMilestones.isEmpty()) return "";
        List<Integer> sorted = new ArrayList<>(claimedMilestones);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(sorted.get(i));
        }
        return sb.toString();
    }

    @NotNull
    public static Set<Integer> decodeClaimedMilestones(@NotNull String encoded) {
        Set<Integer> set = new HashSet<>();
        if (encoded == null || encoded.isBlank()) return set;
        for (String part : encoded.split(",")) {
            try {
                set.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return set;
    }
}
