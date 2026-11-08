package com.skyrealms.islands.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service providing a 3-second teleport warmup with animated swirling particle double-helix,
 * movement cancellation, action bar countdowns, destination burst effects, and cooldowns.
 */
public final class TeleportWarmupService {

    private static final long COOLDOWN_MILLIS = 5000L; // 5-second cooldown
    private static final int WARMUP_TICKS = 60;        // 3 seconds (20 ticks/sec)

    private final Plugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportWarmupService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Teleports a player with a 3-second warmup, particle aura, and movement check.
     */
    public void teleportWithWarmup(Player player, Location destination, String destinationName, Runnable onComplete) {
        UUID uuid = player.getUniqueId();

        // 1. Admin/OP bypass
        if (player.hasPermission("skyrealms.teleport.bypass") || player.isOp()) {
            executeInstantTeleport(player, destination, destinationName, onComplete);
            return;
        }

        // 2. Cooldown check
        if (cooldowns.containsKey(uuid)) {
            long elapsed = System.currentTimeMillis() - cooldowns.get(uuid);
            if (elapsed < COOLDOWN_MILLIS) {
                long remaining = ((COOLDOWN_MILLIS - elapsed) / 1000L) + 1L;
                player.sendMessage(Component.text("⏳ Please wait " + remaining + "s before teleporting again!", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        // 3. Pending check
        if (pendingTeleports.containsKey(uuid)) {
            player.sendMessage(Component.text("✦ A teleport is already in progress! Hold still.", NamedTextColor.YELLOW));
            return;
        }

        // 4. Start 3-second warmup
        final Location startLoc = player.getLocation().clone();
        player.playSound(startLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);
        player.sendMessage(Component.text("✦ Teleporting to ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(destinationName, NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" in 3 seconds. Do not move!", NamedTextColor.LIGHT_PURPLE)));

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // If player went offline or died
                if (!player.isOnline() || player.isDead()) {
                    pendingTeleports.remove(uuid);
                    cancel();
                    return;
                }

                // Movement check (tolerance: 0.35 blocks)
                Location current = player.getLocation();
                if (current.distanceSquared(startLoc) > 0.35) {
                    pendingTeleports.remove(uuid);
                    cancel();
                    player.playSound(current, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                    player.sendMessage(Component.text("❌ Teleportation cancelled because you moved!", NamedTextColor.RED));
                    player.sendActionBar(Component.text("❌ Teleport cancelled (moved)", NamedTextColor.RED));
                    player.spawnParticle(Particle.SMOKE, current.clone().add(0, 0.2, 0), 15, 0.2, 0.2, 0.2, 0.02);
                    return;
                }

                // Hourly second notifications (at tick 0, 20, 40)
                if (ticks % 20 == 0) {
                    int secondsLeft = (WARMUP_TICKS - ticks) / 20;
                    if (secondsLeft > 0) {
                        player.sendActionBar(Component.text("✦ Teleporting in ", NamedTextColor.LIGHT_PURPLE)
                                .append(Component.text(secondsLeft + "s", NamedTextColor.YELLOW, TextDecoration.BOLD))
                                .append(Component.text("... Hold still! ✦", NamedTextColor.LIGHT_PURPLE)));

                        Title title = Title.title(
                                Component.text("✦ " + secondsLeft + " ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                                Component.text("Warps in " + secondsLeft + "s... Do not move", NamedTextColor.WHITE),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))
                        );
                        player.showTitle(title);
                        player.playSound(current, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f + (3 - secondsLeft) * 0.3f);
                    }
                }

                // Swirling dual-helix particle aura
                double angle1 = ticks * 0.35;
                double angle2 = angle1 + Math.PI;
                double heightOffset = ((ticks * 2) % 40) / 20.0 * 1.8;

                double x1 = Math.cos(angle1) * 0.8;
                double z1 = Math.sin(angle1) * 0.8;
                double x2 = Math.cos(angle2) * 0.8;
                double z2 = Math.sin(angle2) * 0.8;

                player.spawnParticle(Particle.CHERRY_LEAVES, current.getX() + x1, current.getY() + heightOffset, current.getZ() + z1, 1, 0, 0, 0, 0);
                player.spawnParticle(Particle.END_ROD, current.getX() + x2, current.getY() + heightOffset, current.getZ() + z2, 1, 0, 0, 0, 0);

                ticks++;

                // Warmup Finished!
                if (ticks >= WARMUP_TICKS) {
                    pendingTeleports.remove(uuid);
                    cancel();

                    cooldowns.put(uuid, System.currentTimeMillis());
                    executeInstantTeleport(player, destination, destinationName, onComplete);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        pendingTeleports.put(uuid, task);
    }

    private void executeInstantTeleport(Player player, Location destination, String destinationName, Runnable onComplete) {
        player.teleportAsync(destination).thenAccept(success -> {
            if (success) {
                player.playSound(destination, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 1.0f);
                player.playSound(destination, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);

                player.spawnParticle(Particle.CHERRY_LEAVES, destination.clone().add(0, 1, 0), 50, 0.8, 0.8, 0.8, 0.1);
                player.spawnParticle(Particle.PORTAL, destination.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

                Title title = Title.title(
                        Component.text("✦ ARRIVED ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                        Component.text("Welcome to " + destinationName, NamedTextColor.WHITE),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
                );
                player.showTitle(title);
                player.sendActionBar(Component.text("✔ Teleported to " + destinationName, NamedTextColor.GREEN));

                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }
}
