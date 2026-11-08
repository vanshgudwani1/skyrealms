package com.skyrealms.farming;

import com.skyrealms.farming.module.FarmingModule;
import com.skyrealms.platform.api.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Paper plugin class for SkyRealmsFarming.
 */
public final class SkyRealmsFarmingPlugin extends JavaPlugin {

    private FarmingModule farmingModule;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        this.farmingModule = new FarmingModule(this);

        // Register module with Core ModuleRegistry
        ModuleRegistry registry = Bukkit.getServicesManager().load(ModuleRegistry.class);
        if (registry != null) {
            registry.register(farmingModule);
            getSLF4JLogger().info("Registered 'farming' module with Core ModuleRegistry.");
        } else {
            getSLF4JLogger().warn("ModuleRegistry not found in ServicesManager during onLoad.");
        }
    }

    @Override
    public void onEnable() {
        getSLF4JLogger().info("SkyRealmsFarming plugin active.");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("SkyRealmsFarming plugin disabled.");
    }

    public FarmingModule farmingModule() {
        return farmingModule;
    }
}
