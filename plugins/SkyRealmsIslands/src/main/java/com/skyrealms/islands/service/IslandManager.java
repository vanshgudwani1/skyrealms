package com.skyrealms.islands.service;

import com.skyrealms.economy.model.TransactionResult;
import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.islands.event.*;
import com.skyrealms.islands.fx.IslandEffectsService;
import com.skyrealms.islands.lobby.TeleportWarmupService;
import com.skyrealms.islands.model.*;
import com.skyrealms.islands.repository.IslandRepository;
import com.skyrealms.islands.world.SpatialGridAllocator;
import com.skyrealms.islands.world.StarterIslandGenerator;
import com.skyrealms.platform.api.event.EventBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Authoritative manager handling island lifecycle, spatial caching, permissions, and upgrades.
 */
public final class IslandManager implements IslandService, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(IslandManager.class);

    private final JavaPlugin plugin;
    private final IslandRepository repository;
    private final EventBus eventBus;
    private final EconomyService economyService;
    private final SpatialGridAllocator gridAllocator;
    private final String worldName;
    private final int defaultSize;
    private final int centerY;

    private final IslandEffectsService effectsService;
    private final ExecutorService executor;
    private TeleportWarmupService teleportWarmupService;

    // Cache: Spatial grid key -> Island
    private final Map<Long, Island> spatialIndex = new ConcurrentHashMap<>();

    // Cache: Island ID -> Island
    private final Map<UUID, Island> islandCache = new ConcurrentHashMap<>();

    // Cache: Player UUID -> Island ID
    private final Map<UUID, UUID> playerToIsland = new ConcurrentHashMap<>();

    // Pending invites: Player UUID -> Set of Island UUIDs
    private final Map<UUID, Set<UUID>> pendingInvites = new ConcurrentHashMap<>();

    public IslandManager(
            @NotNull JavaPlugin plugin,
            @NotNull IslandRepository repository,
            @NotNull EventBus eventBus,
            @NotNull EconomyService economyService,
            @NotNull String worldName,
            int spacing,
            int defaultSize,
            int centerY
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.gridAllocator = new SpatialGridAllocator(spacing);
        this.worldName = worldName;
        this.defaultSize = defaultSize;
        this.centerY = centerY;
        this.effectsService = new IslandEffectsService(plugin);

        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "SkyRealms-Islands-Worker");
            t.setDaemon(true);
            return t;
        });

        loadAllIslands();
    }

    /**
     * Initializes the in-memory spatial grid and player lookup map from database.
     */
    public void loadAllIslands() {
        try {
            List<Island> islands = repository.loadAllActiveIslands();
            spatialIndex.clear();
            islandCache.clear();
            playerToIsland.clear();

            for (Island island : islands) {
                indexIsland(island);
            }
            LOGGER.info("Loaded {} active islands into spatial grid index.", islands.size());
        } catch (SQLException e) {
            LOGGER.error("Failed to load islands into memory: {}", e.getMessage(), e);
        }
    }

    private void indexIsland(@NotNull Island island) {
        islandCache.put(island.id(), island);
        long key = SpatialGridAllocator.gridKey(island.location().centerX(), island.location().centerZ());
        spatialIndex.put(key, island);

        playerToIsland.put(island.ownerUuid(), island.id());
        for (IslandMember member : island.members().values()) {
            playerToIsland.put(member.playerUuid(), island.id());
        }
    }

    private void deindexIsland(@NotNull Island island) {
        islandCache.remove(island.id());
        long key = SpatialGridAllocator.gridKey(island.location().centerX(), island.location().centerZ());
        spatialIndex.remove(key);

        playerToIsland.remove(island.ownerUuid());
        for (IslandMember member : island.members().values()) {
            playerToIsland.remove(member.playerUuid());
        }
    }

    @Override
    public boolean isProtectedWorld(@NotNull String targetWorldName) {
        return this.worldName.equalsIgnoreCase(targetWorldName);
    }

    @Override
    @NotNull
    public Optional<Island> getIsland(@NotNull UUID islandId) {
        return Optional.ofNullable(islandCache.get(islandId));
    }

    @Override
    @NotNull
    public Optional<Island> getIslandByPlayer(@NotNull UUID playerId) {
        UUID islandId = playerToIsland.get(playerId);
        if (islandId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(islandCache.get(islandId));
    }

    @Override
    @NotNull
    public Optional<Island> getIslandAt(@NotNull String targetWorld, int x, int z) {
        if (!isProtectedWorld(targetWorld)) {
            return Optional.empty();
        }

        SpatialGridAllocator.GridCoord anchor = gridAllocator.getNearestGridAnchor(x, z);
        long key = SpatialGridAllocator.gridKey(anchor.x(), anchor.z());
        Island island = spatialIndex.get(key);

        if (island != null && island.location().contains(targetWorld, x, z, island.size())) {
            return Optional.of(island);
        }
        return Optional.empty();
    }

    @Override
    @NotNull
    public CompletableFuture<Island> createIsland(@NotNull UUID ownerId, @Nullable String islandName) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerToIsland.containsKey(ownerId)) {
                throw new IllegalStateException("You are already part of an island.");
            }

            try {
                // Find next collision-free grid coordinate
                int slot = repository.getNextGridIndex();
                SpatialGridAllocator.GridCoord center = gridAllocator.getIslandCenter(slot);
                long key = SpatialGridAllocator.gridKey(center.x(), center.z());

                while (spatialIndex.containsKey(key)) {
                    slot++;
                    center = gridAllocator.getIslandCenter(slot);
                    key = SpatialGridAllocator.gridKey(center.x(), center.z());
                }

                UUID islandId = UUID.randomUUID();
                IslandLocation location = new IslandLocation(worldName, center.x(), centerY, center.z());
                Instant now = Instant.now();
                String displayName = (islandName != null && !islandName.isBlank()) ? islandName.trim() : "Island #" + slot;

                Island island = new Island(
                        islandId,
                        ownerId,
                        location,
                        defaultSize,
                        0L,
                        displayName,
                        IslandStatus.ACTIVE,
                        now,
                        now,
                        IslandSettings.defaultSettings(),
                        List.of(new IslandMember(islandId, ownerId, IslandRole.LEADER, now)),
                        List.of()
                );

                repository.createIsland(island);
                indexIsland(island);

                // Build island platform synchronously on server main thread
                final int cx = center.x();
                final int cz = center.z();
                CompletableFuture<Void> worldFuture = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        Location spawn = StarterIslandGenerator.generateStarterIsland(world, cx, centerY, cz);
                        Player player = Bukkit.getPlayer(ownerId);
                        if (player != null && player.isOnline()) {
                            effectsService.playGenesisBurst(player, spawn);
                        }
                    }
                    worldFuture.complete(null);
                });
                worldFuture.join();

                eventBus.publishAsync(new IslandCreatedEvent(island));
                return island;
            } catch (SQLException e) {
                LOGGER.error("Failed to persist new island for {}: {}", ownerId, e.getMessage(), e);
                throw new RuntimeException("Database error creating island: " + e.getMessage(), e);
            }
        }, executor);
    }

    @Override
    @NotNull
    public Location getServerSpawnLocation() {
        World overworld = Bukkit.getWorld("world");
        if (overworld == null && !Bukkit.getWorlds().isEmpty()) {
            overworld = Bukkit.getWorlds().get(0);
        }
        if (overworld != null) {
            return overworld.getSpawnLocation().clone().add(0.5, 0.5, 0.5);
        }
        return new Location(Bukkit.getWorlds().get(0), 0.5, 100, 0.5);
    }

    private void clearIslandBlocks(@NotNull Island island) {
        World world = Bukkit.getWorld(island.location().worldName());
        if (world == null) return;

        int half = island.size() / 2;
        int minX = island.location().centerX() - half;
        int maxX = island.location().centerX() + half;
        int minZ = island.location().centerZ() - half;
        int maxZ = island.location().centerZ() + half;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int highestY = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                if (highestY >= world.getMinHeight()) {
                    for (int y = world.getMinHeight(); y <= highestY; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) {
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> deleteIsland(@NotNull UUID islandId) {
        return CompletableFuture.supplyAsync(() -> {
            Island island = islandCache.get(islandId);
            if (island == null) return false;

            // 1. Evacuate players on this island to server spawn and clear blocks
            CompletableFuture<Void> evictFuture = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location serverSpawn = getServerSpawnLocation();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    boolean isOnIsland = island.location().contains(p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockZ(), island.size());
                    if (isOnIsland || island.isMember(p.getUniqueId())) {
                        p.teleportAsync(serverSpawn);
                        p.sendMessage(Component.text("🌸 You were teleported to spawn because the island was deleted.", NamedTextColor.YELLOW));
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                }

                // Clear all blocks in the void world
                clearIslandBlocks(island);
                evictFuture.complete(null);
            });
            evictFuture.join();

            // 2. Persist deletion in database
            try {
                island.setStatus(IslandStatus.DELETED);
                repository.updateIsland(island);
                deindexIsland(island);

                eventBus.publishAsync(new IslandDeletedEvent(islandId, island.ownerUuid()));
                return true;
            } catch (SQLException e) {
                LOGGER.error("Failed to delete island {}: {}", islandId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> resetIsland(@NotNull UUID islandId) {
        return CompletableFuture.supplyAsync(() -> {
            Island island = islandCache.get(islandId);
            if (island == null) return false;

            CompletableFuture<Void> resetFuture = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                World world = Bukkit.getWorld(island.location().worldName());
                if (world != null) {
                    // 1. Clear old blocks
                    clearIslandBlocks(island);

                    // 2. Generate fresh Cherry Blossom island
                    Location newSpawn = StarterIslandGenerator.generateStarterIsland(
                            world,
                            island.location().centerX(),
                            island.location().centerY(),
                            island.location().centerZ()
                    );

                    // 3. Teleport owner and members to the fresh spawn
                    Player owner = Bukkit.getPlayer(island.ownerUuid());
                    if (owner != null && owner.isOnline()) {
                        owner.teleportAsync(newSpawn).thenRun(() -> {
                            effectsService.playGenesisBurst(owner, newSpawn);
                        });
                        owner.sendMessage(Component.text("🌸 Your island has been reset to the Cherry Blossom starter platform!", TextColor.fromHexString("#FF77A8"), TextDecoration.BOLD));
                    }
                }
                resetFuture.complete(null);
            });
            resetFuture.join();
            return true;
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> invitePlayer(@NotNull UUID islandId, @NotNull UUID actorId, @NotNull UUID targetId) {
        return CompletableFuture.supplyAsync(() -> {
            Island island = islandCache.get(islandId);
            if (island == null || !island.hasPermission(actorId, IslandPermission.INVITE_MEMBERS)) {
                return false;
            }
            if (island.isMember(targetId)) {
                return false;
            }

            pendingInvites.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(islandId);
            return true;
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> acceptInvite(@NotNull UUID playerId, @NotNull UUID islandId) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> invites = pendingInvites.get(playerId);
            if (invites == null || !invites.contains(islandId)) {
                return false;
            }

            if (playerToIsland.containsKey(playerId)) {
                return false;
            }

            Island island = islandCache.get(islandId);
            if (island == null || island.status() != IslandStatus.ACTIVE) {
                return false;
            }

            try {
                IslandMember member = new IslandMember(islandId, playerId, IslandRole.MEMBER, Instant.now());
                repository.addMember(member);
                island.addMember(member);
                playerToIsland.put(playerId, islandId);
                invites.remove(islandId);

                eventBus.publishAsync(new IslandMemberJoinedEvent(islandId, member));
                return true;
            } catch (SQLException e) {
                LOGGER.error("Failed to accept invite for {} to island {}: {}", playerId, islandId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> kickPlayer(@NotNull UUID islandId, @NotNull UUID actorId, @NotNull UUID targetId) {
        return CompletableFuture.supplyAsync(() -> {
            Island island = islandCache.get(islandId);
            if (island == null || !island.hasPermission(actorId, IslandPermission.KICK_MEMBERS)) {
                return false;
            }

            IslandRole actorRole = island.getRole(actorId);
            IslandRole targetRole = island.getRole(targetId);

            if (actorRole.level() <= targetRole.level() || targetRole == IslandRole.LEADER) {
                return false;
            }

            try {
                repository.removeMember(islandId, targetId);
                island.removeMember(targetId);
                playerToIsland.remove(targetId);

                eventBus.publishAsync(new IslandMemberLeftEvent(islandId, targetId));
                return true;
            } catch (SQLException e) {
                LOGGER.error("Failed to kick member {} from island {}: {}", targetId, islandId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> leaveIsland(@NotNull UUID playerId, @NotNull UUID islandId) {
        return CompletableFuture.supplyAsync(() -> {
            Island island = islandCache.get(islandId);
            if (island == null) return false;

            if (island.ownerUuid().equals(playerId)) {
                return false; // Leader cannot leave; must delete or transfer
            }

            try {
                repository.removeMember(islandId, playerId);
                island.removeMember(playerId);
                playerToIsland.remove(playerId);

                eventBus.publishAsync(new IslandMemberLeftEvent(islandId, playerId));
                return true;
            } catch (SQLException e) {
                LOGGER.error("Failed to leave island {}: {}", islandId, e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> upgradeIsland(@NotNull UUID islandId, @NotNull String upgradeKey) {
        return CompletableFuture.supplyAsync(() -> {
            Island island = islandCache.get(islandId);
            if (island == null) return false;

            int currentLevel = island.upgrades().containsKey(upgradeKey) ? island.upgrades().get(upgradeKey).upgradeLevel() : 1;
            int nextLevel = currentLevel + 1;

            double costDouble = plugin.getConfig().getDouble("upgrades." + upgradeKey + ".tier-" + nextLevel + ".cost", 0.0);
            int newSize = plugin.getConfig().getInt("upgrades." + upgradeKey + ".tier-" + nextLevel + ".size", 0);

            if (costDouble <= 0 && newSize <= 0) {
                return false; // No more tiers
            }

            BigDecimal cost = BigDecimal.valueOf(costDouble);
            String idempotencyKey = "island:upgrade:" + islandId + ":" + upgradeKey + ":" + nextLevel;

            // Atomic currency withdrawal from owner
            TransactionResult tx = economyService.withdraw(island.ownerUuid(), "coins", cost, "Island Upgrade: " + upgradeKey + " Tier " + nextLevel, idempotencyKey).join();
            if (!tx.isSuccess()) {
                return false;
            }

            try {
                IslandUpgrade upgrade = new IslandUpgrade(islandId, upgradeKey, nextLevel, Instant.now());
                repository.saveUpgrade(upgrade);
                island.addUpgrade(upgrade);

                if (newSize > 0) {
                    island.setSize(newSize);
                    repository.updateIsland(island);
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player ownerPlayer = Bukkit.getPlayer(island.ownerUuid());
                    if (ownerPlayer != null && ownerPlayer.isOnline()) {
                        effectsService.playUpgradeCelebration(ownerPlayer);
                    }
                });

                eventBus.publishAsync(new IslandUpgradedEvent(islandId, upgrade));
                return true;
            } catch (SQLException e) {
                LOGGER.error("Database failure applying upgrade {} for island {}. Refunding...", upgradeKey, islandId, e);
                // Rollback refund
                economyService.deposit(island.ownerUuid(), "coins", cost, "Rollback: Upgrade Failure", "rollback:" + idempotencyKey).join();
                return false;
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> updateSettings(@NotNull UUID islandId, @NotNull IslandSettings settings) {
        return CompletableFuture.runAsync(() -> {
            Island island = islandCache.get(islandId);
            if (island == null) return;

            try {
                repository.updateSettings(islandId, settings);
                island.setSettings(settings);
            } catch (SQLException e) {
                LOGGER.error("Failed to update settings for island {}: {}", islandId, e.getMessage(), e);
            }
        }, executor);
    }

    @Override
    @NotNull
    public java.util.Collection<Island> getAllActiveIslands() {
        return Collections.unmodifiableCollection(islandCache.values());
    }

    @NotNull
    public IslandEffectsService effectsService() {
        return effectsService;
    }

    @Override
    @NotNull
    public Location getIslandSpawnLocation(@NotNull Island island) {
        World world = Bukkit.getWorld(island.location().worldName());
        return new Location(
                world,
                island.location().centerX() + 0.5,
                island.location().centerY() + 1.0,
                island.location().centerZ() + 0.5,
                0.0f,
                0.0f
        );
    }

    public void setTeleportWarmupService(@Nullable TeleportWarmupService teleportWarmupService) {
        this.teleportWarmupService = teleportWarmupService;
    }

    @Nullable
    public TeleportWarmupService teleportWarmupService() {
        return teleportWarmupService;
    }

    @Override
    public void teleportHome(@NotNull Player player) {
        Optional<Island> opt = getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage(Component.text("You do not belong to an island! Create one with /island create.", NamedTextColor.RED));
            return;
        }

        Location spawn = getIslandSpawnLocation(opt.get());
        if (teleportWarmupService != null) {
            teleportWarmupService.teleportWithWarmup(player, spawn, "Island Home", () -> {
                effectsService.playTeleportHelix(player);
                player.sendMessage(Component.text("🌸 Welcome home to your Cherry Sanctuary.", NamedTextColor.LIGHT_PURPLE));
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleportAsync(spawn).thenRun(() -> {
                    effectsService.playTeleportHelix(player);
                });
                player.sendMessage(Component.text("🌸 Welcome home to your Cherry Sanctuary.", NamedTextColor.LIGHT_PURPLE));
            });
        }
    }

    @Override
    public void visitIsland(@NotNull Player visitor, @NotNull UUID targetIslandId) {
        Island target = islandCache.get(targetIslandId);
        if (target == null || target.status() != IslandStatus.ACTIVE) {
            visitor.sendMessage(Component.text("That island does not exist or is inactive.", NamedTextColor.RED));
            return;
        }

        if (!target.settings().publicVisits() && !target.isMember(visitor.getUniqueId()) && !visitor.isOp()) {
            visitor.sendMessage(Component.text("This island has public visits disabled.", NamedTextColor.RED));
            return;
        }

        Location spawn = getIslandSpawnLocation(target);
        String targetName = target.name() != null ? target.name() : "Island";
        if (teleportWarmupService != null) {
            teleportWarmupService.teleportWithWarmup(visitor, spawn, targetName, () -> {
                effectsService.playTeleportHelix(visitor);
                visitor.sendMessage(Component.text("🌸 Visiting " + targetName + "...", NamedTextColor.LIGHT_PURPLE));

                IslandVisit visit = new IslandVisit(UUID.randomUUID(), targetIslandId, visitor.getUniqueId(), Instant.now(), 0L);
                executor.execute(() -> {
                    try {
                        repository.recordVisit(visit);
                        eventBus.publishAsync(new IslandVisitedEvent(targetIslandId, visitor.getUniqueId()));
                    } catch (SQLException e) {
                        LOGGER.warn("Failed to record visit: {}", e.getMessage());
                    }
                });
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                visitor.teleportAsync(spawn).thenRun(() -> {
                    effectsService.playTeleportHelix(visitor);
                });
                visitor.sendMessage(Component.text("🌸 Visiting " + targetName + "...", NamedTextColor.LIGHT_PURPLE));

                IslandVisit visit = new IslandVisit(UUID.randomUUID(), targetIslandId, visitor.getUniqueId(), Instant.now(), 0L);
                executor.execute(() -> {
                    try {
                        repository.recordVisit(visit);
                        eventBus.publishAsync(new IslandVisitedEvent(targetIslandId, visitor.getUniqueId()));
                    } catch (SQLException e) {
                        LOGGER.warn("Failed to record visit: {}", e.getMessage());
                    }
                });
            });
        }
    }

    @Override
    public void close() {
        effectsService.cleanup();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        spatialIndex.clear();
        islandCache.clear();
        playerToIsland.clear();
        pendingInvites.clear();
    }
}
