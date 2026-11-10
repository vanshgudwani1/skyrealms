package com.skyrealms.placeholders;

import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.platform.integrations.CustomItemStatProvider;
import com.skyrealms.platform.integrations.EconomyProvider;
import com.skyrealms.platform.integrations.PermissionsProvider;
import com.skyrealms.platform.integrations.PetStatProvider;
import com.skyrealms.player.model.PlayerProfile;
import com.skyrealms.player.service.PlayerService;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Runtime service locator resolving SkyRealms, Vault, and mcMMO services via Bukkit's {@link ServicesManager}.
 */
public final class LiveSkyRealmsServiceAccess implements SkyRealmsServiceAccess {

    private final ServicesManager servicesManager;

    public LiveSkyRealmsServiceAccess() {
        this(Bukkit.getServicesManager());
    }

    public LiveSkyRealmsServiceAccess(@NotNull ServicesManager servicesManager) {
        this.servicesManager = servicesManager;
    }

    @Override
    @NotNull
    public Optional<PlayerService> playerService() {
        return loadOptional(PlayerService.class);
    }

    @Override
    @NotNull
    public Optional<EconomyService> economyService() {
        return loadOptional(EconomyService.class);
    }

    @Override
    @NotNull
    public Optional<EconomyProvider> economyProvider() {
        return loadOptional(EconomyProvider.class);
    }

    @Override
    @NotNull
    public Optional<IslandService> islandService() {
        return loadOptional(IslandService.class);
    }

    @Override
    @NotNull
    public Optional<GardenService> gardenService() {
        return loadOptional(GardenService.class);
    }

    @Override
    @NotNull
    public Optional<McMMOFarmingService> mcMmoFarmingService() {
        return loadOptional(McMMOFarmingService.class);
    }

    @Override
    @NotNull
    public Optional<GardenAbilityService> gardenAbilityService() {
        return loadOptional(GardenAbilityService.class);
    }

    @Override
    @NotNull
    public Optional<CustomItemStatProvider> itemStatProvider() {
        return loadOptional(CustomItemStatProvider.class);
    }

    @Override
    @NotNull
    public Optional<PetStatProvider> petStatProvider() {
        return loadOptional(PetStatProvider.class);
    }

    @Override
    @NotNull
    public Optional<PermissionsProvider> permissionsProvider() {
        return loadOptional(PermissionsProvider.class);
    }

    @Override
    @NotNull
    public Optional<Permission> vaultPermission() {
        try {
            return Optional.ofNullable(servicesManager.load(Permission.class));
        } catch (NoClassDefFoundError | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    @NotNull
    public Collection<? extends Player> onlinePlayers() {
        return Bukkit.getOnlinePlayers();
    }

    @Override
    public int maxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    @Override
    @Nullable
    public String resolvePlayerName(@NotNull UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        Optional<String> cached = playerService()
                .flatMap(svc -> svc.getCached(uuid))
                .map(PlayerProfile::username);
        if (cached.isPresent()) {
            return cached.get();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName();
    }

    @Override
    public int getMcMmoSkillLevel(@NotNull Player player, @NotNull String skillKey) {
        try {
            PrimarySkillType skill = PrimarySkillType.valueOf(skillKey.toUpperCase(Locale.ROOT));
            return Math.max(0, ExperienceAPI.getLevel(player, skill));
        } catch (NoClassDefFoundError | RuntimeException ignored) {
            return 0;
        }
    }

    @NotNull
    private <T> Optional<T> loadOptional(@NotNull Class<T> serviceClass) {
        try {
            return Optional.ofNullable(servicesManager.load(serviceClass));
        } catch (NoClassDefFoundError | RuntimeException ignored) {
            return Optional.empty();
        }
    }
}

