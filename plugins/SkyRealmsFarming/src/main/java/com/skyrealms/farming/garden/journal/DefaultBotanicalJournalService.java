package com.skyrealms.farming.garden.journal;

import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropMasteryService;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.farming.model.FarmingCropCollection;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.farming.service.FarmingProgressionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Authoritative Botanical Journal service connecting existing collections,
 * mutation discoveries, Crop Mastery discoveries, and journal achievements.
 */
public final class DefaultBotanicalJournalService implements BotanicalJournalService {

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final FarmingProgressionService progressionService;
    private final CropMasteryService masteryService;
    private final McMMOFarmingService mcMmoService;
    private final Logger logger;

    private final Map<UUID, BotanicalJournalEntry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, List<FarmingRepository.MutationDiscoveryRecord>> mutationCache = new ConcurrentHashMap<>();

    public DefaultBotanicalJournalService(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull FarmingProgressionService progressionService,
            @NotNull CropMasteryService masteryService,
            @NotNull McMMOFarmingService mcMmoService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
        this.masteryService = Objects.requireNonNull(masteryService, "masteryService cannot be null");
        this.mcMmoService = Objects.requireNonNull(mcMmoService, "mcMmoService cannot be null");
        this.logger = plugin.getLogger();
    }

    @Override
    @NotNull
    public BotanicalJournalEntry getJournal(@NotNull UUID playerUuid) {
        BotanicalJournalEntry cached = cache.get(playerUuid);
        if (cached != null) return cached;

        try {
            Optional<FarmingRepository.BotanicalJournalRecord> opt = repository.findBotanicalJournal(playerUuid);
            if (opt.isPresent()) {
                FarmingRepository.BotanicalJournalRecord rec = opt.get();
                BotanicalJournalEntry entry = new BotanicalJournalEntry(
                        rec.playerUuid(),
                        rec.highestHarvestStreak(),
                        rec.bestSingleHarvest(),
                        BotanicalJournalEntry.decodeSet(rec.unlockedAchievements()),
                        BotanicalJournalEntry.decodeSet(rec.discoveredVarieties())
                );
                cache.put(playerUuid, entry);
                return entry;
            }
        } catch (SQLException e) {
            logger.warning("Failed to load Botanical Journal for " + playerUuid + ": " + e.getMessage());
        }

        BotanicalJournalEntry fresh = new BotanicalJournalEntry(playerUuid, 0, 1, Set.of(), Set.of());
        cache.put(playerUuid, fresh);
        return fresh;
    }

    @Override
    public void recordHarvest(@NotNull Player player, @NotNull CropType cropType, int itemsHarvested, @NotNull CropMastery mastery) {
        UUID uuid = player.getUniqueId();
        BotanicalJournalEntry entry = getJournal(uuid);
        boolean dirty = entry.recordHarvest(itemsHarvested);

        // Sync mastery variety discoveries
        for (String discovery : mastery.getUnlockedDiscoveries()) {
            if (entry.addVariety(discovery)) {
                dirty = true;
            }
        }

        // Evaluate botanical achievements
        List<CropMastery> allMasteries = masteryService.getAllMasteries(uuid);
        long totalHarvests = 0L;
        int distinctHarvestedCrops = 0;
        boolean anyTier3 = false;
        boolean anyTier7 = false;
        for (CropMastery cm : allMasteries) {
            totalHarvests += cm.harvestCount();
            if (cm.harvestCount() > 0) distinctHarvestedCrops++;
            if (cm.tier() >= 3) anyTier3 = true;
            if (cm.tier() >= 7) anyTier7 = true;
        }

        Map<String, FarmingCropCollection> collections = getCollections(uuid);
        boolean any10kCollection = collections.values().stream().anyMatch(c -> c.amountCollected() >= 10_000L);
        boolean hasMutation = !getMutationDiscoveries(uuid).isEmpty();

        if (totalHarvests >= 100L) dirty |= unlockAndAnnounce(player, entry, BotanicalAchievement.FIRST_BOUNTY);
        if (entry.highestHarvestStreak() >= 25) dirty |= unlockAndAnnounce(player, entry, BotanicalAchievement.HARVEST_STREAK_25);
        if (entry.highestHarvestStreak() >= 100) dirty |= unlockAndAnnounce(player, entry, BotanicalAchievement.HARVEST_STREAK_100);
        if (itemsHarvested >= 5) dirty |= unlockAndAnnounce(player, entry, BotanicalAchievement.SURGE_HARVESTER);
        if (distinctHarvestedCrops >= CropType.values().length) dirty |= unlockAndAnnounce(player, entry, BotanicalAchievement.DIVERSE_GARDENER);
        if (anyTier3) dirty |= unlockAndAnnounce(player, entry, BotanicalAchievement.STRAIN_DISCOVERER);
        if (anyTier7) dirty |= unlockAndAnnounce(player, entry, BotanicalAchievement.GRANDMASTER_BOTANIST);
        if (any10kCollection) dirty |= unlockAndAnnounce(player, entry, BotanicalAchievement.MYTHIC_COLLECTOR);
        if (hasMutation) {
            if (entry.addVariety("Rare Crop Mutation")) dirty = true;
        }

        if (dirty) {
            saveAsync(entry);
        }
    }

    private boolean unlockAndAnnounce(@NotNull Player player, @NotNull BotanicalJournalEntry entry, @NotNull BotanicalAchievement ach) {
        if (!entry.unlockAchievement(ach)) {
            return false;
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        player.sendMessage(Component.text("📖 Journal: ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(ach.title(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" unlocked!", NamedTextColor.GREEN))
                .append(Component.text(" (/garden journal)", NamedTextColor.DARK_GRAY))
                .hoverEvent(HoverEvent.showText(Component.text(ach.description(), NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/garden journal")));
        return true;
    }

    @Override
    @NotNull
    public Map<String, FarmingCropCollection> getCollections(@NotNull UUID playerUuid) {
        return progressionService.getCollections(playerUuid);
    }

    @Override
    @NotNull
    public List<FarmingRepository.MutationDiscoveryRecord> getMutationDiscoveries(@NotNull UUID playerUuid) {
        List<FarmingRepository.MutationDiscoveryRecord> cached = mutationCache.get(playerUuid);
        if (cached != null) return cached;
        try {
            List<FarmingRepository.MutationDiscoveryRecord> list = repository.findMutationDiscoveries(playerUuid);
            mutationCache.put(playerUuid, list);
            return list;
        } catch (SQLException e) {
            logger.warning("Failed to load mutation discoveries for " + playerUuid + ": " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public void flushAndEvict(@NotNull UUID playerUuid) {
        BotanicalJournalEntry entry = cache.remove(playerUuid);
        mutationCache.remove(playerUuid);
        if (entry != null) {
            entry.resetSessionStreak();
            saveAsync(entry);
        }
    }

    @Override
    public void openJournalGui(@NotNull Player player) {
        BotanicalJournalGui.open(player, this, masteryService, mcMmoService);
    }

    private void saveAsync(@NotNull BotanicalJournalEntry entry) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.saveBotanicalJournal(new FarmingRepository.BotanicalJournalRecord(
                        entry.playerUuid(),
                        entry.highestHarvestStreak(),
                        entry.bestSingleHarvest(),
                        BotanicalJournalEntry.encodeSet(entry.unlockedAchievements()),
                        BotanicalJournalEntry.encodeSet(entry.discoveredVarieties()),
                        Instant.now()
                ));
            } catch (SQLException e) {
                logger.severe("Failed to save Botanical Journal for " + entry.playerUuid() + ": " + e.getMessage());
            }
        });
    }
}

