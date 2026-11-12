package com.skyrealms.islands.lobby;

import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Handles lobby protection, island void rescue, interactive hub pedestals,
 * Sakura Leap double-jump, lobby flow buffs, and clickable join welcome UI.
 */
public final class LobbyListener implements Listener {

    private static final double LOBBY_VOID_Y = 25.0;
    private static final double ISLAND_VOID_Y = 0.0;
    private static final long VOID_RESCUE_COOLDOWN_MS = 1500L;
    private static final long STATION_CLICK_COOLDOWN_MS = 400L;
    private static final long LEAP_COOLDOWN_MS = 2000L;

    private final @Nullable Plugin plugin;
    private final Supplier<Location> lobbySpawnSupplier;
    private final IslandService islandService;
    private final @Nullable NamespacedKey stationCommandKey;

    private final Map<UUID, Long> recentVoidRescueMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentStationClickMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentLeapMs = new ConcurrentHashMap<>();
    private final Set<UUID> lobbyBuffedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> leapFlightGranted = ConcurrentHashMap.newKeySet();

    public LobbyListener(Plugin plugin, LobbyService lobbyService, IslandService islandService) {
        this(
                plugin,
                lobbyService::getLobbySpawnLocation,
                islandService,
                lobbyService.getHologramService().stationCommandKey()
        );
    }

    LobbyListener(Supplier<Location> lobbySpawnSupplier, IslandService islandService) {
        this(null, lobbySpawnSupplier, islandService, null);
    }

    private LobbyListener(
            @Nullable Plugin plugin,
            Supplier<Location> lobbySpawnSupplier,
            IslandService islandService,
            @Nullable NamespacedKey stationCommandKey
    ) {
        this.plugin = plugin;
        this.lobbySpawnSupplier = lobbySpawnSupplier;
        this.islandService = islandService;
        this.stationCommandKey = stationCommandKey;
    }

    // --- PHYSICS FREEZE & ANTI-BREAK SHIELD ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")
                && event.getEntityType() == EntityType.FALLING_BLOCK) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")
                && !event.getPlayer().hasPermission("skyrealms.admin.build")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld().getName().equals("world")
                && !event.getPlayer().hasPermission("skyrealms.admin.build")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        String worldName = event.getEntity().getWorld().getName();
        if (worldName.equals("world")) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player player) {
                catchVoidPlayer(player);
            }
            return;
        }

        if (islandService.isProtectedWorld(worldName)
                && event.getCause() == EntityDamageEvent.DamageCause.VOID
                && event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            catchVoidPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity().getWorld().getName().equals("world")) {
            event.setCancelled(true);
            event.setFoodLevel(20);
        }
    }

    // --- VOID FALL RESCUE (LOBBY & ISLANDS) ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getWorld() == null) {
            return;
        }
        String worldName = to.getWorld().getName();
        double voidY = voidThresholdForWorld(worldName);
        if (!Double.isNaN(voidY) && to.getY() < voidY) {
            catchVoidPlayer(event.getPlayer());
        }
    }

    private double voidThresholdForWorld(String worldName) {
        if (worldName.equals("world")) {
            return LOBBY_VOID_Y;
        }
        if (islandService.isProtectedWorld(worldName)) {
            return ISLAND_VOID_Y;
        }
        return Double.NaN;
    }

    private void catchVoidPlayer(Player player) {
        long now = System.currentTimeMillis();
        Long lastRescue = recentVoidRescueMs.get(player.getUniqueId());
        if (lastRescue != null && (now - lastRescue) < VOID_RESCUE_COOLDOWN_MS) {
            return;
        }
        recentVoidRescueMs.put(player.getUniqueId(), now);

        player.setFallDistance(0.0f);
        Location spawn = lobbySpawnSupplier.get();
        player.teleportAsync(spawn).thenAccept(success -> {
            if (success) {
                player.setFallDistance(0.0f);
                player.playSound(spawn, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 1.2f);
                player.playSound(spawn, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
                player.spawnParticle(Particle.CHERRY_LEAVES, spawn, 40, 1.0, 1.0, 1.0, 0.05);
                player.sendMessage(Component.text("🌸 Caught by the celestial breeze!", NamedTextColor.LIGHT_PURPLE));
            }
        });
    }

    // --- INTERACTIVE PEDESTAL STATIONS ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPedestalRightClick(PlayerInteractEntityEvent event) {
        if (handleStationTrigger(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPedestalLeftClick(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && handleStationTrigger(player, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean handleStationTrigger(Player player, Entity target) {
        if (stationCommandKey == null || !(target instanceof Interaction interaction)) {
            return false;
        }
        String command = interaction.getPersistentDataContainer().get(stationCommandKey, PersistentDataType.STRING);
        if (command == null || command.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long lastClick = recentStationClickMs.get(player.getUniqueId());
        if (lastClick != null && (now - lastClick) < STATION_CLICK_COOLDOWN_MS) {
            return true;
        }
        recentStationClickMs.put(player.getUniqueId(), now);

        Location loc = interaction.getLocation().clone().add(0.0, 1.3, 0.0);
        player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.45f);
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc, 18, 0.4, 0.4, 0.4, 0.04);
        player.performCommand(command);
        return true;
    }

    // --- LOBBY QUALITY-OF-LIFE: FLOW BUFFS, SAKURA LEAP, & QUICK BAR ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals("world")) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return;
        }
        if (!leapFlightGranted.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        long now = System.currentTimeMillis();
        Long lastLeap = recentLeapMs.get(player.getUniqueId());
        if (lastLeap == null || (now - lastLeap) >= LEAP_COOLDOWN_MS) {
            recentLeapMs.put(player.getUniqueId(), now);
            Vector boost = player.getLocation().getDirection().normalize().multiply(1.15);
            boost.setY(Math.max(0.55, Math.min(0.85, boost.getY() + 0.55)));
            player.setVelocity(boost);
            player.setFallDistance(0.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.9f, 1.4f);
            player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 0.3, 0), 22, 0.5, 0.2, 0.5, 0.06);
            player.sendActionBar(Component.text("✿ Sakura Leap!", TextColor.fromHexString("#FF9EBB")));
        }

        if (plugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getWorld().getName().equals("world")
                        && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                        && leapFlightGranted.contains(player.getUniqueId())) {
                    player.setAllowFlight(true);
                }
            }, 35L);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals("world")) {
            Location spawn = lobbySpawnSupplier.get();
            player.teleportAsync(spawn);
            enterLobbyState(player, spawn);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getName().equals("world")) {
            leaveLobbyState(player);
        }
        if (player.getWorld().getName().equals("world")) {
            enterLobbyState(player, player.getLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leaveLobbyState(player);
        recentVoidRescueMs.remove(player.getUniqueId());
        recentStationClickMs.remove(player.getUniqueId());
        recentLeapMs.remove(player.getUniqueId());
    }

    private void enterLobbyState(Player player, Location cueLoc) {
        lobbyBuffedPlayers.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 0, true, false, false));

        GameMode mode = player.getGameMode();
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            leapFlightGranted.add(player.getUniqueId());
            player.setAllowFlight(true);
        }

        Title title = Title.title(
                Component.text("✦ SKYREALMS ✦", TextColor.fromHexString("#FF9EBB"), TextDecoration.BOLD),
                Component.text("Sakura Sanctuary Hub", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(450))
        );
        player.showTitle(title);
        player.playSound(cueLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.15f);
        sendQuickBar(player);
    }

    private void leaveLobbyState(Player player) {
        if (lobbyBuffedPlayers.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
        if (leapFlightGranted.remove(player.getUniqueId())) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
    }

    private void sendQuickBar(Player player) {
        Component bar = Component.text("✿ Hub Quick Nav: ", TextColor.fromHexString("#FF9EBB"), TextDecoration.BOLD)
                .append(quickLink("[☁ Islands]", "/island", "Open or teleport to your SkyBlock Island", NamedTextColor.GREEN))
                .append(Component.space())
                .append(quickLink("[✿ Garden]", "/garden", "Visit the Project Sakura Garden", TextColor.fromHexString("#FFB3C6")))
                .append(Component.space())
                .append(quickLink("[✦ Pets]", "/pets", "Manage your Cubees 3D Pets", NamedTextColor.LIGHT_PURPLE))
                .append(Component.space())
                .append(quickLink("[⚔ Items]", "/items", "Open Custom Hoes & Gear", NamedTextColor.AQUA))
                .append(Component.space())
                .append(quickLink("[★ Menu]", "/menu", "Open the full SkyRealms Hub Menu", NamedTextColor.GOLD));
        player.sendMessage(bar);
    }

    private static Component quickLink(String label, String command, String tooltip, TextColor color) {
        return Component.text(label, color, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(tooltip + " (" + command + ")", NamedTextColor.GRAY)));
    }
}
