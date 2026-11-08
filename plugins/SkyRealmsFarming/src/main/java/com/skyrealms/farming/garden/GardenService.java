package com.skyrealms.farming.garden;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public service interface for the SkyRealms Garden System.
 * Coordinates spatial allocation, greenhouse domes, 16 crop plots, and region protection.
 */
public interface GardenService {

    @NotNull
    Optional<Garden> getGarden(@NotNull UUID islandId);

    @NotNull
    Optional<Garden> getGardenByPlayer(@NotNull UUID playerId);

    @NotNull
    Optional<Garden> getGardenAt(@NotNull Location location);

    @NotNull
    Collection<Garden> getAllActiveGardens();

    @NotNull
    CompletableFuture<Garden> getOrCreateGarden(@NotNull UUID islandId);

    @NotNull
    List<GardenPlot> getPlots(@NotNull UUID islandId);

    @NotNull
    Optional<GardenPlot> getPlotAt(@NotNull Location location);

    @NotNull
    CompletableFuture<Boolean> unlockPlot(@NotNull UUID islandId, int plotIndex);

    void teleportToGarden(@NotNull Player player, @NotNull UUID islandId);

    void teleportToIsland(@NotNull Player player, @NotNull UUID islandId);

    boolean isGardenWorld(@NotNull World world);

    boolean isGardenWorld(@NotNull String worldName);

    @NotNull
    GardenSpatialAllocator allocator();

    @NotNull
    GardenWorldManager worldManager();
}
