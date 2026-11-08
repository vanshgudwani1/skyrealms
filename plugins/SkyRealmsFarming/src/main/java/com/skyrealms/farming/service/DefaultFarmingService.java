package com.skyrealms.farming.service;

import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import com.skyrealms.farming.model.FarmingPlayerProgress;
import com.skyrealms.farming.mutation.CropMutationEngine;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of the public FarmingService.
 */
public final class DefaultFarmingService implements FarmingService {

    private final FarmingGeneratorManager generatorManager;
    private final FarmingProgressionService progressionService;
    private final FarmingUpgradeService upgradeService;
    private final CropMutationEngine mutationEngine;
    private final IslandService islandService;

    public DefaultFarmingService(
            @NotNull FarmingGeneratorManager generatorManager,
            @NotNull FarmingProgressionService progressionService,
            @NotNull FarmingUpgradeService upgradeService,
            @NotNull CropMutationEngine mutationEngine,
            @NotNull IslandService islandService
    ) {
        this.generatorManager = generatorManager;
        this.progressionService = progressionService;
        this.upgradeService = upgradeService;
        this.mutationEngine = mutationEngine;
        this.islandService = islandService;
    }

    @Override
    @NotNull
    public Optional<FarmingGenerator> getGenerator(@NotNull UUID generatorId) {
        return generatorManager.getGenerator(generatorId);
    }

    @Override
    @NotNull
    public Optional<FarmingGenerator> getGeneratorAt(@NotNull Location location) {
        return generatorManager.getGeneratorAtCore(location);
    }

    @Override
    @NotNull
    public List<FarmingGenerator> getGeneratorsByIsland(@NotNull UUID islandId) {
        return generatorManager.getGeneratorsByIsland(islandId);
    }

    @Override
    @NotNull
    public Collection<FarmingGenerator> getAllActiveGenerators() {
        return generatorManager.getAllActiveGenerators();
    }

    @Override
    @NotNull
    public CompletableFuture<FarmingGeneratorValidator.ValidationResult> createGenerator(
            @NotNull Player player,
            @NotNull Location coreLocation,
            @NotNull FarmingGeneratorTier tier
    ) {
        Optional<Island> optIsland = islandService.getIslandAt(coreLocation);
        if (optIsland.isEmpty()) {
            return CompletableFuture.completedFuture(
                    FarmingGeneratorValidator.ValidationResult.failure("Must be located on an active SkyBlock island.")
            );
        }

        return generatorManager.createAndTerraformGenerator(player, coreLocation, tier, null);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> removeGenerator(@NotNull UUID generatorId) {
        return generatorManager.removeGenerator(generatorId);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> evolveGenerator(@NotNull Player player, @NotNull UUID generatorId) {
        Optional<FarmingGenerator> optGen = generatorManager.getGenerator(generatorId);
        if (optGen.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return generatorManager.evolveGenerator(player, optGen.get());
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> dismantleGenerator(@NotNull Player player, @NotNull UUID generatorId) {
        Optional<FarmingGenerator> optGen = generatorManager.getGenerator(generatorId);
        if (optGen.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return generatorManager.dismantleGenerator(player, optGen.get());
    }

    @Override
    @NotNull
    public CompletableFuture<FarmingUpgradeService.UpgradeResult> upgradeGenerator(
            @NotNull Player player,
            @NotNull UUID generatorId,
            @NotNull String upgradeKey
    ) {
        Optional<FarmingGenerator> optGen = generatorManager.getGenerator(generatorId);
        if (optGen.isEmpty()) {
            return CompletableFuture.completedFuture(
                    FarmingUpgradeService.UpgradeResult.failure("Generator not found.")
            );
        }
        return upgradeService.purchaseUpgrade(player, optGen.get(), upgradeKey);
    }

    @Override
    @NotNull
    public FarmingPlayerProgress getPlayerProgress(@NotNull UUID playerUuid) {
        return progressionService.getProgress(playerUuid);
    }

    @Override
    public void awardFarmingXp(@NotNull Player player, long amount) {
        progressionService.awardXp(player, amount);
    }

    @Override
    @NotNull
    public FarmingProgressionService progressionService() {
        return progressionService;
    }

    @Override
    @NotNull
    public FarmingGeneratorManager generatorManager() {
        return generatorManager;
    }

    @Override
    @NotNull
    public FarmingUpgradeService upgradeService() {
        return upgradeService;
    }

    @Override
    @NotNull
    public CropMutationEngine mutationEngine() {
        return mutationEngine;
    }
}
