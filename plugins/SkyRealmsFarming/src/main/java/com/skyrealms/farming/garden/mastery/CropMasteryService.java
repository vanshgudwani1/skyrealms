package com.skyrealms.farming.garden.mastery;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Service managing mcMMO-driven agricultural Crop Mastery progression,
 * tier milestones, and GUI display.
 */
public interface CropMasteryService {

    @NotNull
    CropMastery getMastery(@NotNull UUID playerUuid, @NotNull CropType cropType);

    @NotNull
    List<CropMastery> getAllMasteries(@NotNull UUID playerUuid);

    /**
     * Records a mature Garden crop harvest and checks mcMMO Farming level gates for tier advancement.
     */
    void recordCropHarvest(@NotNull Player player, @NotNull CropType cropType, long harvestAmount, int mcMmoFarmingLevel);

    /**
     * Re-evaluates all crop masteries when the player levels up in mcMMO Farming (Herbalism).
     */
    void recheckAllMasteries(@NotNull Player player, int newMcMmoLevel);

    boolean claimMilestoneReward(@NotNull Player player, @NotNull CropType cropType, int milestoneTier);

    void openMasteryGui(@NotNull Player player);

    void openCropDetailGui(@NotNull Player player, @NotNull CropType cropType);
}
