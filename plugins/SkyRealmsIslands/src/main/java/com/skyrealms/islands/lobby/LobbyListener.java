package com.skyrealms.islands.lobby;

import com.skyrealms.islands.SkyRealmsIslandsPlugin;
import com.skyrealms.islands.gui.IslandGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

/**
 * Handles lobby world protection, void catch, join greeting, and portal interaction.
 */
public final class LobbyListener implements Listener {

    private final Plugin plugin;
    private final LobbyService lobbyService;
    private final IslandGui islandGui;

    public LobbyListener(Plugin plugin, LobbyService lobbyService, IslandGui islandGui) {
        this.plugin = plugin;
        this.lobbyService = lobbyService;
        this.islandGui = islandGui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            if (!event.getPlayer().hasPermission("skyrealms.admin.build")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            if (!event.getPlayer().hasPermission("skyrealms.admin.build")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().getWorld().getName().equals("world")) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player player) {
                catchVoidPlayer(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity().getWorld().getName().equals("world")) {
            event.setCancelled(true);
            event.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getWorld() != null && to.getWorld().getName().equals("world")) {
            if (to.getY() < 25.0) {
                catchVoidPlayer(event.getPlayer());
            }
        }
    }

    private void catchVoidPlayer(Player player) {
        Location spawn = lobbyService.getLobbySpawnLocation();
        player.teleportAsync(spawn).thenAccept(success -> {
            if (success) {
                player.playSound(spawn, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 1.2f);
                player.playSound(spawn, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
                player.spawnParticle(Particle.CHERRY_LEAVES, spawn, 40, 1.0, 1.0, 1.0, 0.05);
                player.sendMessage(Component.text("🌸 Caught by the celestial breeze!", NamedTextColor.LIGHT_PURPLE));
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals("world")) {
            Location spawn = lobbyService.getLobbySpawnLocation();
            player.teleportAsync(spawn);

            Title title = Title.title(
                    Component.text("✦ SKYREALMS ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    Component.text("Celestial Dragon Nest Lobby", NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            player.showTitle(title);
            player.playSound(spawn, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getPlayer().getWorld().getName().equals("world") && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock() != null) {
                Location loc = event.getClickedBlock().getLocation();
                // Check if clicking near the Portal Station (X=13, Z=0)
                if (Math.abs(loc.getBlockX() - 13) <= 2 && Math.abs(loc.getBlockZ()) <= 2) {
                    event.setCancelled(true);
                    islandGui.openMenu(event.getPlayer());
                }
            }
        }
    }
}
