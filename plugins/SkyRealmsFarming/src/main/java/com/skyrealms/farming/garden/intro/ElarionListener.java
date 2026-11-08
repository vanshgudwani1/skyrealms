package com.skyrealms.farming.garden.intro;

import com.skyrealms.farming.garden.GardenService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Listener detecting when a player enters the Garden world to trigger Elarion's welcome sequence.
 */
public final class ElarionListener implements Listener {

    private final GardenService gardenService;
    private final ElarionService elarionService;

    public ElarionListener(
            @NotNull GardenService gardenService,
            @NotNull ElarionService elarionService
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.elarionService = Objects.requireNonNull(elarionService, "elarionService cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World currentWorld = player.getWorld();
        if (gardenService.isGardenWorld(currentWorld)) {
            elarionService.triggerIntro(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (gardenService.isGardenWorld(player.getWorld())) {
            elarionService.triggerIntro(player);
        }
    }
}
