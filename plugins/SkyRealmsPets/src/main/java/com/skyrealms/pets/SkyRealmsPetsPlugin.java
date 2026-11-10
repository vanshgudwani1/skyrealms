package com.skyrealms.pets;

import com.skyrealms.pets.module.PetsModule;
import com.skyrealms.platform.api.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Paper plugin entry point for SkyRealmsPets.
 */
public final class SkyRealmsPetsPlugin extends JavaPlugin {

    private PetsModule petsModule;

    @Override
    public void onLoad() {
        this.petsModule = new PetsModule(this);

        ModuleRegistry registry = Bukkit.getServicesManager().load(ModuleRegistry.class);
        if (registry != null) {
            registry.register(petsModule);
            getSLF4JLogger().info("Registered 'pets' module with Core ModuleRegistry.");
        } else {
            getSLF4JLogger().warn("ModuleRegistry not found in ServicesManager during onLoad.");
        }
    }

    @Override
    public void onEnable() {
        getSLF4JLogger().info("SkyRealmsPets plugin active.");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("SkyRealmsPets plugin disabled.");
    }

    public PetsModule petsModule() {
        return petsModule;
    }
}

