package com.skyrealms.islands.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Generates the classic starter SkyBlock platform with chest, tree, and bedrock anchor.
 */
public final class StarterIslandGenerator {

    /**
     * Pastes the starter island on the main server thread.
     */
    @NotNull
    public static Location generateStarterIsland(@NotNull World world, int centerX, int centerY, int centerZ) {
        // 1. Build circular platform with radius 3
        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius + 1) {
                    int x = centerX + dx;
                    int z = centerZ + dz;

                    // Top grass
                    world.getBlockAt(x, centerY, z).setType(Material.GRASS_BLOCK, false);
                    // Dirt layer
                    world.getBlockAt(x, centerY - 1, z).setType(Material.DIRT, false);
                    world.getBlockAt(x, centerY - 2, z).setType(Material.DIRT, false);
                }
            }
        }

        // 2. Bedrock anchor at the bottom center
        world.getBlockAt(centerX, centerY - 3, centerZ).setType(Material.BEDROCK, false);

        // 3. Place small starter tree at (centerX + 2, centerY + 1, centerZ + 2)
        int treeX = centerX + 2;
        int treeZ = centerZ + 2;
        int trunkBase = centerY + 1;
        for (int ty = 0; ty < 4; ty++) {
            world.getBlockAt(treeX, trunkBase + ty, treeZ).setType(Material.OAK_LOG, false);
        }

        // Leaves
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                for (int ly = 2; ly <= 4; ly++) {
                    if (lx == 0 && lz == 0 && ly < 4) continue;
                    Block leaf = world.getBlockAt(treeX + lx, trunkBase + ly, treeZ + lz);
                    if (leaf.getType() == Material.AIR) {
                        leaf.setType(Material.OAK_LEAVES, false);
                    }
                }
            }
        }
        world.getBlockAt(treeX, trunkBase + 4, treeZ).setType(Material.OAK_LEAVES, false);

        // 4. Place starter chest at (centerX - 2, centerY + 1, centerZ)
        Block chestBlock = world.getBlockAt(centerX - 2, centerY + 1, centerZ);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            inv.addItem(new ItemStack(Material.ICE, 2));
            inv.addItem(new ItemStack(Material.LAVA_BUCKET, 1));
            inv.addItem(new ItemStack(Material.OAK_SAPLING, 1));
            inv.addItem(new ItemStack(Material.BONE_MEAL, 3));
            inv.addItem(new ItemStack(Material.BREAD, 16));
            inv.addItem(new ItemStack(Material.WHEAT_SEEDS, 5));
            inv.addItem(new ItemStack(Material.SUGAR_CANE, 1));
            inv.addItem(new ItemStack(Material.CACTUS, 1));
        }

        // Safe player spawn location on the platform
        return new Location(world, centerX + 0.5, centerY + 1.0, centerZ + 0.5, 0.0f, 0.0f);
    }
}
