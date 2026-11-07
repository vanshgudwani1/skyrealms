package com.skyrealms.economy;

import com.skyrealms.economy.module.EconomyModule;
import com.skyrealms.platform.api.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Paper plugin class for SkyRealmsEconomy.
 */
public final class SkyRealmsEconomyPlugin extends JavaPlugin {

    private EconomyModule economyModule;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        this.economyModule = new EconomyModule(this);

        // Register module with Core ModuleRegistry
        ModuleRegistry registry = Bukkit.getServicesManager().load(ModuleRegistry.class);
        if (registry != null) {
            registry.register(economyModule);
            getSLF4JLogger().info("Registered 'economy' module with Core ModuleRegistry.");
        } else {
            getSLF4JLogger().warn("ModuleRegistry not found in ServicesManager during onLoad.");
        }
    }

    @Override
    public void onEnable() {
        getSLF4JLogger().info("SkyRealmsEconomy plugin active.");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("SkyRealmsEconomy plugin disabled.");
    }

    public EconomyModule economyModule() {
        return economyModule;
    }
}
