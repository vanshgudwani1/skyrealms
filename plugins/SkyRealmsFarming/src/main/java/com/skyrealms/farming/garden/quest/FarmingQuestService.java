package com.skyrealms.farming.garden.quest;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Service managing player agricultural quests, objective tracking, and reward disbursement.
 */
public interface FarmingQuestService {

    String QUEST_FIRST_HARVEST = "first_harvest";
    int FIRST_HARVEST_TARGET = 16;

    /**
     * Retrieves a player's quest progress.
     */
    @NotNull
    Optional<FarmingQuestProgress> getQuest(@NotNull UUID playerUuid, @NotNull String questId);

    /**
     * Starts or ensures a quest is active for a player.
     */
    void startQuest(@NotNull UUID playerUuid, @NotNull String questId);

    /**
     * Records soil tilling progress in the Garden.
     */
    void recordTill(@NotNull Player player);

    /**
     * Records crop planting progress in the Garden.
     */
    void recordPlant(@NotNull Player player, @NotNull Material cropMat);

    /**
     * Records valid mature crop harvest progress.
     */
    void recordHarvest(@NotNull Player player, @NotNull String cropType);

    /**
     * Resets a player's quest progress.
     */
    void resetQuest(@NotNull UUID playerUuid, @NotNull String questId);

    record FarmingQuestProgress(
            UUID playerUuid,
            String questId,
            String status,
            int tilledCount,
            int plantedCount,
            int harvestedCount,
            boolean isCompleted,
            boolean claimedRewards
    ) {}
}
