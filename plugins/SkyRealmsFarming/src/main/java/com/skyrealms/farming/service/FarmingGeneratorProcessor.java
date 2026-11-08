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
 * Handles the harvest, drop evaluation, anti-duplication locking, combo streaks, and regeneration cycle.
 */
public final class FarmingGeneratorProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmingGeneratorProcessor.class);

    private final Plugin plugin;
    private final FarmingProgressionService progressionService;
    private final CropMutationEngine mutationEngine;
    private final FarmingRepository repository;
    private final EventBus eventBus;
    private final FarmingHologramService hologramService;
    private final OverclockManager overclockManager;
    private final HarvestComboTracker comboTracker;
    private final Random random = new Random();

    public FarmingGeneratorProcessor(
            @NotNull Plugin plugin,
            @NotNull FarmingProgressionService progressionService,
            @NotNull CropMutationEngine mutationEngine,
            @NotNull FarmingRepository repository,
            @NotNull EventBus eventBus,
            @NotNull FarmingHologramService hologramService,
            @NotNull OverclockManager overclockManager,
            @NotNull HarvestComboTracker comboTracker
    ) {
        this.plugin = plugin;
        this.progressionService = progressionService;
        this.mutationEngine = mutationEngine;
        this.repository = repository;
        this.eventBus = eventBus;
        this.hologramService = hologramService;
        this.overclockManager = overclockManager;
        this.comboTracker = comboTracker;
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

        // 3. Evaluate harvest combo streak
        double comboMult = comboTracker.registerHarvest(player);

        // 4. Evaluate drops and yield
        int quantity = 1;
        if (yieldUpgradeLevel > 0 && random.nextDouble() < (yieldUpgradeLevel * 0.15)) {
            quantity += 1;
        }
        if (comboMult >= 2.0 && random.nextDouble() < 0.25) {
            quantity += 1; // Combo bonus drop
        }

        // 5. Evaluate Overclock & Crop Mutation
        boolean overclocked = overclockManager.isOverclocked(generator);
        Optional<CropMutationType> optMutation = mutationEngine.evaluateMutation(generator, random);

        ItemStack dropItem;
        double xpMultiplier = (1.0 + (xpBoostUpgradeLevel * 0.20)) * comboMult;

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

        // 6. Deliver drops safely (inventory or floor)
        giveOrDrop(player, dropItem);

        // Also give secondary seeds for wheat if applicable
        if (tier == FarmingGeneratorTier.TIER_1_WHEAT) {
            int seedCount = 1 + random.nextInt(2);
            giveOrDrop(player, new ItemStack(Material.WHEAT_SEEDS, seedCount));
        }

        // 7. Award Progression & Farming XP
        long awardedXp = Math.max(1, Math.round(tier.baseFarmingXp() * xpMultiplier));
        progressionService.awardXp(player, awardedXp);
        progressionService.recordCropHarvest(player, tier.primaryDropMaterial(), quantity);

        generator.incrementHarvests();
        eventBus.publishAsync(new FarmingGeneratorHarvestEvent(generator, node, player, awardedXp));

        // 8. Schedule Regeneration Loop
        int speedUpgradeLevel = generator.getUpgradeLevel(GeneratorUpgrade.KEY_SPEED);
        int baseDelay = Math.max(6, 30 - (speedUpgradeLevel * 2));
        int regenDelayTicks = overclocked ? Math.max(4, baseDelay / 2) : baseDelay;

        // Reset block to seedling
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable, false);
        } else {
            block.setType(Material.AIR, false);
        }

        // Update DecentHologram status
        if (!overclocked) {
            String timeSec = String.format("%.1f", regenDelayTicks / 20.0);
            hologramService.updateStatus(generator, "&e⏳ REGENERATING &8[&a■■■■&7□□&8] &b" + timeSec + "s");
        }

        scheduleRegeneration(generator, node, block, regenDelayTicks);
        return true;
    }

    private void scheduleRegeneration(
            @NotNull FarmingGenerator generator,
            @NotNull CropNode node,
            @NotNull Block block,
            int delayTicks
    ) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
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

                // If not overclocked, restore status line to READY
                if (!overclockManager.isOverclocked(generator)) {
                    hologramService.updateStatus(generator, "&a● READY TO HARVEST");
                }
            } finally {
                node.releaseLock();
            }
        }, delayTicks);
    }

    public void handleIllegitimateDisturbance(@NotNull FarmingGenerator generator, @NotNull CropNode node, @NotNull Block block) {
        if (!node.tryLockForHarvest()) {
            return;
        }

        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable, false);
        } else {
            block.setType(Material.AIR, false);
        }

        scheduleRegeneration(generator, node, block, 40);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
    }
}
