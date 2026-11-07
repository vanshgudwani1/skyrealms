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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.PinkPetals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for managing, pasting, and decorating the Celestial Dragon Nest Lobby.
 */
public final class LobbyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyService.class);
    private final Plugin plugin;
    private final File markerFile;

    public LobbyService(Plugin plugin) {
        this.plugin = plugin;
        this.markerFile = new File(plugin.getDataFolder(), "lobby_initialized.flag");
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
            return new Location(world, 0.5, 71.0, 0.5, 0.0f, 0.0f);
        }
        return new Location(Bukkit.getWorlds().get(0), 0.5, 100.0, 0.5, 0.0f, 0.0f);
    }

    /**
     * Initializes the Dragon Nest lobby if not already initialized or if forced.
     */
    public CompletableFuture<Boolean> initLobby(boolean force) {
        if (!force && isLobbyInitialized()) {
            LOGGER.info("Lobby is already initialized according to marker flag.");
            return CompletableFuture.completedFuture(true);
        }

        World world = Bukkit.getWorld("world");
        if (world == null) {
            LOGGER.warn("Lobby world 'world' not found! Skipping lobby initialization.");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Run heavy schematic loading and pasting asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LOGGER.info("Initiating Celestial Dragon Nest Lobby generation...");

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

                        LOGGER.info("Pasted schematic! Now transforming foliage to Cherry theme...");
                        CuboidRegion region = new CuboidRegion(weWorld, BlockVector3.at(-350, 0, -350), BlockVector3.at(350, 260, 350));

                        // 1. Replace leaves -> CHERRY_LEAVES
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

                // Schedule main thread interior decorations & spawn setup
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        buildServerInterior(world);
                        world.setSpawnLocation(new Location(world, 0.5, 71.0, 0.5, 0.0f, 0.0f));

                        // Write marker flag
                        plugin.getDataFolder().mkdirs();
                        Files.writeString(markerFile.toPath(), "initialized");

                        LOGGER.info("Celestial Dragon Nest Lobby fully built, decorated, and active!");
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
     * Builds the handcrafted Celestial Dais and server stations at the lobby center.
     */
    public void buildServerInterior(World world) {
        LOGGER.info("Constructing handcrafted Celestial Dais & server interior...");

        // 1. Circular Central Dais around (0, 70, 0)
        int centerY = 70;
        int radius = 8;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= radius) {
                    Block block = world.getBlockAt(x, centerY, z);
                    Block above = world.getBlockAt(x, centerY + 1, z);

                    if (dist < 2.0) {
                        block.setType(Material.CHERRY_PLANKS);
                    } else if (dist >= 3.8 && dist <= 4.6) {
                        // Embedded Sea Lantern illumination ring
                        block.setType(Material.SEA_LANTERN);
                    } else if (dist > 6.8) {
                        block.setType(Material.POLISHED_DEEPSLATE);
                    } else {
                        block.setType((x + z) % 2 == 0 ? Material.CHERRY_PLANKS : Material.POLISHED_DEEPSLATE);
                    }

                    // Clear above
                    for (int y = centerY + 1; y <= centerY + 6; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }

                    // Pink petals scattering on floor
                    if (dist > 2.0 && dist < 7.0 && (x * 31 + z * 17) % 5 == 0) {
                        above.setType(Material.PINK_PETALS);
                        if (above.getBlockData() instanceof PinkPetals petals) {
                            petals.setFlowerAmount(3);
                            above.setBlockData(petals);
                        }
                    }
                }
            }
        }

        // Center Dais Core: Lodestone pedestal with pink glass crown
        world.getBlockAt(0, centerY, 0).setType(Material.LODESTONE);
        world.getBlockAt(0, centerY + 1, 0).setType(Material.PINK_STAINED_GLASS);

        // 2. Cherry Torii Pergola over central dais
        int[][] pillarCoords = {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}};
        for (int[] p : pillarCoords) {
            int px = p[0];
            int pz = p[1];
            for (int y = 1; y <= 5; y++) {
                world.getBlockAt(px, centerY + y, pz).setType(Material.STRIPPED_CHERRY_LOG);
            }
            // Hanging soul lantern
            world.getBlockAt(px, centerY + 4, pz + (pz > 0 ? -1 : 1)).setType(Material.CHAIN);
            world.getBlockAt(px, centerY + 3, pz + (pz > 0 ? -1 : 1)).setType(Material.SOUL_LANTERN);
        }

        // Cross beams
        for (int x = -5; x <= 5; x++) {
            world.getBlockAt(x, centerY + 6, -5).setType(Material.CHERRY_SLAB);
            world.getBlockAt(x, centerY + 6, 5).setType(Material.CHERRY_SLAB);
        }
        for (int z = -5; z <= 5; z++) {
            world.getBlockAt(-5, centerY + 6, z).setType(Material.CHERRY_SLAB);
            world.getBlockAt(5, centerY + 6, z).setType(Material.CHERRY_SLAB);
        }

        // 3. Station 1: Island Travel Portal (East: X = 13, Z = 0)
        buildPortalStation(world, 13, centerY, 0);

        // 4. Station 2: Server Info & Commands Shrine (North: X = 0, Z = -13)
        buildInfoStation(world, 0, centerY, -13);

        // 5. Station 3: Hall of Fame / Leaderboard Podiums (West: X = -13, Z = 0)
        buildLeaderboardStation(world, -13, centerY, 0);

        // 6. Station 4: Rewards & Crates Pavilion (South: X = 0, Z = 13)
        buildRewardsStation(world, 0, centerY, 13);
    }

    private void buildPortalStation(World world, int x, int y, int z) {
        // Base
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.POLISHED_DEEPSLATE);
            }
        }
        world.getBlockAt(x, y, z).setType(Material.SEA_LANTERN);

        // Archway
        for (int dy = 1; dy <= 4; dy++) {
            world.getBlockAt(x, y + dy, z - 2).setType(Material.CHERRY_LOG);
            world.getBlockAt(x, y + dy, z + 2).setType(Material.CHERRY_LOG);
        }
        for (int dz = -2; dz <= 2; dz++) {
            world.getBlockAt(x, y + 5, z + dz).setType(Material.PINK_CONCRETE);
        }

        // Portal Gate Center
        world.getBlockAt(x, y + 1, z).setType(Material.END_ROD);
        world.getBlockAt(x, y + 2, z).setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);

        // Hanging Portal Sign
        Block signBlock = world.getBlockAt(x - 1, y + 3, z);
        signBlock.setType(Material.OAK_WALL_SIGN);
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0, Component.text("✦ SKYBLOCK ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text("Island Portal", NamedTextColor.WHITE));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(2, Component.text("Click / Step In", NamedTextColor.GREEN));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(3, Component.text("/is create", NamedTextColor.YELLOW));
            sign.update();
        }
    }

    private void buildInfoStation(World world, int x, int y, int z) {
        // Pedestals
        for (int dx = -2; dx <= 2; dx++) {
            world.getBlockAt(x + dx, y, z).setType(Material.POLISHED_DEEPSLATE);
            world.getBlockAt(x + dx, y + 1, z).setType(Material.CHERRY_PLANKS);
        }
        world.getBlockAt(x, y + 1, z).setType(Material.SEA_LANTERN);

        // Lectern / Guide Sign
        Block signBlock = world.getBlockAt(x, y + 2, z);
        signBlock.setType(Material.OAK_WALL_SIGN);
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0, Component.text("✦ COMMANDS ✦", NamedTextColor.AQUA, TextDecoration.BOLD));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text("/is - Island Menu", NamedTextColor.WHITE));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(2, Component.text("/spawn - Lobby", NamedTextColor.YELLOW));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(3, Component.text("/is visit <player>", NamedTextColor.GRAY));
            sign.update();
        }
    }

    private void buildLeaderboardStation(World world, int x, int y, int z) {
        // 3-tier podium: 1st (center, Gold), 2nd (left, Iron), 3rd (right, Copper)
        world.getBlockAt(x, y, z).setType(Material.POLISHED_DEEPSLATE);
        world.getBlockAt(x, y + 1, z).setType(Material.GOLD_BLOCK);

        world.getBlockAt(x, y, z - 2).setType(Material.POLISHED_DEEPSLATE);
        world.getBlockAt(x, y + 1, z - 2).setType(Material.IRON_BLOCK);

        world.getBlockAt(x, y, z + 2).setType(Material.POLISHED_DEEPSLATE);
        world.getBlockAt(x, y + 1, z + 2).setType(Material.COPPER_BLOCK);

        // Armor stand with golden crown at 1st place
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

        // Leaderboard Sign
        Block signBlock = world.getBlockAt(x + 1, y + 2, z);
        signBlock.setType(Material.OAK_WALL_SIGN);
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0, Component.text("✦ HALL OF FAME ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text("Top SkyBlock", NamedTextColor.YELLOW));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(2, Component.text("Island Levels", NamedTextColor.WHITE));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(3, Component.text("/is top", NamedTextColor.GRAY));
            sign.update();
        }
    }

    private void buildRewardsStation(World world, int x, int y, int z) {
        // Vault shrine
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.POLISHED_DEEPSLATE);
            }
        }
        world.getBlockAt(x, y, z).setType(Material.SEA_LANTERN);
        world.getBlockAt(x, y + 1, z).setType(Material.ENDER_CHEST);

        // End rods on sides
        world.getBlockAt(x - 1, y + 1, z).setType(Material.END_ROD);
        world.getBlockAt(x + 1, y + 1, z).setType(Material.END_ROD);

        Block signBlock = world.getBlockAt(x, y + 2, z - 1);
        signBlock.setType(Material.OAK_WALL_SIGN);
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0, Component.text("✦ REWARDS ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text("Celestial Shrine", NamedTextColor.WHITE));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(2, Component.text("Daily Perks", NamedTextColor.YELLOW));
            sign.getSide(org.bukkit.block.sign.Side.FRONT).line(3, Component.text("/rewards", NamedTextColor.GRAY));
            sign.update();
        }
    }
}
