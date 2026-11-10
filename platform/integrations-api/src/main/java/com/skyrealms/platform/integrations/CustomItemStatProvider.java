package com.skyrealms.platform.integrations;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Platform contract allowing profession and combat plugins (Farming, Mining, Slayers, Fishing)
 * to inspect SkyRealms custom items and player-equipped gear stats without hard plugin coupling.
 */
public interface CustomItemStatProvider {

    @NotNull
    Optional<String> getCustomItemId(@Nullable ItemStack item);

    @NotNull
    SkyItemStats readItemStats(@Nullable ItemStack item);

    @NotNull
    SkyItemStats getPlayerEquippedStats(@NotNull Player player);

    @NotNull
    Optional<ItemStack> createCustomItem(@NotNull String itemId, int amount);
}

