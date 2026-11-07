package com.skyrealms.player;

import com.skyrealms.player.module.PlayerModule;
import com.skyrealms.platform.api.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for SkyRealmsPlayer.
 */
public final class SkyRealmsPlayerPlugin extends JavaPlugin {

    private PlayerModule playerModule;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        this.playerModule = new PlayerModule(this);

        // Register module with Core ModuleRegistry
        ModuleRegistry registry = Bukkit.getServicesManager().load(ModuleRegistry.class);
        if (registry != null) {
            registry.register(playerModule);
            getSLF4JLogger().info("Registered 'player' module with Core ModuleRegistry.");
        } else {
            getSLF4JLogger().warn("ModuleRegistry not found in ServicesManager during onLoad.");
        }
    }

    @Override
    public void onEnable() {
        getSLF4JLogger().info("SkyRealmsPlayer plugin active.");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("SkyRealmsPlayer plugin disabled.");
    }

    public PlayerModule playerModule() {
        return playerModule;
    }
}
