package com.skyrealms.end.listener;

import com.skyrealms.end.wilderness.EndWildernessService;
import com.skyrealms.end.wilderness.WildernessRegion;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Handles End Wilderness monster loot drops, Bestiary kill updates, region discovery,
 * and strict enforcement blocking Dragon encounters outside the scope of this module.
 */
public final class WildernessListener implements Listener {

    private final EndWildernessService wildernessService;

    public WildernessListener(@NotNull EndWildernessService wildernessService) {
        this.wildernessService = Objects.requireNonNull(wildernessService, "wildernessService cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonSpawnGuard(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof EnderDragon
                && wildernessService.isWildernessWorld(event.getLocation().getWorld().getName())) {
            // Hard scope rule: no dragon dungeons or dragon encounters in End Wilderness.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWildMobDeath(EntityDeathEvent event) {
        wildernessService.handleMobDeathDropsAndBestiary(
                event.getEntity(),
                event.getEntity().getKiller(),
                event.getDrops()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWildMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
                && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4) {
            return;
        }
        Location to = event.getTo();
        if (to.getWorld() == null || !wildernessService.isWildernessWorld(to.getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();
        WildernessRegion region = WildernessRegion.resolveAt(to.getX(), to.getZ());
        wildernessService.announceRegionDiscovery(player, region);
    }
}

