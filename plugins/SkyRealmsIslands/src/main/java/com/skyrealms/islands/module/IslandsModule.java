package com.skyrealms.islands.module;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.islands.command.IslandCommand;
import com.skyrealms.islands.gui.IslandGui;
import com.skyrealms.islands.protection.IslandProtectionService;
import com.skyrealms.islands.repository.IslandRepository;
import com.skyrealms.islands.service.IslandManager;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.islands.world.VoidChunkGenerator;
import com.skyrealms.platform.api.module.*;
import com.skyrealms.platform.common.command.DynamicCommandRegistrar;
import com.skyrealms.platform.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * SkyRealms Islands Module.
 * Provides custom void world generation, spatial island allocation, protection, and upgrades.
 */
public final class IslandsModule implements SkyModule {

    public static final ModuleId ID = ModuleId.of("islands");

    private final JavaPlugin plugin;
    private final ModuleMetadata metadata;
    private ModuleState state = ModuleState.UNLOADED;

    private IslandManager islandManager;

    public IslandsModule(@NotNull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.metadata = ModuleMetadata.builder(ID)
                .name("SkyRealms Islands")
                .version("1.0.0")
                .dependsOn(ModuleId.of("database"))
                .dependsOn(ModuleId.of("player"))
                .dependsOn(ModuleId.of("economy"))
                .build();
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
        EconomyService economyService = context.services().get(EconomyService.class);

        String worldName = plugin.getConfig().getString("world.name", "skyrealms_islands");
        int spacing = plugin.getConfig().getInt("grid.spacing", 500);
        int defaultSize = plugin.getConfig().getInt("grid.default-size", 100);
        int centerY = plugin.getConfig().getInt("grid.center-y", 100);

        // 1. Initialize dedicated void world if not yet loaded
        World islandWorld = Bukkit.getWorld(worldName);
        if (islandWorld == null) {
            context.logger().info("Initializing dedicated void world '{}'...", worldName);
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.generator(new VoidChunkGenerator());
            islandWorld = creator.createWorld();
        }

        if (islandWorld != null) {
            islandWorld.setSpawnLocation(0, 100, 0);
            context.logger().info("Void world '{}' is ready.", worldName);
        }

        // 2. Initialize Island Repository & Manager
        IslandRepository repository = new IslandRepository(databaseService);
        this.islandManager = new IslandManager(
                plugin,
                repository,
                context.eventBus(),
                economyService,
                worldName,
                spacing,
                defaultSize,
                centerY
        );

        // 3. Register in Service Registries
        context.services().register(IslandService.class, islandManager);
        Bukkit.getServicesManager().register(IslandService.class, islandManager, plugin, ServicePriority.High);

        // 4. Register GUI & Protection Listeners
        IslandGui gui = new IslandGui(islandManager);
        Bukkit.getPluginManager().registerEvents(gui, plugin);

        IslandProtectionService protection = new IslandProtectionService(islandManager);
        Bukkit.getPluginManager().registerEvents(protection, plugin);

        // 5. Register Commands
        IslandCommand handler = new IslandCommand(islandManager, gui);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "island",
                "Core SkyBlock island management command",
                "/island [help|create|home|invite|accept|kick|leave|visit|settings|info|members|name|reset|delete|gui]",
                java.util.List.of("is"),
                "skyrealms.island.use",
                org.bukkit.permissions.PermissionDefault.TRUE,
                "§cYou do not have permission to use /island.",
                handler
        );

        // 6. Initialize Celestial Dragon Nest Lobby Service & Commands
        com.skyrealms.islands.lobby.LobbyService lobbyService = new com.skyrealms.islands.lobby.LobbyService(plugin);
        com.skyrealms.islands.lobby.LobbyListener lobbyListener = new com.skyrealms.islands.lobby.LobbyListener(plugin, lobbyService, gui);
        Bukkit.getPluginManager().registerEvents(lobbyListener, plugin);

        com.skyrealms.islands.lobby.LobbyCommand lobbyCommand = new com.skyrealms.islands.lobby.LobbyCommand(plugin, lobbyService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "spawn",
                "Teleport to the Celestial Dragon Nest Lobby",
                "/spawn",
                java.util.List.of("lobby", "hub"),
                "skyrealms.spawn.use",
                org.bukkit.permissions.PermissionDefault.TRUE,
                "§cYou do not have permission to use /spawn.",
                lobbyCommand
        );

        // Bootstrap lobby generation if needed
        lobbyService.initLobby(false);

        state = ModuleState.ENABLED;
        context.logger().info("SkyRealms Islands module successfully enabled.");
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        context.logger().info("Disabling SkyRealms Islands module...");

        if (islandManager != null) {
            islandManager.close();
            islandManager = null;
        }

        state = ModuleState.DISABLED;
        context.logger().info("SkyRealms Islands module disabled.");
    }

    public IslandManager islandManager() {
        return islandManager;
    }
}
