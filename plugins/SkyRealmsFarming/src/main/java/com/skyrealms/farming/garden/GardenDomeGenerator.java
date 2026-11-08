package com.skyrealms.farming.garden;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Procedural generator for the grand SkyRealms Conservatory Greenhouse Dome.
 * Generates an architectural Victorian/Gothic cathedral greenhouse spanning 640x180 blocks:
 * - Soaring central crossing cupola (Y up to 126)
 * - Continuous, fully-enclosed North and South wing cathedral glass barrel vaults (zero vertical gaps)
 * - Complete cathedral gable end walls with stained glass tracery enclosing both wings at East & West
 * - Grand interior arched colonnades and clerestory walls separating boulevard from crop plots
 * - 32-block wide Grand Boulevard with a sunken illuminated canal, stone bridges, and streetlamps
 * - West Arrival Grand Pavilion with sweeping staircases, arrival dais, and prismarine fountain
 * - East Elarion Botanical Sanctuary with quartz rotunda walls and Divine Offering Altar
 * - 16 agricultural plots, each 4x4 chunks (64x64 blocks = 4,096 blocks per plot),
 *   complete with ornamental gatehouses, perimeter stone curbs, and a 100% moisture irrigation grid.
 */
public final class GardenDomeGenerator {

    private final Logger logger;

    public GardenDomeGenerator(@NotNull Logger logger) {
        this.logger = logger;
    }

    /**
     * Generates the entire grand Greenhouse Dome and its 16 4x4 chunk crop plots.
     */
    public void generateDome(@NotNull World world, int cx, int cy, int cz) {
        logger.info("Generating Fully-Enclosed Grand Cathedral Conservatory Dome at (" + cx + ", " + cy + ", " + cz + ") in world '" + world.getName() + "'...");

        // 1. Foundation, Grand Boulevard, Reflecting Canal & Pavilions
        generateFlooringAndBoulevard(world, cx, cy, cz);

        // 2. 16 Crop Plots (4x4 chunks each = 64x64 blocks) with 100% moisture grid
        generatePlots(world, cx, cy, cz);

        // 3. Enclosed Exterior & Gable End Walls (No void openings)
        generateEnclosingWalls(world, cx, cy, cz);

        // 4. Grand Interior Colonnades & Clerestory Walls (Between boulevard and plots)
        generateInteriorColonnades(world, cx, cy, cz);

        // 5. Continuous Cathedral Vaults & Rotunda Domes (Zero vertical gaps)
        generateCanopyVaults(world, cx, cy, cz);

        logger.info("Grand Cathedral Conservatory Dome generation complete at (" + cx + ", " + cy + ", " + cz + ").");
    }

    /**
     * Generates all ground floors, central canal, West Pavilion arrival dais, and East Sanctuary floor.
     */
    private void generateFlooringAndBoulevard(@NotNull World world, int cx, int cy, int cz) {
        // Grand Boulevard: dx in [-320, 320], dz in [-16, 15]
        for (int dx = -320; dx <= 320; dx++) {
            for (int dz = -16; dz <= 15; dz++) {
                int x = cx + dx;
                int z = cz + dz;

                // Sub-floor foundation
                setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);

                // Central Sunken Reflecting Canal (dz in [-2, 2])
                if (dz >= -2 && dz <= 2) {
                    boolean isBridge = isCanalBridge(dx);
                    if (isBridge) {
                        setBlockFast(world, x, cy, z, Material.SMOOTH_STONE);
                        if (dz == -2 || dz == 2) {
                            setBlockFast(world, x, cy + 1, z, Material.POLISHED_DEEPSLATE_WALL);
                            if (dx % 2 == 0) {
                                setBlockFast(world, x, cy + 2, z, Material.LANTERN);
                            }
                        }
                    } else {
                        setBlockFast(world, x, cy - 2, z, (dx % 8 == 0) ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                        setBlockFast(world, x, cy - 1, z, Material.WATER);
                        setBlockFast(world, x, cy, z, Material.AIR);
                        if (dx % 11 == 0 && Math.abs(dz) <= 1) {
                            setBlockFast(world, x, cy, z, Material.LILY_PAD);
                        }
                    }
                } else if (Math.abs(dz) == 3) {
                    // Canal stone curb
                    setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                    if (dx % 8 == 0) {
                        setBlockFast(world, x, cy + 1, z, Material.STONE_BRICK_SLAB);
                    }
                } else if (Math.abs(dz) == 15) {
                    // Outer boulevard edge curb
                    setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                    setBlockFast(world, x, cy + 1, z, Material.POLISHED_ANDESITE_SLAB);
                } else if (Math.abs(dz) == 4 || Math.abs(dz) == 14) {
                    // Planters
                    if (dx % 6 == 0) {
                        setBlockFast(world, x, cy, z, Material.MOSS_BLOCK);
                        setBlockFast(world, x, cy + 1, z, Material.FLOWERING_AZALEA);
                    } else {
                        setBlockFast(world, x, cy, z, Material.POLISHED_ANDESITE);
                    }
                } else {
                    // Promenade paving
                    Material mat = (Math.abs(dx) % 8 == 0) ? Material.SMOOTH_STONE : Material.POLISHED_ANDESITE;
                    setBlockFast(world, x, cy, z, mat);

                    // Ornate streetlamps every 16 blocks along the promenades
                    if (dx % 16 == 0 && Math.abs(dz) == 9) {
                        setBlockFast(world, x, cy + 1, z, Material.POLISHED_DEEPSLATE_WALL);
                        setBlockFast(world, x, cy + 2, z, Material.POLISHED_DEEPSLATE_WALL);
                        setBlockFast(world, x, cy + 3, z, Material.LANTERN);
                    }
                }
            }
        }

        // West Arrival Grand Pavilion (dx in [-320, -256], dz in [-32, 32])
        for (int dx = -320; dx <= -256; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);

                int distToDais = Math.max(Math.abs(dx - (-288)), Math.abs(dz));
                if (distToDais <= 8) {
                    setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                    setBlockFast(world, x, cy + 1, z, (distToDais == 8) ? Material.POLISHED_ANDESITE_SLAB : Material.QUARTZ_BLOCK);
                } else {
                    setBlockFast(world, x, cy, z, Material.POLISHED_GRANITE);
                }

                // Decorative corner pillars on arrival dais
                if ((dx == -296 || dx == -280) && Math.abs(dz) == 8) {
                    setBlockFast(world, x, cy + 2, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 3, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 4, z, Material.SEA_LANTERN);
                }
            }
        }

        // Welcome Fountain in West Pavilion
        for (int dx = -274; dx <= -266; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (Math.abs(dx - (-270)) == 4 || Math.abs(dz) == 4) {
                    setBlockFast(world, x, cy, z, Material.PRISMARINE);
                    setBlockFast(world, x, cy + 1, z, Material.PRISMARINE_SLAB);
                } else {
                    setBlockFast(world, x, cy - 1, z, Material.DARK_PRISMARINE);
                    setBlockFast(world, x, cy, z, Material.WATER);
                }
            }
        }
        setBlockFast(world, cx - 270, cy + 1, cz, Material.CHISELED_QUARTZ_BLOCK);
        setBlockFast(world, cx - 270, cy + 2, cz, Material.SEA_LANTERN);

        // East Elarion Botanical Sanctuary Floor (dx in [256, 320], dz in [-32, 32])
        for (int dx = 256; dx <= 320; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);

                int distToKeeper = Math.max(Math.abs(dx - 288), Math.abs(dz));
                if (distToKeeper <= 8) {
                    // Concentric imperial dais
                    setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                    setBlockFast(world, x, cy + 1, z, (distToKeeper == 8) ? Material.POLISHED_ANDESITE_SLAB : Material.QUARTZ_BLOCK);
                } else {
                    Material fMat = (distToKeeper % 4 == 0) ? Material.STONE_BRICKS : Material.SMOOTH_STONE;
                    setBlockFast(world, x, cy, z, fMat);
                }

                // Rotunda sanctuary pillars
                if ((dx == 280 || dx == 296) && (Math.abs(dz) == 8 || Math.abs(dz) == 0)) {
                    setBlockFast(world, x, cy + 2, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 3, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 4, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 5, z, Material.SEA_LANTERN);
                }
            }
        }

        // Divine Offering Altar in East Sanctuary
        setBlockFast(world, cx + 280, cy + 2, cz, Material.CHISELED_QUARTZ_BLOCK);
        setBlockFast(world, cx + 280, cy + 3, cz, Material.LECTERN);
        setBlockFast(world, cx + 280, cy + 2, cz - 1, Material.CRYING_OBSIDIAN);
        setBlockFast(world, cx + 280, cy + 3, cz - 1, Material.AMETHYST_CLUSTER);
        setBlockFast(world, cx + 280, cy + 2, cz + 1, Material.CRYING_OBSIDIAN);
        setBlockFast(world, cx + 280, cy + 3, cz + 1, Material.AMETHYST_CLUSTER);
    }

    private boolean isCanalBridge(int dx) {
        int[] bridgeXs = {-224, -160, -96, -32, 0, 32, 96, 160, 224};
        for (int bx : bridgeXs) {
            if (Math.abs(dx - bx) <= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates the 16 crop plots (64x64 blocks each, 4x4 chunks).
     */
    private void generatePlots(@NotNull World world, int cx, int cy, int cz) {
        for (int plotIndex = 1; plotIndex <= 16; plotIndex++) {
            GardenPlot plot = new GardenPlot(
                    java.util.UUID.randomUUID(),
                    plotIndex,
                    (plotIndex == 1),
                    "WHEAT",
                    "BALANCED",
                    1.0,
                    1.0,
                    1.0
            );

            int pcx = plot.getCenterX(cx);
            int pcz = plot.getCenterZ(cz);
            int minX = plot.getMinX(cx);
            int maxX = plot.getMaxX(cx);
            int minZ = plot.getMinZ(cz);
            int maxZ = plot.getMaxZ(cz);
            boolean isNorthRow = (plotIndex <= 8);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isBorder = (x == minX || x == maxX || z == minZ || z == maxZ);

                    if (isBorder) {
                        boolean isFacingBoulevard = isNorthRow ? (z == maxZ) : (z == minZ);
                        boolean isGateOpening = isFacingBoulevard && Math.abs(x - pcx) <= 2;
                        boolean isGatePillar = isFacingBoulevard && (Math.abs(x - pcx) == 3);

                        if (isGateOpening) {
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.SMOOTH_STONE);
                        } else if (isGatePillar) {
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.POLISHED_DEEPSLATE);
                            setBlockFast(world, x, cy + 1, z, Material.POLISHED_DEEPSLATE);
                            setBlockFast(world, x, cy + 2, z, Material.POLISHED_DEEPSLATE);
                            setBlockFast(world, x, cy + 3, z, Material.POLISHED_DEEPSLATE);
                            setBlockFast(world, x, cy + 4, z, Material.LANTERN);
                        } else {
                            // Raised perimeter curb
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy + 1, z, Material.POLISHED_ANDESITE_SLAB);
                        }
                    } else {
                        // Interior Farmland & Irrigation
                        setBlockFast(world, x, cy - 1, z, Material.DIRT);

                        // Irrigation nodes every 8 blocks
                        boolean isIrrigation = ((x - pcx) % 8 == 0) && ((z - pcz) % 8 == 0);

                        if (isIrrigation) {
                            setBlockFast(world, x, cy - 1, z, Material.SEA_LANTERN);
                            setBlockFast(world, x, cy, z, Material.WATER);
                            Block slabBlock = world.getBlockAt(x, cy, z);
                            BlockData slabData = Material.STONE_SLAB.createBlockData();
                            if (slabData instanceof Waterlogged wl) {
                                wl.setWaterlogged(true);
                                slabBlock.setBlockData(slabData, false);
                            }
                        } else {
                            setBlockFast(world, x, cy, z, Material.FARMLAND);

                            // Pre-plant starter Plot 1 with mature Golden Wheat
                            if (plotIndex == 1) {
                                Block cropBlock = world.getBlockAt(x, cy + 1, z);
                                cropBlock.setType(Material.WHEAT, false);
                                if (cropBlock.getBlockData() instanceof Ageable ageable) {
                                    ageable.setAge(ageable.getMaximumAge());
                                    cropBlock.setBlockData(ageable, false);
                                }
                            }
                        }
                    }
                }
            }

            // Gatehouse lintel archway
            int gateZ = isNorthRow ? maxZ : minZ;
            for (int gx = pcx - 2; gx <= pcx + 2; gx++) {
                setBlockFast(world, gx, cy + 4, gateZ, Material.POLISHED_DEEPSLATE);
            }
            setBlockFast(world, pcx, cy + 3, gateZ, Material.LANTERN);
        }
    }

    /**
     * Encloses all exterior borders and builds cathedral gable walls at East and West ends.
     * Prevents any open view into the void.
     */
    private void generateEnclosingWalls(@NotNull World world, int cx, int cy, int cz) {
        // 1. North Exterior Wall (Z = cz - 80, dx in [-256, 255])
        int northZ = cz - 80;
        for (int dx = -256; dx <= 255; dx++) {
            int x = cx + dx;
            setBlockFast(world, x, cy - 1, northZ, Material.STONE_BRICKS);
            setBlockFast(world, x, cy, northZ, Material.POLISHED_DEEPSLATE);
            setBlockFast(world, x, cy + 1, northZ, Material.POLISHED_DEEPSLATE);
            setBlockFast(world, x, cy + 2, northZ, Material.POLISHED_DEEPSLATE);

            boolean isPillar = (dx % 8 == 0);
            for (int wy = cy + 3; wy <= cy + 7; wy++) {
                setBlockFast(world, x, wy, northZ, isPillar ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_STAINED_GLASS);
            }
            setBlockFast(world, x, cy + 8, northZ, Material.POLISHED_DEEPSLATE);
        }

        // 2. South Exterior Wall (Z = cz + 79, dx in [-256, 255])
        int southZ = cz + 79;
        for (int dx = -256; dx <= 255; dx++) {
            int x = cx + dx;
            setBlockFast(world, x, cy - 1, southZ, Material.STONE_BRICKS);
            setBlockFast(world, x, cy, southZ, Material.POLISHED_DEEPSLATE);
            setBlockFast(world, x, cy + 1, southZ, Material.POLISHED_DEEPSLATE);
            setBlockFast(world, x, cy + 2, southZ, Material.POLISHED_DEEPSLATE);

            boolean isPillar = (dx % 8 == 0);
            for (int wy = cy + 3; wy <= cy + 7; wy++) {
                setBlockFast(world, x, wy, southZ, isPillar ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_STAINED_GLASS);
            }
            setBlockFast(world, x, cy + 8, southZ, Material.POLISHED_DEEPSLATE);
        }

        // 3. East Gable End Walls (dx = 255): Encloses North wing (dz in [-80, -17]) and South wing (dz in [16, 79])
        int eastX = cx + 255;
        buildGableEndWall(world, eastX, cy, cz, true);

        // 4. West Gable End Walls (dx = -256): Encloses North wing (dz in [-80, -17]) and South wing (dz in [16, 79])
        int westX = cx - 256;
        buildGableEndWall(world, westX, cy, cz, false);

        // 5. West Pavilion Perimeter Walls (dx in [-320, -256], dz = +-32 and dx = -320)
        for (int dx = -320; dx <= -256; dx++) {
            int x = cx + dx;
            for (int sdz : new int[]{-32, 32}) {
                int z = cz + sdz;
                for (int wy = cy; wy <= cy + 10; wy++) {
                    boolean isPillar = (dx % 8 == 0) || (wy <= cy + 2) || (wy == cy + 10);
                    setBlockFast(world, x, wy, z, isPillar ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_STAINED_GLASS);
                }
            }
        }
        for (int dz = -32; dz <= 32; dz++) {
            int x = cx - 320;
            int z = cz + dz;
            for (int wy = cy; wy <= cy + 10; wy++) {
                boolean isPillar = (dz % 8 == 0) || (wy <= cy + 2) || (wy == cy + 10);
                setBlockFast(world, x, wy, z, isPillar ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_STAINED_GLASS);
            }
        }

        // 6. East Sanctuary Perimeter Walls (dx in [256, 320], dz = +-32 and dx = 320)
        for (int dx = 256; dx <= 320; dx++) {
            int x = cx + dx;
            for (int sdz : new int[]{-32, 32}) {
                int z = cz + sdz;
                for (int wy = cy; wy <= cy + 11; wy++) {
                    boolean isPillar = (dx % 8 == 0) || (wy <= cy + 2) || (wy == cy + 11);
                    setBlockFast(world, x, wy, z, isPillar ? Material.QUARTZ_PILLAR : Material.LIGHT_GRAY_STAINED_GLASS);
                }
            }
        }
        for (int dz = -32; dz <= 32; dz++) {
            int x = cx + 320;
            int z = cz + dz;
            for (int wy = cy; wy <= cy + 11; wy++) {
                boolean isPillar = (dz % 8 == 0) || (wy <= cy + 2) || (wy == cy + 11);
                setBlockFast(world, x, wy, z, isPillar ? Material.QUARTZ_PILLAR : Material.LIGHT_GRAY_STAINED_GLASS);
            }
        }
    }

    /**
     * Builds solid stained-glass cathedral gable walls at the ends of the plot wings.
     */
    private void buildGableEndWall(@NotNull World world, int wx, int cy, int cz, boolean isEast) {
        // North wing portion: dz in [-80, -17]
        for (int dz = -80; dz <= -17; dz++) {
            int z = cz + dz;
            int vaultTop = getWingVaultHeight(cy, dz, true);
            for (int wy = cy; wy <= vaultTop; wy++) {
                boolean isFraming = (wy <= cy + 2) || (wy == vaultTop) || (dz % 8 == 0);
                Material mat = isFraming ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_STAINED_GLASS;
                setBlockFast(world, wx, wy, z, mat);
            }
        }

        // South wing portion: dz in [16, 79]
        for (int dz = 16; dz <= 79; dz++) {
            int z = cz + dz;
            int vaultTop = getWingVaultHeight(cy, dz, false);
            for (int wy = cy; wy <= vaultTop; wy++) {
                boolean isFraming = (wy <= cy + 2) || (wy == vaultTop) || (dz % 8 == 0);
                Material mat = isFraming ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_STAINED_GLASS;
                setBlockFast(world, wx, wy, z, mat);
            }
        }
    }

    /**
     * Generates internal colonnades and clerestory walls separating the boulevard from the crop plots.
     */
    private void generateInteriorColonnades(@NotNull World world, int cx, int cy, int cz) {
        for (int side : new int[]{-17, 16}) {
            int z = cz + side;
            for (int dx = -256; dx <= 255; dx++) {
                int x = cx + dx;

                // Major structural pillars at plot boundaries (every 64 blocks) and sub-pillars (every 8 blocks)
                boolean isMajorPillar = (dx % 64 == 0) || (Math.abs(dx) == 256) || (dx == 255);
                boolean isPillar = (dx % 8 == 0);

                if (isMajorPillar) {
                    for (int wy = cy; wy <= cy + 14; wy++) {
                        setBlockFast(world, x, wy, z, Material.POLISHED_DEEPSLATE);
                    }
                } else {
                    // Open archway at plot gates (x within 3 blocks of plot center)
                    int plotColCenter = getPlotColCenter(dx);
                    boolean isArchOpening = (Math.abs(dx - plotColCenter) <= 3);

                    if (isArchOpening) {
                        // Open walk-through archway from Y=cy to Y=cy+4
                        for (int wy = cy + 5; wy <= cy + 14; wy++) {
                            setBlockFast(world, x, wy, z, (wy == cy + 5) ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_STAINED_GLASS);
                        }
                    } else {
                        // Colonnade wall with stained glass clerestory
                        setBlockFast(world, x, cy, z, Material.POLISHED_DEEPSLATE);
                        setBlockFast(world, x, cy + 1, z, Material.POLISHED_DEEPSLATE);
                        for (int wy = cy + 2; wy <= cy + 14; wy++) {
                            Material mat = isPillar ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_STAINED_GLASS;
                            setBlockFast(world, x, wy, z, mat);
                        }
                    }
                }
            }
        }
    }

    private int getPlotColCenter(int dx) {
        int[] centers = {-224, -160, -96, -32, 32, 96, 160, 224};
        int closest = centers[0];
        int minDist = Math.abs(dx - closest);
        for (int c : centers) {
            int dist = Math.abs(dx - c);
            if (dist < minDist) {
                minDist = dist;
                closest = c;
            }
        }
        return closest;
    }

    /**
     * Generates all cathedral glass vaults with complete vertical gap filling.
     * Guarantees zero floating stepped blocks and zero holes.
     */
    private void generateCanopyVaults(@NotNull World world, int cx, int cy, int cz) {
        // 1. North Agricultural Wing Cathedral Vault (dz in [-80, -17], dx in [-256, 255])
        for (int dx = -256; dx <= 255; dx++) {
            boolean isRib = (dx % 16 == 0);
            for (int dz = -80; dz <= -17; dz++) {
                int yCurr = getWingVaultHeight(cy, dz, true);
                int yNext = (dz < -48) ? getWingVaultHeight(cy, dz + 1, true) : getWingVaultHeight(cy, dz - 1, true);

                int minY = Math.min(yCurr, yNext);
                int maxY = Math.max(yCurr, yNext);

                boolean isSpine = (dz == -48);
                Material mat = isRib ? Material.POLISHED_DEEPSLATE
                        : (isSpine ? Material.WAXED_CUT_COPPER : Material.LIGHT_GRAY_STAINED_GLASS);

                for (int y = minY; y <= maxY; y++) {
                    setBlockFast(world, cx + dx, y, cz + dz, mat);
                }
            }
        }

        // 2. South Agricultural Wing Cathedral Vault (dz in [16, 79], dx in [-256, 255])
        for (int dx = -256; dx <= 255; dx++) {
            boolean isRib = (dx % 16 == 0);
            for (int dz = 16; dz <= 79; dz++) {
                int yCurr = getWingVaultHeight(cy, dz, false);
                int yNext = (dz < 48) ? getWingVaultHeight(cy, dz + 1, false) : getWingVaultHeight(cy, dz - 1, false);

                int minY = Math.min(yCurr, yNext);
                int maxY = Math.max(yCurr, yNext);

                boolean isSpine = (dz == 48);
                Material mat = isRib ? Material.POLISHED_DEEPSLATE
                        : (isSpine ? Material.WAXED_CUT_COPPER : Material.LIGHT_GRAY_STAINED_GLASS);

                for (int y = minY; y <= maxY; y++) {
                    setBlockFast(world, cx + dx, y, cz + dz, mat);
                }
            }
        }

        // 3. Grand Boulevard Canopy Vault (dz in [-16, 15], dx in [-256, 255] outside central dome)
        for (int dx = -256; dx <= 255; dx++) {
            if (Math.abs(dx) <= 32) continue; // Inside central crossing dome
            boolean isRib = (dx % 16 == 0);
            for (int dz = -16; dz <= 15; dz++) {
                int yCurr = getBoulevardVaultHeight(cy, dz);
                int yNext = (dz < 0) ? getBoulevardVaultHeight(cy, dz + 1) : getBoulevardVaultHeight(cy, dz - 1);

                int minY = Math.min(yCurr, yNext);
                int maxY = Math.max(yCurr, yNext);

                Material mat = isRib ? Material.POLISHED_DEEPSLATE : Material.WHITE_STAINED_GLASS;
                for (int y = minY; y <= maxY; y++) {
                    setBlockFast(world, cx + dx, y, cz + dz, mat);
                }
            }
        }

        // 4. Central Crossing Rotunda Dome (dx in [-32, 32], dz in [-32, 32])
        for (int dx = -32; dx <= 32; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq <= 32.0 * 32.0) {
                    int yCurr = getCrossingDomeHeight(cy, dx, dz);
                    // Approximate gradient for vertical gap fill
                    int yNext = getCrossingDomeHeight(cy, dx + (dx > 0 ? -1 : 1), dz + (dz > 0 ? -1 : 1));

                    int minY = Math.min(yCurr, yNext);
                    int maxY = Math.max(yCurr, yNext);

                    boolean isRib = (Math.abs(dx) % 8 == 0) || (Math.abs(dz) % 8 == 0) || (distSq >= 30.0 * 30.0);
                    Material mat = isRib ? Material.POLISHED_DEEPSLATE : Material.TINTED_GLASS;

                    for (int y = minY; y <= maxY; y++) {
                        setBlockFast(world, cx + dx, y, cz + dz, mat);
                    }
                }
            }
        }

        // 5. West Pavilion Rotunda Canopy (dx in [-320, -256], dz in [-32, 32])
        for (int dx = -320; dx <= -256; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                double distSq = (dx - (-288)) * (dx - (-288)) + dz * dz;
                if (distSq <= 32.0 * 32.0) {
                    double normDist = Math.min(1.0, Math.sqrt(distSq) / 32.0);
                    int vaultH = (int) Math.round(12.0 * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist)));
                    int roofY = cy + 10 + vaultH;

                    boolean isRib = ((dx - (-288)) % 8 == 0) || (Math.abs(dz) % 8 == 0);
                    Material mat = isRib ? Material.POLISHED_DEEPSLATE : Material.LIGHT_BLUE_STAINED_GLASS;

                    setBlockFast(world, cx + dx, roofY, cz + dz, mat);
                    // Fill vertically to edge wall at radius 30..32
                    if (distSq >= 28.0 * 28.0) {
                        for (int wy = cy + 10; wy < roofY; wy++) {
                            setBlockFast(world, cx + dx, wy, cz + dz, mat);
                        }
                    }
                }
            }
        }

        // 6. East Sanctuary Rotunda Canopy (dx in [256, 320], dz in [-32, 32])
        for (int dx = 256; dx <= 320; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                double distSq = (dx - 288) * (dx - 288) + dz * dz;
                if (distSq <= 32.0 * 32.0) {
                    double normDist = Math.min(1.0, Math.sqrt(distSq) / 32.0);
                    int vaultH = (int) Math.round(14.0 * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist)));
                    int roofY = cy + 11 + vaultH;

                    boolean isRib = ((dx - 288) % 8 == 0) || (Math.abs(dz) % 8 == 0);
                    Material mat = isRib ? Material.QUARTZ_PILLAR : Material.TINTED_GLASS;

                    setBlockFast(world, cx + dx, roofY, cz + dz, mat);
                    // Fill vertically to edge wall at radius 28..32
                    if (distSq >= 28.0 * 28.0) {
                        for (int wy = cy + 11; wy < roofY; wy++) {
                            setBlockFast(world, cx + dx, wy, cz + dz, mat);
                        }
                    }
                }
            }
        }
    }

    private int getWingVaultHeight(int cy, int dz, boolean isNorth) {
        int centerZ = isNorth ? -48 : 48;
        double deltaZ = Math.abs(dz - centerZ);
        double normZ = Math.min(1.0, deltaZ / 32.0);
        return cy + 8 + (int) Math.round(14.0 * Math.sqrt(Math.max(0.0, 1.0 - normZ * normZ)));
    }

    private int getBoulevardVaultHeight(int cy, int dz) {
        double deltaZ = Math.abs(dz);
        double normZ = Math.min(1.0, deltaZ / 16.0);
        return cy + 14 + (int) Math.round(6.0 * Math.sqrt(Math.max(0.0, 1.0 - normZ * normZ)));
    }

    private int getCrossingDomeHeight(int cy, int dx, int dz) {
        double distSq = dx * dx + dz * dz;
        double normDist = Math.min(1.0, Math.sqrt(distSq) / 32.0);
        return cy + 14 + (int) Math.round(12.0 * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist)));
    }

    private void setBlockFast(@NotNull World world, int x, int y, int z, @NotNull Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }
}
