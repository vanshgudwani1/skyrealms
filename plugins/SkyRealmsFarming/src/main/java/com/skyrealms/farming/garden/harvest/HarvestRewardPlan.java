package com.skyrealms.farming.garden.harvest;

import com.skyrealms.farming.garden.mastery.CropType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative single-harvest reward result computed by HarvestRewardPipeline.
 * Guarantees each harvest awards drops, XP, coins, mastery, and collections at most once.
 */
public record HarvestRewardPlan(
        @NotNull UUID harvestId,
        @NotNull CropType cropType,
        @NotNull List<ItemStack> baseDrops,
        @NotNull List<ItemStack> bonusDrops,
        double combinedBonusChance,
        boolean bonusDropTriggered,
        float baseCustomMcMmoXp,
        float bonusCustomMcMmoXp,
        double xpMultiplierApplied,
        boolean goldenSproutTriggered,
        double goldenSproutCoins,
        long masteryIncrement,
        long collectionIncrement
) {
    public HarvestRewardPlan {
        Objects.requireNonNull(harvestId, "harvestId cannot be null");
        Objects.requireNonNull(cropType, "cropType cannot be null");
        baseDrops = List.copyOf(baseDrops);
        bonusDrops = List.copyOf(bonusDrops);
    }

    public float totalCustomMcMmoXp() {
        return baseCustomMcMmoXp + bonusCustomMcMmoXp;
    }

    public int totalDropItemCount() {
        int count = 0;
        for (ItemStack item : baseDrops) {
            if (item != null) count += item.getAmount();
        }
        for (ItemStack item : bonusDrops) {
            if (item != null) count += item.getAmount();
        }
        return Math.max(1, count);
    }
}

