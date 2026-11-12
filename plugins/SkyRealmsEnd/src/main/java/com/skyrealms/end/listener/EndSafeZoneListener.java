package com.skyrealms.end.listener;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.district.EndDistrict;
import com.skyrealms.end.district.EndVillageBounds;
import com.skyrealms.end.quest.EndQuestService;
import com.skyrealms.end.quest.QuestObjectiveType;
import com.skyrealms.end.service.EndProfileRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces SkyRealms End Village safe-zone protection, void rescue, and district discovery alerts.
 */
public final class EndSafeZoneListener implements Listener {

    private static final String ADMIN_BYPASS_PERM = "skyrealms.end.admin";
    private static final long RESCUE_COOLDOWN_MS = 1500L;

    private final Plugin plugin;
    private final EndVillageConfig config;
    private final EndVillageBounds bounds;
    private final EndProfileRepository profileRepository;
    private final Logger logger;

    private final Map<UUID, EndDistrict> lastDistrictByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rescueCooldowns = new ConcurrentHashMap<>();
    private EndQuestService questService;

    public EndSafeZoneListener(
            @NotNull Plugin plugin,
            @NotNull EndVillageConfig config,
            @NotNull EndVillageBounds bounds,
            @NotNull EndProfileRepository profileRepository,
            @NotNull Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.bounds = Objects.requireNonNull(bounds, "bounds cannot be null");
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    public void setQuestService(@org.jetbrains.annotations.Nullable EndQuestService questService) {
        this.questService = questService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        if (!config.safeZone().protectBlocks()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission(ADMIN_BYPASS_PERM)) {
            return;
        }
        Location loc = event.getBlock().getLocation();
        if (bounds.isInSafeZone(loc.getWorld() != null ? loc.getWorld().getName() : null, loc.getX(), loc.getY(), loc.getZ())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("✦ The ancient stonework of the End Village is protected.", NamedTextColor.LIGHT_PURPLE));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (!config.safeZone().protectBlocks()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission(ADMIN_BYPASS_PERM)) {
            return;
        }
        Location loc = event.getBlock().getLocation();
        if (bounds.isInSafeZone(loc.getWorld() != null ? loc.getWorld().getName() : null, loc.getX(), loc.getY(), loc.getZ())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("✦ You cannot alter structures within the End Village.", NamedTextColor.LIGHT_PURPLE));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(@NotNull EntityExplodeEvent event) {
        if (!config.safeZone().preventExplosions()) {
            return;
        }
        Location center = event.getLocation();
        if (bounds.isInSafeZone(center.getWorld() != null ? center.getWorld().getName() : null, center.getX(), center.getY(), center.getZ())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(@NotNull CreatureSpawnEvent event) {
        if (!config.safeZone().preventHostileSpawns()) {
            return;
        }
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return; // Controlled plugin-managed training dummies / arena encounters
        }
        Location loc = event.getLocation();
        if (bounds.isInSafeZone(loc.getWorld() != null ? loc.getWorld().getName() : null, loc.getX(), loc.getY(), loc.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvpDamage(@NotNull EntityDamageByEntityEvent event) {
        if (!config.safeZone().preventPvp()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Location loc = victim.getLocation();
        if (bounds.isInSafeZone(loc.getWorld() != null ? loc.getWorld().getName() : null, loc.getX(), loc.getY(), loc.getZ())) {
            // Never heal/cancel hits on controlled training targets; block player-vs-player damage only
            if (event.getDamager() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamageInSafeZone(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Location loc = player.getLocation();
            if (loc.getWorld() != null && bounds.isEndWorld(loc.getWorld().getName())) {
                event.setCancelled(true);
                rescueToCentralPlaza(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Location to = event.getTo();
        World world = to.getWorld();
        if (world == null || !bounds.isEndWorld(world.getName())) {
            lastDistrictByPlayer.remove(event.getPlayer().getUniqueId());
            return;
        }

        Player player = event.getPlayer();
        if (bounds.isVoidFall(world.getName(), to.getY()) && player.getGameMode() != GameMode.SPECTATOR) {
            rescueToCentralPlaza(player);
            return;
        }

        EndDistrict current = bounds.resolveDistrict(to.getX(), to.getY(), to.getZ());
        if (current == null) {
            lastDistrictByPlayer.remove(player.getUniqueId());
            return;
        }

        EndDistrict previous = lastDistrictByPlayer.put(player.getUniqueId(), current);
        if (previous != current) {
            notifyDistrictTransition(player, current);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastDistrictByPlayer.remove(uuid);
        rescueCooldowns.remove(uuid);
    }

    private void notifyDistrictTransition(@NotNull Player player, @NotNull EndDistrict district) {
        EndVillageConfig.DistrictSpec spec = config.districts().get(district);
        String titleText = spec != null ? spec.displayName() : district.displayName();
        String subText = spec != null ? spec.subtitle() : district.subtitle();
        TextColor accent = TextColor.fromHexString(spec != null ? spec.hexColor() : district.hexColor());
        if (accent == null) {
            accent = NamedTextColor.LIGHT_PURPLE;
        }

        final TextColor finalAccent = accent;
        player.sendActionBar(
                Component.text("✦ ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text(titleText, finalAccent, TextDecoration.BOLD))
                        .append(Component.text(" — " + subText, NamedTextColor.GRAY))
        );

        UUID uuid = player.getUniqueId();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                boolean newlyDiscovered = profileRepository.markDistrictDiscovered(uuid, district);
                if (newlyDiscovered) {
                    if (questService != null) {
                        int count = profileRepository.loadOrCreate(uuid).discoveredDistricts().size();
                        questService.recordEvent(uuid, QuestObjectiveType.DISCOVER_DISTRICT, null, count, true);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        player.showTitle(Title.title(
                                Component.text(titleText, finalAccent, TextDecoration.BOLD),
                                Component.text("District Discovered", NamedTextColor.GRAY),
                                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1600), Duration.ofMillis(450))
                        ));
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.25f);
                    });
                }
            } catch (SQLException ex) {
                logger.warn("Failed saving district discovery {} for {}: {}", district.id(), uuid, ex.getMessage());
            }
        });
    }

    private void rescueToCentralPlaza(@NotNull Player player) {
        long now = System.currentTimeMillis();
        Long prev = rescueCooldowns.get(player.getUniqueId());
        if (prev != null && now - prev < RESCUE_COOLDOWN_MS) {
            return;
        }
        rescueCooldowns.put(player.getUniqueId(), now);

        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            return;
        }
        EndVillageConfig.AnchorPoint sp = config.spawnPoint();
        Location dest = new Location(world, sp.x(), sp.y(), sp.z(), sp.yaw(), sp.pitch());
        player.setFallDistance(0f);
        player.teleportAsync(dest).thenAccept(ok -> {
            if (ok) {
                player.sendMessage(Component.text("✦ Astral currents returned you to Central Plaza.", NamedTextColor.LIGHT_PURPLE));
                player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            }
        });
    }
}

