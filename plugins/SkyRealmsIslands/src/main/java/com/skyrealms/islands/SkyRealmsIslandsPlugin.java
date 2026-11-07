package com.skyrealms.islands;

import com.skyrealms.islands.module.IslandsModule;
import com.skyrealms.islands.world.VoidChunkGenerator;
import com.skyrealms.platform.api.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main Paper plugin class for SkyRealmsIslands.
 */
public final class SkyRealmsIslandsPlugin extends JavaPlugin {

    private IslandsModule islandsModule;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        this.islandsModule = new IslandsModule(this);

        // Register module with Core ModuleRegistry
        ModuleRegistry registry = Bukkit.getServicesManager().load(ModuleRegistry.class);
        if (registry != null) {
            registry.register(islandsModule);
            getSLF4JLogger().info("Registered 'islands' module with Core ModuleRegistry.");
        } else {
            getSLF4JLogger().warn("ModuleRegistry not found in ServicesManager during onLoad.");
        }
    }

    @Override
    public void onEnable() {
        getSLF4JLogger().info("SkyRealmsIslands plugin active.");
    }

    @Override
    public void onDisable() {
        getSLF4JLogger().info("SkyRealmsIslands plugin disabled.");
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return new VoidChunkGenerator();
    }

    public IslandsModule islandsModule() {
        return islandsModule;
    }
}
