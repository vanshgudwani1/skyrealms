package com.skyrealms.farming.garden;

import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * High-priority security listener enforcing strict boundary containment,
 * visitor isolation, plot permissions, and anti-griefing in skyrealms_garden.
 */
public final class GardenProtectionListener implements Listener {

    private final GardenService gardenService;
    private final IslandService islandService;

    public GardenProtectionListener(
            @NotNull GardenService gardenService,
            @NotNull IslandService islandService
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
    }

    private boolean isGardenWorld(Location loc) {
        return loc != null && loc.getWorld() != null && gardenService.isGardenWorld(loc.getWorld());
    }

    private boolean canBypass(Player player) {
        return player.isOp() || player.hasPermission("skyrealms.garden.admin");
    }

    /**
     * Strict boundary containment: prevents players from walking or flying outside their Garden region.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (!isGardenWorld(to)) return;

        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getBlockY() == to.getBlockY()) {
            return;
        }

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        Optional<Garden> optGarden = gardenService.getGardenByPlayer(player.getUniqueId());
        if (optGarden.isEmpty()) {
            // Player does not belong to any garden in this world, return them to island or spawn
            event.setCancelled(true);
            player.sendMessage(Component.text("⛔ You do not have access to a Garden.", NamedTextColor.RED));
            return;
        }

        Garden garden = optGarden.get();
        if (!garden.isInRegion(to)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("⛔ Garden Boundary: You cannot leave your designated Garden region!", NamedTextColor.RED));
            player.playSound(from, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            // Gentle push back towards garden center
            Vector dir = garden.getCenterLocation().toVector().subtract(to.toVector()).normalize().multiply(0.4);
            dir.setY(0.1);
            player.setVelocity(dir);
        }
    }

    /**
     * Prevents teleporting (Ender Pearl, Chorus Fruit) outside designated region.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (!isGardenWorld(to)) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            Optional<Garden> optGarden = gardenService.getGardenByPlayer(player.getUniqueId());
            if (optGarden.isEmpty() || !optGarden.get().isInRegion(to)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("⛔ You cannot teleport outside your Garden region.", NamedTextColor.RED));
            }
        }
    }

    /**
     * Enforces plot permissions, island membership, and protects dome architecture.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isGardenWorld(block.getLocation())) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        Optional<Garden> optGarden = gardenService.getGardenAt(block.getLocation());
        if (optGarden.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        Garden garden = optGarden.get();
        Optional<Island> optIsland = islandService.getIsland(garden.islandId());
        if (optIsland.isEmpty() || !optIsland.get().hasPermission(player.getUniqueId(), IslandPermission.BLOCK_BREAK)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("⛔ You do not have permission to break blocks in this Garden.", NamedTextColor.RED));
            return;
        }

        // Check if breaking within a plot
        Optional<GardenPlot> optPlot = gardenService.getPlotAt(block.getLocation());
        if (optPlot.isPresent()) {
            GardenPlot plot = optPlot.get();
            if (!plot.unlocked()) {
                event.setCancelled(true);
                player.sendMessage(Component.text("🔒 Plot #" + plot.plotIndex() + " is locked! Unlock it with /garden plots.", NamedTextColor.RED));
                player.playSound(block.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            // If unlocked: allow harvesting crops!
        } else {
            // Outside of plots: protect conservatory glass, paths, and shrine structure
            Material mat = block.getType();
            if (mat == Material.GLASS || mat == Material.TINTED_GLASS || mat == Material.DARK_OAK_WOOD
                    || mat == Material.STONE_BRICKS || mat == Material.SMOOTH_STONE || mat == Material.QUARTZ_BLOCK
                    || mat == Material.QUARTZ_PILLAR || mat == Material.LECTERN || mat == Material.SEA_LANTERN) {
                event.setCancelled(true);
                player.sendMessage(Component.text("⛔ You cannot dismantle Greenhouse Dome structural architecture.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isGardenWorld(block.getLocation())) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        Optional<Garden> optGarden = gardenService.getGardenAt(block.getLocation());
        if (optGarden.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        Garden garden = optGarden.get();
        Optional<Island> optIsland = islandService.getIsland(garden.islandId());
        if (optIsland.isEmpty() || !optIsland.get().hasPermission(player.getUniqueId(), IslandPermission.BLOCK_PLACE)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("⛔ You do not have permission to place blocks in this Garden.", NamedTextColor.RED));
            return;
        }

        // Check if placing in a locked plot
        Optional<GardenPlot> optPlot = gardenService.getPlotAt(block.getLocation());
        if (optPlot.isPresent()) {
            GardenPlot plot = optPlot.get();
            if (!plot.unlocked()) {
                event.setCancelled(true);
                player.sendMessage(Component.text("🔒 Plot #" + plot.plotIndex() + " is locked! Unlock it with /garden plots.", NamedTextColor.RED));
                player.playSound(block.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Prevents players and mobs from trampling farmland in gardens.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Block b = event.getClickedBlock();
            if (b != null && isGardenWorld(b.getLocation()) && b.getType() == Material.FARMLAND) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        Block b = event.getBlock();
        if (isGardenWorld(b.getLocation()) && b.getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    /**
     * Farmland moisture preservation.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandFade(BlockFadeEvent event) {
        Block b = event.getBlock();
        if (isGardenWorld(b.getLocation()) && b.getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    /**
     * Anti-Explosion Shield.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isGardenWorld(event.getBlock().getLocation())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isGardenWorld(event.getLocation())) {
            event.blockList().clear();
        }
    }
}
