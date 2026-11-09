package com.skyrealms.farming.garden.cinematic;

import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Procedural cinematic orchestrator for the Divine Garden Offering unlock sequence.
 * Plays divine lightning, sound effects, particle vortexes, spawns a glowing divine emissary,
 * runs dialogue subtitles, and permanently unlocks the island Garden without client-side mods.
 */
public final class DivineCinematicService {

    private final Plugin plugin;
    private final GardenService gardenService;
    private final FarmingRepository repository;
    private final IslandService islandService;
    private final Logger logger;

    private final Set<UUID> activeCinematics = ConcurrentHashMap.newKeySet();

    public DivineCinematicService(
            @NotNull Plugin plugin,
            @NotNull GardenService gardenService,
            @NotNull FarmingRepository repository,
            @NotNull IslandService islandService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.logger = plugin.getLogger();
    }

    public boolean isCinematicActive(@NotNull UUID islandId) {
        return activeCinematics.contains(islandId);
    }

    /**
     * Executes the Divine Cinematic sequence.
     */
    public void playCinematic(@NotNull Player player, @NotNull Location chestLoc, @NotNull UUID islandId) {
        if (!activeCinematics.add(islandId)) {
            return; // Prevent duplicate concurrent cinematic
        }

        World world = chestLoc.getWorld();
        if (world == null) {
            activeCinematics.remove(islandId);
            return;
        }

        logger.info("Starting Divine Offering Cinematic on island " + islandId + " triggered by " + player.getName());

        // 1. Initial Divine Strike & Sounds
        world.strikeLightningEffect(chestLoc);
        world.playSound(chestLoc, Sound.ITEM_TOTEM_USE, 2.0f, 1.0f);
        world.playSound(chestLoc, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.8f);

        // 2. Spawn Divine Emissary entity hovering 6 blocks above chest
        Location spawnLoc = chestLoc.clone().add(0.5, 6.0, 0.5);
        Allay emissary = (Allay) world.spawnEntity(spawnLoc, EntityType.ALLAY);
        emissary.customName(Component.text("Celestial Emissary", NamedTextColor.GOLD));
        emissary.setCustomNameVisible(false); // Keep name invisible so it does not block the player's view
        emissary.setGlowing(true);
        emissary.setInvulnerable(true);
        emissary.setAI(false);

        final Location targetLoc = chestLoc.clone().add(0.5, 1.5, 0.5);

        // 3. Cinematic step runner (ticks 0 to 140 = 7 seconds)
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!emissary.isValid() || ticks > 140) {
                    cleanup();
                    cancel();
                    return;
                }

                // Smooth descent toward the offering chest
                if (ticks < 60) {
                    double progress = ticks / 60.0;
                    double currentY = spawnLoc.getY() - (progress * 4.5);
                    emissary.teleport(new Location(world, targetLoc.getX(), currentY, targetLoc.getZ(), (float) (ticks * 6), 0f));
                }

                // Radiant golden particle vortex
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, emissary.getLocation(), 10, 0.3, 0.3, 0.3, 0.05);
                world.spawnParticle(Particle.HAPPY_VILLAGER, emissary.getLocation(), 5, 0.2, 0.2, 0.2, 0.02);

                // Dialogue and title milestones (crisp, compact, non-obtrusive)
                if (ticks == 20) {
                    Title title1 = Title.title(
                            Component.empty(),
                            Component.text("The heavens tremble with celestial light...", NamedTextColor.GOLD),
                            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(300))
                    );
                    player.showTitle(title1);
                    player.playSound(chestLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
                } else if (ticks == 60) {
                    Title title2 = Title.title(
                            Component.text("DIVINE BLESSING", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                            Component.text("Your sacred tribute has pleased the Gods!", NamedTextColor.WHITE),
                            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(300))
                    );
                    player.showTitle(title2);
                    player.playSound(chestLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.7f);
                } else if (ticks == 100) {
                    Title title3 = Title.title(
                            Component.text("GARDEN UNLOCKED", NamedTextColor.GREEN, TextDecoration.BOLD),
                            Component.text("Use /garden to enter your sanctuary", NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2000), Duration.ofMillis(400))
                    );
                    player.showTitle(title3);
                    player.playSound(chestLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, targetLoc, 2);
                } else if (ticks == 130) {
                    // Finalize Unlock
                    finalizeUnlock(islandId, player);
                }

                ticks += 2;
            }

            private void cleanup() {
                if (emissary.isValid()) {
                    emissary.remove();
                }
                activeCinematics.remove(islandId);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void finalizeUnlock(@NotNull UUID islandId, @NotNull Player triggeringPlayer) {
        try {
            // Persist unlock in database
            repository.saveGardenUnlock(new FarmingRepository.GardenUnlock(
                    islandId,
                    true,
                    true,
                    Instant.now()
            ));

            // Notify all island members online
            Optional<Island> optIsland = islandService.getIsland(islandId);
            if (optIsland.isPresent()) {
                Island island = optIsland.get();
                Component msg = Component.text("---------------------------------------------", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.newline())
                        .append(Component.text("✦ SkyRealms Garden Unlocked! ✦", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.text("Your island has earned the blessing of the Gods! Permanent access to the Sakura Conservatory is now unlocked.", NamedTextColor.YELLOW))
                        .append(Component.newline())
                        .append(Component.text("Use ", NamedTextColor.GRAY))
                        .append(Component.text("/garden", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text(" to enter your Garden sanctuary.", NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.text("---------------------------------------------", NamedTextColor.LIGHT_PURPLE));

                for (UUID memberUuid : island.members().keySet()) {
                    Player p = Bukkit.getPlayer(memberUuid);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(msg);
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
                    }
                }
            }

            logger.info("Island " + islandId + " has successfully unlocked permanent Garden access!");
        } catch (Exception e) {
            logger.severe("Error finalizing garden unlock for island " + islandId + ": " + e.getMessage());
        }
    }
}
