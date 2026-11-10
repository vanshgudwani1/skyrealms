package com.skyrealms.farming.garden.harvest;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable input context for a single validated Garden crop harvest.
 */
public record HarvestRewardContext(
        @NotNull UUID harvestId,
        @NotNull UUID playerUuid,
        @NotNull Garden garden,
        @NotNull GardenPlot plot,
        @NotNull CropType cropType,
        int farmingLevel,
        @NotNull CropMastery cropMastery,
        @NotNull List<ItemStack> baseDrops,
        boolean primaryBlockBreak,
        boolean surgeAoE,
        double extraDropChanceModifier,
        double extraXpMultiplierModifier,
        double bonusDropRoll,
        double goldenSproutRoll
) {
    public HarvestRewardContext {
        Objects.requireNonNull(harvestId, "harvestId cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(garden, "garden cannot be null");
        Objects.requireNonNull(plot, "plot cannot be null");
        Objects.requireNonNull(cropType, "cropType cannot be null");
        Objects.requireNonNull(cropMastery, "cropMastery cannot be null");
        baseDrops = List.copyOf(Objects.requireNonNull(baseDrops, "baseDrops cannot be null"));
    }
}

