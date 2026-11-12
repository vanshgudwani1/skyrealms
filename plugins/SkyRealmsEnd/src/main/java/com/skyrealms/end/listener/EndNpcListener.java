package com.skyrealms.end.listener;

import com.skyrealms.end.npc.EndNpcManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Protects End Village NPCs against damage or item manipulation and routes clicks cleanly
 * to the reusable NPC interaction engine.
 */
public final class EndNpcListener implements Listener {

    private final EndNpcManager npcManager;

    public EndNpcListener(@NotNull EndNpcManager npcManager) {
        this.npcManager = Objects.requireNonNull(npcManager, "npcManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        if (npcManager.isConfiguredWorld(event.getWorld().getName())) {
            npcManager.spawnAllLoaded();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        npcManager.ensureNpcsInChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAt(@NotNull PlayerInteractAtEntityEvent event) {
        String npcId = npcManager.resolveNpcId(event.getRightClicked());
        if (npcId == null) {
            return;
        }
        event.setCancelled(true);
        npcManager.handleInteraction(event.getPlayer(), npcId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        String npcId = npcManager.resolveNpcId(event.getRightClicked());
        if (npcId == null) {
            return;
        }
        event.setCancelled(true);
        npcManager.handleInteraction(event.getPlayer(), npcId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(@NotNull PlayerArmorStandManipulateEvent event) {
        if (npcManager.resolveNpcId(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (npcManager.resolveNpcId(victim) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        String npcId = npcManager.resolveNpcId(event.getEntity());
        if (npcId == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            npcManager.handleInteraction(player, npcId);
        }
    }
}

