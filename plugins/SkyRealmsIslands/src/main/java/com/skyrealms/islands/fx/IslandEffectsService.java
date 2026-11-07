package com.skyrealms.islands.fx;

import com.skyrealms.islands.model.Island;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles animated particles, celestial Cherry Blossom teleportation helixes,
 * dynamic boundary visualizers, and celebratory audio-visual FX.
 */
public final class IslandEffectsService {

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> activeBorderTasks = new ConcurrentHashMap<>();

    public IslandEffectsService(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Plays a rising double-helix of cherry leaves and end rods around the player upon teleportation.
     */
    public void playTeleportHelix(@NotNull Player player) {
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) return;

        world.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
        world.playSound(base, Sound.BLOCK_CHERRY_SAPLING_PLACE, 1.0f, 0.9f);

        new BukkitRunnable() {
            double y = 0;
            double angle = 0;

            @Override
            public void run() {
                if (y > 2.5 || !player.isOnline()) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 2; i++) {
                    double offsetAngle = angle + (i * Math.PI);
                    double x = 0.8 * Math.cos(offsetAngle);
                    double z = 0.8 * Math.sin(offsetAngle);
                    Location pLoc = base.clone().add(x, y, z);

                    world.spawnParticle(Particle.CHERRY_LEAVES, pLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.END_ROD, pLoc, 1, 0, 0, 0, 0.01);
                }

                y += 0.25;
                angle += Math.PI / 4;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Spawns celestial genesis burst when a new island is formed.
     */
    public void playGenesisBurst(@NotNull Player player, @NotNull Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Ring shockwave
        for (int degree = 0; degree < 360; degree += 10) {
            double rad = Math.toRadians(degree);
            double x = 3.0 * Math.cos(rad);
            double z = 3.0 * Math.sin(rad);
            world.spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(x, 1.0, z), 2, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(x, 1.0, z), 3, 0.1, 0.2, 0.1, 0.1);
        }
    }

    /**
     * Spawns upgrade celebration firework & chime sound.
     */
    public void playUpgradeCelebration(@NotNull Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.8f);

        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1.5, 0), 25, 0.5, 0.5, 0.5, 0.15);
        world.spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 2.0, 0), 30, 0.8, 0.5, 0.8, 0.1);
    }

    /**
     * Toggles an animated boundary border visualizer for the player's island perimeter (lasts 20s).
     */
    public void toggleBorderVisualizer(@NotNull Player player, @NotNull Island island) {
        UUID uuid = player.getUniqueId();
        if (activeBorderTasks.containsKey(uuid)) {
            activeBorderTasks.remove(uuid).cancel();
            player.sendActionBar(Component.text("✖ Island Border Visualizer deactivated.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        player.sendActionBar(Component.text("🌸 Island Border Visualizer Active (20s)", NamedTextColor.LIGHT_PURPLE));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);

        int half = island.size() / 2;
        int minX = island.location().centerX() - half;
        int maxX = island.location().centerX() + half;
        int minZ = island.location().centerZ() - half;
        int maxZ = island.location().centerZ() + half;

        Particle.DustOptions pinkDust = new Particle.DustOptions(Color.fromRGB(255, 150, 190), 1.5f);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks++ >= 40) { // 40 * 10 ticks = 20s
                    activeBorderTasks.remove(uuid);
                    cancel();
                    if (player.isOnline()) {
                        player.sendActionBar(Component.text("Island Border Visualizer ended.", NamedTextColor.GRAY));
                    }
                    return;
                }

                Location pLoc = player.getLocation();
                World world = pLoc.getWorld();
                if (world == null || !world.getName().equalsIgnoreCase(island.location().worldName())) return;

                double eyeY = pLoc.getY() + 1.0;

                // Render boundary line particles near the player's vicinity
                int range = 40; // Only render boundary points within 40 blocks of the player
                int px = pLoc.getBlockX();
                int pz = pLoc.getBlockZ();

                // North / South edges
                for (int x = Math.max(minX, px - range); x <= Math.min(maxX, px + range); x += 2) {
                    if (Math.abs(pz - minZ) <= range) {
                        world.spawnParticle(Particle.DUST, x + 0.5, eyeY, minZ + 0.5, 1, 0, 0, 0, pinkDust);
                        world.spawnParticle(Particle.CHERRY_LEAVES, x + 0.5, eyeY, minZ + 0.5, 1, 0, 0, 0, 0);
                    }
                    if (Math.abs(pz - maxZ) <= range) {
                        world.spawnParticle(Particle.DUST, x + 0.5, eyeY, maxZ + 0.5, 1, 0, 0, 0, pinkDust);
                        world.spawnParticle(Particle.CHERRY_LEAVES, x + 0.5, eyeY, maxZ + 0.5, 1, 0, 0, 0, 0);
                    }
                }

                // East / West edges
                for (int z = Math.max(minZ, pz - range); z <= Math.min(maxZ, pz + range); z += 2) {
                    if (Math.abs(px - minX) <= range) {
                        world.spawnParticle(Particle.DUST, minX + 0.5, eyeY, z + 0.5, 1, 0, 0, 0, pinkDust);
                        world.spawnParticle(Particle.CHERRY_LEAVES, minX + 0.5, eyeY, z + 0.5, 1, 0, 0, 0, 0);
                    }
                    if (Math.abs(px - maxX) <= range) {
                        world.spawnParticle(Particle.DUST, maxX + 0.5, eyeY, z + 0.5, 1, 0, 0, 0, pinkDust);
                        world.spawnParticle(Particle.CHERRY_LEAVES, maxX + 0.5, eyeY, z + 0.5, 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);

        activeBorderTasks.put(uuid, task);
    }

    public void cleanup() {
        activeBorderTasks.values().forEach(BukkitTask::cancel);
        activeBorderTasks.clear();
    }
}
