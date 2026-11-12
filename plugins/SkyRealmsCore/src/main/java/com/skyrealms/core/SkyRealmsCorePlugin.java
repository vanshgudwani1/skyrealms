package com.skyrealms.core;

import com.skyrealms.core.command.SkyRealmsCommand;
import com.skyrealms.core.command.WorldCommand;
import com.skyrealms.core.module.CoreModule;
import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.api.module.ModuleLifecycleManager;
import com.skyrealms.platform.api.module.ModuleRegistry;
import com.skyrealms.platform.api.service.ServiceRegistry;
import com.skyrealms.platform.common.command.DynamicCommandRegistrar;
import com.skyrealms.platform.common.event.DefaultEventBus;
import com.skyrealms.platform.common.module.DefaultModuleLifecycleManager;
import com.skyrealms.platform.common.module.DefaultModuleRegistry;
import com.skyrealms.platform.common.module.DependencyResolver;
import com.skyrealms.platform.common.module.DirectedGraphDependencyResolver;
import com.skyrealms.platform.common.service.ConcurrentServiceRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;

/**
 * Root Paper plugin for SkyRealms.
 */
public final class SkyRealmsCorePlugin extends JavaPlugin implements Listener {

    private ConcurrentServiceRegistry serviceRegistry;
    private DefaultEventBus eventBus;
    private DefaultModuleRegistry moduleRegistry;
    private DirectedGraphDependencyResolver dependencyResolver;
    private DefaultModuleLifecycleManager lifecycleManager;

    @Override
    public void onLoad() {
        saveDefaultConfig();

        Logger slf4jLogger = getSLF4JLogger();
        slf4jLogger.info("Bootstrapping SkyRealms Core runtime...");

        this.serviceRegistry = new ConcurrentServiceRegistry();
        this.eventBus = new DefaultEventBus();
        this.moduleRegistry = new DefaultModuleRegistry();
        this.dependencyResolver = new DirectedGraphDependencyResolver();

        File modulesDir = new File(getDataFolder(), "modules");
        if (!modulesDir.exists()) {
            modulesDir.mkdirs();
        }

        this.lifecycleManager = new DefaultModuleLifecycleManager(
                moduleRegistry,
                dependencyResolver,
                serviceRegistry,
                eventBus,
                modulesDir.toPath(),
                slf4jLogger
        );

        // Register core platform singletons in ServiceRegistry
        serviceRegistry.register(ServiceRegistry.class, serviceRegistry);
        serviceRegistry.register(EventBus.class, eventBus);
        serviceRegistry.register(ModuleRegistry.class, moduleRegistry);
        serviceRegistry.register(DependencyResolver.class, dependencyResolver);
        serviceRegistry.register(ModuleLifecycleManager.class, lifecycleManager);

        // Register in Bukkit ServicesManager for downstream Paper plugins
        getServer().getServicesManager().register(ServiceRegistry.class, serviceRegistry, this, ServicePriority.Highest);
        getServer().getServicesManager().register(EventBus.class, eventBus, this, ServicePriority.Highest);
        getServer().getServicesManager().register(ModuleRegistry.class, moduleRegistry, this, ServicePriority.Highest);
        getServer().getServicesManager().register(ModuleLifecycleManager.class, lifecycleManager, this, ServicePriority.Highest);

        // Register default CoreModule
        moduleRegistry.register(new CoreModule());
    }

    @Override
    public void onEnable() {
        Logger slf4jLogger = getSLF4JLogger();
        slf4jLogger.info("Starting SkyRealms Core plugin...");

        // Register event listener for server boot completion
        getServer().getPluginManager().registerEvents(this, this);

        // Register commands
        SkyRealmsCommand commandHandler = new SkyRealmsCommand(this, moduleRegistry, lifecycleManager);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "skyrealms",
                "Main administrative command for SkyRealms",
                "/skyrealms [help|modules|version|reload]",
                java.util.List.of("sr"),
                "skyrealms.admin",
                org.bukkit.permissions.PermissionDefault.OP,
                "§cYou do not have permission to use /skyrealms.",
                commandHandler
        );

        WorldCommand worldCommand = new WorldCommand();
        DynamicCommandRegistrar.register(
                "skyrealms",
                "tpworld",
                "Load and teleport to any server world",
                "/tpworld [world]",
                java.util.List.of("visitworld", "worlds"),
                "skyrealms.admin",
                org.bukkit.permissions.PermissionDefault.OP,
                "§cYou do not have permission to use /tpworld.",
                worldCommand
        );

        slf4jLogger.info("SkyRealms Core plugin successfully started. Awaiting complete server load to activate modules.");
    }

    @EventHandler
    public void onServerLoad(@NotNull ServerLoadEvent event) {
        if (getConfig().getBoolean("modules.auto-enable-on-boot", true)) {
            getSLF4JLogger().info("Server load completed ({}); activating all registered SkyRealms modules...", event.getType());
            lifecycleManager.enableAll();
        }
        for (String extraWorld : getConfig().getStringList("extra-worlds")) {
            if (WorldCommand.loadWorldIfPresent(extraWorld) != null) {
                getSLF4JLogger().info("Loaded configured extra world '{}'.", extraWorld);
            }
        }
    }

    @Override
    public void onDisable() {
        Logger slf4jLogger = getSLF4JLogger();
        slf4jLogger.info("Stopping SkyRealms Core plugin...");

        if (lifecycleManager != null) {
            lifecycleManager.disableAll();
        }

        if (eventBus != null) {
            eventBus.close();
        }

        if (serviceRegistry != null) {
            serviceRegistry.clear();
        }

        getServer().getServicesManager().unregisterAll(this);
        slf4jLogger.info("SkyRealms Core shutdown complete.");
    }

    public ServiceRegistry serviceRegistry() {
        return serviceRegistry;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public ModuleRegistry moduleRegistry() {
        return moduleRegistry;
    }

    public ModuleLifecycleManager lifecycleManager() {
        return lifecycleManager;
    }
}
