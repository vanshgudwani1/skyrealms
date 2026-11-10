package com.skyrealms.farming.garden.journal;

import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.model.FarmingCropCollection;
import com.skyrealms.farming.repository.FarmingRepository;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing the permanent Botanical Journal, connecting crop collections,
 * mutation discoveries, Crop Mastery discoveries, harvest streaks, and achievements.
 */
public interface BotanicalJournalService {

    @NotNull
    BotanicalJournalEntry getJournal(@NotNull UUID playerUuid);

    void recordHarvest(@NotNull Player player, @NotNull CropType cropType, int itemsHarvested, @NotNull CropMastery mastery);

    @NotNull
    Map<String, FarmingCropCollection> getCollections(@NotNull UUID playerUuid);

    @NotNull
    List<FarmingRepository.MutationDiscoveryRecord> getMutationDiscoveries(@NotNull UUID playerUuid);

    void flushAndEvict(@NotNull UUID playerUuid);

    void openJournalGui(@NotNull Player player);
}

