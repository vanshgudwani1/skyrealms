package com.skyrealms.pets.listener;

import com.skyrealms.pets.service.PetService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Loads player pet unlocks/active state on join and cleans up spawned entities on quit.
 */
public final class PetConnectionListener implements Listener {

    private final PetService petService;

    public PetConnectionListener(@NotNull PetService petService) {
        this.petService = Objects.requireNonNull(petService, "petService cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        petService.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        petService.handlePlayerQuit(event.getPlayer());
    }
}

