package com.skyrealms.farming.garden.quest;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.farming.service.FarmingProgressionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Production implementation of FarmingQuestService.
 * Tracks progress for First Harvest quest, protects against duplicate rewards,
 * and persists progress safely to H2/PostgreSQL.
 */
public final class DefaultFarmingQuestService implements FarmingQuestService {

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final FarmingProgressionService progressionService;
    private final EconomyService economyService;
    private final Logger logger;

    // Cache: playerUuid -> (questId -> FarmingQuestProgress)
    private final Map<UUID, Map<String, FarmingQuestProgress>> questCache = new ConcurrentHashMap<>();

    public DefaultFarmingQuestService(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull FarmingProgressionService progressionService,
            @NotNull EconomyService economyService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.logger = plugin.getLogger();
    }

    @Override
    @NotNull
    public Optional<FarmingQuestProgress> getQuest(@NotNull UUID playerUuid, @NotNull String questId) {
        Map<String, FarmingQuestProgress> playerQuests = questCache.get(playerUuid);
        if (playerQuests != null && playerQuests.containsKey(questId)) {
            return Optional.ofNullable(playerQuests.get(questId));
        }

        try {
            Optional<FarmingRepository.FarmingQuest> opt = repository.findQuestProgress(playerUuid, questId);
            if (opt.isPresent()) {
                FarmingRepository.FarmingQuest q = opt.get();
                FarmingQuestProgress progress = new FarmingQuestProgress(
                        q.playerUuid(),
                        q.questId(),
                        q.status(),
                        q.tilledCount(),
                        q.plantedCount(),
                        q.harvestedCount(),
                        "COMPLETED".equalsIgnoreCase(q.status()),
                        q.claimedRewards()
                );
                questCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(questId, progress);
                return Optional.of(progress);
            }
        } catch (SQLException e) {
            logger.warning("Failed to fetch quest " + questId + " for player " + playerUuid + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void startQuest(@NotNull UUID playerUuid, @NotNull String questId) {
        Optional<FarmingQuestProgress> existing = getQuest(playerUuid, questId);
        if (existing.isPresent()) {
            return; // Already started or completed
        }

        FarmingQuestProgress progress = new FarmingQuestProgress(
                playerUuid,
                questId,
                "IN_PROGRESS",
                0,
                0,
                0,
                false,
                false
        );
        questCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(questId, progress);

        saveAsync(progress, null);
    }

    @Override
    public void recordTill(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        FarmingQuestProgress progress = getQuest(uuid, QUEST_FIRST_HARVEST).orElse(null);
        if (progress == null || progress.isCompleted() || progress.tilledCount() >= FIRST_HARVEST_TARGET) {
            return;
        }

        int newTilled = progress.tilledCount() + 1;
        FarmingQuestProgress updated = new FarmingQuestProgress(
                uuid,
                QUEST_FIRST_HARVEST,
                progress.status(),
                newTilled,
                progress.plantedCount(),
                progress.harvestedCount(),
                false,
                progress.claimedRewards()
        );

        questCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(QUEST_FIRST_HARVEST, updated);
        saveAsync(updated, null);

        player.sendMessage(Component.text("🌱 Quest Progress: ", NamedTextColor.GREEN)
                .append(Component.text("Farmland Prepared (" + newTilled + "/" + FIRST_HARVEST_TARGET + ")", NamedTextColor.YELLOW)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    @Override
    public void recordPlant(@NotNull Player player, @NotNull Material cropMat) {
        if (cropMat != Material.WHEAT && cropMat != Material.WHEAT_SEEDS) {
            return;
        }

        UUID uuid = player.getUniqueId();
        FarmingQuestProgress progress = getQuest(uuid, QUEST_FIRST_HARVEST).orElse(null);
        if (progress == null || progress.isCompleted() || progress.plantedCount() >= FIRST_HARVEST_TARGET) {
            return;
        }

        int newPlanted = progress.plantedCount() + 1;
        FarmingQuestProgress updated = new FarmingQuestProgress(
                uuid,
                QUEST_FIRST_HARVEST,
                progress.status(),
                progress.tilledCount(),
                newPlanted,
                progress.harvestedCount(),
                false,
                progress.claimedRewards()
        );

        questCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(QUEST_FIRST_HARVEST, updated);
        saveAsync(updated, null);

        player.sendMessage(Component.text("🌾 Quest Progress: ", NamedTextColor.GREEN)
                .append(Component.text("Wheat Planted (" + newPlanted + "/" + FIRST_HARVEST_TARGET + ")", NamedTextColor.YELLOW)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.3f);
    }

    @Override
    public void recordHarvest(@NotNull Player player, @NotNull String cropType) {
        if (!"WHEAT".equalsIgnoreCase(cropType)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        FarmingQuestProgress progress = getQuest(uuid, QUEST_FIRST_HARVEST).orElse(null);
        if (progress == null || progress.isCompleted() || progress.harvestedCount() >= FIRST_HARVEST_TARGET) {
            return;
        }

        int newHarvested = progress.harvestedCount() + 1;
        boolean allComplete = (progress.tilledCount() >= FIRST_HARVEST_TARGET
                && progress.plantedCount() >= FIRST_HARVEST_TARGET
                && newHarvested >= FIRST_HARVEST_TARGET);

        FarmingQuestProgress updated = new FarmingQuestProgress(
                uuid,
                QUEST_FIRST_HARVEST,
                allComplete ? "COMPLETED" : "IN_PROGRESS",
                progress.tilledCount(),
                progress.plantedCount(),
                newHarvested,
                allComplete,
                allComplete // Will claim immediately
        );

        questCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(QUEST_FIRST_HARVEST, updated);
        saveAsync(updated, allComplete ? Instant.now() : null);

        if (allComplete) {
            completeFirstHarvestQuest(player);
        } else {
            player.sendMessage(Component.text("✨ Quest Progress: ", NamedTextColor.GREEN)
                    .append(Component.text("Wheat Harvested (" + newHarvested + "/" + FIRST_HARVEST_TARGET + ")", NamedTextColor.YELLOW)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
        }
    }

    private void completeFirstHarvestQuest(@NotNull Player player) {
        // 1. Award 500 Farming XP
        progressionService.awardXp(player, 500);

        // 2. Award 2,500 coins via EconomyService
        economyService.deposit(
                player.getUniqueId(),
                "coins",
                BigDecimal.valueOf(2500),
                "Quest Completed: First Harvest",
                "quest-first-harvest-" + player.getUniqueId()
        );

        // 3. Give 16 Carrots
        ItemStack carrots = new ItemStack(Material.CARROT, 16);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(carrots);
        if (!overflow.isEmpty()) {
            for (ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        // 4. Celebration effects & fanfare
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
        Title title = Title.title(
                Component.text("✦ QUEST COMPLETED! ✦", TextColor.fromHexString("#77DD77"), TextDecoration.BOLD),
                Component.text("First Harvest: You are now an established farmer!", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(1000))
        );
        player.showTitle(title);

        Component rewardMsg = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.LIGHT_PURPLE, TextDecoration.STRIKETHROUGH)
                .append(Component.newline())
                .append(Component.text("🎉 QUEST COMPLETE: FIRST HARVEST", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Elarion is proud of your dedication to the land!", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("Rewards Claimed:", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("  • +500 Farming XP", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("  • +2,500 Coins", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("  • 16x Crisp Garden Carrots", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.LIGHT_PURPLE, TextDecoration.STRIKETHROUGH));

        player.sendMessage(rewardMsg);
    }

    private void saveAsync(@NotNull FarmingQuestProgress progress, Instant completedAt) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.saveQuestProgress(new FarmingRepository.FarmingQuest(
                        progress.playerUuid(),
                        progress.questId(),
                        progress.status(),
                        progress.tilledCount(),
                        progress.plantedCount(),
                        progress.harvestedCount(),
                        completedAt,
                        progress.claimedRewards(),
                        Instant.now()
                ));
            } catch (SQLException e) {
                logger.severe("Failed to persist quest progress for player " + progress.playerUuid() + ": " + e.getMessage());
            }
        });
    }
}
