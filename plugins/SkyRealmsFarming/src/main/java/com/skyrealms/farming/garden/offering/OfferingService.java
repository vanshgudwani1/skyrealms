package com.skyrealms.farming.garden.offering;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing divine offering validation, atomic item consumption,
 * and garden unlock transitions.
 */
public interface OfferingService {

    record OfferingValidationResult(
            boolean valid,
            int hayBalesFound,
            int carrotsFound,
            int sugarCaneFound,
            String message
    ) {}

    /**
     * Validates whether an inventory contains the required 3 Enchanted Hay Bales,
     * 3 Enchanted Carrots, and 3 Enchanted Sugar Cane.
     */
    @NotNull
    OfferingValidationResult validateInventory(@NotNull Inventory inventory);

    /**
     * Attempts to process a divine offering submission from a named offering chest.
     * Atomically consumes the items and triggers the divine cinematic.
     */
    @NotNull
    CompletableFuture<Boolean> processOffering(@NotNull Player player, @NotNull Block chestBlock, @NotNull UUID islandId);

    /**
     * Checks if an island has already completed the divine offering and unlocked Garden access.
     */
    boolean isGardenUnlocked(@NotNull UUID islandId);

    /**
     * Resets divine offering and garden unlock state for an island, requiring the ritual to be completed again.
     */
    void resetOfferingAndUnlock(@NotNull UUID islandId);
}
