package com.skyrealms.islands.service;

import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public service interface for island operations.
 */
public interface IslandService {

    @NotNull
    Optional<Island> getIsland(@NotNull UUID islandId);

    @NotNull
    Optional<Island> getIslandByPlayer(@NotNull UUID playerId);

    @NotNull
    Optional<Island> getIslandAt(@NotNull String worldName, int x, int z);

    @NotNull
    default Optional<Island> getIslandAt(@NotNull Location location) {
        if (location.getWorld() == null) return Optional.empty();
        return getIslandAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    boolean isProtectedWorld(@NotNull String worldName);

    @NotNull
    CompletableFuture<Island> createIsland(@NotNull UUID ownerId, @Nullable String islandName);

    @NotNull
    CompletableFuture<Boolean> deleteIsland(@NotNull UUID islandId);

    @NotNull
    CompletableFuture<Boolean> invitePlayer(@NotNull UUID islandId, @NotNull UUID actorId, @NotNull UUID targetId);

    @NotNull
    CompletableFuture<Boolean> acceptInvite(@NotNull UUID playerId, @NotNull UUID islandId);

    @NotNull
    CompletableFuture<Boolean> kickPlayer(@NotNull UUID islandId, @NotNull UUID actorId, @NotNull UUID targetId);

    @NotNull
    CompletableFuture<Boolean> leaveIsland(@NotNull UUID playerId, @NotNull UUID islandId);

    @NotNull
    CompletableFuture<Boolean> upgradeIsland(@NotNull UUID islandId, @NotNull String upgradeKey);

    @NotNull
    CompletableFuture<Void> updateSettings(@NotNull UUID islandId, @NotNull IslandSettings settings);

    @NotNull
    Location getIslandSpawnLocation(@NotNull Island island);

    void teleportHome(@NotNull Player player);

    void visitIsland(@NotNull Player visitor, @NotNull UUID targetIslandId);
}
