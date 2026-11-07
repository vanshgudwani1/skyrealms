package com.skyrealms.player.listener;

import com.skyrealms.player.model.PlayerProfile;
import com.skyrealms.player.service.PlayerService;
import com.skyrealms.platform.database.DatabaseService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Handles Bukkit player login lifecycle with asynchronous preloading and zero main-thread blocking.
 */
public final class PlayerConnectionListener implements Listener {

    private static final Logger log = LoggerFactory.getLogger(PlayerConnectionListener.class);

    private final PlayerService playerService;
    private final Supplier<DatabaseService> databaseServiceSupplier;
    private final Map<UUID, PlayerProfile> preloaded = new ConcurrentHashMap<>();

    public PlayerConnectionListener(
            @NotNull PlayerService playerService,
            @NotNull Supplier<DatabaseService> databaseServiceSupplier
    ) {
        this.playerService = Objects.requireNonNull(playerService, "playerService cannot be null");
        this.databaseServiceSupplier = Objects.requireNonNull(databaseServiceSupplier, "databaseServiceSupplier cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        DatabaseService db = databaseServiceSupplier.get();
        if (db == null || !db.isHealthy()) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("SkyRealms database is currently synchronizing.\nPlease reconnect in a few moments.", NamedTextColor.RED)
            );
            return;
        }

        UUID uuid = event.getUniqueId();
        String username = event.getName();
        String ipAddress = event.getAddress().getHostAddress();

        try {
            PlayerProfile profile = playerService.loadOrCreateAsync(uuid, username, ipAddress)
                    .get(5, TimeUnit.SECONDS);

            preloaded.put(uuid, profile);
            log.info("Preloaded profile for player '{}' ({})", username, uuid);
        } catch (Exception e) {
            log.error("Failed to load profile for '{}' during prelogin: {}", username, e.getMessage(), e);
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("An error occurred while loading your profile data.\nPlease try reconnecting.", NamedTextColor.RED)
            );
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ipAddress = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;

        PlayerProfile profile = preloaded.remove(uuid);
        if (profile == null) {
            // Safety fallback if prelogin cache was missed
            log.warn("Profile for '{}' was not found in preloaded cache on join. Loading synchronously...", player.getName());
            try {
                profile = playerService.loadOrCreateAsync(uuid, player.getName(), ipAddress).join();
            } catch (Exception e) {
                player.kick(Component.text("Profile initialization failed. Please reconnect.", NamedTextColor.RED));
                return;
            }
        }

        playerService.activate(uuid, profile, ipAddress);

        // Welcoming
        if (profile.stats().joinsCount() <= 1) {
            player.sendMessage(Component.text("========================================", NamedTextColor.DARK_AQUA));
            player.sendMessage(Component.text(" Welcome to SkyRealms, ", NamedTextColor.GOLD)
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GOLD)));
            player.sendMessage(Component.text(" Your epic SkyBlock journey begins now.", NamedTextColor.GRAY));
            player.sendMessage(Component.text(" Type ", NamedTextColor.GRAY)
                    .append(Component.text("/profile", NamedTextColor.AQUA))
                    .append(Component.text(" to view your statistics.", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("========================================", NamedTextColor.DARK_AQUA));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        preloaded.remove(uuid);
        playerService.deactivateAsync(uuid);
        log.info("Player '{}' disconnected. Profile queued for save.", event.getPlayer().getName());
    }
}
