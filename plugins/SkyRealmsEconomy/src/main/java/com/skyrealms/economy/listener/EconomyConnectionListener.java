package com.skyrealms.economy.listener;

import com.skyrealms.economy.service.EconomyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Listens for player connections to preload currency balances and evict inactive sessions.
 */
public final class EconomyConnectionListener implements Listener {

    private final EconomyService economyService;

    public EconomyConnectionListener(@NotNull EconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        // Asynchronously initialize and preload wallets into cache during async pre-login
        try {
            economyService.preloadBalances(event.getUniqueId()).join();
        } catch (Exception e) {
            // Log but allow join; fallback will query database on demand
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        economyService.evict(event.getPlayer().getUniqueId());
    }
}
