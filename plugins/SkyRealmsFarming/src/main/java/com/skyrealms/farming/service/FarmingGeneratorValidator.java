package com.skyrealms.farming.service;

import com.skyrealms.farming.model.CropNode;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.model.IslandRole;
import com.skyrealms.islands.service.IslandService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Validates physical structure, island ownership, and block layouts for farming generators.
 */
public final class FarmingGeneratorValidator {

    private final IslandService islandService;
    private final Material coreMaterial;
    private final int maxGeneratorsPerIsland;

    public FarmingGeneratorValidator(
            @NotNull IslandService islandService,
            @NotNull Material coreMaterial,
            int maxGeneratorsPerIsland
    ) {
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.coreMaterial = Objects.requireNonNull(coreMaterial, "coreMaterial cannot be null");
        this.maxGeneratorsPerIsland = Math.max(1, maxGeneratorsPerIsland);
    }

    public record ValidationResult(boolean valid, @NotNull String message, @NotNull List<CropNode> detectedNodes) {
        public static ValidationResult success(@NotNull List<CropNode> nodes) {
            return new ValidationResult(true, "Structure is valid.", nodes);
        }

        public static ValidationResult failure(@NotNull String message) {
            return new ValidationResult(false, message, Collections.emptyList());
        }
    }

    /**
     * Validates whether a physical generator can be registered or activated at the given core location.
     */
    @NotNull
    public ValidationResult validateStructure(
            @NotNull Player player,
            @NotNull Location coreLocation,
            @NotNull FarmingGeneratorTier tier,
            int currentIslandGeneratorCount,
            int capacityLimit
    ) {
        World world = coreLocation.getWorld();
        if (world == null) {
            return ValidationResult.failure("World not found.");
        }

        // 1. Core block material check
        Block coreBlock = coreLocation.getBlock();
        if (coreBlock.getType() != coreMaterial) {
            return ValidationResult.failure("Core block must be " + coreMaterial.name() + ".");
        }

        // 2. Island ownership and boundary check
        Optional<Island> optIsland = islandService.getIslandAt(coreLocation);
        if (optIsland.isEmpty()) {
            return ValidationResult.failure("Farming Generators must be built inside a SkyBlock island.");
        }

        Island island = optIsland.get();
        if (!island.isMember(player.getUniqueId()) && !player.hasPermission("skyrealms.admin")) {
            return ValidationResult.failure("You are not an authorized member of this island.");
        }

        IslandRole role = island.getRole(player.getUniqueId());
        if (role == IslandRole.VISITOR && !player.hasPermission("skyrealms.admin")) {
            return ValidationResult.failure("Visitors cannot activate or modify farming generators.");
        }

        if (currentIslandGeneratorCount >= maxGeneratorsPerIsland && !player.hasPermission("skyrealms.admin")) {
            return ValidationResult.failure("This island has reached the maximum limit of " + maxGeneratorsPerIsland + " generators.");
        }

        // 3. Scan physical structure in 5x5 footprint around core
        int cx = coreBlock.getX();
        int cy = coreBlock.getY();
        int cz = coreBlock.getZ();

        boolean hasWater = false;
        List<CropNode> validNodes = new ArrayList<>();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue; // Skip core position

                Block block = world.getBlockAt(cx + dx, cy, cz + dz);
                Block below = world.getBlockAt(cx + dx, cy - 1, cz + dz);

                // Water check within radius 2
                if (block.getType() == Material.WATER || below.getType() == Material.WATER) {
                    hasWater = true;
                }

                // Soil check
                if (tier.requiredSoilMaterials().contains(below.getType())) {
                    // Check supporting block under soil
                    Block baseBlock = world.getBlockAt(cx + dx, cy - 2, cz + dz);
                    if (!baseBlock.getType().isSolid()) {
                        continue; // Soil must have solid foundation
                    }

                    // Check crop position directly above soil
                    if (block.getType().isAir() || tier.supportsCrop(block.getType())) {
                        validNodes.add(new CropNode(world.getName(), cx + dx, cy, cz + dz, tier.primaryDropMaterial()));
                    }
                }
            }
        }

        // Water requirement (Tier 1-3 requires water irrigation)
        if (tier.requiredSoilMaterials().contains(Material.FARMLAND) && !hasWater) {
            return ValidationResult.failure("No water irrigation source found within 2 blocks of the farmland.");
        }

        if (validNodes.isEmpty()) {
            return ValidationResult.failure("No valid farmland/soil nodes found around the core for tier " + tier.displayName() + ".");
        }

        // Truncate to generator capacity limit
        int maxAllowed = Math.min(validNodes.size(), capacityLimit);
        List<CropNode> activeNodes = validNodes.subList(0, maxAllowed);

        return ValidationResult.success(activeNodes);
    }
}
