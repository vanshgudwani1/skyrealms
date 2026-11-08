package com.skyrealms.farming.service;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.event.FarmingGeneratorActivateEvent;
import com.skyrealms.farming.event.FarmingGeneratorCreateEvent;
import com.skyrealms.farming.model.*;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.islands.event.IslandDeletedEvent;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.platform.api.event.EventBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages active generator state, in-memory lookups, and lifecycle coordination.
 */
public final class FarmingGeneratorManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmingGeneratorManager.class);

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final FarmingGeneratorValidator validator;
    private final EventBus eventBus;
    private final IslandService islandService;
    private final ExecutorService executor;

    private FarmingHologramService hologramService;
    private FarmingTerraformService terraformService;
    private OverclockManager overclockManager;
    private HarvestComboTracker comboTracker;
    private EconomyService economyService;
    private FarmingProgressionService progressionService;

    // Cache: Generator UUID -> FarmingGenerator
    private final Map<UUID, FarmingGenerator> generators = new ConcurrentHashMap<>();

    // Spatial Index: 64-bit coordinate key -> Generator
    private final Map<Long, FarmingGenerator> coreLocationIndex = new ConcurrentHashMap<>();

    // Spatial Index: 64-bit coordinate key -> (Generator, CropNode)
    private final Map<Long, NodeEntry> cropLocationIndex = new ConcurrentHashMap<>();

    public record NodeEntry(@NotNull FarmingGenerator generator, @NotNull CropNode node) {}

    public FarmingGeneratorManager(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull FarmingGeneratorValidator validator,
            @NotNull EventBus eventBus,
            @NotNull IslandService islandService
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.validator = validator;
        this.eventBus = eventBus;
        this.islandService = islandService;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SkyRealms-Farming-Manager-Worker");
            t.setDaemon(true);
            return t;
        });

        // Listen for island deletion events to clean up generators
        eventBus.subscribe(IslandDeletedEvent.class, event -> {
            onIslandDeleted(event.islandId());
        });
    }

    public void setServices(
            @NotNull FarmingHologramService hologramService,
            @NotNull FarmingTerraformService terraformService,
            @NotNull OverclockManager overclockManager,
            @NotNull HarvestComboTracker comboTracker,
            @NotNull EconomyService economyService,
            @NotNull FarmingProgressionService progressionService
    ) {
        this.hologramService = hologramService;
        this.terraformService = terraformService;
        this.overclockManager = overclockManager;
        this.comboTracker = comboTracker;
        this.economyService = economyService;
        this.progressionService = progressionService;
    }

    public FarmingHologramService hologramService() {
        return hologramService;
    }

    public FarmingTerraformService terraformService() {
        return terraformService;
    }

    public OverclockManager overclockManager() {
        return overclockManager;
    }

    public HarvestComboTracker comboTracker() {
        return comboTracker;
    }

    /**
     * Loads all active generators on server boot into memory and recreates their holograms.
     */
    public void loadActiveGenerators() {
        try {
            List<FarmingGenerator> loaded = repository.loadAllActiveGenerators();
            for (FarmingGenerator gen : loaded) {
                indexGenerator(gen);
                if (hologramService != null) {
                    hologramService.createOrUpdateHologram(gen, "&a● READY TO HARVEST");
                }
            }
            LOGGER.info("Loaded {} active farming generators from database.", loaded.size());
        } catch (SQLException e) {
            LOGGER.error("Failed to load farming generators from database: {}", e.getMessage(), e);
        }
    }

    /**
     * Validates placement, registers the generator, and triggers the Earth Bloom animation.
     */
    @NotNull
    public CompletableFuture<FarmingGeneratorValidator.ValidationResult> createAndTerraformGenerator(
            @NotNull Player player,
            @NotNull Location coreLocation,
            @NotNull FarmingGeneratorTier tier,
            @Nullable Map<String, Integer> initialUpgrades
    ) {
        Optional<Island> optIsland = islandService.getIslandAt(coreLocation);
        if (optIsland.isEmpty()) {
            return CompletableFuture.completedFuture(
                    FarmingGeneratorValidator.ValidationResult.failure("Must be placed inside an active SkyBlock island.")
            );
        }

        UUID islandId = optIsland.get().id();
        int currentCount = getGeneratorsByIsland(islandId).size();

        FarmingGeneratorValidator.ValidationResult validation = validator.validatePlacement(
                player, coreLocation, tier, currentCount
        );

        if (!validation.valid()) {
            // Draw red dust particles at invalid locations
            World w = coreLocation.getWorld();
            if (w != null) {
                for (Location loc : validation.invalidLocations()) {
                    w.spawnParticle(Particle.DUST, loc, 5, 0.1, 0.1, 0.1, new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
                }
            }
            return CompletableFuture.completedFuture(validation);
        }

        UUID generatorId = UUID.randomUUID();
        List<GeneratorUpgrade> upgradesList = new ArrayList<>();
        if (initialUpgrades != null) {
            for (Map.Entry<String, Integer> entry : initialUpgrades.entrySet()) {
                upgradesList.add(new GeneratorUpgrade(entry.getKey(), entry.getValue(), Instant.now()));
            }
        }

        FarmingGenerator generator = new FarmingGenerator(
                generatorId,
                islandId,
                player.getUniqueId(),
                tier,
                coreLocation.getWorld().getName(),
                coreLocation.getBlockX(),
                coreLocation.getBlockY(),
                coreLocation.getBlockZ(),
                GeneratorStatus.ACTIVE,
                0L,
                Instant.now(),
                Instant.now(),
                upgradesList
        );
        generator.setCropNodes(validation.detectedNodes());

        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.saveGenerator(generator);
                indexGenerator(generator);

                // Run main thread Earth Bloom auto-terraforming sequence
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (terraformService != null) {
                        terraformService.executeTerraformAnimation(player, generator, () -> {
                            eventBus.publishAsync(new FarmingGeneratorCreateEvent(generator, player.getUniqueId()));
                            eventBus.publishAsync(new FarmingGeneratorActivateEvent(generator, player.getUniqueId()));
                        });
                    }
                });

                return validation;
            } catch (SQLException e) {
                LOGGER.error("Database error saving generator {}: {}", generatorId, e.getMessage(), e);
                return FarmingGeneratorValidator.ValidationResult.failure("Database error occurred while activating generator.");
            }
        }, executor);
    }

    public void indexGenerator(@NotNull FarmingGenerator generator) {
        generators.put(generator.generatorId(), generator);
        long coreKey = locationToKey(generator.worldName(), generator.coreX(), generator.coreY(), generator.coreZ());
        coreLocationIndex.put(coreKey, generator);

        for (CropNode node : generator.cropNodes()) {
            long nodeKey = locationToKey(node.worldName(), node.x(), node.y(), node.z());
            cropLocationIndex.put(nodeKey, new NodeEntry(generator, node));
        }
    }

    public void unindexGenerator(@NotNull FarmingGenerator generator) {
        generators.remove(generator.generatorId());
        long coreKey = locationToKey(generator.worldName(), generator.coreX(), generator.coreY(), generator.coreZ());
        coreLocationIndex.remove(coreKey);

        for (CropNode node : generator.cropNodes()) {
            long nodeKey = locationToKey(node.worldName(), node.x(), node.y(), node.z());
            cropLocationIndex.remove(nodeKey);
        }

        if (hologramService != null) {
            hologramService.removeHologram(generator);
        }
    }

    @NotNull
    public Optional<FarmingGenerator> getGenerator(@NotNull UUID generatorId) {
        return Optional.ofNullable(generators.get(generatorId));
    }

    @NotNull
    public Optional<FarmingGenerator> getGeneratorAtCore(@NotNull Location location) {
        if (location.getWorld() == null) return Optional.empty();
        long key = locationToKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return Optional.ofNullable(coreLocationIndex.get(key));
    }

    @NotNull
    public Optional<NodeEntry> findNodeAt(@NotNull Location location) {
        if (location.getWorld() == null) return Optional.empty();
        long key = locationToKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return Optional.ofNullable(cropLocationIndex.get(key));
    }

    @NotNull
    public List<FarmingGenerator> getGeneratorsByIsland(@NotNull UUID islandId) {
        List<FarmingGenerator> list = new ArrayList<>();
        for (FarmingGenerator gen : generators.values()) {
            if (gen.islandId().equals(islandId)) {
                list.add(gen);
            }
        }
        return list;
    }

    @NotNull
    public Collection<FarmingGenerator> getAllActiveGenerators() {
        return Collections.unmodifiableCollection(generators.values());
    }

    /**
     * Removes and deletes a generator permanently.
     */
    @NotNull
    public CompletableFuture<Boolean> removeGenerator(@NotNull UUID generatorId) {
        FarmingGenerator gen = generators.get(generatorId);
        if (gen != null) {
            unindexGenerator(gen);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.deleteGenerator(generatorId);
                return true;
            } catch (SQLException e) {
                LOGGER.error("Failed to delete generator {}: {}", generatorId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    /**
     * Safely dismantles a generator, clears core block, and refunds custom Core item with upgrades.
     */
    @NotNull
    public CompletableFuture<Boolean> dismantleGenerator(@NotNull Player player, @NotNull FarmingGenerator generator) {
        UUID generatorId = generator.generatorId();
        unindexGenerator(generator);

        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.deleteGenerator(generatorId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    World w = Bukkit.getWorld(generator.worldName());
                    if (w != null) {
                        Block coreBlock = w.getBlockAt(generator.coreX(), generator.coreY(), generator.coreZ());
                        coreBlock.setType(Material.AIR);

                        Map<String, Integer> preservedUpgrades = new HashMap<>();
                        for (GeneratorUpgrade u : generator.upgrades().values()) {
                            preservedUpgrades.put(u.key(), u.level());
                        }

                        ItemStack refundedCore = FarmingCoreItemFactory.createCoreItem(generator.tier(), preservedUpgrades);
                        var leftover = player.getInventory().addItem(refundedCore);
                        for (ItemStack drop : leftover.values()) {
                            w.dropItemNaturally(player.getLocation(), drop);
                        }

                        w.playSound(coreBlock.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                        w.spawnParticle(Particle.POOF, coreBlock.getLocation().clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.05);
                        player.sendMessage(Component.text("✦ [Farming Generator] Safely dismantled! Your Core has been refunded with upgrades preserved.", NamedTextColor.GREEN));
                    }
                });
                return true;
            } catch (SQLException e) {
                LOGGER.error("Failed to delete generator during dismantle: {}", e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    /**
     * Evolves an existing generator in-place to the next progression tier.
     */
    @NotNull
    public CompletableFuture<Boolean> evolveGenerator(@NotNull Player player, @NotNull FarmingGenerator generator) {
        FarmingGeneratorTier nextTier = generator.tier().nextTier();
        if (nextTier == null) {
            player.sendMessage(Component.text("⚠ Generator is already at the maximum tier (Mystic)!", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }

        if (progressionService != null) {
            FarmingPlayerProgress progress = progressionService.getProgress(player.getUniqueId());
            if (progress.farmingLevel() < nextTier.requiredFarmingLevel()) {
                player.sendMessage(Component.text("⚠ You must reach Farming Level " + nextTier.requiredFarmingLevel() + " to evolve to " + nextTier.displayName() + "!", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
        }

        BigDecimal cost = nextTier.evolutionCost();
        if (economyService == null) {
            return CompletableFuture.completedFuture(false);
        }

        String idKey = "farm-evolve-" + generator.generatorId() + "-" + nextTier.name();
        return economyService.withdraw(player.getUniqueId(), "coins", cost, "Evolve Generator to " + nextTier.name(), idKey).thenApply(txResult -> {
            if (!txResult.isSuccess()) {
                player.sendMessage(Component.text("⚠ Insufficient coins! Evolution requires " + cost + " Coins.", NamedTextColor.RED));
                return false;
            }

            generator.setTier(nextTier);
            try {
                repository.saveGenerator(generator);
            } catch (SQLException e) {
                LOGGER.error("Failed to persist evolved tier for generator {}: {}", generator.generatorId(), e.getMessage(), e);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                World w = Bukkit.getWorld(generator.worldName());
                Location coreLoc = new Location(w, generator.coreX() + 0.5, generator.coreY() + 0.5, generator.coreZ() + 0.5);

                if (hologramService != null) {
                    hologramService.createOrUpdateHologram(generator, "&a● READY TO HARVEST");
                }

                if (w != null) {
                    Material cropMat = switch (nextTier) {
                        case TIER_1_WHEAT -> Material.WHEAT;
                        case TIER_2_ROOT -> Material.CARROTS;
                        case TIER_3_FRUIT -> Material.MELON;
                        case TIER_4_TROPICAL -> Material.SUGAR_CANE;
                        case TIER_5_MYSTIC -> Material.NETHER_WART;
                    };

                    for (CropNode node : generator.cropNodes()) {
                        Block b = w.getBlockAt(node.x(), node.y(), node.z());
                        b.setType(cropMat, false);
                        if (b.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                            ageable.setAge(ageable.getMaximumAge());
                            b.setBlockData(ageable, false);
                        }
                        w.spawnParticle(Particle.HAPPY_VILLAGER, new Location(w, node.x() + 0.5, node.y() + 0.4, node.z() + 0.5), 5, 0.2, 0.2, 0.2, 0.0);
                    }

                    w.playSound(coreLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    w.playSound(coreLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, coreLoc.clone().add(0, 1.0, 0), 30, 0.5, 0.5, 0.5, 0.1);
                }

                player.sendMessage(Component.text("★ [Generator Evolved] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("Successfully evolved to " + nextTier.displayName() + " for " + cost + " Coins!", NamedTextColor.GREEN)));
            });

            return true;
        });
    }

    /**
     * Handles island deletion: cleans up all active generators tied to that island.
     */
    public void onIslandDeleted(@NotNull UUID islandId) {
        List<FarmingGenerator> islandGens = getGeneratorsByIsland(islandId);
        for (FarmingGenerator gen : islandGens) {
            unindexGenerator(gen);
            executor.execute(() -> {
                try {
                    repository.deleteGenerator(gen.generatorId());
                } catch (SQLException e) {
                    LOGGER.warn("Error deleting generator {} during island deletion: {}", gen.generatorId(), e.getMessage());
                }
            });
        }
    }

    public static long locationToKey(@NotNull String world, int x, int y, int z) {
        long h = world.hashCode();
        h = 31 * h + x;
        h = 31 * h + y;
        h = 31 * h + z;
        return h;
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
