package com.skyrealms.islands.lobby;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
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
import org.bukkit.block.data.type.PinkPetals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing the Celestial Cherry Dragon Nest Lobby, Castle aspects, and Holograms.
 */
public final class LobbyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyService.class);
    private final Plugin plugin;
    private final File markerFile;
    private final HologramService hologramService;
    private final TeleportWarmupService teleportWarmupService;

    private BukkitTask coreParticleTask;

    public LobbyService(Plugin plugin) {
        this.plugin = plugin;
        this.markerFile = new File(plugin.getDataFolder(), "lobby_initialized.flag");
        this.hologramService = new HologramService(plugin);
        this.teleportWarmupService = new TeleportWarmupService(plugin);
    }

    public HologramService getHologramService() {
        return hologramService;
    }

    public TeleportWarmupService getTeleportWarmupService() {
        return teleportWarmupService;
    }

    public boolean isLobbyInitialized() {
        return markerFile.exists();
    }

    public Location getLobbySpawnLocation() {
        World world = Bukkit.getWorld("world");
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world != null) {
            Location spawn = world.getSpawnLocation();
            return new Location(world, spawn.getBlockX() + 0.5, spawn.getY(), spawn.getBlockZ() + 0.5, spawn.getYaw(), spawn.getPitch());
        }
        return new Location(Bukkit.getWorlds().get(0), 0.5, 89.0, 0.5, 0.0f, 0.0f);
    }

    /**
     * Initializes lobby ambient effects if already provisioned.
     */
    public CompletableFuture<Boolean> initLobby(boolean force) {
        if (!force && isLobbyInitialized()) {
            LOGGER.info("Lobby initialized: spawning animated Sakura Hub pedestals, core relic & particles...");
            World world = Bukkit.getWorld("world");
            if (world != null) {
                hologramService.spawnAllHolograms(world);
                startCoreParticleEngine(world);
            }
            return CompletableFuture.completedFuture(true);
        }

        World world = Bukkit.getWorld("world");
        if (world == null) {
            LOGGER.warn("Lobby world 'world' not found! Skipping lobby initialization.");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LOGGER.info("Initiating Celestial Dragon Nest Lobby generation & palette transform...");

                File schemFile = new File("plugins/FastAsyncWorldEdit/schematics/dragonnest.schematic");
                if (!schemFile.exists()) {
                    schemFile = new File("plugins/WorldEdit/schematics/dragonnest.schematic");
                }
                if (!schemFile.exists()) {
                    LOGGER.error("Schematic file not found at: {}", schemFile.getAbsolutePath());
                    future.complete(false);
                    return;
                }

                ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
                if (format == null) {
                    LOGGER.error("Could not find WorldEdit/FAWE format for {}", schemFile.getName());
                    future.complete(false);
                    return;
                }

                LOGGER.info("Reading schematic with FAWE: {}...", schemFile.getName());
                try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
                    Clipboard clipboard = reader.read();
                    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

                    try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                        LOGGER.info("Pasting Dragon Nest schematic at (0, 70, 0)...");
                        Operation operation = new ClipboardHolder(clipboard)
                                .createPaste(editSession)
                                .to(BlockVector3.at(0, 70, 0))
                                .ignoreAirBlocks(true)
                                .copyEntities(false)
                                .build();
                        Operations.complete(operation);

                        LOGGER.info("Pasted schematic! Now transforming foliage to Cherry Blossom theme...");
                        CuboidRegion region = new CuboidRegion(weWorld, BlockVector3.at(-350, 0, -350), BlockVector3.at(350, 260, 350));

                        // 1. Replace all leaves -> CHERRY_LEAVES
                        editSession.replaceBlocks(region, new BlockTypeMask(editSession,
                                BlockTypes.OAK_LEAVES, BlockTypes.SPRUCE_LEAVES, BlockTypes.BIRCH_LEAVES,
                                BlockTypes.DARK_OAK_LEAVES, BlockTypes.JUNGLE_LEAVES, BlockTypes.ACACIA_LEAVES),
                                BlockTypes.CHERRY_LEAVES.getDefaultState());

                        // 2. Replace dark oak & spruce planks -> CHERRY_PLANKS
                        editSession.replaceBlocks(region, new BlockTypeMask(editSession,
                                BlockTypes.DARK_OAK_PLANKS, BlockTypes.SPRUCE_PLANKS, BlockTypes.OAK_PLANKS),
                                BlockTypes.CHERRY_PLANKS.getDefaultState());

                        // 3. Replace dark oak & spruce logs/wood -> CHERRY_LOG
                        editSession.replaceBlocks(region, new BlockTypeMask(editSession,
                                BlockTypes.DARK_OAK_LOG, BlockTypes.SPRUCE_LOG, BlockTypes.OAK_LOG,
                                BlockTypes.DARK_OAK_WOOD, BlockTypes.SPRUCE_WOOD),
                                BlockTypes.CHERRY_LOG.getDefaultState());

                        LOGGER.info("Foliage and wood palette successfully converted to Cherry Blossom!");
                    }
                }

                // Schedule main thread interior decorations, castle aspects, particle engine & hologram setup
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        buildServerInterior(world);
                        buildCastleAspects(world);
                        hologramService.spawnAllHolograms(world);
                        startCoreParticleEngine(world);

                        world.setSpawnLocation(new Location(world, 0.5, 71.0, 0.5, 0.0f, 0.0f));

                        // Clear any loose dropped items
                        world.getEntitiesByClass(org.bukkit.entity.Item.class).forEach(Entity::remove);

                        // Write marker flag
                        plugin.getDataFolder().mkdirs();
                        Files.writeString(markerFile.toPath(), "initialized");

                        LOGGER.info("Celestial Dragon Nest Lobby & Castle Aspects fully built, decorated, and active!");
                        future.complete(true);
                    } catch (Exception ex) {
                        LOGGER.error("Error building server lobby interior on main thread: ", ex);
                        future.complete(false);
                    }
                });

            } catch (Exception e) {
                LOGGER.error("Failed to paste and initialize Celestial Lobby: ", e);
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Builds the stunning Multi-Tiered Celestial Core with sunken glowing glass floor and floating star crystal.
     */
    public void buildServerInterior(World world) {
        LOGGER.info("Constructing handcrafted Multi-Tiered Celestial Core & server interior...");

        int centerY = 70;

        // 1. Outer Ring (Radius 7.5 to 9.2): Polished Deepslate with embedded Amethyst chime blocks
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > 7.2 && dist <= 9.2) {
                    Block block = world.getBlockAt(x, centerY, z);
                    block.setType((x * 3 + z * 5) % 3 == 0 ? Material.AMETHYST_BLOCK : Material.POLISHED_DEEPSLATE);
                    for (int y = centerY + 1; y <= centerY + 6; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }

        // 2. Sunken Glowing Glass Trench (Radius 4.8 to 7.2, Y=69): Sea Lantern base with Pink/Magenta Stained Glass
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > 4.6 && dist <= 7.2) {
                    world.getBlockAt(x, centerY - 2, z).setType(Material.SEA_LANTERN);
                    world.getBlockAt(x, centerY - 1, z).setType((x + z) % 2 == 0 ? Material.PINK_STAINED_GLASS : Material.MAGENTA_STAINED_GLASS);
                    for (int y = centerY; y <= centerY + 6; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }

        // 3. Inner Island Dais (Radius 0 to 4.6, Y=70)
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= 4.6) {
                    Block block = world.getBlockAt(x, centerY, z);
                    if (dist < 1.8) {
                        block.setType(Material.GILDED_BLACKSTONE);
                    } else if (dist >= 3.2 && dist <= 4.2) {
                        block.setType(Material.DEEPSLATE_BRICKS);
                    } else {
                        block.setType(Material.CHERRY_PLANKS);
                    }

                    for (int y = centerY + 1; y <= centerY + 6; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }

                    if (dist > 1.8 && dist <= 4.4 && (x * 17 + z * 31) % 3 == 0) {
                        Block above = world.getBlockAt(x, centerY + 1, z);
                        above.setType(Material.PINK_PETALS);
                        if (above.getBlockData() instanceof PinkPetals petals) {
                            petals.setFlowerAmount(4);
                            above.setBlockData(petals);
                        }
                    }
                }
            }
        }

        // 4. Central Celestial Core Levitating Crystal (Anchored at Y=70, Floating at Y=73)
        world.getBlockAt(0, centerY, 0).setType(Material.LODESTONE);
        world.getBlockAt(0, centerY + 1, 0).setType(Material.CRYING_OBSIDIAN);

        int crystalY = centerY + 3; // Y = 73
        world.getBlockAt(0, crystalY, 0).setType(Material.BEACON);
        world.getBlockAt(0, crystalY + 1, 0).setType(Material.PINK_STAINED_GLASS);
        world.getBlockAt(0, crystalY - 1, 0).setType(Material.MAGENTA_STAINED_GLASS);
        world.getBlockAt(1, crystalY, 0).setType(Material.PINK_STAINED_GLASS);
        world.getBlockAt(-1, crystalY, 0).setType(Material.PINK_STAINED_GLASS);
        world.getBlockAt(0, crystalY, 1).setType(Material.MAGENTA_STAINED_GLASS);
        world.getBlockAt(0, crystalY, -1).setType(Material.MAGENTA_STAINED_GLASS);

        // Radiant End Rod spikes radiating like an ethereal star
        world.getBlockAt(2, crystalY, 0).setType(Material.END_ROD);
        world.getBlockAt(-2, crystalY, 0).setType(Material.END_ROD);
        world.getBlockAt(0, crystalY, 2).setType(Material.END_ROD);
        world.getBlockAt(0, crystalY, -2).setType(Material.END_ROD);
        world.getBlockAt(0, crystalY + 2, 0).setType(Material.END_ROD);

        // 5. Grand Torii Shinto Pergola Pillars around the dais
        int[][] pillarCoords = {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}};
        for (int[] p : pillarCoords) {
            int px = p[0];
            int pz = p[1];
            for (int y = 1; y <= 6; y++) {
                world.getBlockAt(px, centerY + y, pz).setType(Material.STRIPPED_CHERRY_LOG);
            }
            world.getBlockAt(px, centerY + 5, pz + (pz > 0 ? -1 : 1)).setType(Material.CHAIN);
            world.getBlockAt(px, centerY + 4, pz + (pz > 0 ? -1 : 1)).setType(Material.SOUL_LANTERN);
            world.getBlockAt(px, centerY + 7, pz).setType(Material.CHERRY_LEAVES);
        }

        for (int x = -6; x <= 6; x++) {
            world.getBlockAt(x, centerY + 7, -5).setType(Material.CHERRY_SLAB);
            world.getBlockAt(x, centerY + 7, 5).setType(Material.CHERRY_SLAB);
        }
        for (int z = -6; z <= 6; z++) {
            world.getBlockAt(-5, centerY + 7, z).setType(Material.CHERRY_SLAB);
            world.getBlockAt(5, centerY + 7, z).setType(Material.CHERRY_SLAB);
        }

        // Feature Stations
        buildPortalStation(world, 13, centerY, 0);
        buildInfoStation(world, 0, centerY, -13);
        buildLeaderboardStation(world, -13, centerY, 0);
        buildRewardsStation(world, 0, centerY, 13);
    }

    /**
     * Stops lobby animation tasks and clears non-persistent display entities cleanly.
     */
    public void shutdown() {
        if (coreParticleTask != null && !coreParticleTask.isCancelled()) {
            coreParticleTask.cancel();
            coreParticleTask = null;
        }
        hologramService.clearHolograms(Bukkit.getWorld("world"));
    }

    /**
     * Ambient animation loop driving smooth 3D relic interpolation, banner shimmer, and sakura breeze particles.
     */
    public void startCoreParticleEngine(World world) {
        if (coreParticleTask != null && !coreParticleTask.isCancelled()) {
            coreParticleTask.cancel();
        }

        Location spawn = getLobbySpawnLocation();
        final Location coreLoc = new Location(world, spawn.getX(), spawn.getY() + 3.2, spawn.getZ());

        coreParticleTask = new BukkitRunnable() {
            double angle = 0.0;
            int stepCounter = 0;

            @Override
            public void run() {
                if (world.getPlayers().isEmpty()) {
                    return;
                }

                // Client-interpolated 3D rotation & vertical bobbing across core + pedestals
                hologramService.animateStep(angle, stepCounter);

                // Revolving cherry petals & fireflies around the central Sakura Core
                double x1 = Math.cos(angle) * 2.1;
                double z1 = Math.sin(angle) * 2.1;
                world.spawnParticle(Particle.CHERRY_LEAVES, coreLoc.getX() + x1, coreLoc.getY(), coreLoc.getZ() + z1, 2, 0.15, 0.1, 0.15, 0.01);

                double x2 = Math.cos(angle + Math.PI) * 2.1;
                double z2 = Math.sin(angle + Math.PI) * 2.1;
                double y2 = Math.sin(angle * 2.0) * 0.5;
                world.spawnParticle(Particle.END_ROD, coreLoc.getX() + x2, coreLoc.getY() + y2, coreLoc.getZ() + z2, 1, 0, 0, 0, 0.01);

                // Gentle falling blossom canopy across spawn courtyard & pedestals
                world.spawnParticle(Particle.CHERRY_LEAVES, spawn.getX(), spawn.getY() + 3.5, spawn.getZ(), 5, 7.5, 1.5, 7.5, 0.02);
                if (stepCounter % 2 == 0) {
                    for (LobbyStation station : LobbyStation.DEFAULT_STATIONS) {
                        world.spawnParticle(Particle.END_ROD, station.x(), station.y() + 1.4, station.z(), 1, 0.35, 0.25, 0.35, 0.005);
                    }
                }

                if (stepCounter % 16 == 0) {
                    world.playSound(coreLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.35f);
                }

                angle += Math.PI / 6.0;
                if (angle >= Math.PI * 200) {
                    angle = 0.0;
                }
                stepCounter++;
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    /**
     * Builds the integrated Castle aspects (Grand South Bridge, Royal Bank, Arcane Spire, Citadel Throne, Armory).
     */
    public void buildCastleAspects(World world) {
        LOGGER.info("Constructing Castle Aspects (Bridge, Bank, Arcane Spire, Citadel Keep, Armory)...");

        // 1. Grand South Bridge connecting Spawn Dais (0, 70, 8) to Castle Gatehouse (0, 78, 30)
        for (int z = 8; z <= 30; z++) {
            double progress = (z - 8.0) / 22.0;
            int bridgeY = 70 + (int) Math.round(progress * 8.0);

            for (int x = -2; x <= 2; x++) {
                Block floor = world.getBlockAt(x, bridgeY, z);
                floor.setType(Math.abs(x) == 2 ? Material.POLISHED_DEEPSLATE : Material.CHERRY_PLANKS);

                for (int y = bridgeY + 1; y <= bridgeY + 5; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }

                if (Math.abs(x) == 2) {
                    Block rail = world.getBlockAt(x, bridgeY + 1, z);
                    rail.setType(Material.CHERRY_FENCE);
                    if (z % 5 == 0) {
                        world.getBlockAt(x, bridgeY + 2, z).setType(Material.LANTERN);
                    }
                }

                if (Math.abs(x) < 2 && (x * 13 + z * 7) % 4 == 0) {
                    Block petalBlock = world.getBlockAt(x, bridgeY + 1, z);
                    petalBlock.setType(Material.PINK_PETALS);
                    if (petalBlock.getBlockData() instanceof PinkPetals petals) {
                        petals.setFlowerAmount(2);
                        petalBlock.setBlockData(petals);
                    }
                }
            }
        }

        // Castle Gatehouse Courtyard (0, 78, 30)
        for (int x = -4; x <= 4; x++) {
            for (int z = 29; z <= 33; z++) {
                world.getBlockAt(x, 78, z).setType(Material.POLISHED_DEEPSLATE);
            }
        }
        world.getBlockAt(-4, 79, 31).setType(Material.CHERRY_LOG);
        world.getBlockAt(4, 79, 31).setType(Material.CHERRY_LOG);

        // Castle West Wing: Royal Treasury & Bank (-20, 78, 35)
        buildBankAspect(world, -20, 78, 35);

        // Castle East Wing: Arcane Upgrades Spire (+20, 78, 35)
        buildArcaneSpireAspect(world, 20, 78, 35);

        // Castle High Keep: Grand Citadel Throne & Hall of Fame (0, 82, 55)
        buildCitadelThroneAspect(world, 0, 82, 55);

        // Castle Armory & Forge (-45, 68, -25)
        buildArmoryAspect(world, -45, 68, -25);
    }

    private void buildBankAspect(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.POLISHED_DEEPSLATE);
            }
        }
        world.getBlockAt(x, y + 1, z - 1).setType(Material.VAULT);
        world.getBlockAt(x - 1, y + 1, z - 1).setType(Material.GOLD_BLOCK);
        world.getBlockAt(x + 1, y + 1, z - 1).setType(Material.GOLD_BLOCK);

        Location bankerLoc = new Location(world, x + 0.5, y + 1.0, z - 1.5, 0.0f, 0.0f);
        if (world.getNearbyEntities(bankerLoc, 1.5, 1.5, 1.5).isEmpty()) {
            ArmorStand banker = (ArmorStand) world.spawnEntity(bankerLoc, EntityType.ARMOR_STAND);
            banker.setCustomNameVisible(true);
            banker.customName(Component.text("★ ROYAL BANKER ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            banker.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            banker.getEquipment().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
            banker.setGravity(false);
            banker.setInvulnerable(true);
        }
    }

    private void buildArcaneSpireAspect(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.POLISHED_DEEPSLATE);
            }
        }
        world.getBlockAt(x, y + 1, z).setType(Material.AMETHYST_BLOCK);
        world.getBlockAt(x, y + 2, z).setType(Material.LODESTONE);
        world.getBlockAt(x - 1, y + 1, z).setType(Material.BOOKSHELF);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.BOOKSHELF);

        Location masterLoc = new Location(world, x + 0.5, y + 1.0, z - 1.5, 0.0f, 0.0f);
        if (world.getNearbyEntities(masterLoc, 1.5, 1.5, 1.5).isEmpty()) {
            ArmorStand master = (ArmorStand) world.spawnEntity(masterLoc, EntityType.ARMOR_STAND);
            master.setCustomNameVisible(true);
            master.customName(Component.text("✦ ARCANE MASTER ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            master.getEquipment().setHelmet(new ItemStack(Material.AMETHYST_SHARD));
            master.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            master.setGravity(false);
            master.setInvulnerable(true);
        }
    }

    private void buildCitadelThroneAspect(World world, int x, int y, int z) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.POLISHED_DEEPSLATE);
            }
        }
        world.getBlockAt(x, y + 1, z + 2).setType(Material.PURPUR_BLOCK);
        world.getBlockAt(x, y + 2, z + 2).setType(Material.PURPUR_PILLAR);
        world.getBlockAt(x, y + 3, z + 2).setType(Material.CHERRY_SLAB);
        world.getBlockAt(x - 1, y + 1, z + 2).setType(Material.GOLD_BLOCK);
        world.getBlockAt(x + 1, y + 1, z + 2).setType(Material.GOLD_BLOCK);

        for (int dz = -2; dz <= 1; dz++) {
            world.getBlockAt(x, y + 1, z + dz).setType(Material.PINK_CARPET);
        }
    }

    private void buildArmoryAspect(World world, int x, int y, int z) {
        world.getBlockAt(x, y + 1, z).setType(Material.BLAST_FURNACE);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.SMITHING_TABLE);
        world.getBlockAt(x - 1, y + 1, z).setType(Material.IRON_BLOCK);
    }

    private void buildPortalStation(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.POLISHED_DEEPSLATE);
            }
        }
        world.getBlockAt(x, y, z).setType(Material.SEA_LANTERN);

        for (int dy = 1; dy <= 4; dy++) {
            world.getBlockAt(x, y + dy, z - 2).setType(Material.CHERRY_LOG);
            world.getBlockAt(x, y + dy, z + 2).setType(Material.CHERRY_LOG);
        }
        for (int dz = -2; dz <= 2; dz++) {
            world.getBlockAt(x, y + 5, z + dz).setType(Material.PINK_CONCRETE);
        }

        world.getBlockAt(x, y + 1, z).setType(Material.END_ROD);
        world.getBlockAt(x, y + 2, z).setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
    }

    private void buildInfoStation(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            world.getBlockAt(x + dx, y, z).setType(Material.POLISHED_DEEPSLATE);
            world.getBlockAt(x + dx, y + 1, z).setType(Material.CHERRY_PLANKS);
        }
        world.getBlockAt(x, y + 1, z).setType(Material.SEA_LANTERN);
    }

    private void buildLeaderboardStation(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.POLISHED_DEEPSLATE);
        world.getBlockAt(x, y + 1, z).setType(Material.GOLD_BLOCK);

        world.getBlockAt(x, y, z - 2).setType(Material.POLISHED_DEEPSLATE);
        world.getBlockAt(x, y + 1, z - 2).setType(Material.IRON_BLOCK);

        world.getBlockAt(x, y, z + 2).setType(Material.POLISHED_DEEPSLATE);
        world.getBlockAt(x, y + 1, z + 2).setType(Material.COPPER_BLOCK);

        Location standLoc = new Location(world, x + 0.5, y + 2.0, z + 0.5, 90.0f, 0.0f);
        if (world.getNearbyEntities(standLoc, 1.0, 1.0, 1.0).isEmpty()) {
            ArmorStand stand = (ArmorStand) world.spawnEntity(standLoc, EntityType.ARMOR_STAND);
            stand.setCustomNameVisible(true);
            stand.customName(Component.text("★ TOP ISLAND #1 ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            stand.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            stand.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            stand.setGravity(false);
            stand.setInvulnerable(true);
        }
    }

    private void buildRewardsStation(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.POLISHED_DEEPSLATE);
            }
        }
        world.getBlockAt(x, y, z).setType(Material.SEA_LANTERN);
        world.getBlockAt(x, y + 1, z).setType(Material.ENDER_CHEST);
        world.getBlockAt(x - 1, y + 1, z).setType(Material.END_ROD);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.END_ROD);
    }
}
