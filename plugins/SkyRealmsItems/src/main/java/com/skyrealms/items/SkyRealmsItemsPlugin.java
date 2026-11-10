package com.skyrealms.items;

import com.skyrealms.items.module.ItemsModule;
import com.skyrealms.platform.api.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Paper plugin entry point for SkyRealmsItems.
 */
public final class SkyRealmsItemsPlugin extends JavaPlugin {

    private ItemsModule itemsModule;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        this.itemsModule = new ItemsModule(this);

        ModuleRegistry registry = Bukkit.getServicesManager().load(ModuleRegistry.class);
        if (registry != null) {
            registry.register(itemsModule);
            getSLF4JLogger().info("Registered 'items' module with Core ModuleRegistry.");
        } else {
            getSLF4JLogger().warn("ModuleRegistry not found in ServicesManager during onLoad.");
        }
    }

    @Override
    public void onEnable() {
        getSLF4JLogger().info("SkyRealmsItems plugin active.");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("SkyRealmsItems plugin disabled.");
    }

    public ItemsModule itemsModule() {
        return itemsModule;
    }
}

