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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final Map<UUID, BukkitTask> regenCountdownTasks = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> regenSecondsRemaining = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> regenTotalSeconds = new ConcurrentHashMap<>();

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
        int baseDelay = plugin.getConfig().getInt("generator.default.base-regeneration-delay-ticks", 200);
        int reductionPerLevel = plugin.getConfig().getInt("generator.upgrades.speed.ticks-reduction-per-level", 12);
        int minDelay = plugin.getConfig().getInt("generator.default.min-regeneration-delay-ticks", 40);

        int calculatedDelay = Math.max(minDelay, baseDelay - (speedUpgradeLevel * reductionPerLevel));
        int regenDelayTicks = overclocked ? Math.max(minDelay / 2, calculatedDelay / 2) : calculatedDelay;

        // Reset block to seedling
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable, false);
        } else {
            block.setType(Material.AIR, false);
        }

        // Live ticking DecentHologram countdown
        int totalSec = Math.max(1, (int) Math.ceil(regenDelayTicks / 20.0));
        regenTotalSeconds.put(generator.generatorId(), totalSec);
        regenSecondsRemaining.compute(generator.generatorId(), (k, v) -> {
            if (v == null) return new AtomicInteger(totalSec);
            v.set(Math.max(v.get(), totalSec));
            return v;
        });

        if (!overclockManager.isOverclocked(generator)) {
            BukkitTask existing = regenCountdownTasks.get(generator.generatorId());
            if (existing == null || existing.isCancelled()) {
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (overclockManager.isOverclocked(generator)) {
                            cancel();
                            regenCountdownTasks.remove(generator.generatorId());
                            return;
                        }
                        AtomicInteger sec = regenSecondsRemaining.get(generator.generatorId());
                        Integer total = regenTotalSeconds.get(generator.generatorId());
                        if (sec == null || total == null || sec.get() <= 0) {
                            hologramService.updateStatus(generator, "&a● READY TO HARVEST");
                            cancel();
                            regenCountdownTasks.remove(generator.generatorId());
                            return;
                        }
                        int rem = sec.decrementAndGet();
                        if (rem <= 0) {
                            hologramService.updateStatus(generator, "&a● READY TO HARVEST");
                            cancel();
                            regenCountdownTasks.remove(generator.generatorId());
                            return;
                        }
                        double pct = 1.0 - ((double) rem / Math.max(1, total));
                        String bar = buildProgressBar(pct, 6);
                        hologramService.updateStatus(generator, "&e⏳ REGENERATING &8[" + bar + "&8] &b" + rem + "s");
                    }
                }.runTaskTimer(plugin, 0L, 20L);
                regenCountdownTasks.put(generator.generatorId(), task);
            }
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
        FarmingGeneratorTier tier = generator.tier();
        Material targetMaterial = tier.primaryDropMaterial() == Material.WHEAT_SEEDS ? Material.WHEAT :
                (tier == FarmingGeneratorTier.TIER_1_WHEAT ? Material.WHEAT :
                        (tier == FarmingGeneratorTier.TIER_2_ROOT ? Material.CARROTS :
                                (tier == FarmingGeneratorTier.TIER_3_FRUIT ? Material.MELON :
                                        (tier == FarmingGeneratorTier.TIER_4_TROPICAL ? Material.SUGAR_CANE : Material.NETHER_WART))));

        // Staged visual growth: gradually advance crop age so growth looks natural and rewarding
        BlockData initData = block.getBlockData();
        if (initData instanceof Ageable ageable && delayTicks >= 60) {
            int maxAge = ageable.getMaximumAge();
            int stage1Age = Math.min(maxAge - 1, Math.max(1, (int) Math.round(maxAge * 0.33)));
            int stage2Age = Math.min(maxAge - 1, Math.max(2, (int) Math.round(maxAge * 0.66)));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    if (block.getBlockData() instanceof Ageable a) {
                        a.setAge(stage1Age);
                        block.setBlockData(a, false);
                    }
                }
            }, delayTicks / 3);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    if (block.getBlockData() instanceof Ageable a) {
                        a.setAge(stage2Age);
                        block.setBlockData(a, false);
                    }
                }
            }, (delayTicks * 2) / 3);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    node.releaseLock();
                    return;
                }

                // 1. Defensive Soil Check: Auto-repair farmland and strictly prevent floating crops
                Block soilBlock = block.getWorld().getBlockAt(node.x(), node.y() - 1, node.z());
                if (soilBlock.getType().isAir()) {
                    soilBlock.setType(Material.FARMLAND, false);
                } else if (soilBlock.getType() == Material.DIRT || soilBlock.getType() == Material.GRASS_BLOCK
                        || soilBlock.getType() == Material.COARSE_DIRT || soilBlock.getType() == Material.ROOTED_DIRT) {
                    soilBlock.setType(Material.FARMLAND, false);
                }

                if (soilBlock.getType().isAir()) {
                    LOGGER.warn("Crop node at ({}, {}, {}) is missing soil! Aborting crop placement.", node.x(), node.y(), node.z());
                    return;
                }

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

    private String buildProgressBar(double progress, int totalSegments) {
        int filled = (int) Math.round(progress * totalSegments);
        filled = Math.max(0, Math.min(totalSegments, filled));
        return "&a" + "■".repeat(filled) + "&7" + "□".repeat(totalSegments - filled);
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
