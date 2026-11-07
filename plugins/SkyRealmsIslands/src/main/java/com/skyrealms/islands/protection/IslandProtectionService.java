package com.skyrealms.islands.protection;

import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Centralized spatial protection listener enforcing island boundary permissions.
 */
public final class IslandProtectionService implements Listener {

    private final IslandService islandService;

    public IslandProtectionService(@NotNull IslandService islandService) {
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
    }

    private boolean isProtectedWorld(@NotNull Location loc) {
        return loc.getWorld() != null && islandService.isProtectedWorld(loc.getWorld().getName());
    }

    private boolean canBypass(@NotNull Player player) {
        return player.isOp() || player.hasPermission("skyrealms.island.admin");
    }

    private void notifyDenied(@NotNull Player player) {
        player.sendActionBar(Component.text("✖ You do not have permission to do that here.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isProtectedWorld(block.getLocation())) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        Optional<Island> opt = islandService.getIslandAt(block.getLocation());
        if (opt.isEmpty() || !opt.get().hasPermission(player.getUniqueId(), IslandPermission.BLOCK_BREAK)) {
            event.setCancelled(true);
            notifyDenied(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!isProtectedWorld(block.getLocation())) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        Optional<Island> opt = islandService.getIslandAt(block.getLocation());
        if (opt.isEmpty() || !opt.get().hasPermission(player.getUniqueId(), IslandPermission.BLOCK_PLACE)) {
            event.setCancelled(true);
            notifyDenied(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !isProtectedWorld(block.getLocation())) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        Optional<Island> opt = islandService.getIslandAt(block.getLocation());
        if (opt.isEmpty()) {
            event.setCancelled(true);
            notifyDenied(player);
            return;
        }

        Island island = opt.get();
        Material mat = block.getType();

        // 1. Container access check
        if (isContainer(mat)) {
            if (!island.hasPermission(player.getUniqueId(), IslandPermission.CONTAINER_ACCESS)) {
                event.setCancelled(true);
                notifyDenied(player);
            }
            return;
        }

        // 2. Redstone / mechanical interaction check
        if (isInteractable(mat) || event.getAction() == Action.PHYSICAL) {
            if (!island.hasPermission(player.getUniqueId(), IslandPermission.INTERACT)) {
                event.setCancelled(true);
                notifyDenied(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(@NotNull PlayerArmorStandManipulateEvent event) {
        if (!isProtectedWorld(event.getRightClicked().getLocation())) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        Optional<Island> opt = islandService.getIslandAt(event.getRightClicked().getLocation());
        if (opt.isEmpty() || !opt.get().hasPermission(player.getUniqueId(), IslandPermission.CONTAINER_ACCESS)) {
            event.setCancelled(true);
            notifyDenied(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageByEntityEvent event) {
        if (!isProtectedWorld(event.getEntity().getLocation())) return;

        if (event.getDamager() instanceof Player damager) {
            if (canBypass(damager)) return;

            Optional<Island> opt = islandService.getIslandAt(event.getEntity().getLocation());
            if (opt.isEmpty()) {
                event.setCancelled(true);
                return;
            }

            Island island = opt.get();

            // PvP check
            if (event.getEntity() instanceof Player target) {
                if (!island.settings().pvpEnabled()) {
                    event.setCancelled(true);
                    damager.sendActionBar(Component.text("✖ PvP is disabled on this island.", NamedTextColor.RED));
                    return;
                }
            }

            // Entity / animals / frames
            if (event.getEntity() instanceof ItemFrame || event.getEntity() instanceof ArmorStand || !(event.getEntity() instanceof Player)) {
                if (!island.hasPermission(damager.getUniqueId(), IslandPermission.ENTITY_DAMAGE)) {
                    event.setCancelled(true);
                    notifyDenied(damager);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(@NotNull BlockPistonExtendEvent event) {
        if (!isProtectedWorld(event.getBlock().getLocation())) return;

        Optional<Island> opt = islandService.getIslandAt(event.getBlock().getLocation());
        if (opt.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        Island island = opt.get();
        for (Block b : event.getBlocks()) {
            Location dest = b.getRelative(event.getDirection()).getLocation();
            if (!island.location().contains(dest.getWorld().getName(), dest.getBlockX(), dest.getBlockZ(), island.size())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(@NotNull BlockPistonRetractEvent event) {
        if (!isProtectedWorld(event.getBlock().getLocation())) return;

        Optional<Island> opt = islandService.getIslandAt(event.getBlock().getLocation());
        if (opt.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        Island island = opt.get();
        for (Block b : event.getBlocks()) {
            Location dest = b.getRelative(event.getDirection()).getLocation();
            if (!island.location().contains(dest.getWorld().getName(), dest.getBlockX(), dest.getBlockZ(), island.size())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlow(@NotNull BlockFromToEvent event) {
        if (!isProtectedWorld(event.getBlock().getLocation())) return;

        Optional<Island> opt = islandService.getIslandAt(event.getBlock().getLocation());
        if (opt.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        Island island = opt.get();
        Location dest = event.getToBlock().getLocation();
        if (!island.location().contains(dest.getWorld().getName(), dest.getBlockX(), dest.getBlockZ(), island.size())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(@NotNull BlockExplodeEvent event) {
        if (!isProtectedWorld(event.getBlock().getLocation())) return;

        Optional<Island> opt = islandService.getIslandAt(event.getBlock().getLocation());
        if (opt.isEmpty() || !opt.get().settings().pvpEnabled()) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplosion(@NotNull EntityExplodeEvent event) {
        if (!isProtectedWorld(event.getEntity().getLocation())) return;

        Optional<Island> opt = islandService.getIslandAt(event.getEntity().getLocation());
        if (opt.isEmpty() || !opt.get().settings().pvpEnabled()) {
            event.blockList().clear();
        }
    }

    private boolean isContainer(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL
                || m == Material.FURNACE || m == Material.BLAST_FURNACE || m == Material.SMOKER
                || m == Material.HOPPER || m == Material.DROPPER || m == Material.DISPENSER
                || m == Material.BREWING_STAND || Tag.SHULKER_BOXES.isTagged(m);
    }

    private boolean isInteractable(Material m) {
        return Tag.BUTTONS.isTagged(m) || Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m)
                || Tag.FENCE_GATES.isTagged(m) || m == Material.LEVER
                || m == Material.REPEATER || m == Material.COMPARATOR;
    }
}
