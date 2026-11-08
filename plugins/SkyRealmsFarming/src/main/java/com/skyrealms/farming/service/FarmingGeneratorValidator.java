package com.skyrealms.farming.service;

import com.skyrealms.farming.model.CropNode;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import com.skyrealms.islands.model.Island;
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
 * Validates 5x5 ground footprints, island permissions, and structural clearance for farming generators.
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

    public record ValidationResult(
            boolean valid,
            @NotNull String message,
            @NotNull List<CropNode> detectedNodes,
            @NotNull List<Location> invalidLocations
    ) {
        public static ValidationResult success(@NotNull List<CropNode> nodes) {
            return new ValidationResult(true, "Structure is valid.", nodes, Collections.emptyList());
        }

        public static ValidationResult failure(@NotNull String message, @NotNull List<Location> invalidLocations) {
            return new ValidationResult(false, message, Collections.emptyList(), invalidLocations);
        }

        public static ValidationResult failure(@NotNull String message) {
            return failure(message, Collections.emptyList());
        }
    }

    /**
     * Validates whether a 5x5 flat ground area exists and is eligible for generator placement.
     */
    @NotNull
    public ValidationResult validatePlacement(
            @NotNull Player player,
            @NotNull Location coreLocation,
            @NotNull FarmingGeneratorTier tier,
            int currentIslandGeneratorCount
    ) {
        World world = coreLocation.getWorld();
        if (world == null) {
            return ValidationResult.failure("Invalid world.");
        }

        // 1. Island ownership & role check
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
            return ValidationResult.failure("Visitors cannot place or modify farming generators.");
        }

        if (currentIslandGeneratorCount >= maxGeneratorsPerIsland && !player.hasPermission("skyrealms.admin")) {
            return ValidationResult.failure("This island has reached the maximum limit of " + maxGeneratorsPerIsland + " generators.");
        }

        // 2. Strict 5x5 Flat Ground Validation
        int cx = coreLocation.getBlockX();
        int cy = coreLocation.getBlockY();
        int cz = coreLocation.getBlockZ();

        List<Location> invalidLocs = new ArrayList<>();
        List<CropNode> cropNodes = new ArrayList<>();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int groundX = cx + dx;
                int groundY = cy - 1;
                int groundZ = cz + dz;

                Block groundBlock = world.getBlockAt(groundX, groundY, groundZ);
                Block cropLayerBlock = world.getBlockAt(groundX, cy, groundZ);

                // Check ground material
                boolean validGround = isValidGroundForTier(groundBlock.getType(), tier);
                if (!validGround) {
                    invalidLocs.add(new Location(world, groundX + 0.5, groundY + 1.1, groundZ + 0.5));
                }

                // Check crop clearance above ground (except center core position)
                if (dx != 0 || dz != 0) {
                    if (!cropLayerBlock.getType().isAir() && !tier.supportsCrop(cropLayerBlock.getType())) {
                        invalidLocs.add(new Location(world, groundX + 0.5, cy + 0.5, groundZ + 0.5));
                    } else if (validGround) {
                        cropNodes.add(new CropNode(world.getName(), groundX, cy, groundZ, tier.primaryDropMaterial()));
                    }
                }
            }
        }

        if (!invalidLocs.isEmpty()) {
            return ValidationResult.failure(
                    "Requires a flat 5x5 unobstructed area of " + getGroundDescriptionForTier(tier) + ".",
                    invalidLocs
            );
        }

        return ValidationResult.success(cropNodes);
    }

    private boolean isValidGroundForTier(Material material, FarmingGeneratorTier tier) {
        if (tier == FarmingGeneratorTier.TIER_4_TROPICAL) {
            return material == Material.SAND || material == Material.RED_SAND
                    || material == Material.DIRT || material == Material.GRASS_BLOCK || material == Material.FARMLAND;
        } else if (tier == FarmingGeneratorTier.TIER_5_MYSTIC) {
            return material == Material.SOUL_SAND || material == Material.SOUL_SOIL
                    || material == Material.END_STONE || material == Material.DIRT || material == Material.GRASS_BLOCK;
        } else {
            return material == Material.DIRT || material == Material.GRASS_BLOCK
                    || material == Material.COARSE_DIRT || material == Material.ROOTED_DIRT || material == Material.FARMLAND;
        }
    }

    private String getGroundDescriptionForTier(FarmingGeneratorTier tier) {
        if (tier == FarmingGeneratorTier.TIER_4_TROPICAL) return "Sand or Dirt";
        if (tier == FarmingGeneratorTier.TIER_5_MYSTIC) return "Soul Sand or Dirt";
        return "Dirt or Grass";
    }
}
