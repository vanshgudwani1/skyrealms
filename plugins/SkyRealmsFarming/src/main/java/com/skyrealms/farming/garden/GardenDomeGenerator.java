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
 * Procedural generator for Project: Sakura Glass Garden.
 * Generates a luxurious Japanese Cherry Blossom Botanical Garden inside a gigantic
 * transparent glass conservatory across a 2x8 plot layout (512x128 farming footprint):
 * - Unified 520x168 block greenhouse envelope (zero disconnected igloos or trenches)
 * - Transparent curved glass canopy with dark oak, spruce & quartz structural supports
 * - Central Japanese Garden: koi pond, arched moon bridge, tea house, waterfall & rock garden
 * - Enormous custom-built Sakura trees with cherry leaves, pink petals, and moss gardens
 * - Grand Central Promenade with wooden walkways, Torii arches, stone lanterns, and fallen petals
 * - 16 agricultural plots (4x4 chunks = 64x64 blocks each) with 100% moisture irrigation grid,
 *   unobstructed farming clearance, and starter mature golden wheat in Plot 1
 * - West Arrival Grand Torii Gate & East Sacred Sakura Shrine of Elarion
 */
public final class GardenDomeGenerator {

    private final Logger logger;

    public GardenDomeGenerator(@NotNull Logger logger) {
        this.logger = logger;
    }

    /**
     * Generates the entire Sakura Glass Garden conservatory.
     */
    public void generateDome(@NotNull World world, int cx, int cy, int cz) {
        logger.info("Generating Project: Sakura Glass Garden at (" + cx + ", " + cy + ", " + cz + ") in world '" + world.getName() + "'...");

        // 1. Unified Sub-floor, Promenade & Perimeter Landscaping
        generatePromenadeAndFlooring(world, cx, cy, cz);

        // 2. Central Japanese Garden (Koi Pond, Moon Bridge, Tea House, Waterfall)
        generateCentralGarden(world, cx, cy, cz);

        // 3. 16 Crop Plots (4x4 chunks = 64x64 blocks each) with 100% moisture grid
        generatePlots(world, cx, cy, cz);

        // 4. Fully Enclosing Japanese Exterior Walls (White Quartz, Dark Timber, Stained Glass)
        generateEnclosingJapaneseWalls(world, cx, cy, cz);

        // 5. West Grand Torii Gate & East Sacred Sakura Shrine
        generateWestToriiGate(world, cx, cy, cz);
        generateEastSacredShrine(world, cx, cy, cz);

        // 6. Custom Cherry Blossom Trees & Botanical Landscaping
        generateSakuraTreesAndFlora(world, cx, cy, cz);

        // 7. Gigantic Transparent Glass Canopy & Central Pagoda Atrium (Zero vertical gaps)
        generateGlassCanopy(world, cx, cy, cz);

        logger.info("Project: Sakura Glass Garden generation complete at (" + cx + ", " + cy + ", " + cz + ").");
    }

    /**
     * Generates the central Japanese wooden walkway, borders, and sub-flooring.
     */
    private void generatePromenadeAndFlooring(@NotNull World world, int cx, int cy, int cz) {
        // Grand Promenade spans dx in [-260, 259], dz in [-16, 15]
        for (int dx = -260; dx <= 259; dx++) {
            for (int dz = -16; dz <= 15; dz++) {
                int x = cx + dx;
                int z = cz + dz;

                // Sub-floor foundation
                setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);

                // Central garden area is reserved for the Koi Pond & Tea House
                if (Math.abs(dx) <= 36 && Math.abs(dz) <= 14) {
                    continue; // Generated in generateCentralGarden
                }

                // Promenade Wooden Walkway & Landscaping
                if (Math.abs(dz) <= 4) {
                    // Center walking path: Smooth stone slab center with spruce plank borders
                    if (Math.abs(dz) <= 2) {
                        setBlockFast(world, x, cy, z, Material.SMOOTH_STONE_SLAB);
                    } else {
                        setBlockFast(world, x, cy, z, Material.SPRUCE_PLANKS);
                    }
                    // Scatter fallen cherry petals on the path
                    if ((dx * 13 + dz * 7) % 19 == 0) {
                        setBlockFast(world, x, cy + 1, z, Material.PINK_PETALS);
                    }
                } else if (Math.abs(dz) == 5) {
                    // Dark oak timber border
                    setBlockFast(world, x, cy, z, Material.STRIPPED_DARK_OAK_LOG);
                } else if (Math.abs(dz) >= 6 && Math.abs(dz) <= 14) {
                    // Green moss garden banks flanking the promenade
                    int seed = Math.abs(dx * 31 + dz * 17);
                    Material gardenMat = (seed % 5 == 0) ? Material.FLOWERING_AZALEA
                            : ((seed % 3 == 0) ? Material.MOSS_CARPET : Material.MOSS_BLOCK);

                    setBlockFast(world, x, cy, z, Material.MOSS_BLOCK);
                    if (gardenMat != Material.MOSS_BLOCK) {
                        setBlockFast(world, x, cy + 1, z, gardenMat);
                    }

                    // Bamboo clusters along the garden banks
                    if (seed % 17 == 0 && Math.abs(dz) >= 9) {
                        for (int bh = 1; bh <= 3 + (seed % 3); bh++) {
                            setBlockFast(world, x, cy + bh, z, Material.BAMBOO);
                        }
                    }

                    // Traditional Japanese stone lanterns every 16 blocks
                    if (dx % 16 == 0 && Math.abs(dz) == 8) {
                        setBlockFast(world, x, cy + 1, z, Material.STONE_BRICK_WALL);
                        setBlockFast(world, x, cy + 2, z, Material.CHISELED_STONE_BRICKS);
                        setBlockFast(world, x, cy + 3, z, Material.LANTERN);
                    }
                } else {
                    // Outer plot access curb at dz = +-15
                    setBlockFast(world, x, cy, z, Material.SPRUCE_PLANKS);
                    setBlockFast(world, x, cy + 1, z, Material.STONE_BRICK_SLAB);
                }
            }

            // Repeating Japanese Torii Arches across the promenade every 64 blocks
            if ((dx % 64 == 0) && Math.abs(dx) <= 200 && dx != 0) {
                buildPromenadeToriiArch(world, cx + dx, cy, cz);
            }
        }
    }

    /**
     * Builds a decorative Japanese Torii wooden arch spanning the promenade at the given X.
     */
    private void buildPromenadeToriiArch(@NotNull World world, int x, int cy, int cz) {
        for (int side : new int[]{-6, 6}) {
            int z = cz + side;
            for (int h = 1; h <= 7; h++) {
                setBlockFast(world, x, cy + h, z, Material.DARK_OAK_LOG);
            }
            setBlockFast(world, x, cy + 8, z, Material.SMOOTH_QUARTZ);
        }
        // Crossbeams
        for (int dz = -7; dz <= 7; dz++) {
            int z = cz + dz;
            setBlockFast(world, x, cy + 7, z, Material.DARK_OAK_SLAB);
            setBlockFast(world, x, cy + 9, z, Material.DARK_OAK_PLANKS);
        }
        // Upturned eave ends
        setBlockFast(world, x, cy + 10, cz - 8, Material.DARK_OAK_STAIRS);
        setBlockFast(world, x, cy + 10, cz + 8, Material.DARK_OAK_STAIRS);
        // Hanging lanterns
        setBlockFast(world, x, cy + 6, cz - 3, Material.LANTERN);
        setBlockFast(world, x, cy + 6, cz + 3, Material.LANTERN);
    }

    /**
     * Central Japanese Garden: Koi pond, arched moon bridge, tea house, waterfall & rock garden.
     */
    private void generateCentralGarden(@NotNull World world, int cx, int cy, int cz) {
        // 1. Koi Pond: organic basin spanning dx in [-26, 26], dz in [-12, 12]
        for (int dx = -26; dx <= 26; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                int x = cx + dx;
                int z = cz + dz;

                // Oval pond shape with organic variations
                double normX = dx / 24.0;
                double normZ = dz / 10.0;
                double dist = normX * normX + normZ * normZ;

                if (dist <= 1.0) {
                    // Sunken pond bed
                    setBlockFast(world, x, cy - 2, z, (Math.abs(dx * dz) % 7 == 0) ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                    setBlockFast(world, x, cy - 1, z, Material.WATER);
                    setBlockFast(world, x, cy, z, Material.WATER);

                    // Lily pads on water surface
                    if ((dx * 17 + dz * 13) % 23 == 0) {
                        setBlockFast(world, x, cy + 1, z, Material.LILY_PAD);
                    }
                } else if (dist <= 1.25) {
                    // Natural pond bank: smooth stone, mossy cobblestone & moss
                    int bSeed = Math.abs(dx * 19 + dz * 23);
                    Material bankMat = (bSeed % 4 == 0) ? Material.MOSSY_COBBLESTONE
                            : ((bSeed % 3 == 0) ? Material.MOSS_BLOCK : Material.SMOOTH_STONE);

                    setBlockFast(world, x, cy, z, bankMat);
                    if (bSeed % 5 == 0) {
                        setBlockFast(world, x, cy + 1, z, Material.PINK_PETALS);
                    }
                }
            }
        }

        // 2. Traditional Japanese Arched Moon Bridge spanning the pond at X = cx
        for (int dz = -12; dz <= 12; dz++) {
            int z = cz + dz;
            int archH = (int) Math.round(2.5 * (1.0 - (dz / 12.0) * (dz / 12.0)));
            int bridgeY = cy + 1 + archH;

            // Bridge deck (Spruce planks & Dark Oak slab trim)
            for (int dx = -2; dx <= 2; dx++) {
                int x = cx + dx;
                setBlockFast(world, x, bridgeY, z, (Math.abs(dx) == 2) ? Material.DARK_OAK_SLAB : Material.SPRUCE_SLAB);
                // Clear clearance above
                setBlockFast(world, x, bridgeY + 1, z, Material.AIR);
                setBlockFast(world, x, bridgeY + 2, z, Material.AIR);
            }

            // Cherry fence balustrades with lanterns at bridge ends and crest
            setBlockFast(world, cx - 2, bridgeY + 1, z, Material.CHERRY_FENCE);
            setBlockFast(world, cx + 2, bridgeY + 1, z, Material.CHERRY_FENCE);

            if (dz == 0 || Math.abs(dz) == 10) {
                setBlockFast(world, cx - 2, bridgeY + 2, z, Material.LANTERN);
                setBlockFast(world, cx + 2, bridgeY + 2, z, Material.LANTERN);
            }
        }

        // 3. Japanese Tea House (dx in [10, 22], dz in [5, 14]) overlooking the pond
        for (int dx = 10; dx <= 22; dx++) {
            for (int dz = 5; dz <= 14; dz++) {
                int x = cx + dx;
                int z = cz + dz;

                // Raised wooden deck at Y = cy + 1
                setBlockFast(world, x, cy, z, Material.STONE_BRICKS);
                setBlockFast(world, x, cy + 1, z, Material.SPRUCE_PLANKS);

                // Corner posts
                boolean isCorner = (dx == 10 || dx == 22) && (dz == 5 || dz == 14);
                if (isCorner) {
                    for (int th = 2; th <= 6; th++) {
                        setBlockFast(world, x, cy + th, z, Material.DARK_OAK_LOG);
                    }
                } else if ((dx == 10 || dx == 22 || dz == 14) && !(dx == 16 && dz == 5)) {
                    // Low quartz walls with paper screen windows
                    setBlockFast(world, x, cy + 2, z, Material.SMOOTH_QUARTZ);
                    setBlockFast(world, x, cy + 3, z, Material.WHITE_STAINED_GLASS_PANE);
                    setBlockFast(world, x, cy + 4, z, Material.DARK_OAK_SLAB);
                }
            }
        }

        // Tea House Pagoda Curved Roof (Y = cy + 6 to cy + 8)
        for (int dx = 9; dx <= 23; dx++) {
            for (int dz = 4; dz <= 15; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                boolean isEave = (dx == 9 || dx == 23 || dz == 4 || dz == 15);
                setBlockFast(world, x, cy + 6, z, isEave ? Material.DARK_OAK_STAIRS : Material.DARK_OAK_PLANKS);
                if (isEave) {
                    setBlockFast(world, x, cy + 7, z, Material.DARK_OAK_SLAB);
                }
            }
        }
        // Tea house hanging lanterns at corners
        setBlockFast(world, cx + 9, cy + 5, cz + 4, Material.LANTERN);
        setBlockFast(world, cx + 23, cy + 5, cz + 4, Material.LANTERN);
        setBlockFast(world, cx + 9, cy + 5, cz + 15, Material.LANTERN);
        setBlockFast(world, cx + 23, cy + 5, cz + 15, Material.LANTERN);

        // 4. Waterfall & Rock Garden on North pond bank (dx in [-20, -12], dz in [-12, -7])
        for (int dx = -20; dx <= -12; dx++) {
            for (int dz = -12; dz <= -7; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int rH = (Math.abs(dx + 16) + Math.abs(dz + 9));
                int rockTop = Math.max(1, 4 - rH);

                for (int rh = 1; rh <= rockTop; rh++) {
                    setBlockFast(world, x, cy + rh, z, (rh % 2 == 0) ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
                }
            }
        }
        // Waterfall cascade from rock peak
        setBlockFast(world, cx - 16, cy + 4, cz - 10, Material.WATER);
    }

    /**
     * Generates all 16 crop plots (64x64 blocks each = 4,096 blocks).
     * Plot boundaries and farming functionality are strictly preserved.
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
                        boolean isFacingPromenade = isNorthRow ? (z == maxZ) : (z == minZ);
                        boolean isGateOpening = isFacingPromenade && Math.abs(x - pcx) <= 3;
                        boolean isGatePillar = isFacingPromenade && Math.abs(x - pcx) == 4;

                        if (isGateOpening) {
                            // Smooth stone ramp threshold
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.SMOOTH_STONE);
                        } else if (isGatePillar) {
                            // Traditional Torii gate pillars
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.DARK_OAK_LOG);
                            setBlockFast(world, x, cy + 1, z, Material.DARK_OAK_LOG);
                            setBlockFast(world, x, cy + 2, z, Material.DARK_OAK_LOG);
                            setBlockFast(world, x, cy + 3, z, Material.DARK_OAK_LOG);
                            setBlockFast(world, x, cy + 4, z, Material.LANTERN);
                        } else {
                            // Clean Japanese plot curb (spruce with stone brick slab cap)
                            setBlockFast(world, x, cy - 1, z, Material.STONE_BRICKS);
                            setBlockFast(world, x, cy, z, Material.SPRUCE_PLANKS);
                            setBlockFast(world, x, cy + 1, z, Material.STONE_BRICK_SLAB);
                        }
                    } else {
                        // Interior Farmland & Irrigation Grid
                        setBlockFast(world, x, cy - 1, z, Material.DIRT);

                        // 100% moisture irrigation grid every 8 blocks with sea lantern illumination
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

                            // Pre-plant starter Plot 1 with lush golden wheat
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

            // Gatehouse Torii lintel across the plot entrance
            int gateZ = isNorthRow ? maxZ : minZ;
            for (int gx = pcx - 4; gx <= pcx + 4; gx++) {
                setBlockFast(world, gx, cy + 4, gateZ, Material.DARK_OAK_SLAB);
            }
            setBlockFast(world, pcx, cy + 3, gateZ, Material.LANTERN);
        }
    }

    /**
     * Fully enclosing Japanese exterior walls (White Quartz, Dark Timber, Stained Glass).
     * Eliminates all open void gaps and seals the 520x168 building perimeter.
     */
    private void generateEnclosingJapaneseWalls(@NotNull World world, int cx, int cy, int cz) {
        // 1. North Exterior Wall (Z = cz - 84, dx in [-260, 259])
        int northZ = cz - 84;
        for (int dx = -260; dx <= 259; dx++) {
            int x = cx + dx;
            setBlockFast(world, x, cy - 1, northZ, Material.STONE_BRICKS);
            setBlockFast(world, x, cy, northZ, Material.POLISHED_DEEPSLATE);
            setBlockFast(world, x, cy + 1, northZ, Material.POLISHED_DEEPSLATE);

            boolean isPillar = (dx % 8 == 0);
            for (int wy = cy + 2; wy <= cy + 7; wy++) {
                Material wMat = isPillar ? Material.DARK_OAK_LOG : Material.LIGHT_GRAY_STAINED_GLASS;
                setBlockFast(world, x, wy, northZ, wMat);
            }
            setBlockFast(world, x, cy + 8, northZ, Material.SMOOTH_QUARTZ);
            setBlockFast(world, x, cy + 9, northZ, Material.DARK_OAK_STAIRS); // Eave trim
        }

        // 2. South Exterior Wall (Z = cz + 83, dx in [-260, 259])
        int southZ = cz + 83;
        for (int dx = -260; dx <= 259; dx++) {
            int x = cx + dx;
            setBlockFast(world, x, cy - 1, southZ, Material.STONE_BRICKS);
            setBlockFast(world, x, cy, southZ, Material.POLISHED_DEEPSLATE);
            setBlockFast(world, x, cy + 1, southZ, Material.POLISHED_DEEPSLATE);

            boolean isPillar = (dx % 8 == 0);
            for (int wy = cy + 2; wy <= cy + 7; wy++) {
                Material wMat = isPillar ? Material.DARK_OAK_LOG : Material.LIGHT_GRAY_STAINED_GLASS;
                setBlockFast(world, x, wy, southZ, wMat);
            }
            setBlockFast(world, x, cy + 8, southZ, Material.SMOOTH_QUARTZ);
            setBlockFast(world, x, cy + 9, southZ, Material.DARK_OAK_STAIRS);
        }

        // 3. West Exterior Gable Wall (X = cx - 260, dz in [-84, 83])
        int westX = cx - 260;
        buildJapaneseGableWall(world, westX, cy, cz);

        // 4. East Exterior Gable Wall (X = cx + 259, dz in [-84, 83])
        int eastX = cx + 259;
        buildJapaneseGableWall(world, eastX, cy, cz);
    }

    /**
     * Builds an enclosed Japanese timber & stained glass gable wall with pagoda curves.
     */
    private void buildJapaneseGableWall(@NotNull World world, int wx, int cy, int cz) {
        for (int dz = -84; dz <= 83; dz++) {
            int z = cz + dz;
            int vaultTop = getRoofVaultHeight(cy, dz);

            for (int wy = cy; wy <= vaultTop; wy++) {
                boolean isTimber = (wy <= cy + 1) || (wy == vaultTop) || (dz % 8 == 0);
                Material mat = isTimber ? Material.DARK_OAK_LOG : Material.LIGHT_GRAY_STAINED_GLASS;
                setBlockFast(world, wx, wy, z, mat);
            }
        }
    }

    /**
     * West Arrival Grand Torii Gate and viewing terrace.
     */
    private void generateWestToriiGate(@NotNull World world, int cx, int cy, int cz) {
        int x = cx - 248;

        // Arrival Dais
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                setBlockFast(world, x + dx, cy, cz + dz, Material.SPRUCE_PLANKS);
                setBlockFast(world, x + dx, cy + 1, cz + dz, (Math.abs(dx) == 4 || Math.abs(dz) == 6) ? Material.SMOOTH_STONE_SLAB : Material.SMOOTH_QUARTZ);
            }
        }

        // Grand Torii Gate Pillars
        for (int side : new int[]{-4, 4}) {
            int z = cz + side;
            for (int h = 2; h <= 9; h++) {
                setBlockFast(world, x, cy + h, z, Material.DARK_OAK_LOG);
            }
            setBlockFast(world, x, cy + 10, z, Material.SMOOTH_QUARTZ);
        }

        // Torii Crossbeams
        for (int dz = -6; dz <= 6; dz++) {
            int z = cz + dz;
            setBlockFast(world, x, cy + 8, z, Material.DARK_OAK_SLAB);
            setBlockFast(world, x, cy + 10, z, Material.DARK_OAK_PLANKS);
        }
        setBlockFast(world, x, cy + 11, cz - 7, Material.DARK_OAK_STAIRS);
        setBlockFast(world, x, cy + 11, cz + 7, Material.DARK_OAK_STAIRS);
        setBlockFast(world, x, cy + 7, cz - 2, Material.LANTERN);
        setBlockFast(world, x, cy + 7, cz + 2, Material.LANTERN);
    }

    /**
     * East Sacred Sakura Shrine of Elarion with ancient weeping cherry tree & Divine Offering Altar.
     */
    private void generateEastSacredShrine(@NotNull World world, int cx, int cy, int cz) {
        int x = cx + 248;

        // Sacred Shrine Platform
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                setBlockFast(world, x + dx, cy, cz + dz, Material.STONE_BRICKS);
                setBlockFast(world, x + dx, cy + 1, cz + dz, Material.SMOOTH_QUARTZ);
            }
        }

        // Shrine Pagoda Pavilion Columns
        for (int dx : new int[]{-3, 3}) {
            for (int dz : new int[]{-4, 4}) {
                for (int h = 2; h <= 6; h++) {
                    setBlockFast(world, x + dx, cy + h, cz + dz, Material.SPRUCE_LOG);
                }
            }
        }

        // Pagoda Shrine Roof
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                boolean isEave = (Math.abs(dx) == 4 || Math.abs(dz) == 5);
                setBlockFast(world, x + dx, cy + 7, cz + dz, isEave ? Material.DARK_OAK_STAIRS : Material.DARK_OAK_PLANKS);
            }
        }

        // Divine Offering Altar Table
        setBlockFast(world, x, cy + 2, cz, Material.CHISELED_QUARTZ_BLOCK);
        setBlockFast(world, x, cy + 3, cz, Material.LECTERN);
        setBlockFast(world, x, cy + 2, cz - 1, Material.CRYING_OBSIDIAN);
        setBlockFast(world, x, cy + 3, cz - 1, Material.AMETHYST_CLUSTER);
        setBlockFast(world, x, cy + 2, cz + 1, Material.CRYING_OBSIDIAN);
        setBlockFast(world, x, cy + 3, cz + 1, Material.AMETHYST_CLUSTER);

        // Water basin (tsukubai)
        setBlockFast(world, x - 2, cy + 2, cz + 2, Material.SMOOTH_STONE);
        setBlockFast(world, x - 2, cy + 2, cz + 2, Material.WATER);
    }

    /**
     * Generates custom-built Sakura trees and lush cherry blossom botanical landscaping.
     */
    private void generateSakuraTreesAndFlora(@NotNull World world, int cx, int cy, int cz) {
        // 1. Two Central Sakura Trees framing the Koi Pond
        buildSakuraTree(world, cx - 12, cy, cz + 6, 8, 4);
        buildSakuraTree(world, cx + 12, cy, cz - 6, 8, 4);

        // 2. Sakura trees flanking West Torii Gate
        buildSakuraTree(world, cx - 240, cy, cz - 10, 7, 4);
        buildSakuraTree(world, cx - 240, cy, cz + 10, 7, 4);

        // 3. Giant Ancient Weeping Sakura Tree behind East Shrine
        buildSakuraTree(world, cx + 254, cy, cz, 11, 6);

        // 4. Perimeter Sakura trees along North and South garden borders
        int[] treeXs = {-192, -128, -64, 64, 128, 192};
        for (int tx : treeXs) {
            buildSakuraTree(world, cx + tx, cy, cz - 12, 7, 3);
            buildSakuraTree(world, cx + tx, cy, cz + 12, 7, 3);
        }
    }

    /**
     * Deterministically builds a custom Sakura tree with twisting dark oak trunk,
     * blossoming cherry leaves canopy, pink wool core, and fallen petals.
     */
    private void buildSakuraTree(@NotNull World world, int x, int cy, int z, int height, int radius) {
        // Natural curved trunk
        for (int h = 1; h <= height; h++) {
            int ox = (h > 4) ? 1 : 0;
            int oz = (h > 6) ? 1 : 0;
            setBlockFast(world, x + ox, cy + h, z + oz, Material.DARK_OAK_LOG);
        }

        int topX = x + ((height > 4) ? 1 : 0);
        int topY = cy + height;
        int topZ = z + ((height > 6) ? 1 : 0);

        // Radial blossoming canopy
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    double distSq = dx * dx + dz * dz + dy * dy * 2.0;
                    if (distSq <= radius * radius) {
                        int lx = topX + dx;
                        int ly = topY + dy;
                        int lz = topZ + dz;

                        // Only replace air
                        Block b = world.getBlockAt(lx, ly, lz);
                        if (b.getType() == Material.AIR) {
                            boolean isCore = (distSq <= 2.0);
                            Material leafMat = isCore ? Material.PINK_WOOL : Material.CHERRY_LEAVES;
                            setBlockFast(world, lx, ly, lz, leafMat);
                        }
                    }
                }
            }
        }

        // Drooping hanging cherry blossom branches
        for (int dx : new int[]{-radius + 1, radius - 1}) {
            for (int dz : new int[]{-radius + 1, radius - 1}) {
                setBlockFast(world, topX + dx, topY - 2, topZ + dz, Material.CHERRY_LEAVES);
                setBlockFast(world, topX + dx, topY - 3, topZ + dz, Material.CHERRY_LEAVES);
            }
        }

        // Ground moss and scattered fallen pink petals beneath the tree
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= (radius + 1) * (radius + 1)) {
                    int gx = topX + dx;
                    int gz = topZ + dz;
                    Block ground = world.getBlockAt(gx, cy, gz);
                    if (ground.getType() == Material.MOSS_BLOCK || ground.getType() == Material.STONE_BRICKS || ground.getType() == Material.DIRT) {
                        if ((dx * 7 + dz * 11) % 5 == 0) {
                            setBlockFast(world, gx, cy + 1, gz, Material.PINK_PETALS);
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates the gigantic transparent glass canopy and central pagoda atrium.
     * Implements zero-gap vertical filling so all curves connect solidly without holes.
     */
    private void generateGlassCanopy(@NotNull World world, int cx, int cy, int cz) {
        // 1. North & South Wing Curved Glass Barrel Vaults (dx in [-260, 259], dz in [-84, 83])
        for (int dx = -260; dx <= 259; dx++) {
            boolean isJapaneseFrame = (dx % 16 == 0);

            for (int dz = -84; dz <= 83; dz++) {
                // Central glass atrium is handled separately
                if (Math.abs(dx) <= 40 && Math.abs(dz) <= 24) {
                    continue;
                }

                int yCurr = getRoofVaultHeight(cy, dz);
                int yNext = (dz < -48 || (dz >= 0 && dz < 48))
                        ? getRoofVaultHeight(cy, dz + 1)
                        : getRoofVaultHeight(cy, dz - 1);

                int minY = Math.min(yCurr, yNext);
                int maxY = Math.max(yCurr, yNext);

                boolean isRidgeSpine = (dz == -48 || dz == 48);
                Material frameMat = isJapaneseFrame ? Material.DARK_OAK_WOOD
                        : (isRidgeSpine ? Material.DARK_OAK_SLAB
                        : (((dx * 5 + dz * 3) % 17 == 0) ? Material.PINK_STAINED_GLASS : Material.GLASS));

                for (int y = minY; y <= maxY; y++) {
                    setBlockFast(world, cx + dx, y, cz + dz, frameMat);
                }
            }
        }

        // 2. Central Pagoda Glass Atrium (dx in [-40, 40], dz in [-24, 24])
        for (int dx = -40; dx <= 40; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                double normX = dx / 40.0;
                double normZ = dz / 24.0;
                double distSq = normX * normX + normZ * normZ;

                if (distSq <= 1.0) {
                    double normDist = Math.sqrt(distSq);
                    int atriumH = (int) Math.round(18.0 * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist)));
                    int roofY = cy + 14 + atriumH; // Peak at Y = cy + 32 (132)

                    boolean isRib = (Math.abs(dx) % 8 == 0) || (Math.abs(dz) % 8 == 0) || (distSq >= 0.92);
                    Material mat = isRib ? Material.DARK_OAK_WOOD
                            : (((dx + dz) % 7 == 0) ? Material.PINK_STAINED_GLASS : Material.GLASS);

                    setBlockFast(world, cx + dx, roofY, cz + dz, mat);

                    // Connect vertically to surrounding vault at perimeter
                    if (distSq >= 0.88) {
                        int baseH = getRoofVaultHeight(cy, dz);
                        for (int wy = baseH; wy < roofY; wy++) {
                            setBlockFast(world, cx + dx, wy, cz + dz, isRib ? Material.DARK_OAK_WOOD : Material.GLASS);
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes the mathematical curved roof height for any Z block across the 168-block width.
     */
    private int getRoofVaultHeight(int cy, int dz) {
        if (dz >= -84 && dz <= -17) {
            // North Wing Vault centered at Z = -48
            double deltaZ = Math.abs(dz - (-48));
            double normZ = Math.min(1.0, deltaZ / 36.0);
            return cy + 8 + (int) Math.round(14.0 * Math.sqrt(Math.max(0.0, 1.0 - normZ * normZ)));
        } else if (dz >= 16 && dz <= 83) {
            // South Wing Vault centered at Z = 48
            double deltaZ = Math.abs(dz - 48);
            double normZ = Math.min(1.0, deltaZ / 36.0);
            return cy + 8 + (int) Math.round(14.0 * Math.sqrt(Math.max(0.0, 1.0 - normZ * normZ)));
        } else {
            // Boulevard Nave Vault connecting North and South wings
            double deltaZ = Math.abs(dz);
            double normZ = Math.min(1.0, deltaZ / 16.0);
            return cy + 14 + (int) Math.round(6.0 * Math.sqrt(Math.max(0.0, 1.0 - normZ * normZ)));
        }
    }

    private void setBlockFast(@NotNull World world, int x, int y, int z, @NotNull Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }
}
