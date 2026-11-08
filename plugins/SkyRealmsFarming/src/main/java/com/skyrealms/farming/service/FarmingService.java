package com.skyrealms.farming.service;

import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import com.skyrealms.farming.model.FarmingPlayerProgress;
import com.skyrealms.farming.mutation.CropMutationEngine;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public service interface for the SkyRealms Farming Engine.
 * Exposes methods for generator management, progression tracking, and upgrades.
 */
public interface FarmingService {

    @NotNull
    Optional<FarmingGenerator> getGenerator(@NotNull UUID generatorId);

    @NotNull
    Optional<FarmingGenerator> getGeneratorAt(@NotNull Location location);

    @NotNull
    List<FarmingGenerator> getGeneratorsByIsland(@NotNull UUID islandId);

    @NotNull
    Collection<FarmingGenerator> getAllActiveGenerators();

    @NotNull
    CompletableFuture<FarmingGeneratorValidator.ValidationResult> createGenerator(
            @NotNull Player player,
            @NotNull Location coreLocation,
            @NotNull FarmingGeneratorTier tier
    );

    @NotNull
    CompletableFuture<Boolean> removeGenerator(@NotNull UUID generatorId);

    @NotNull
    CompletableFuture<FarmingUpgradeService.UpgradeResult> upgradeGenerator(
            @NotNull Player player,
            @NotNull UUID generatorId,
            @NotNull String upgradeKey
    );

    @NotNull
    FarmingPlayerProgress getPlayerProgress(@NotNull UUID playerUuid);

    void awardFarmingXp(@NotNull Player player, long amount);

    @NotNull
    FarmingProgressionService progressionService();

    @NotNull
    FarmingGeneratorManager generatorManager();

    @NotNull
    FarmingUpgradeService upgradeService();

    @NotNull
    CropMutationEngine mutationEngine();
}
