package com.skyrealms.islands.lobby;

import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Handles lobby world physics freeze, protection, void catch (lobby and islands), and join greeting.
 */
public final class LobbyListener implements Listener {

    private static final double LOBBY_VOID_Y = 25.0;
    private static final double ISLAND_VOID_Y = 0.0;
    private static final long VOID_RESCUE_COOLDOWN_MS = 1500L;

    private final Supplier<Location> lobbySpawnSupplier;
    private final IslandService islandService;
    private final Map<UUID, Long> recentVoidRescueMs = new ConcurrentHashMap<>();

    public LobbyListener(Plugin plugin, LobbyService lobbyService, IslandService islandService) {
        this(lobbyService::getLobbySpawnLocation, islandService);
    }

    LobbyListener(Supplier<Location> lobbySpawnSupplier, IslandService islandService) {
        this.lobbySpawnSupplier = lobbySpawnSupplier;
        this.islandService = islandService;
    }

    // --- PHYSICS FREEZE & ANTI-BREAK SHIELD ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            if (event.getEntityType() == EntityType.FALLING_BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
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
        String worldName = event.getEntity().getWorld().getName();
        if (worldName.equals("world")) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player player) {
                catchVoidPlayer(player);
            }
            return;
        }

        if (islandService.isProtectedWorld(worldName)
                && event.getCause() == EntityDamageEvent.DamageCause.VOID
                && event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            catchVoidPlayer(player);
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
        if (to.getWorld() == null) {
            return;
        }
        String worldName = to.getWorld().getName();
        double voidY = voidThresholdForWorld(worldName);
        if (!Double.isNaN(voidY) && to.getY() < voidY) {
            catchVoidPlayer(event.getPlayer());
        }
    }

    private double voidThresholdForWorld(String worldName) {
        if (worldName.equals("world")) {
            return LOBBY_VOID_Y;
        }
        if (islandService.isProtectedWorld(worldName)) {
            return ISLAND_VOID_Y;
        }
        return Double.NaN;
    }

    private void catchVoidPlayer(Player player) {
        long now = System.currentTimeMillis();
        Long lastRescue = recentVoidRescueMs.get(player.getUniqueId());
        if (lastRescue != null && (now - lastRescue) < VOID_RESCUE_COOLDOWN_MS) {
            return;
        }
        recentVoidRescueMs.put(player.getUniqueId(), now);

        player.setFallDistance(0.0f);
        Location spawn = lobbySpawnSupplier.get();
        player.teleportAsync(spawn).thenAccept(success -> {
            if (success) {
                player.setFallDistance(0.0f);
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
            Location spawn = lobbySpawnSupplier.get();
            player.teleportAsync(spawn);

            Title title = Title.title(
                    Component.text("✦ SKYREALMS ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    Component.text("Celestial Sakura Hub", NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            player.showTitle(title);
            player.playSound(spawn, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }
}
