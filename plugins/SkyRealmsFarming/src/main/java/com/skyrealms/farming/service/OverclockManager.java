package com.skyrealms.farming.service;

import com.skyrealms.farming.model.FarmingGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Bio-Fuel and Organic Fertilizer overclocking for Farming Generators.
 */
public final class OverclockManager {

    private final Plugin plugin;
    private final FarmingHologramService hologramService;

    // Generator UUID -> Expiration Instant
    private final Map<UUID, Instant> overclockExpirations = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    public OverclockManager(@NotNull Plugin plugin, @NotNull FarmingHologramService hologramService) {
        this.plugin = plugin;
        this.hologramService = hologramService;
    }

    public boolean isOverclocked(@NotNull FarmingGenerator generator) {
        Instant exp = overclockExpirations.get(generator.generatorId());
        return exp != null && exp.isAfter(Instant.now());
    }

    public long getRemainingSeconds(@NotNull FarmingGenerator generator) {
        Instant exp = overclockExpirations.get(generator.generatorId());
        if (exp == null) return 0;
        return Math.max(0, Duration.between(Instant.now(), exp).getSeconds());
    }

    /**
     * Activates a 5-minute overclock mode on the generator.
     */
    public void activateOverclock(@NotNull Player player, @NotNull FarmingGenerator generator, int durationSeconds) {
        Instant expiration = Instant.now().plusSeconds(durationSeconds);
        overclockExpirations.put(generator.generatorId(), expiration);

        // Cancel previous task if running
        BukkitTask prev = activeTasks.remove(generator.generatorId());
        if (prev != null) prev.cancel();

        Location coreLoc = new Location(Bukkit.getWorld(generator.worldName()), generator.coreX() + 0.5, generator.coreY() + 0.5, generator.coreZ() + 0.5);
        coreLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, coreLoc, 35, 0.6, 0.6, 0.6, 0.05);
        coreLoc.getWorld().spawnParticle(Particle.END_ROD, coreLoc, 20, 0.5, 0.5, 0.5, 0.03);
        player.playSound(coreLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);

        player.sendMessage(Component.text("⚡ [OVERCLOCK ACTIVATED] ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text("Generator speed is doubled for " + (durationSeconds / 60) + " minutes!", NamedTextColor.YELLOW)));

        // Repeating task to update the hologram countdown every second
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                long remaining = getRemainingSeconds(generator);
                if (remaining <= 0) {
                    overclockExpirations.remove(generator.generatorId());
                    activeTasks.remove(generator.generatorId());
                    hologramService.updateStatus(generator, "&a● READY TO HARVEST");
                    cancel();
                    return;
                }

                long mins = remaining / 60;
                long secs = remaining % 60;
                String timeStr = String.format("%02d:%02d", mins, secs);

                hologramService.updateStatus(generator, "<RAINBOW:1>&l⚡ OVERCLOCKED (+100% Speed) " + timeStr + " ⚡</RAINBOW>");
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeTasks.put(generator.generatorId(), task);
    }
}
