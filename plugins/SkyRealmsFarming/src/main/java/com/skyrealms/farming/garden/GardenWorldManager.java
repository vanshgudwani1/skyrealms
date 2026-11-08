package com.skyrealms.farming.garden;

import com.skyrealms.islands.world.VoidBiomeProvider;
import com.skyrealms.islands.world.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * World manager responsible for initializing and configuring the shared skyrealms_garden world.
 */
public final class GardenWorldManager {

    public static final String DEFAULT_GARDEN_WORLD = "skyrealms_garden";

    private final Plugin plugin;
    private final String worldName;
    private final Logger logger;

    public GardenWorldManager(@NotNull Plugin plugin, @NotNull String worldName) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.logger = plugin.getLogger();
    }

    public GardenWorldManager(@NotNull Plugin plugin) {
        this(plugin, DEFAULT_GARDEN_WORLD);
    }

    /**
     * Loads or creates the dedicated garden void world.
     */
    @NotNull
    public World getOrCreateGardenWorld() {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            configureWorld(world);
            return world;
        }

        logger.info("Initializing dedicated SkyRealms Garden void world '" + worldName + "'...");
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.generator(new VoidChunkGenerator());
        creator.biomeProvider(new VoidBiomeProvider(Biome.PLAINS));

        world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create garden world: " + worldName);
        }

        configureWorld(world);
        logger.info("SkyRealms Garden void world '" + worldName + "' is ready.");
        return world;
    }

    private void configureWorld(@NotNull World world) {
        world.setSpawnLocation(0, 100, 0);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(6000L); // High noon - beautiful sunlight for the greenhouse
        world.setStorm(false);
        world.setThundering(false);
    }

    @NotNull
    public String worldName() {
        return worldName;
    }
}
