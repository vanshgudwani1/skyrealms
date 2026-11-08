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
 * - Soaring central crossing cupola (Y up to 125)
 * - North and South wing cathedral glass barrel vaults with polished deepslate ribs and copper spines
 * - Grand Central Boulevard (32 blocks wide) with a sunken reflecting canal, illuminated bridges, and streetlamps
 * - West Arrival Grand Pavilion with sweeping terraces and spawn dais
 * - East Elarion Botanical Sanctuary with quartz rotunda and Divine Offering Altar
 * - 16 agricultural plots, each at least 4x4 chunks (64x64 blocks = 4,096 blocks per plot),
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
        logger.info("Generating Grand Greenhouse Conservatory Dome at (" + cx + ", " + cy + ", " + cz + ") in world '" + world.getName() + "'...");

        // 1. Grand Boulevard & Central Aqueduct / Canal
        generateBoulevard(world, cx, cy, cz);

        // 2. West Arrival Grand Pavilion
        generateWestPavilion(world, cx, cy, cz);

        // 3. East Elarion Botanical Sanctuary & Altar
        generateEastSanctuary(world, cx, cy, cz);

        // 4. 16 Crop Plots (4x4 chunks each = 64x64 blocks)
        generatePlots(world, cx, cy, cz);

        // 5. Cathedral Vaulting, Crossing Cupola & Glass Canopy
        generateCanopy(world, cx, cy, cz);

        logger.info("Grand Greenhouse Conservatory Dome generation complete at (" + cx + ", " + cy + ", " + cz + ").");
    }

    /**
     * Generates the 32-block wide Grand Boulevard spanning X in [-320, 320] and Z in [-16, 15].
     */
    private void generateBoulevard(@NotNull World world, int cx, int cy, int cz) {
        for (int dx = -320; dx <= 320; dx++) {
            for (int dz = -16; dz <= 15; dz++) {
                int x = cx + dx;
                int z = cz + dz;

                // Sub-floor foundation at Y = cy - 1
                setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);

                // Central Sunken Reflecting Canal (dz in [-2, 2])
                if (dz >= -2 && dz <= 2) {
                    boolean isBridge = isCanalBridge(dx);
                    if (isBridge) {
                        // Bridge surface flush with promenade
                        setBlockFast(world, x, cy, z, Material.SMOOTH_STONE);
                        if (dz == -2 || dz == 2) {
                            setBlockFast(world, x, cy + 1, z, Material.POLISHED_DEEPSLATE_WALL);
                            if (dx % 2 == 0) {
                                setBlockFast(world, x, cy + 2, z, Material.LANTERN);
                            }
                        }
                    } else {
                        // Sunken water canal with submerged sea lanterns
                        setBlockFast(world, x, cy - 2, z, (dx % 8 == 0) ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                        setBlockFast(world, x, cy - 1, z, Material.WATER);
                        setBlockFast(world, x, cy, z, Material.AIR);
                        if (dx % 11 == 0 && Math.abs(dz) <= 1) {
                            setBlockFast(world, x, cy, z, Material.LILY_PAD);
                        }
                    }
                } else if (Math.abs(dz) == 3) {
                    // Canal border stone curb
                    setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                    if (dx % 8 == 0) {
                        setBlockFast(world, x, cy + 1, z, Material.STONE_BRICK_SLAB);
                    }
                } else if (Math.abs(dz) == 15) {
                    // Outer boulevard promenade border
                    setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                    setBlockFast(world, x, cy + 1, z, Material.POLISHED_ANDESITE_SLAB);
                } else if (Math.abs(dz) == 4 || Math.abs(dz) == 14) {
                    // Ornamental floral planters lining walkways
                    if (dx % 6 == 0) {
                        setBlockFast(world, x, cy, z, Material.MOSS_BLOCK);
                        setBlockFast(world, x, cy + 1, z, Material.FLOWERING_AZALEA);
                    } else {
                        setBlockFast(world, x, cy, z, Material.POLISHED_ANDESITE);
                    }
                } else {
                    // Paved promenade walkway
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
    }

    private boolean isCanalBridge(int dx) {
        // Bridges align with the center of each of the 8 plot columns
        int[] bridgeXs = {-224, -160, -96, -32, 0, 32, 96, 160, 224};
        for (int bx : bridgeXs) {
            if (Math.abs(dx - bx) <= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * West Arrival Grand Pavilion (X in [-320, -256], Z in [-32, 32]).
     * Features sweeping arrival stairs, reflecting fountain, and player spawn dais at X = -288.
     */
    private void generateWestPavilion(@NotNull World world, int cx, int cy, int cz) {
        for (int dx = -320; dx <= -256; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);

                // Terraced pavilion flooring
                int distToDais = Math.max(Math.abs(dx - (-288)), Math.abs(dz));
                if (distToDais <= 8) {
                    // Raised arrival dais at Y = cy + 1
                    setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                    setBlockFast(world, x, cy + 1, z, (distToDais == 8) ? Material.POLISHED_ANDESITE_SLAB : Material.QUARTZ_BLOCK);
                } else {
                    setBlockFast(world, x, cy, z, Material.POLISHED_GRANITE);
                }

                // Decorative corner pillars with lanterns
                if ((dx == -296 || dx == -280) && Math.abs(dz) == 8) {
                    setBlockFast(world, x, cy + 2, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 3, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 4, z, Material.SEA_LANTERN);
                }
            }
        }

        // Central Welcome Fountain (X in [-274, -266], Z in [-4, 4])
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
    }

    /**
     * East Elarion Botanical Sanctuary & Altar (X in [256, 320], Z in [-32, 32]).
     * Features quartz rotunda, Elarion Keeper dais at X = +288, and Divine Offering Altar at X = +280.
     */
    private void generateEastSanctuary(@NotNull World world, int cx, int cy, int cz) {
        for (int dx = 256; dx <= 320; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);

                int distToKeeper = Math.max(Math.abs(dx - 288), Math.abs(dz));
                if (distToKeeper <= 8) {
                    setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                    setBlockFast(world, x, cy + 1, z, Material.QUARTZ_BLOCK);
                } else {
                    setBlockFast(world, x, cy, z, (distToKeeper % 2 == 0) ? Material.SMOOTH_STONE : Material.DARK_PRISMARINE);
                }

                // Rotunda columns
                if ((dx == 280 || dx == 296) && (Math.abs(dz) == 8 || Math.abs(dz) == 0)) {
                    setBlockFast(world, x, cy + 2, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 3, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 4, z, Material.QUARTZ_PILLAR);
                    setBlockFast(world, x, cy + 5, z, Material.SEA_LANTERN);
                }
            }
        }

        // Divine Offering Altar Table (X = cx + 280, Z = cz)
        setBlockFast(world, cx + 280, cy + 2, cz, Material.CHISELED_QUARTZ_BLOCK);
        setBlockFast(world, cx + 280, cy + 3, cz, Material.LECTERN);
        setBlockFast(world, cx + 280, cy + 2, cz - 1, Material.CRYING_OBSIDIAN);
        setBlockFast(world, cx + 280, cy + 3, cz - 1, Material.AMETHYST_CLUSTER);
        setBlockFast(world, cx + 280, cy + 2, cz + 1, Material.CRYING_OBSIDIAN);
        setBlockFast(world, cx + 280, cy + 3, cz + 1, Material.AMETHYST_CLUSTER);
    }

    /**
     * Generates all 16 crop plots.
     * Each plot is 4x4 chunks (64x64 blocks = 4,096 blocks).
     * Includes gatehouse portico, boundary curb, 100% moisture irrigation grid,
     * and starter mature golden wheat in Plot 1.
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
                        // Plot Gatehouse Archway facing the Grand Boulevard
                        boolean isFacingBoulevard = isNorthRow ? (z == maxZ) : (z == minZ);
                        boolean isGateOpening = isFacingBoulevard && Math.abs(x - pcx) <= 2;
                        boolean isGatePillar = isFacingBoulevard && (Math.abs(x - pcx) == 3);

                        if (isGateOpening) {
                            // Smooth stone entryway
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.SMOOTH_STONE);
                        } else if (isGatePillar) {
                            // Grand gatehouse pillars
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.POLISHED_DEEPSLATE);
                            setBlockFast(world, x, cy + 1, z, Material.POLISHED_DEEPSLATE);
                            setBlockFast(world, x, cy + 2, z, Material.POLISHED_DEEPSLATE);
                            setBlockFast(world, x, cy + 3, z, Material.POLISHED_DEEPSLATE);
                            setBlockFast(world, x, cy + 4, z, Material.LANTERN);
                        } else {
                            // Perimeter Curb
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy + 1, z, Material.POLISHED_ANDESITE_SLAB);
                        }
                    } else {
                        // Interior Farmland & Irrigation Grid
                        setBlockFast(world, x, cy - 1, z, Material.DIRT);

                        // Irrigation nodes every 8 blocks for 100% hydration and light level >= 14
                        boolean isIrrigation = ((x - pcx) % 8 == 0) && ((z - pcz) % 8 == 0);

                        if (isIrrigation) {
                            // Submerged Sea Lantern under waterlogged slab
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

            // Gatehouse lintel archway across x in [pcx - 3, pcx + 3]
            int gateZ = isNorthRow ? maxZ : minZ;
            for (int gx = pcx - 2; gx <= pcx + 2; gx++) {
                setBlockFast(world, gx, cy + 4, gateZ, Material.POLISHED_DEEPSLATE);
            }
            setBlockFast(world, pcx, cy + 3, gateZ, Material.LANTERN);
        }
    }

    /**
     * Generates the soaring cathedral glass canopy, central crossing cupola,
     * polished deepslate ribs, copper ridge spines, and perimeter walls.
     */
    private void generateCanopy(@NotNull World world, int cx, int cy, int cz) {
        // 1. Central Crossing Rotunda Dome (X in [-32, 32], Z in [-32, 32])
        for (int dx = -32; dx <= 32; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq <= 32.0 * 32.0) {
                    double normDist = Math.sqrt(distSq) / 32.0;
                    int vaultH = (int) Math.round(18.0 * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist)));
                    int roofY = cy + 6 + vaultH; // Peak at Y = cy + 24 (124)

                    boolean isRib = (Math.abs(dx) % 8 == 0) || (Math.abs(dz) % 8 == 0) || (normDist >= 0.95);
                    Material mat = isRib ? Material.POLISHED_DEEPSLATE : Material.TINTED_GLASS;
                    setBlockFast(world, cx + dx, roofY, cz + dz, mat);
                }
            }
        }

        // 2. North Agricultural Wing Cathedral Vault (X in [-256, 255], Z in [-80, -17])
        for (int dx = -256; dx <= 255; dx++) {
            for (int dz = -80; dz <= -17; dz++) {
                double deltaZ = Math.abs(dz - (-48));
                double normZ = deltaZ / 32.0;
                int vaultH = (int) Math.round(12.0 * Math.sqrt(Math.max(0.0, 1.0 - normZ * normZ)));
                int roofY = cy + 6 + vaultH; // Peak at Y = cy + 18 (118)

                boolean isRib = (Math.abs(dx) % 16 == 0);
                boolean isSpine = (dz == -48);
                Material mat = isRib ? Material.POLISHED_DEEPSLATE
                        : (isSpine ? Material.WAXED_CUT_COPPER : Material.GLASS);

                setBlockFast(world, cx + dx, roofY, cz + dz, mat);

                // North exterior perimeter wall (dz == -80)
                if (dz == -80) {
                    for (int wy = cy + 1; wy <= cy + 6; wy++) {
                        Material wMat = isRib ? Material.POLISHED_DEEPSLATE : Material.GLASS;
                        setBlockFast(world, cx + dx, wy, cz + dz, wMat);
                    }
                }
            }
        }

        // 3. South Agricultural Wing Cathedral Vault (X in [-256, 255], Z in [16, 79])
        for (int dx = -256; dx <= 255; dx++) {
            for (int dz = 16; dz <= 79; dz++) {
                double deltaZ = Math.abs(dz - 48);
                double normZ = deltaZ / 32.0;
                int vaultH = (int) Math.round(12.0 * Math.sqrt(Math.max(0.0, 1.0 - normZ * normZ)));
                int roofY = cy + 6 + vaultH; // Peak at Y = cy + 18 (118)

                boolean isRib = (Math.abs(dx) % 16 == 0);
                boolean isSpine = (dz == 48);
                Material mat = isRib ? Material.POLISHED_DEEPSLATE
                        : (isSpine ? Material.WAXED_CUT_COPPER : Material.GLASS);

                setBlockFast(world, cx + dx, roofY, cz + dz, mat);

                // South exterior perimeter wall (dz == 79)
                if (dz == 79) {
                    for (int wy = cy + 1; wy <= cy + 6; wy++) {
                        Material wMat = isRib ? Material.POLISHED_DEEPSLATE : Material.GLASS;
                        setBlockFast(world, cx + dx, wy, cz + dz, wMat);
                    }
                }
            }
        }

        // 4. Boulevard Clerestory Canopy Vault (X in [-256, 255], Z in [-16, 15] outside crossing)
        for (int dx = -256; dx <= 255; dx++) {
            if (Math.abs(dx) <= 32) continue; // Inside central crossing dome
            for (int dz = -16; dz <= 15; dz++) {
                double deltaZ = Math.abs(dz);
                double normZ = deltaZ / 16.0;
                int vaultH = (int) Math.round(6.0 * Math.sqrt(Math.max(0.0, 1.0 - normZ * normZ)));
                int roofY = cy + 8 + vaultH; // Peak at Y = cy + 14 (114)

                boolean isRib = (Math.abs(dx) % 16 == 0);
                Material mat = isRib ? Material.POLISHED_DEEPSLATE : Material.WHITE_STAINED_GLASS;
                setBlockFast(world, cx + dx, roofY, cz + dz, mat);
            }
        }

        // 5. West Pavilion Rotunda Canopy (X centered at -288, Z centered at 0, R = 28)
        for (int dx = -316; dx <= -260; dx++) {
            for (int dz = -28; dz <= 28; dz++) {
                double distSq = (dx - (-288)) * (dx - (-288)) + dz * dz;
                if (distSq <= 28.0 * 28.0) {
                    double normDist = Math.sqrt(distSq) / 28.0;
                    int vaultH = (int) Math.round(12.0 * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist)));
                    int roofY = cy + 6 + vaultH;

                    boolean isRib = ((dx - (-288)) % 8 == 0) || (Math.abs(dz) % 8 == 0);
                    Material mat = isRib ? Material.POLISHED_DEEPSLATE : Material.LIGHT_BLUE_STAINED_GLASS;
                    setBlockFast(world, cx + dx, roofY, cz + dz, mat);
                }
            }
        }

        // 6. East Sanctuary Rotunda Canopy (X centered at 288, Z centered at 0, R = 28)
        for (int dx = 260; dx <= 316; dx++) {
            for (int dz = -28; dz <= 28; dz++) {
                double distSq = (dx - 288) * (dx - 288) + dz * dz;
                if (distSq <= 28.0 * 28.0) {
                    double normDist = Math.sqrt(distSq) / 28.0;
                    int vaultH = (int) Math.round(14.0 * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist)));
                    int roofY = cy + 6 + vaultH;

                    boolean isRib = ((dx - 288) % 8 == 0) || (Math.abs(dz) % 8 == 0);
                    Material mat = isRib ? Material.QUARTZ_PILLAR : Material.TINTED_GLASS;
                    setBlockFast(world, cx + dx, roofY, cz + dz, mat);
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
