package com.skyrealms.farming.garden;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Procedural generator for the SkyRealms Garden Greenhouse Dome.
 * Generates the glass conservatory dome, central boulevard, entrance plaza,
 * Garden Keeper dais, 4 specialized alcoves, and 16 crop plots in 2 rows of 8.
 */
public final class GardenDomeGenerator {

    private static final int DOME_RADIUS = 34;
    private static final int DOME_BASE_WALL_HEIGHT = 3;

    private final Logger logger;

    public GardenDomeGenerator(@NotNull Logger logger) {
        this.logger = logger;
    }

    /**
     * Generates the entire Greenhouse Dome at the given garden coordinates.
     */
    public void generateDome(@NotNull World world, int cx, int cy, int cz) {
        logger.info("Generating Greenhouse Dome at (" + cx + ", " + cy + ", " + cz + ") in world '" + world.getName() + "'...");

        // 1. Foundation & Sub-floor (Y = cy - 4 to cy - 1)
        generateSubFloor(world, cx, cy, cz);

        // 2. Main Floor & Landscaping (Y = cy)
        generateMainFloor(world, cx, cy, cz);

        // 3. Central Boulevard & Entrance & Keeper Dais
        generateBoulevardAndFeatures(world, cx, cy, cz);

        // 4. Arched Glass Conservatory Canopy Shell
        generateCanopy(world, cx, cy, cz);

        // 5. Generate 16 Crop Plots (2 rows x 8 columns)
        generatePlots(world, cx, cy, cz);

        logger.info("Greenhouse Dome generation complete at (" + cx + ", " + cy + ", " + cz + ").");
    }

    private void generateSubFloor(@NotNull World world, int cx, int cy, int cz) {
        for (int dx = -DOME_RADIUS; dx <= DOME_RADIUS; dx++) {
            for (int dz = -DOME_RADIUS; dz <= DOME_RADIUS; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= DOME_RADIUS * DOME_RADIUS) {
                    for (int dy = -4; dy < 0; dy++) {
                        Material mat = (dy == -1) ? Material.DIRT : Material.STONE_BRICKS;
                        setBlockFast(world, cx + dx, cy + dy, cz + dz, mat);
                    }
                }
            }
        }
    }

    private void generateMainFloor(@NotNull World world, int cx, int cy, int cz) {
        for (int dx = -DOME_RADIUS; dx <= DOME_RADIUS; dx++) {
            for (int dz = -DOME_RADIUS; dz <= DOME_RADIUS; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= DOME_RADIUS * DOME_RADIUS) {
                    // Outer decorative flower beds and perimeter grass
                    if (distSq >= 28 * 28) {
                        Material mat = (distSq % 5 == 0) ? Material.MOSS_BLOCK
                                : ((distSq % 7 == 0) ? Material.FLOWERING_AZALEA : Material.GRASS_BLOCK);
                        setBlockFast(world, cx + dx, cy, cz + dz, mat);
                    } else {
                        // Interior base floor
                        setBlockFast(world, cx + dx, cy, cz + dz, Material.POLISHED_ANDESITE);
                    }
                }
            }
        }
    }

    private void generateBoulevardAndFeatures(@NotNull World world, int cx, int cy, int cz) {
        // Grand Central Boulevard: dx from -30 to 30, dz from -3 to 3
        for (int dx = -30; dx <= 30; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dz) == 3) {
                    setBlockFast(world, cx + dx, cy, cz + dz, Material.STONE_BRICK_SLAB);
                } else if (dz == 0 && dx % 4 == 0) {
                    // Recessed lighting
                    setBlockFast(world, cx + dx, cy, cz + dz, Material.SEA_LANTERN);
                } else {
                    setBlockFast(world, cx + dx, cy, cz + dz, Material.SMOOTH_STONE);
                }
            }
        }

        // Entrance Plaza (West End): dx in [-28, -22], dz in [-5, 5]
        for (int dx = -28; dx <= -22; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                setBlockFast(world, cx + dx, cy, cz + dz, Material.POLISHED_GRANITE);
            }
        }
        // Arrival Pillars & Lanterns
        setBlockFast(world, cx - 27, cy + 1, cz - 3, Material.DARK_OAK_FENCE);
        setBlockFast(world, cx - 27, cy + 2, cz - 3, Material.LANTERN);
        setBlockFast(world, cx - 27, cy + 1, cz + 3, Material.DARK_OAK_FENCE);
        setBlockFast(world, cx - 27, cy + 2, cz + 3, Material.LANTERN);

        // Garden Keeper Dais / Shrine (East End): dx in [22, 28], dz in [-5, 5]
        for (int dx = 22; dx <= 28; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                setBlockFast(world, cx + dx, cy + 1, cz + dz, Material.QUARTZ_BLOCK);
            }
        }
        // Shrine Columns & Altar
        setBlockFast(world, cx + 23, cy + 2, cz - 4, Material.QUARTZ_PILLAR);
        setBlockFast(world, cx + 23, cy + 3, cz - 4, Material.QUARTZ_PILLAR);
        setBlockFast(world, cx + 23, cy + 4, cz - 4, Material.SEA_LANTERN);

        setBlockFast(world, cx + 23, cy + 2, cz + 4, Material.QUARTZ_PILLAR);
        setBlockFast(world, cx + 23, cy + 3, cz + 4, Material.QUARTZ_PILLAR);
        setBlockFast(world, cx + 23, cy + 4, cz + 4, Material.SEA_LANTERN);

        setBlockFast(world, cx + 27, cy + 2, cz - 4, Material.QUARTZ_PILLAR);
        setBlockFast(world, cx + 27, cy + 3, cz - 4, Material.QUARTZ_PILLAR);
        setBlockFast(world, cx + 27, cy + 4, cz - 4, Material.SEA_LANTERN);

        setBlockFast(world, cx + 27, cy + 2, cz + 4, Material.QUARTZ_PILLAR);
        setBlockFast(world, cx + 27, cy + 3, cz + 4, Material.QUARTZ_PILLAR);
        setBlockFast(world, cx + 27, cy + 4, cz + 4, Material.SEA_LANTERN);

        // Central Altar Table
        setBlockFast(world, cx + 25, cy + 2, cz, Material.LECTERN);

        // North-West Alcove: Research Bench
        setBlockFast(world, cx - 22, cy + 1, cz - 22, Material.BOOKSHELF);
        setBlockFast(world, cx - 21, cy + 1, cz - 22, Material.BOOKSHELF);
        setBlockFast(world, cx - 20, cy + 1, cz - 22, Material.ENCHANTING_TABLE);

        // South-West Alcove: Equipment Workshop
        setBlockFast(world, cx - 22, cy + 1, cz + 22, Material.ANVIL);
        setBlockFast(world, cx - 21, cy + 1, cz + 22, Material.SMITHING_TABLE);
        setBlockFast(world, cx - 20, cy + 1, cz + 22, Material.GRINDSTONE);

        // North-East Alcove: Botanical Showcase
        setBlockFast(world, cx + 21, cy + 1, cz - 22, Material.CHISELED_QUARTZ_BLOCK);
        setBlockFast(world, cx + 21, cy + 2, cz - 22, Material.GLASS);

        // South-East Alcove: Divine Altar
        setBlockFast(world, cx + 21, cy + 1, cz + 22, Material.CRYING_OBSIDIAN);
        setBlockFast(world, cx + 21, cy + 2, cz + 22, Material.AMETHYST_CLUSTER);
    }

    private void generateCanopy(@NotNull World world, int cx, int cy, int cz) {
        // Vaulted conservatory glass dome shell
        for (int dx = -DOME_RADIUS; dx <= DOME_RADIUS; dx++) {
            for (int dz = -DOME_RADIUS; dz <= DOME_RADIUS; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= DOME_RADIUS * DOME_RADIUS) {
                    // Outer base wall (Y = cy + 1 to cy + 3)
                    if (distSq >= 33 * 33) {
                        for (int h = 1; h <= DOME_BASE_WALL_HEIGHT; h++) {
                            setBlockFast(world, cx + dx, cy + h, cz + dz, Material.STONE_BRICKS);
                        }
                    }

                    // Vaulted Glass Ceiling
                    double r = Math.sqrt(distSq);
                    int vaultH = (int) (Math.sqrt(DOME_RADIUS * DOME_RADIUS - distSq) * 0.58) + DOME_BASE_WALL_HEIGHT;
                    int canopyY = cy + vaultH;

                    // Framing ribs every 7 blocks or cardinal axes
                    boolean isRib = (Math.abs(dx) % 7 == 0) || (Math.abs(dz) % 7 == 0) || (dx == 0) || (dz == 0);
                    Material canopyMat = isRib ? Material.DARK_OAK_WOOD : Material.TINTED_GLASS;

                    setBlockFast(world, cx + dx, canopyY, cz + dz, canopyMat);
                }
            }
        }
    }

    private void generatePlots(@NotNull World world, int cx, int cy, int cz) {
        // Generate all 16 plots (Plot 1 through 16)
        for (int plotIndex = 1; plotIndex <= 16; plotIndex++) {
            GardenPlot plot = new GardenPlot(java.util.UUID.randomUUID(), plotIndex, (plotIndex == 1), "WHEAT", "BALANCED", 1.0, 1.0, 1.0);
            int pcx = plot.getCenterX(cx);
            int pcz = plot.getCenterZ(cz);

            // 7x7 plot perimeter and interior 5x5 farmland
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int bx = pcx + dx;
                    int bz = pcz + dz;

                    // Perimeter Border (dx == +-3 or dz == +-3)
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                        // Plot entrance towards central boulevard (skip border in middle of facing edge)
                        boolean isFacingEntrance = (plotIndex <= 8 && dz == 3 && dx == 0)
                                || (plotIndex > 8 && dz == -3 && dx == 0);

                        if (isFacingEntrance) {
                            setBlockFast(world, bx, cy, bz, Material.SMOOTH_STONE);
                        } else {
                            setBlockFast(world, bx, cy + 1, bz, Material.POLISHED_ANDESITE_SLAB);
                            setBlockFast(world, bx, cy, bz, Material.STONE_BRICKS);
                        }
                    } else {
                        // Internal 5x5 area
                        if (dx == 0 && dz == 0) {
                            // Sub-surface water source with waterlogged slab at Y=cy
                            setBlockFast(world, bx, cy, bz, Material.WATER);
                            Block slabBlock = world.getBlockAt(bx, cy, bz);
                            BlockData slabData = Material.STONE_SLAB.createBlockData();
                            if (slabData instanceof Waterlogged wl) {
                                wl.setWaterlogged(true);
                                slabBlock.setBlockData(slabData, false);
                            }
                        } else {
                            // Farmland with support dirt beneath
                            setBlockFast(world, bx, cy - 1, bz, Material.DIRT);
                            setBlockFast(world, bx, cy, bz, Material.FARMLAND);

                            // Initial crops for starter Plot 1
                            if (plotIndex == 1) {
                                Block cropBlock = world.getBlockAt(bx, cy + 1, bz);
                                cropBlock.setType(Material.WHEAT, false);
                                if (cropBlock.getBlockData() instanceof Ageable ageable) {
                                    ageable.setAge(ageable.getMaximumAge()); // Mature wheat for tutorial first harvest
                                    cropBlock.setBlockData(ageable, false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void setBlockFast(@NotNull World world, int x, int y, int z, @NotNull Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }
}
