package com.skyrealms.islands.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.PinkPetals;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Generates an artistic, handcrafted Celestial Cherry Blossom floating island.
 * Features an inverted floating rock underside, cherry tree canopy, pink petal ground cover,
 * moss accents, water spring, and custom starter supplies.
 */
public final class StarterIslandGenerator {

    private StarterIslandGenerator() {}

    /**
     * Pastes the handcrafted Cherry Blossom starter island on the main server thread.
     */
    @NotNull
    public static Location generateStarterIsland(@NotNull World world, int centerX, int centerY, int centerZ) {
        Random rand = new Random();

        // 1. Build Inverted Floating Rock Underside (Layer by layer down to bedrock anchor)
        // Layer Y: Surface (radius 5)
        buildRockLayer(world, centerX, centerY, centerZ, 5.2, Material.GRASS_BLOCK, Material.MOSS_BLOCK, 0.25f);
        // Layer Y - 1: Subsurface dirt & moss (radius 4.6)
        buildRockLayer(world, centerX, centerY - 1, centerZ, 4.6, Material.DIRT, Material.ROOTED_DIRT, 0.40f);
        // Layer Y - 2: Dirt & stone transition (radius 3.8)
        buildRockLayer(world, centerX, centerY - 2, centerZ, 3.8, Material.DIRT, Material.STONE, 0.35f);
        // Layer Y - 3: Cobblestone & andesite (radius 2.9)
        buildRockLayer(world, centerX, centerY - 3, centerZ, 2.9, Material.COBBLESTONE, Material.ANDESITE, 0.50f);
        // Layer Y - 4: Deepslate & tuff (radius 2.0)
        buildRockLayer(world, centerX, centerY - 4, centerZ, 2.0, Material.COBBLED_DEEPSLATE, Material.TUFF, 0.50f);
        // Layer Y - 5: Deepslate stalactite tip (radius 1.2)
        buildRockLayer(world, centerX, centerY - 5, centerZ, 1.2, Material.DEEPSLATE, Material.POLISHED_DEEPSLATE, 0.30f);

        // Bedrock anchor at the lowest point
        world.getBlockAt(centerX, centerY - 6, centerZ).setType(Material.BEDROCK, false);

        // Hanging roots dangling underneath into the void
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (rand.nextFloat() < 0.35f) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    // Find lowest solid block
                    for (int y = centerY - 2; y >= centerY - 6; y--) {
                        Block b = world.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) {
                            Block under = world.getBlockAt(x, y - 1, z);
                            if (under.getType().isAir()) {
                                under.setType(Material.HANGING_ROOTS, false);
                            }
                            break;
                        }
                    }
                }
            }
        }

        // 2. Stepping Stone Pathway from spawn towards chest and tree
        world.getBlockAt(centerX, centerY, centerZ).setType(Material.POLISHED_ANDESITE, false);
        world.getBlockAt(centerX + 1, centerY, centerZ).setType(Material.CHERRY_PLANKS, false);
        world.getBlockAt(centerX - 1, centerY, centerZ).setType(Material.CHERRY_PLANKS, false);
        world.getBlockAt(centerX, centerY, centerZ + 1).setType(Material.CHERRY_SLAB, false);

        // 3. Mini Water Spring Basin
        world.getBlockAt(centerX - 3, centerY, centerZ - 2).setType(Material.MOSSY_COBBLESTONE, false);
        world.getBlockAt(centerX - 2, centerY, centerZ - 2).setType(Material.MOSSY_COBBLESTONE, false);
        world.getBlockAt(centerX - 3, centerY, centerZ - 1).setType(Material.MOSSY_COBBLESTONE, false);
        world.getBlockAt(centerX - 2, centerY, centerZ - 1).setType(Material.WATER, false);
        world.getBlockAt(centerX - 2, centerY + 1, centerZ - 2).setType(Material.MOSS_CARPET, false);

        // 4. Ground Foliage: Scattered Pink Petals & Moss Carpets
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue; // Keep center clear
                int x = centerX + dx;
                int z = centerZ + dz;
                Block surface = world.getBlockAt(x, centerY, z);
                Block above = world.getBlockAt(x, centerY + 1, z);

                if (surface.getType() == Material.GRASS_BLOCK && above.getType().isAir()) {
                    float roll = rand.nextFloat();
                    if (roll < 0.25f) {
                        above.setType(Material.PINK_PETALS, false);
                        if (above.getBlockData() instanceof PinkPetals petals) {
                            petals.setFlowerAmount(rand.nextInt(3) + 1);
                            above.setBlockData(petals, false);
                        }
                    } else if (roll < 0.35f) {
                        above.setType(Material.MOSS_CARPET, false);
                    }
                }
            }
        }

        // 5. Build Handcrafted Cherry Blossom Tree
        int treeBaseX = centerX + 2;
        int treeBaseZ = centerX_Z(centerZ, 2);
        int baseHeadY = centerY + 1;

        // Tree root flares
        world.getBlockAt(treeBaseX - 1, baseHeadY, treeBaseZ).setType(Material.CHERRY_WOOD, false);
        world.getBlockAt(treeBaseX, baseHeadY, treeBaseZ - 1).setType(Material.CHERRY_WOOD, false);

        // Organic curved trunk
        world.getBlockAt(treeBaseX, baseHeadY, treeBaseZ).setType(Material.CHERRY_LOG, false);
        world.getBlockAt(treeBaseX, baseHeadY + 1, treeBaseZ).setType(Material.CHERRY_LOG, false);
        world.getBlockAt(treeBaseX + 1, baseHeadY + 2, treeBaseZ).setType(Material.CHERRY_LOG, false);
        world.getBlockAt(treeBaseX + 1, baseHeadY + 3, treeBaseZ + 1).setType(Material.CHERRY_LOG, false);
        world.getBlockAt(treeBaseX + 1, baseHeadY + 4, treeBaseZ + 1).setType(Material.CHERRY_LOG, false);

        // Cherry Blossom Leaves Canopy (Lush dome with drooping petals)
        int canopyY = baseHeadY + 4;
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                for (int oy = -1; oy <= 2; oy++) {
                    int distSq = ox * ox + oz * oz + oy * oy;
                    if (distSq <= 6) {
                        Block leaf = world.getBlockAt(treeBaseX + 1 + ox, canopyY + oy, treeBaseZ + 1 + oz);
                        if (leaf.getType().isAir()) {
                            leaf.setType(Material.CHERRY_LEAVES, false);
                        }
                    }
                }
            }
        }

        // Drooping leaf tendrils
        world.getBlockAt(treeBaseX + 3, canopyY - 2, treeBaseZ + 1).setType(Material.CHERRY_LEAVES, false);
        world.getBlockAt(treeBaseX - 1, canopyY - 2, treeBaseZ + 2).setType(Material.CHERRY_LEAVES, false);

        // Hanging Blossom Lantern on Cherry Fence
        world.getBlockAt(treeBaseX - 1, canopyY - 1, treeBaseZ + 1).setType(Material.CHERRY_FENCE, false);
        world.getBlockAt(treeBaseX - 1, canopyY - 2, treeBaseZ + 1).setType(Material.LANTERN, false);

        // 6. Cherry Planks Pedestal & Starter Supply Chest
        Block pedestal = world.getBlockAt(centerX - 2, centerY, centerZ + 1);
        pedestal.setType(Material.CHERRY_PLANKS, false);

        Block chestBlock = world.getBlockAt(centerX - 2, centerY + 1, centerZ + 1);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            inv.addItem(new ItemStack(Material.ICE, 2));
            inv.addItem(new ItemStack(Material.LAVA_BUCKET, 1));
            inv.addItem(new ItemStack(Material.CHERRY_SAPLING, 4));
            inv.addItem(new ItemStack(Material.PINK_PETALS, 16));
            inv.addItem(new ItemStack(Material.CHERRY_PLANKS, 16));
            inv.addItem(new ItemStack(Material.BONE_MEAL, 6));
            inv.addItem(new ItemStack(Material.BREAD, 16));
            inv.addItem(new ItemStack(Material.WHEAT_SEEDS, 5));
            inv.addItem(new ItemStack(Material.SUGAR_CANE, 1));
            inv.addItem(new ItemStack(Material.BAMBOO, 2));
            inv.addItem(new ItemStack(Material.CHERRY_BOAT, 1));
        }

        // 7. Safe Player Spawn Location facing the glowing Cherry Tree
        return new Location(world, centerX + 0.5, centerY + 1.0, centerZ + 0.5, 45.0f, 0.0f);
    }

    private static int centerX_Z(int center, int offset) {
        return center + offset;
    }

    private static void buildRockLayer(
            @NotNull World world,
            int cx,
            int y,
            int cz,
            double radius,
            @NotNull Material primary,
            @NotNull Material secondary,
            float secondaryChance
    ) {
        Random rand = new Random(cx * 31L + y * 17L + cz);
        int r = (int) Math.ceil(radius);
        double rSq = radius * radius;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= rSq) {
                    Material mat = (rand.nextFloat() < secondaryChance) ? secondary : primary;
                    world.getBlockAt(cx + dx, y, cz + dz).setType(mat, false);
                }
            }
        }
    }
}
