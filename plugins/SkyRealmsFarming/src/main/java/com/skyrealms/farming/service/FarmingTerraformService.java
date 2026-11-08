package com.skyrealms.farming.service;

import com.skyrealms.farming.model.CropNode;
import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the staged Earth Bloom animation, sub-surface irrigation placement,
 * and automated 5x5 farmland creation.
 */
public final class FarmingTerraformService {

    private final Plugin plugin;
    private final FarmingHologramService hologramService;

    public FarmingTerraformService(@NotNull Plugin plugin, @NotNull FarmingHologramService hologramService) {
        this.plugin = plugin;
        this.hologramService = hologramService;
    }

    /**
     * Executes the sequential auto-terraforming sequence on the main server thread.
     */
    public void executeTerraformAnimation(
            @NotNull Player player,
            @NotNull FarmingGenerator generator,
            @NotNull Runnable onComplete
    ) {
        World world = Bukkit.getWorld(generator.worldName());
        if (world == null) return;

        int cx = generator.coreX();
        int cy = generator.coreY();
        int cz = generator.coreZ();
        Location coreLoc = new Location(world, cx + 0.5, cy + 0.5, cz + 0.5);

        // Phase 1: Vortex & Chime Fanfare
        world.playSound(coreLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        world.playSound(coreLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        world.spawnParticle(Particle.CHERRY_LEAVES, coreLoc, 40, 1.2, 0.8, 1.2, 0.05);

        // Phase 2 (Tick 2): Sub-surface Irrigation Water under Core
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block underCore = world.getBlockAt(cx, cy - 1, cz);
            underCore.setType(Material.WATER, false);
            world.spawnParticle(Particle.SPLASH, new Location(world, cx + 0.5, cy - 0.2, cz + 0.5), 15, 0.3, 0.1, 0.3, 0.1);
        }, 2L);

        // Phase 3 (Ticks 4 - 10): Outward Farmland Tilling Ripple
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tillRing(world, cx, cy, cz, 1);
            world.playSound(coreLoc, Sound.ITEM_HOE_TILL, 0.8f, 1.0f);
        }, 5L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tillRing(world, cx, cy, cz, 2);
            world.playSound(coreLoc, Sound.ITEM_HOE_TILL, 0.9f, 1.2f);
        }, 10L);

        // Phase 4 (Ticks 14 - 18): Plant & Bloom Crop Nodes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FarmingGeneratorTier tier = generator.tier();
            Material cropMat = getCropMaterialForTier(tier);

            for (CropNode node : generator.cropNodes()) {
                Block b = world.getBlockAt(node.x(), node.y(), node.z());
                b.setType(cropMat, false);
                BlockData bd = b.getBlockData();
                if (bd instanceof Ageable ageable) {
                    ageable.setAge(ageable.getMaximumAge());
                    b.setBlockData(ageable, false);
                }
                Location nodeLoc = new Location(world, node.x() + 0.5, node.y() + 0.4, node.z() + 0.5);
                world.spawnParticle(Particle.HAPPY_VILLAGER, nodeLoc, 6, 0.2, 0.2, 0.2, 0.0);
            }
            world.playSound(coreLoc, Sound.BLOCK_CROP_BREAK, 1.0f, 1.4f);
        }, 15L);

        // Phase 5 (Tick 22): Spawn DecentHologram & Complete
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            hologramService.createOrUpdateHologram(generator, "&a● READY TO HARVEST");
            world.spawnParticle(Particle.END_ROD, coreLoc.clone().add(0, 1.5, 0), 20, 0.4, 0.6, 0.4, 0.03);

            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
            player.sendMessage(Component.text("🌱 [Earth Bloom] 5x5 Farming Generator online!", NamedTextColor.GREEN, TextDecoration.BOLD));
            player.sendMessage(Component.text("✦ Automatic sub-surface irrigation placed.", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("✦ Animated DecentHologram active above Core.", NamedTextColor.AQUA));
            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));

            onComplete.run();
        }, 22L);
    }

    private void tillRing(World world, int cx, int cy, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                    Block ground = world.getBlockAt(cx + dx, cy - 1, cz + dz);
                    if (ground.getType() == Material.DIRT || ground.getType() == Material.GRASS_BLOCK
                            || ground.getType() == Material.COARSE_DIRT || ground.getType() == Material.ROOTED_DIRT) {
                        ground.setType(Material.FARMLAND, false);
                    }
                }
            }
        }
    }

    private Material getCropMaterialForTier(FarmingGeneratorTier tier) {
        return switch (tier) {
            case TIER_1_WHEAT -> Material.WHEAT;
            case TIER_2_ROOT -> Material.CARROTS;
            case TIER_3_FRUIT -> Material.MELON;
            case TIER_4_TROPICAL -> Material.SUGAR_CANE;
            case TIER_5_MYSTIC -> Material.NETHER_WART;
        };
    }
}
