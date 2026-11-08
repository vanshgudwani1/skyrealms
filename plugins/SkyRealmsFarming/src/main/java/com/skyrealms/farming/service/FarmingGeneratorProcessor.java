package com.skyrealms.farming.service;

import com.skyrealms.farming.event.FarmingCropGenerateEvent;
import com.skyrealms.farming.event.FarmingCropMutationEvent;
import com.skyrealms.farming.event.FarmingGeneratorHarvestEvent;
import com.skyrealms.farming.model.*;
import com.skyrealms.farming.mutation.CropMutationEngine;
import com.skyrealms.farming.mutation.CropMutationType;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.platform.api.event.EventBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Random;

/**
 * Handles the harvest, drop evaluation, anti-duplication locking, and regeneration cycle.
 */
public final class FarmingGeneratorProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmingGeneratorProcessor.class);

    private final Plugin plugin;
    private final FarmingProgressionService progressionService;
    private final CropMutationEngine mutationEngine;
    private final FarmingRepository repository;
    private final EventBus eventBus;
    private final Random random = new Random();

    public FarmingGeneratorProcessor(
            @NotNull Plugin plugin,
            @NotNull FarmingProgressionService progressionService,
            @NotNull CropMutationEngine mutationEngine,
            @NotNull FarmingRepository repository,
            @NotNull EventBus eventBus
    ) {
        this.plugin = plugin;
        this.progressionService = progressionService;
        this.mutationEngine = mutationEngine;
        this.repository = repository;
        this.eventBus = eventBus;
    }

    /**
     * Processes a legitimate player harvest on an active generator crop node.
     */
    public boolean handleHarvest(
            @NotNull Player player,
            @NotNull FarmingGenerator generator,
            @NotNull CropNode node,
            @NotNull Block block
    ) {
        // 1. Check maturity if block is Ageable
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                player.sendMessage(Component.text("⚠ This generator crop is not fully grown yet!", NamedTextColor.YELLOW));
                return false;
            }
        }

        // 2. Concurrency lock: prevent double-harvesting in same tick
        if (!node.tryLockForHarvest()) {
            return false;
        }

        FarmingGeneratorTier tier = generator.tier();
        int yieldUpgradeLevel = generator.getUpgradeLevel(GeneratorUpgrade.KEY_YIELD);
        int xpBoostUpgradeLevel = generator.getUpgradeLevel(GeneratorUpgrade.KEY_XP_BOOST);

        // 3. Evaluate drops and yield
        int quantity = 1;
        if (yieldUpgradeLevel > 0 && random.nextDouble() < (yieldUpgradeLevel * 0.15)) {
            quantity += 1; // Bonus yield
        }

        // 4. Evaluate Crop Mutation
        Optional<CropMutationType> optMutation = mutationEngine.evaluateMutation(generator, random);
        ItemStack dropItem;
        double xpMultiplier = 1.0 + (xpBoostUpgradeLevel * 0.20);

        if (optMutation.isPresent()) {
            CropMutationType mutation = optMutation.get();
            quantity = (int) Math.round(quantity * mutation.dropMultiplier());
            dropItem = mutationEngine.createMutatedItem(tier.primaryDropMaterial(), mutation, quantity);
            xpMultiplier *= mutation.xpMultiplier();

            // Broadcast discovery and play effects
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 25, 0.4, 0.4, 0.4, 0.05);
            loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc, 20, 0.5, 0.5, 0.5, 0.02);
            player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);

            player.sendMessage(Component.text("✦ [RARE MUTATION] ", NamedTextColor.GOLD)
                    .append(Component.text("Your generator produced a " + mutation.displayName() + " " + tier.displayName() + "!", mutation.color())));

            eventBus.publishAsync(new FarmingCropMutationEvent(generator, player, tier.primaryDropMaterial(), mutation));

            // Record discovery in DB
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    repository.recordMutationDiscovery(player.getUniqueId(), mutation.name(), tier.primaryDropMaterial().name());
                } catch (SQLException e) {
                    LOGGER.warn("Failed to record mutation discovery: {}", e.getMessage());
                }
            });
        } else {
            dropItem = new ItemStack(tier.primaryDropMaterial(), quantity);
            Location loc = block.getLocation().add(0.5, 0.3, 0.5);
            loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc, 12, 0.3, 0.3, 0.3, 0.02);
            player.playSound(loc, Sound.BLOCK_CROP_BREAK, 1.0f, 1.2f);
        }

        // 5. Deliver drops safely (inventory or floor)
        giveOrDrop(player, dropItem);

        // Also give secondary seeds for wheat if applicable
        if (tier == FarmingGeneratorTier.TIER_1_WHEAT) {
            int seedCount = 1 + random.nextInt(2);
            giveOrDrop(player, new ItemStack(Material.WHEAT_SEEDS, seedCount));
        }

        // 6. Award Progression & Farming XP
        long awardedXp = Math.max(1, Math.round(tier.baseFarmingXp() * xpMultiplier));
        progressionService.awardXp(player, awardedXp);
        progressionService.recordCropHarvest(player, tier.primaryDropMaterial(), quantity);

        generator.incrementHarvests();
        eventBus.publishAsync(new FarmingGeneratorHarvestEvent(generator, node, player, awardedXp));

        // 7. Schedule Regeneration Loop
        int speedUpgradeLevel = generator.getUpgradeLevel(GeneratorUpgrade.KEY_SPEED);
        int regenDelayTicks = Math.max(5, 30 - (speedUpgradeLevel * 2));

        // Reset block to seedling
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable, false);
        } else {
            block.setType(Material.AIR, false);
        }

        scheduleRegeneration(generator, node, block, regenDelayTicks);
        return true;
    }

    /**
     * Safely schedules main thread block regeneration after delay ticks.
     */
    private void scheduleRegeneration(
            @NotNull FarmingGenerator generator,
            @NotNull CropNode node,
            @NotNull Block block,
            int delayTicks
    ) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // If chunk is not loaded or block is no longer part of generator, release lock and abort
                if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    node.releaseLock();
                    return;
                }

                FarmingGeneratorTier tier = generator.tier();
                Material targetMaterial = tier.primaryDropMaterial() == Material.WHEAT_SEEDS ? Material.WHEAT :
                        (tier == FarmingGeneratorTier.TIER_1_WHEAT ? Material.WHEAT :
                                (tier == FarmingGeneratorTier.TIER_2_ROOT ? Material.CARROTS :
                                        (tier == FarmingGeneratorTier.TIER_3_FRUIT ? Material.MELON :
                                                (tier == FarmingGeneratorTier.TIER_4_TROPICAL ? Material.SUGAR_CANE : Material.NETHER_WART))));

                FarmingCropGenerateEvent generateEvent = new FarmingCropGenerateEvent(generator, node, targetMaterial);
                eventBus.publishAsync(generateEvent);

                if (!generateEvent.isCancelled()) {
                    block.setType(targetMaterial, false);
                    BlockData data = block.getBlockData();
                    if (data instanceof Ageable ageable) {
                        ageable.setAge(ageable.getMaximumAge());
                        block.setBlockData(ageable, false);
                    }

                    Location loc = block.getLocation().add(0.5, 0.4, 0.5);
                    block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.2, 0.3, 0.0);
                }
            } finally {
                node.releaseLock();
            }
        }, delayTicks);
    }

    /**
     * Handles non-player disturbance (e.g. water flushing or piston pushes).
     * Prevents automated AFK farming without player participation.
     */
    public void handleIllegitimateDisturbance(@NotNull FarmingGenerator generator, @NotNull CropNode node, @NotNull Block block) {
        if (!node.tryLockForHarvest()) {
            return;
        }

        // Instantly replant seedling without giving drops or XP
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable, false);
        } else {
            block.setType(Material.AIR, false);
        }

        // Delayed regeneration
        scheduleRegeneration(generator, node, block, 40);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
    }
}
