package com.skyrealms.platform.integrations;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Shared contract exposing active SkyRealms pet bonuses and pet XP awards.
 */
public interface PetStatProvider {

    @NotNull
    SkyItemStats getActivePetStats(@NotNull Player player);

    void awardActivePetXp(@NotNull Player player, double xpAmount);
}

