package com.skyrealms.islands.lobby;

import com.skyrealms.islands.service.IslandService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LobbyVoidRescueTest {

    private IslandService islandService;
    private LobbyListener listener;
    private Location spawnLoc;
    private World islandWorld;
    private Player player;

    @BeforeEach
    void setUp() {
        islandService = mock(IslandService.class);

        World spawnWorld = mock(World.class);
        when(spawnWorld.getName()).thenReturn("world");
        spawnLoc = new Location(spawnWorld, 0.5, 89.0, 0.5);
        listener = new LobbyListener(() -> spawnLoc, islandService);

        islandWorld = mock(World.class);
        when(islandWorld.getName()).thenReturn("skyrealms_islands");
        when(islandService.isProtectedWorld("skyrealms_islands")).thenReturn(true);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(islandWorld);
        when(player.teleportAsync(spawnLoc)).thenReturn(CompletableFuture.completedFuture(true));
    }

    @Test
    void islandVoidFallMoveTeleportsToLobbySpawnAndResetsFallDistance() {
        Location from = new Location(islandWorld, 12.0, 2.0, -5.0);
        Location to = new Location(islandWorld, 12.0, -0.5, -5.0);
        PlayerMoveEvent moveEvent = new PlayerMoveEvent(player, from, to);

        listener.onMove(moveEvent);

        verify(player, times(2)).setFallDistance(0.0f);
        verify(player).teleportAsync(spawnLoc);
    }

    @Test
    void islandVoidDamageIsCancelledAndTeleportsToLobbySpawn() {
        EntityDamageEvent damageEvent = mock(EntityDamageEvent.class);
        when(damageEvent.getEntity()).thenReturn(player);
        when(damageEvent.getCause()).thenReturn(EntityDamageEvent.DamageCause.VOID);

        listener.onEntityDamage(damageEvent);

        verify(damageEvent).setCancelled(true);
        verify(player, times(2)).setFallDistance(0.0f);
        verify(player).teleportAsync(spawnLoc);
    }

    @Test
    void islandNonVoidDamageDoesNotTeleportToLobbySpawn() {
        EntityDamageEvent damageEvent = mock(EntityDamageEvent.class);
        when(damageEvent.getEntity()).thenReturn(player);
        when(damageEvent.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);

        listener.onEntityDamage(damageEvent);

        verify(damageEvent, org.mockito.Mockito.never()).setCancelled(true);
        verify(player, org.mockito.Mockito.never()).teleportAsync(spawnLoc);
    }

    @Test
    void defaultLobbyStationsExposeDistinctNonBlankCommands() {
        java.util.List<LobbyStation> stations = LobbyStation.DEFAULT_STATIONS;
        org.assertj.core.api.Assertions.assertThat(stations).hasSize(6);
        org.assertj.core.api.Assertions.assertThat(stations)
                .extracting(LobbyStation::command)
                .containsExactlyInAnyOrder("island", "garden", "pets", "menu", "items", "balance");
    }
}

