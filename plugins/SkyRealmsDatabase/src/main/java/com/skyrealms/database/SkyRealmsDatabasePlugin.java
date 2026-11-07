package com.skyrealms.database;

import com.skyrealms.database.module.DatabaseModule;
import com.skyrealms.platform.api.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for SkyRealmsDatabase.
 */
public final class SkyRealmsDatabasePlugin extends JavaPlugin {

    private DatabaseModule databaseModule;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        this.databaseModule = new DatabaseModule(this);

        // Register module with Core ModuleRegistry
        ModuleRegistry registry = Bukkit.getServicesManager().load(ModuleRegistry.class);
        if (registry != null) {
            registry.register(databaseModule);
            getSLF4JLogger().info("Registered 'database' module with Core ModuleRegistry.");
        } else {
            getSLF4JLogger().warn("ModuleRegistry not found in ServicesManager during onLoad.");
        }
    }

    @Override
    public void onEnable() {
        getSLF4JLogger().info("SkyRealmsDatabase plugin active.");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("SkyRealmsDatabase plugin disabled.");
    }

    public DatabaseModule databaseModule() {
        return databaseModule;
    }
}
