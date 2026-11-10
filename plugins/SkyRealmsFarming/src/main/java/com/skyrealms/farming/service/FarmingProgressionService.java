package com.skyrealms.farming.service;

import com.skyrealms.farming.event.FarmingLevelUpEvent;
import com.skyrealms.farming.model.FarmingCropCollection;
import com.skyrealms.farming.model.FarmingPlayerProgress;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.platform.api.event.EventBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Authoritative Farming Progression Service.
 * Manages player Farming XP, Level curves, and Crop Collections.
 */
public final class FarmingProgressionService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmingProgressionService.class);

    private final FarmingRepository repository;
    private final EventBus eventBus;
    private final ExecutorService executor;

    // Cache: Player UUID -> FarmingPlayerProgress
    private final Map<UUID, FarmingPlayerProgress> progressCache = new ConcurrentHashMap<>();

    // Cache: Player UUID -> (CropType -> FarmingCropCollection)
    private final Map<UUID, Map<String, FarmingCropCollection>> collectionsCache = new ConcurrentHashMap<>();

    public FarmingProgressionService(@NotNull FarmingRepository repository, @NotNull EventBus eventBus) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SkyRealms-Farming-AsyncWorker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Calculates the required cumulative XP to reach a specific farming level.
     */
    public static long getRequiredXpForLevel(int level) {
        if (level <= 1) return 0;
        return (long) (100 * Math.pow(level - 1, 1.8));
    }

    /**
     * Calculates farming level from cumulative XP.
     */
    public static int calculateLevel(long cumulativeXp) {
        if (cumulativeXp <= 0) return 1;
        int level = 1;
        while (getRequiredXpForLevel(level + 1) <= cumulativeXp) {
            level++;
            if (level >= 100) break; // Level cap
        }
        return level;
    }

    /**
     * Asynchronously preloads player progression on join.
     */
    @NotNull
    public CompletableFuture<Void> preloadPlayer(@NotNull UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                FarmingPlayerProgress progress = repository.getOrCreatePlayerProgress(playerUuid);
                progressCache.put(playerUuid, progress);

                Map<String, FarmingCropCollection> collections = repository.loadCollections(playerUuid);
                collectionsCache.put(playerUuid, new ConcurrentHashMap<>(collections));
            } catch (SQLException e) {
                LOGGER.error("Failed to preload farming data for player {}: {}", playerUuid, e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Evicts player data from in-memory cache on quit.
     */
    public void evictPlayer(@NotNull UUID playerUuid) {
        FarmingPlayerProgress progress = progressCache.remove(playerUuid);
        if (progress != null) {
            executor.execute(() -> {
                try {
                    repository.savePlayerProgress(progress);
                } catch (SQLException e) {
                    LOGGER.error("Failed to flush farming progress on evict for {}: {}", playerUuid, e.getMessage(), e);
                }
            });
        }
        collectionsCache.remove(playerUuid);
    }

    /**
     * Gets player progress from cache, or fetches from DB synchronously if not cached.
     */
    @NotNull
    public FarmingPlayerProgress getProgress(@NotNull UUID playerUuid) {
        FarmingPlayerProgress cached = progressCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        try {
            FarmingPlayerProgress loaded = repository.getOrCreatePlayerProgress(playerUuid);
            progressCache.put(playerUuid, loaded);
            return loaded;
        } catch (SQLException e) {
            LOGGER.error("Error retrieving farming progress for {}: {}", playerUuid, e.getMessage());
            return FarmingPlayerProgress.createDefault(playerUuid);
        }
    }

    /**
     * Awards Farming XP to a player and checks for level transitions.
     */
    public void awardXp(@NotNull Player player, long amount) {
        if (amount <= 0) return;

        FarmingPlayerProgress progress = getProgress(player.getUniqueId());
        int oldLevel = progress.farmingLevel();

        progress.addXp(amount);
        progress.incrementHarvests();

        int newLevel = calculateLevel(progress.farmingXp());
        if (newLevel > oldLevel) {
            progress.setFarmingLevel(newLevel);
            handleLevelUp(player, oldLevel, newLevel);
        }

        // Action bar notification
        long currentLevelXp = getRequiredXpForLevel(newLevel);
        long nextLevelXp = getRequiredXpForLevel(newLevel + 1);
        long progressInLevel = progress.farmingXp() - currentLevelXp;
        long neededForNext = Math.max(1, nextLevelXp - currentLevelXp);

        Component actionBar = Component.text("+" + amount + " Farming XP", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(" (Lvl " + newLevel + " • " + progressInLevel + "/" + neededForNext + ")", NamedTextColor.GRAY));
        player.sendActionBar(actionBar);

        // Async save
        executor.execute(() -> {
            try {
                repository.savePlayerProgress(progress);
            } catch (SQLException e) {
                LOGGER.error("Failed to save progress for {}: {}", player.getUniqueId(), e.getMessage());
            }
        });
    }

    /**
     * Increments crop harvest count in collection by Material.
     */
    public void recordCropHarvest(@NotNull Player player, @NotNull Material cropMaterial, long amount) {
        recordCropHarvest(player, cropMaterial.name(), amount);
    }

    /**
     * Increments crop harvest count in collection by crop identifier string.
     */
    public void recordCropHarvest(@NotNull Player player, @NotNull String cropName, long amount) {
        cropName = cropName.toUpperCase();
        Map<String, FarmingCropCollection> playerCollections = collectionsCache.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        final String finalCropName = cropName;
        FarmingCropCollection col = playerCollections.computeIfAbsent(cropName, k -> new FarmingCropCollection(player.getUniqueId(), finalCropName, 0L, 1, java.time.Instant.now()));

        col.addAmount(amount);

        // Check milestones: 100, 500, 2500, 10000, 50000
        int currentTier = col.tierUnlocked();
        int newTier = calculateCollectionTier(col.amountCollected());
        if (newTier > currentTier) {
            col.setTierUnlocked(newTier);
            player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                    .append(Component.text(finalCropName + " Collection ", NamedTextColor.YELLOW))
                    .append(Component.text("Tier " + newTier, NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" (/garden journal)", NamedTextColor.DARK_GRAY)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.5f);
        }

        final String persistentName = cropName;
        executor.execute(() -> {
            try {
                repository.incrementCollection(player.getUniqueId(), persistentName, amount);
            } catch (SQLException e) {
                LOGGER.error("Failed to increment collection for {}: {}", player.getUniqueId(), e.getMessage());
            }
        });
    }

    public void incrementCollection(@NotNull UUID playerUuid, @NotNull String cropName, long amount) {
        executor.execute(() -> {
            try {
                repository.incrementCollection(playerUuid, cropName.toUpperCase(), amount);
            } catch (SQLException e) {
                LOGGER.error("Failed to increment collection for {}: {}", playerUuid, e.getMessage());
            }
        });
    }

    @NotNull
    public Map<String, FarmingCropCollection> getCollections(@NotNull UUID playerUuid) {
        Map<String, FarmingCropCollection> cached = collectionsCache.get(playerUuid);
        if (cached != null) {
            return Collections.unmodifiableMap(cached);
        }
        try {
            Map<String, FarmingCropCollection> loaded = repository.loadCollections(playerUuid);
            collectionsCache.put(playerUuid, new ConcurrentHashMap<>(loaded));
            return Collections.unmodifiableMap(loaded);
        } catch (SQLException e) {
            LOGGER.error("Error loading collections for {}: {}", playerUuid, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static int calculateCollectionTier(long total) {
        if (total >= 50000) return 5;
        if (total >= 10000) return 4;
        if (total >= 2500) return 3;
        if (total >= 500) return 2;
        if (total >= 100) return 1;
        return 0;
    }

    private void handleLevelUp(@NotNull Player player, int oldLevel, int newLevel) {
        eventBus.publishAsync(new FarmingLevelUpEvent(player, oldLevel, newLevel));

        Title title = Title.title(
                Component.text("FARMING LEVEL UP!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Level " + oldLevel + " ➔ " + newLevel, NamedTextColor.GREEN),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(500))
        );
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
        player.sendMessage(Component.text("🌾 FARMING LEVEL UP! Level " + newLevel, NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Increased generator harvesting mastery!", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("✦ Check /farm to view newly unlocked generator tiers!", NamedTextColor.AQUA));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
    }

    private static String formatName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
