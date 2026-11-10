package com.skyrealms.placeholders;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.platform.integrations.CustomItemStatProvider;
import com.skyrealms.platform.integrations.EconomyProvider;
import com.skyrealms.platform.integrations.PermissionsProvider;
import com.skyrealms.platform.integrations.PetStatProvider;
import com.skyrealms.player.service.PlayerService;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction over live Bukkit services and optional SkyRealms/third-party plugins
 * so placeholder resolution stays decoupled and fully unit-testable without blocking DB calls.
 */
public interface SkyRealmsServiceAccess {

    @NotNull
    Optional<PlayerService> playerService();

    @NotNull
    Optional<EconomyService> economyService();

    @NotNull
    Optional<EconomyProvider> economyProvider();

    @NotNull
    Optional<IslandService> islandService();

    @NotNull
    Optional<GardenService> gardenService();

    @NotNull
    Optional<McMMOFarmingService> mcMmoFarmingService();

    @NotNull
    Optional<GardenAbilityService> gardenAbilityService();

    @NotNull
    Optional<CustomItemStatProvider> itemStatProvider();

    @NotNull
    Optional<PetStatProvider> petStatProvider();

    @NotNull
    Optional<PermissionsProvider> permissionsProvider();

    @NotNull
    Optional<Permission> vaultPermission();

    @NotNull
    Collection<? extends Player> onlinePlayers();

    int maxPlayers();

    @Nullable
    String resolvePlayerName(@NotNull UUID uuid);

    /**
     * Reads a non-herbalism mcMMO primary skill level (e.g. MINING, SWORDS, AXES, ARCHERY, UNARMED).
     */
    int getMcMmoSkillLevel(@NotNull Player player, @NotNull String skillKey);

    @Nullable
    default String resolveOfflineName(@NotNull OfflinePlayer player) {
        return player.getName() != null ? player.getName() : resolvePlayerName(player.getUniqueId());
    }
}

