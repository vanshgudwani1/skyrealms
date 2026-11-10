package com.skyrealms.farming.garden.ability;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.harvest.GardenBalanceConfig;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Garden-exclusive Verdant Aura potion buffs without repeating tasks.
 * Prevents overwriting unrelated stronger/longer effects and cleans up immediately
 * whenever a player leaves their authorized Garden or changes worlds.
 */
public final class VerdantAuraManager implements Listener {

    private final GardenService gardenService;
    private final IslandService islandService;
    private final McMMOFarmingService mcMmoService;
    private final GardenBalanceConfig config;

    // Tracks players currently receiving aura-managed Haste / Speed so we never remove unrelated potion effects
    private final Set<UUID> auraActivePlayers = ConcurrentHashMap.newKeySet();

    public VerdantAuraManager(
            @NotNull GardenService gardenService,
            @NotNull IslandService islandService,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull GardenBalanceConfig config
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.mcMmoService = Objects.requireNonNull(mcMmoService, "mcMmoService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public boolean isAuraActive(@NotNull UUID playerUuid) {
        return auraActivePlayers.contains(playerUuid);
    }

    /**
     * Applies or refreshes Verdant Aura if the player is inside an authorized unlocked Garden
     * and meets the Farming level requirement.
     */
    public void refreshAura(@NotNull Player player, int farmingLevel, boolean notifyOnActivate) {
        if (!config.verdantEnabled() || farmingLevel < config.verdantUnlockLevel()) {
            deactivateAura(player, false);
            return;
        }
        if (!isAuthorizedInGarden(player, player.getLocation())) {
            deactivateAura(player, true);
            return;
        }

        boolean newlyActivated = auraActivePlayers.add(player.getUniqueId());
        int duration = config.verdantDurationTicks();

        if (farmingLevel >= config.verdantHaste1Level()) {
            int hasteAmp = farmingLevel >= config.verdantHaste2Level() ? 1 : 0;
            applyBoundedEffect(player, PotionEffectType.HASTE, duration, hasteAmp);
        }
        if (farmingLevel >= config.verdantSpeed1Level()) {
            applyBoundedEffect(player, PotionEffectType.SPEED, duration, 0);
        }

        if (newlyActivated && notifyOnActivate) {
            String buffs = (farmingLevel >= config.verdantHaste2Level() ? "Haste II" : "Haste I")
                    + (farmingLevel >= config.verdantSpeed1Level() ? " & Speed I" : "");
            player.sendActionBar(Component.text("✿ Verdant Aura active (" + buffs + ")", NamedTextColor.GREEN));
        }
    }

    /**
     * Safely removes only aura-managed effects when leaving the Garden.
     */
    public void deactivateAura(@NotNull Player player, boolean notify) {
        if (!auraActivePlayers.remove(player.getUniqueId())) {
            return;
        }

        removeIfManagedDuration(player, PotionEffectType.HASTE);
        removeIfManagedDuration(player, PotionEffectType.SPEED);

        if (notify && player.isOnline()) {
            player.sendActionBar(Component.text("✿ Verdant Aura faded outside the Garden.", NamedTextColor.GRAY));
        }
    }

    private void applyBoundedEffect(@NotNull Player player, @NotNull PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect existing = player.getPotionEffect(type);
        if (existing != null) {
            // Do not overwrite an unrelated stronger or much longer potion/beacon effect
            if (existing.getAmplifier() > amplifier || existing.getDuration() > durationTicks + 40) {
                return;
            }
            // Do not endlessly reset duration if it still has plenty of time left
            if (existing.getAmplifier() == amplifier && existing.getDuration() > Math.max(40, durationTicks / 2)) {
                return;
            }
        }
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, false, true));
    }

    private void removeIfManagedDuration(@NotNull Player player, @NotNull PotionEffectType type) {
        PotionEffect existing = player.getPotionEffect(type);
        if (existing != null && existing.getDuration() <= config.verdantDurationTicks() + 20) {
            player.removePotionEffect(type);
        }
    }

    private boolean isAuthorizedInGarden(@NotNull Player player, @NotNull Location location) {
        if (location.getWorld() == null || !gardenService.isGardenWorld(location.getWorld())) {
            return false;
        }
        Optional<Garden> optGarden = gardenService.getGardenAt(location);
        if (optGarden.isEmpty() || !optGarden.get().unlocked()) {
            return false;
        }
        if (player.isOp() || player.hasPermission("skyrealms.garden.admin")) {
            return true;
        }
        Optional<Island> optIsland = islandService.getIsland(optGarden.get().islandId());
        return optIsland.isPresent() && optIsland.get().isMember(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isAuthorizedInGarden(player, player.getLocation())) {
            refreshAura(player, mcMmoService.getFarmingLevel(player), true);
        } else {
            auraActivePlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (gardenService.isGardenWorld(player.getWorld())) {
            refreshAura(player, mcMmoService.getFarmingLevel(player), true);
        } else {
            deactivateAura(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(@NotNull PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || !isAuthorizedInGarden(player, to)) {
            deactivateAura(player, true);
        } else {
            refreshAura(player, mcMmoService.getFarmingLevel(player), true);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        deactivateAura(event.getPlayer(), false);
    }
}

