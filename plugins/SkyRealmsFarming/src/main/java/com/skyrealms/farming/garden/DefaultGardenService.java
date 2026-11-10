package com.skyrealms.farming.garden;

import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.islands.lobby.TeleportWarmupService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Authoritative implementation of GardenService.
 * Coordinates spatial allocation, world management, dome procedural generation,
 * caching, and teleport warmup integration.
 */
public final class DefaultGardenService implements GardenService {

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final GardenWorldManager worldManager;
    private final GardenSpatialAllocator allocator;
    private final GardenDomeGenerator domeGenerator;
    private final IslandService islandService;
    private final TeleportWarmupService warmupService;
    private final Logger logger;

    private final Map<UUID, Garden> gardensByIsland = new ConcurrentHashMap<>();
    private final Map<Long, UUID> spatialIndex = new ConcurrentHashMap<>();
    private final Map<UUID, List<GardenPlot>> plotsByIsland = new ConcurrentHashMap<>();

    public DefaultGardenService(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull GardenWorldManager worldManager,
            @NotNull GardenSpatialAllocator allocator,
            @NotNull IslandService islandService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.worldManager = Objects.requireNonNull(worldManager, "worldManager cannot be null");
        this.allocator = Objects.requireNonNull(allocator, "allocator cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.domeGenerator = new GardenDomeGenerator(plugin.getLogger());
        this.warmupService = new TeleportWarmupService(plugin);
        this.logger = plugin.getLogger();

        loadAllGardensFromDatabase();
    }

    private void loadAllGardensFromDatabase() {
        try {
            List<Garden> gardens = repository.loadAllGardens();
            for (Garden loaded : gardens) {
                Garden garden = normalizeRegionSize(loaded);
                gardensByIsland.put(garden.islandId(), garden);
                long key = spatialKey(garden.centerX(), garden.centerZ());
                spatialIndex.put(key, garden.islandId());

                List<GardenPlot> plots = repository.findPlotsByIsland(garden.islandId());
                if (plots.isEmpty()) {
                    logger.info("Auto-initializing 16 plots for Garden of island " + garden.islandId());
                    for (int i = 1; i <= 16; i++) {
                        GardenPlot plot = new GardenPlot(
                                garden.islandId(),
                                i,
                                (i == 1),
                                (i == 1) ? "WHEAT" : null,
                                "BALANCED",
                                1.0, 1.0, 1.0
                        );
                        repository.savePlot(plot);
                        plots.add(plot);
                    }
                }
                plotsByIsland.put(garden.islandId(), new ArrayList<>(plots));
            }
            logger.info("Loaded " + gardens.size() + " active SkyRealms Gardens from database.");
        } catch (SQLException e) {
            logger.warning("Could not preload gardens from database: " + e.getMessage());
        }
    }

    private Garden normalizeRegionSize(@NotNull Garden garden) throws SQLException {
        if (garden.size() >= allocator.regionSize()) {
            return garden;
        }
        GardenSpatialAllocator.GridCoord expectedCenter = allocator.getGardenCenter(garden.regionIndex());
        Garden upgraded = new Garden(
                garden.gardenId(),
                garden.islandId(),
                garden.regionIndex(),
                garden.worldName(),
                expectedCenter.x(),
                garden.centerY(),
                expectedCenter.z(),
                allocator.regionSize(),
                garden.unlocked(),
                garden.unlockedAt(),
                garden.createdAt()
        );
        repository.saveGarden(upgraded);
        logger.info("Upgraded Garden region bounds for island " + garden.islandId()
                + " from " + garden.size() + " to " + allocator.regionSize() + " blocks.");
        return upgraded;
    }

    private long spatialKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    @Override
    @NotNull
    public Optional<Garden> getGarden(@NotNull UUID islandId) {
        return Optional.ofNullable(gardensByIsland.get(islandId));
    }

    @Override
    @NotNull
    public Optional<Garden> getGardenByPlayer(@NotNull UUID playerId) {
        Optional<Island> optIsland = islandService.getIslandByPlayer(playerId);
        return optIsland.flatMap(island -> getGarden(island.id()));
    }

    @Override
    @NotNull
    public Optional<Garden> getGardenAt(@NotNull Location location) {
        if (!isGardenWorld(location.getWorld())) {
            return Optional.empty();
        }

        GardenSpatialAllocator.GridCoord anchor = allocator.getNearestGridAnchor(location.getBlockX(), location.getBlockZ());
        long key = spatialKey(anchor.x(), anchor.z());
        UUID islandId = spatialIndex.get(key);
        if (islandId == null) {
            return Optional.empty();
        }

        Garden garden = gardensByIsland.get(islandId);
        if (garden != null && garden.isInRegion(location)) {
            return Optional.of(garden);
        }
        return Optional.empty();
    }

    @Override
    @NotNull
    public Collection<Garden> getAllActiveGardens() {
        return Collections.unmodifiableCollection(gardensByIsland.values());
    }

    @Override
    @NotNull
    public CompletableFuture<Garden> getOrCreateGarden(@NotNull UUID islandId) {
        Garden existing = gardensByIsland.get(islandId);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Check database first
                Optional<Garden> fromDb = repository.findGardenByIsland(islandId);
                if (fromDb.isPresent()) {
                    Garden garden = normalizeRegionSize(fromDb.get());
                    cacheGarden(garden);
                    List<GardenPlot> plots = repository.findPlotsByIsland(islandId);
                    plotsByIsland.put(islandId, new ArrayList<>(plots));
                    return garden;
                }

                // 2. Allocate new deterministic spatial slot
                int nextIndex = repository.findNextGardenRegionIndex();
                GardenSpatialAllocator.GridCoord center = allocator.getGardenCenter(nextIndex);

                Garden newGarden = new Garden(
                        UUID.randomUUID(),
                        islandId,
                        nextIndex,
                        worldManager.worldName(),
                        center.x(),
                        100,
                        center.z(),
                        allocator.regionSize(),
                        true,
                        Instant.now(),
                        Instant.now()
                );

                // 3. Persist garden
                repository.saveGarden(newGarden);

                // 4. Initialize 16 plots (Plot 1 unlocked, Plots 2-16 locked)
                List<GardenPlot> initialPlots = new ArrayList<>(16);
                for (int i = 1; i <= 16; i++) {
                    GardenPlot plot = new GardenPlot(
                            islandId,
                            i,
                            (i == 1), // Starter plot is unlocked
                            (i == 1) ? "WHEAT" : null,
                            "BALANCED",
                            1.0,
                            1.0,
                            1.0
                    );
                    repository.savePlot(plot);
                    initialPlots.add(plot);
                }

                cacheGarden(newGarden);
                plotsByIsland.put(islandId, initialPlots);

                // 5. Generate Greenhouse Dome on main thread
                CompletableFuture<Void> genFuture = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        World world = worldManager.getOrCreateGardenWorld();
                        domeGenerator.generateDome(world, newGarden.centerX(), newGarden.centerY(), newGarden.centerZ());
                        genFuture.complete(null);
                    } catch (Exception ex) {
                        logger.severe("Error generating greenhouse dome: " + ex.getMessage());
                        genFuture.completeExceptionally(ex);
                    }
                });

                genFuture.join();
                return newGarden;
            } catch (SQLException e) {
                logger.severe("Database error creating garden for island " + islandId + ": " + e.getMessage());
                throw new RuntimeException("Could not create garden", e);
            }
        });
    }

    private void cacheGarden(@NotNull Garden garden) {
        gardensByIsland.put(garden.islandId(), garden);
        long key = spatialKey(garden.centerX(), garden.centerZ());
        spatialIndex.put(key, garden.islandId());
    }

    @Override
    @NotNull
    public List<GardenPlot> getPlots(@NotNull UUID islandId) {
        List<GardenPlot> cached = plotsByIsland.get(islandId);
        if (cached != null && !cached.isEmpty()) {
            return Collections.unmodifiableList(cached);
        }
        try {
            List<GardenPlot> fromDb = repository.findPlotsByIsland(islandId);
            if (fromDb.isEmpty()) {
                logger.info("Initializing 16 default plots on demand for island " + islandId);
                List<GardenPlot> initialPlots = new ArrayList<>(16);
                for (int i = 1; i <= 16; i++) {
                    GardenPlot plot = new GardenPlot(
                            islandId,
                            i,
                            (i == 1),
                            (i == 1) ? "WHEAT" : null,
                            "BALANCED",
                            1.0, 1.0, 1.0
                    );
                    repository.savePlot(plot);
                    initialPlots.add(plot);
                }
                plotsByIsland.put(islandId, initialPlots);
                return Collections.unmodifiableList(initialPlots);
            }
            plotsByIsland.put(islandId, new ArrayList<>(fromDb));
            return Collections.unmodifiableList(fromDb);
        } catch (SQLException e) {
            logger.warning("Error fetching plots for island " + islandId + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @NotNull
    public Optional<GardenPlot> getPlotAt(@NotNull Location location) {
        Optional<Garden> optGarden = getGardenAt(location);
        if (optGarden.isEmpty()) {
            return Optional.empty();
        }
        Garden garden = optGarden.get();
        List<GardenPlot> plots = getPlots(garden.islandId());
        int bx = location.getBlockX();
        int bz = location.getBlockZ();

        for (GardenPlot plot : plots) {
            if (plot.contains(garden.centerX(), garden.centerZ(), bx, bz)) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> unlockPlot(@NotNull UUID islandId, int plotIndex) {
        if (plotIndex < 1 || plotIndex > 16) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<GardenPlot> plots = new ArrayList<>(getPlots(islandId));
                GardenPlot target = null;
                int targetIdx = -1;
                for (int i = 0; i < plots.size(); i++) {
                    if (plots.get(i).plotIndex() == plotIndex) {
                        target = plots.get(i);
                        targetIdx = i;
                        break;
                    }
                }

                if (target == null || target.unlocked()) {
                    return false; // Already unlocked or not found
                }

                GardenPlot updated = new GardenPlot(
                        islandId,
                        plotIndex,
                        true,
                        target.cropType(),
                        target.soilType(),
                        target.fertility(),
                        target.moisture(),
                        target.nutrientLevel()
                );

                repository.savePlot(updated);
                plots.set(targetIdx, updated);
                plotsByIsland.put(islandId, plots);
                return true;
            } catch (SQLException e) {
                logger.severe("Database error unlocking plot " + plotIndex + " for island " + islandId + ": " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public void teleportToGarden(@NotNull Player player, @NotNull UUID islandId) {
        getOrCreateGarden(islandId).thenAccept(garden -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location dest = garden.getSpawnLocation();
                warmupService.teleportWithWarmup(player, dest, "SkyRealms Garden", () -> {
                    player.sendMessage(Component.text("✦ Entered your Sakura Garden ", NamedTextColor.GREEN)
                            .append(Component.text("(/garden mastery | /garden leave)", NamedTextColor.DARK_GRAY)));
                });
            });
        }).exceptionally(ex -> {
            player.sendMessage(Component.text("✖ Could not load your Garden: " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    @Override
    public void teleportToIsland(@NotNull Player player, @NotNull UUID islandId) {
        Optional<Island> opt = islandService.getIsland(islandId);
        if (opt.isEmpty()) {
            player.sendMessage(Component.text("✖ Could not locate your island.", NamedTextColor.RED));
            return;
        }
        Island island = opt.get();
        Location dest = islandService.getIslandSpawnLocation(island);
        warmupService.teleportWithWarmup(player, dest, "Island Home", () -> {
            player.sendMessage(Component.text("🌸 Returned to your Island Sanctuary.", NamedTextColor.LIGHT_PURPLE));
        });
    }

    @Override
    public boolean isGardenWorld(World world) {
        return world != null && isGardenWorld(world.getName());
    }

    @Override
    public boolean isGardenWorld(String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(worldManager.worldName());
    }

    @Override
    @NotNull
    public GardenSpatialAllocator allocator() {
        return allocator;
    }

    @Override
    @NotNull
    public GardenWorldManager worldManager() {
        return worldManager;
    }
}
