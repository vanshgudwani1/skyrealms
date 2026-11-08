package com.skyrealms.farming.service;

import com.skyrealms.farming.event.FarmingGeneratorActivateEvent;
import com.skyrealms.farming.event.FarmingGeneratorCreateEvent;
import com.skyrealms.farming.model.*;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.islands.event.IslandDeletedEvent;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.platform.api.event.EventBus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final ExecutorService executor;

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
            @NotNull EventBus eventBus
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.validator = validator;
        this.eventBus = eventBus;
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

    /**
     * Loads all active generators on server boot into memory.
     */
    public void loadActiveGenerators() {
        try {
            List<FarmingGenerator> loaded = repository.loadAllActiveGenerators();
            for (FarmingGenerator gen : loaded) {
                indexGenerator(gen);
            }
            LOGGER.info("Loaded {} active farming generators from database.", loaded.size());
        } catch (SQLException e) {
            LOGGER.error("Failed to load farming generators from database: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates and activates a new farming generator.
     */
    @NotNull
    public CompletableFuture<FarmingGeneratorValidator.ValidationResult> createAndActivateGenerator(
            @NotNull Player player,
            @NotNull Location coreLocation,
            @NotNull FarmingGeneratorTier tier,
            @NotNull UUID islandId
    ) {
        int currentCount = getGeneratorsByIsland(islandId).size();
        int defaultCapacity = 8;

        FarmingGeneratorValidator.ValidationResult validation = validator.validateStructure(
                player, coreLocation, tier, currentCount, defaultCapacity
        );

        if (!validation.valid()) {
            return CompletableFuture.completedFuture(validation);
        }

        UUID generatorId = UUID.randomUUID();
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
                Collections.emptyList()
        );
        generator.setCropNodes(validation.detectedNodes());

        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.saveGenerator(generator);
                indexGenerator(generator);

                // Run main thread initial crop planting
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plantInitialCrops(generator);
                });

                eventBus.publishAsync(new FarmingGeneratorCreateEvent(generator, player.getUniqueId()));
                eventBus.publishAsync(new FarmingGeneratorActivateEvent(generator, player.getUniqueId()));

                return validation;
            } catch (SQLException e) {
                LOGGER.error("Database error saving generator {}: {}", generatorId, e.getMessage(), e);
                return FarmingGeneratorValidator.ValidationResult.failure("Database error occurred while activating generator.");
            }
        }, executor);
    }

    private void plantInitialCrops(FarmingGenerator generator) {
        World world = Bukkit.getWorld(generator.worldName());
        if (world == null) return;

        FarmingGeneratorTier tier = generator.tier();
        Material targetMaterial = tier.primaryDropMaterial() == Material.WHEAT_SEEDS ? Material.WHEAT :
                (tier == FarmingGeneratorTier.TIER_1_WHEAT ? Material.WHEAT :
                        (tier == FarmingGeneratorTier.TIER_2_ROOT ? Material.CARROTS :
                                (tier == FarmingGeneratorTier.TIER_3_FRUIT ? Material.MELON :
                                        (tier == FarmingGeneratorTier.TIER_4_TROPICAL ? Material.SUGAR_CANE : Material.NETHER_WART))));

        for (CropNode node : generator.cropNodes()) {
            Block block = world.getBlockAt(node.x(), node.y(), node.z());
            block.setType(targetMaterial, false);
            BlockData data = block.getBlockData();
            if (data instanceof Ageable ageable) {
                ageable.setAge(ageable.getMaximumAge());
                block.setBlockData(ageable, false);
            }
        }
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
        // Simple hash key combining coordinates
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
