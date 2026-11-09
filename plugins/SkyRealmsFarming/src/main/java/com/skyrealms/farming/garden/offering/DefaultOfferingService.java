package com.skyrealms.farming.garden.offering;

import com.skyrealms.farming.garden.cinematic.DivineCinematicService;
import com.skyrealms.farming.garden.item.GardenItemFactory;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Authoritative implementation of OfferingService.
 * Enforces atomic item consumption, concurrency locks, and permission validation.
 */
public final class DefaultOfferingService implements OfferingService {

    public static final int REQUIRED_COUNT = 3;

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final IslandService islandService;
    private final DivineCinematicService cinematicService;
    private final Logger logger;

    private final Set<UUID> activeSubmissions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> unlockCache = new ConcurrentHashMap<>();

    public DefaultOfferingService(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull IslandService islandService,
            @NotNull DivineCinematicService cinematicService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.cinematicService = Objects.requireNonNull(cinematicService, "cinematicService cannot be null");
        this.logger = plugin.getLogger();
    }

    @Override
    @NotNull
    public OfferingValidationResult validateInventory(@NotNull Inventory inventory) {
        int hay = 0;
        int carrot = 0;
        int cane = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getAmount() <= 0) continue;

            if (GardenItemFactory.isCustomItem(item, GardenItemFactory.ID_ENCHANTED_HAY_BALE)) {
                hay += item.getAmount();
            } else if (GardenItemFactory.isCustomItem(item, GardenItemFactory.ID_ENCHANTED_CARROT)) {
                carrot += item.getAmount();
            } else if (GardenItemFactory.isCustomItem(item, GardenItemFactory.ID_ENCHANTED_SUGAR_CANE)) {
                cane += item.getAmount();
            }
        }

        boolean valid = (hay >= REQUIRED_COUNT && carrot >= REQUIRED_COUNT && cane >= REQUIRED_COUNT);
        String msg = valid ? "Offering requirements met."
                : String.format("Missing components. Found: %d/3 Hay Bales, %d/3 Carrots, %d/3 Sugar Cane", hay, carrot, cane);

        return new OfferingValidationResult(valid, hay, carrot, cane, msg);
    }

    @Override
    public boolean isGardenUnlocked(@NotNull UUID islandId) {
        Boolean cached = unlockCache.get(islandId);
        if (cached != null) {
            return cached;
        }

        try {
            Optional<FarmingRepository.GardenUnlock> opt = repository.findGardenUnlock(islandId);
            boolean unlocked = opt.map(FarmingRepository.GardenUnlock::offeringCompleted).orElse(false);
            unlockCache.put(islandId, unlocked);
            return unlocked;
        } catch (SQLException e) {
            logger.warning("Could not check garden unlock status for island " + islandId + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public void resetOfferingAndUnlock(@NotNull UUID islandId) {
        unlockCache.remove(islandId);
        try {
            repository.deleteOfferingAndUnlock(islandId);
            logger.info("Reset offering and unlock state for island " + islandId);
        } catch (SQLException e) {
            logger.severe("Failed to reset offering and unlock for island " + islandId + ": " + e.getMessage());
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> processOffering(@NotNull Player player, @NotNull Block chestBlock, @NotNull UUID islandId) {
        // 1. Concurrency Mutex: Prevent simultaneous submissions on the same island
        if (!activeSubmissions.add(islandId)) {
            player.sendMessage(Component.text("⚠ An offering submission is already being processed for this island.", NamedTextColor.YELLOW));
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 2. Validate island permissions
                Optional<Island> optIsland = islandService.getIsland(islandId);
                if (optIsland.isEmpty()) {
                    player.sendMessage(Component.text("✖ Could not find island data.", NamedTextColor.RED));
                    return false;
                }

                Island island = optIsland.get();
                if (!island.hasPermission(player.getUniqueId(), IslandPermission.CONTAINER_ACCESS)) {
                    player.sendMessage(Component.text("⛔ You do not have permission to submit offerings for this island.", NamedTextColor.RED));
                    return false;
                }

                // 3. Check if already unlocked (duplicate prevention)
                if (isGardenUnlocked(islandId)) {
                    player.sendMessage(Component.text("✦ The Gods have already blessed your island with Garden access! Type /garden.", NamedTextColor.GREEN));
                    return false;
                }

                // 4. Synchronize on Bukkit main thread to validate and consume items atomically
                CompletableFuture<Boolean> consumeFuture = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (!(chestBlock.getState() instanceof Chest chest)) {
                            consumeFuture.complete(false);
                            return;
                        }

                        Inventory inv = chest.getBlockInventory();
                        OfferingValidationResult validation = validateInventory(inv);

                        if (!validation.valid()) {
                            player.sendMessage(Component.text("✖ Incomplete offering: " + validation.message(), NamedTextColor.RED));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            consumeFuture.complete(false);
                            return;
                        }

                        // Atomically consume exactly 3 of each item
                        consumeCustomItem(inv, GardenItemFactory.ID_ENCHANTED_HAY_BALE, REQUIRED_COUNT);
                        consumeCustomItem(inv, GardenItemFactory.ID_ENCHANTED_CARROT, REQUIRED_COUNT);
                        consumeCustomItem(inv, GardenItemFactory.ID_ENCHANTED_SUGAR_CANE, REQUIRED_COUNT);

                        consumeFuture.complete(true);
                    } catch (Exception ex) {
                        logger.severe("Error consuming offering items: " + ex.getMessage());
                        consumeFuture.complete(false);
                    }
                });

                boolean consumed = consumeFuture.join();
                if (!consumed) {
                    return false;
                }

                // 5. Persist offering record and unlock state in database
                Instant now = Instant.now();
                repository.saveOffering(new FarmingRepository.GardenOffering(
                        islandId,
                        player.getUniqueId(),
                        REQUIRED_COUNT,
                        REQUIRED_COUNT,
                        REQUIRED_COUNT,
                        "COMPLETED",
                        now
                ));

                repository.saveGardenUnlock(new FarmingRepository.GardenUnlock(
                        islandId,
                        true,
                        false, // cinematic in-progress
                        now
                ));

                unlockCache.put(islandId, true);

                // 6. Launch Divine Cinematic on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cinematicService.playCinematic(player, chestBlock.getLocation(), islandId);
                });

                return true;
            } catch (Exception e) {
                logger.severe("Database or submission error during offering on island " + islandId + ": " + e.getMessage());
                player.sendMessage(Component.text("✖ An error occurred submitting your offering. Please contact staff.", NamedTextColor.RED));
                return false;
            } finally {
                activeSubmissions.remove(islandId);
            }
        });
    }

    private void consumeCustomItem(@NotNull Inventory inventory, @NotNull String customId, int countToConsume) {
        int remaining = countToConsume;
        ItemStack[] contents = inventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getAmount() <= 0) continue;

            if (GardenItemFactory.isCustomItem(item, customId)) {
                int take = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - take);
                if (item.getAmount() <= 0) {
                    inventory.setItem(i, null);
                }
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
    }
}
