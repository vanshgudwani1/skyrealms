package com.skyrealms.platform.integrations;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Shared contract exposing active custom enchantment queries and harvest helpers
 * (such as Telekinesis direct-to-inventory drops and Replenish seed/replant handling).
 */
public interface CustomEnchantProvider {

    String ENCHANT_TELEKINESIS = "telekinesis";
    String ENCHANT_REPLENISH = "replenish";

    boolean hasCustomEnchant(@Nullable ItemStack item, @NotNull String enchantId);

    default boolean hasTelekinesis(@Nullable ItemStack item) {
        return hasCustomEnchant(item, ENCHANT_TELEKINESIS);
    }

    default boolean hasReplenish(@Nullable ItemStack item) {
        return hasCustomEnchant(item, ENCHANT_REPLENISH);
    }

    /**
     * Delivers items directly to the player's inventory (dropping any overflow at {@code fallbackDropLoc})
     * when {@code directToInventory} is true, or spawns all items naturally otherwise.
     */
    void deliverOrDrop(
            @NotNull Player player,
            @NotNull Location fallbackDropLoc,
            @NotNull Collection<ItemStack> items,
            boolean directToInventory
    );

    /**
     * Replants an AoE-harvested ageable crop at age 0 if the player has {@code replenish}
     * and a matching seed is available in {@code mutableDrops} or the player's inventory.
     */
    void handleAoEReplenish(
            @NotNull Player player,
            @NotNull ItemStack tool,
            @NotNull Block block,
            @NotNull BlockData harvestedData,
            @NotNull List<ItemStack> mutableDrops
    );
}

