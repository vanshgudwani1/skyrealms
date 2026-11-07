package com.skyrealms.player.module;

import com.skyrealms.player.command.ProfileCommand;
import com.skyrealms.player.listener.PlayerConnectionListener;
import com.skyrealms.player.repository.PlayerRepository;
import com.skyrealms.player.service.PlayerService;
import com.skyrealms.platform.api.module.*;
import com.skyrealms.platform.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Player module managing authoritative player profiles, stats, and login sessions.
 */
public final class PlayerModule implements SkyModule {

    private static final ModuleId ID = ModuleId.of("player");

    private final Plugin plugin;
    private final ModuleMetadata metadata = ModuleMetadata.builder(ID)
            .name("SkyRealms Player")
            .version("1.0.0")
            .dependsOn(ModuleId.of("core"))
            .dependsOn(ModuleId.of("database"))
            .build();

    private ModuleState state = ModuleState.UNLOADED;
    private PlayerService playerService;

    public PlayerModule(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    @NotNull
    public ModuleId id() {
        return ID;
    }

    @Override
    @NotNull
    public ModuleMetadata metadata() {
        return metadata;
    }

    @Override
    @NotNull
    public ModuleState state() {
        return state;
    }

    @Override
    public void onEnable(@NotNull ModuleContext context) throws Exception {
        state = ModuleState.INITIALIZING;

        DatabaseService databaseService = context.services().get(DatabaseService.class);
        PlayerRepository repository = new PlayerRepository(databaseService);
        this.playerService = new PlayerService(repository, context.eventBus());

        // Register in internal ServiceRegistry & Bukkit ServicesManager
        context.services().register(PlayerService.class, playerService);
        Bukkit.getServicesManager().register(PlayerService.class, playerService, plugin, ServicePriority.High);

        // Register Bukkit listeners
        PlayerConnectionListener listener = new PlayerConnectionListener(playerService, () -> databaseService);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // Register Commands
        PluginCommand profileCmd = plugin.getServer().getPluginCommand("profile");
        if (profileCmd != null) {
            ProfileCommand handler = new ProfileCommand(playerService);
            profileCmd.setExecutor(handler);
            profileCmd.setTabCompleter(handler);
        }

        state = ModuleState.ENABLED;
        context.logger().info("SkyRealms Player module enabled. Profile & session tracking active.");
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        context.logger().info("Disabling SkyRealms Player module...");
        if (playerService != null) {
            playerService.close();
            playerService = null;
        }
        Bukkit.getServicesManager().unregister(PlayerService.class, plugin);
        state = ModuleState.DISABLED;
    }

    public PlayerService playerService() {
        return playerService;
    }
}
