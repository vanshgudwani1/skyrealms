package com.skyrealms.end.wilderness;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.listener.CombatProgressionListener;
import com.skyrealms.end.npc.NpcInteractionHandler;
import com.skyrealms.end.quest.EndQuestService;
import com.skyrealms.end.quest.QuestObjectiveType;
import com.skyrealms.end.wilderness.BestiaryRepository.BestiaryEntry;
import com.skyrealms.end.wilderness.BestiaryRepository.KillRecordOutcome;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates the separate End Wilderness hunting dimension (`skyrealms_end_wilds`),
 * four level-banded hunting regions, custom mobs with unique AI, Elite variants,
 * three non-dragon Field Bosses, loot tables, and Bestiary progression.
 */
public final class EndWildernessService {

    public static final String DEFAULT_WILDERNESS_WORLD = "skyrealms_end_wilds";
    public static final NamespacedKey WILD_MOB_ID_KEY = new NamespacedKey("skyrealms", "end_wild_mob_id");
    public static final NamespacedKey WILD_ELITE_KEY = new NamespacedKey("skyrealms", "end_wild_elite");

    private static final int MAX_TRACKED_MOBS = 48;

    private final Plugin plugin;
    private final EndVillageConfig villageConfig;
    private final BestiaryRepository bestiaryRepository;
    private final EndLootTable lootTable;
    private final EndQuestService questService;
    private final Logger logger;

    private final String wildernessWorldName;
    private final Map<UUID, EndMobArchetype> activeWildMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> aiCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Set<WildernessRegion>> sessionDiscoveredRegions = new ConcurrentHashMap<>();
    private final Random rng = new Random();
    private BukkitTask aiAndSpawnTask;

    public EndWildernessService(
            @NotNull Plugin plugin,
            @NotNull EndVillageConfig villageConfig,
            @NotNull BestiaryRepository bestiaryRepository,
            @NotNull EndLootTable lootTable,
            @NotNull EndQuestService questService,
            @NotNull Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.villageConfig = Objects.requireNonNull(villageConfig, "villageConfig cannot be null");
        this.bestiaryRepository = Objects.requireNonNull(bestiaryRepository, "bestiaryRepository cannot be null");
        this.lootTable = Objects.requireNonNull(lootTable, "lootTable cannot be null");
        this.questService = Objects.requireNonNull(questService, "questService cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.wildernessWorldName = DEFAULT_WILDERNESS_WORLD;
    }

    public void start() {
        stop();
        // Single 1 Hz scheduler handles unique mob AI pulses & bounded regional spawns near hunting players.
        this.aiAndSpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickWilderness, 20L, 20L);
    }

    public void stop() {
        if (aiAndSpawnTask != null) {
            aiAndSpawnTask.cancel();
            aiAndSpawnTask = null;
        }
        activeWildMobs.clear();
        aiCooldowns.clear();
    }

    public boolean isWildernessWorld(@Nullable String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(wildernessWorldName);
    }

    @NotNull
    public String wildernessWorldName() {
        return wildernessWorldName;
    }

    @Nullable
    public World getOrCreateWildernessWorld() {
        if (Bukkit.getServer() == null) {
            return null;
        }
        World existing = Bukkit.getWorld(wildernessWorldName);
        if (existing != null) {
            return existing;
        }
        World created = new WorldCreator(wildernessWorldName)
                .environment(World.Environment.THE_END)
                .createWorld();
        if (created != null) {
            logger.info("Initialized separate End Wilderness hunting world '{}'.", wildernessWorldName);
        }
        return created;
    }

    /**
     * Safely warps a player from the End Village into one of the four wilderness regions,
     * generating a small obsidian arrival pad if the End terrain at the anchor is over void.
     */
    public boolean warpToRegion(@NotNull Player player, @NotNull WildernessRegion region) {
        World world = getOrCreateWildernessWorld();
        if (world == null) {
            player.sendMessage(Component.text("Wilderness map is unavailable right now.", NamedTextColor.RED));
            return false;
        }

        int ax = (int) Math.floor(region.anchorX());
        int az = (int) Math.floor(region.anchorZ());
        int surfaceY = world.getHighestBlockYAt(ax, az);
        double targetY;
        if (surfaceY < 40) {
            buildArrivalPlatform(world, ax, (int) region.anchorY() - 1, az);
            targetY = region.anchorY();
        } else {
            targetY = surfaceY + 1.1;
        }

        Location dest = new Location(world, ax + 0.5, targetY, az + 0.5, 0f, 0f);
        player.teleport(dest);
        player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.1f);
        announceRegionDiscovery(player, region);
        return true;
    }

    public boolean warpBackToVillage(@NotNull Player player) {
        World villageWorld = Bukkit.getWorld(villageConfig.worldName());
        if (villageWorld == null) {
            return false;
        }
        var sp = villageConfig.spawnPoint();
        player.teleport(new Location(villageWorld, sp.x(), sp.y(), sp.z(), sp.yaw(), sp.pitch()));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.3f);
        return true;
    }

    @Nullable
    public LivingEntity spawnCustomMob(@NotNull EndMobArchetype mob, @NotNull Location location, boolean forceElite) {
        World world = location.getWorld();
        if (world == null || activeWildMobs.size() >= MAX_TRACKED_MOBS && !mob.isFieldBoss()) {
            return null;
        }
        boolean elite = !mob.isFieldBoss() && (forceElite || rng.nextDouble() < 0.16);
        Entity entity = world.spawnEntity(location, mob.entityType());
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return null;
        }

        double hpMult = elite ? 1.85 : 1.0;
        double dmgMult = elite ? 1.35 : 1.0;
        long xpReward = elite ? Math.round(mob.baseXp() * 2.5) : mob.baseXp();

        setBaseAttr(living, Attribute.GENERIC_MAX_HEALTH, mob.maxHealth() * hpMult);
        living.setHealth(Math.min(mob.maxHealth() * hpMult,
                living.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
                        ? living.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : mob.maxHealth()));
        setBaseAttr(living, Attribute.GENERIC_ATTACK_DAMAGE, mob.attackDamage() * dmgMult);
        setBaseAttr(living, Attribute.GENERIC_MOVEMENT_SPEED, mob.moveSpeed());

        String prefix = mob.isFieldBoss() ? "☠ [BOSS] " : (elite ? "✦ [ELITE] " : "");
        NamedTextColor nameColor = mob.isFieldBoss()
                ? NamedTextColor.DARK_PURPLE
                : (elite ? NamedTextColor.GOLD : NamedTextColor.RED);
        living.customName(Component.text(prefix + mob.displayName(), nameColor, TextDecoration.BOLD));
        living.setCustomNameVisible(true);
        if (elite || mob.isFieldBoss()) {
            living.setGlowing(true);
        }

        equipMobVisuals(living, mob);
        living.getPersistentDataContainer().set(WILD_MOB_ID_KEY, PersistentDataType.STRING, mob.id());
        living.getPersistentDataContainer().set(WILD_ELITE_KEY, PersistentDataType.BYTE, (byte) (elite ? 1 : 0));
        living.getPersistentDataContainer().set(CombatProgressionListener.END_MOB_XP_KEY, PersistentDataType.LONG, xpReward);

        activeWildMobs.put(living.getUniqueId(), mob);
        return living;
    }

    public void announceRegionDiscovery(@NotNull Player player, @NotNull WildernessRegion region) {
        TextColor accent = TextColor.fromHexString(region.accentHex());
        player.showTitle(Title.title(
                Component.text(region.displayName(), accent, TextDecoration.BOLD),
                Component.text("Wilderness Threat: Lv." + region.minLevel() + "-" + region.maxLevel(), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(400))
        ));

        Set<WildernessRegion> seen = sessionDiscoveredRegions.computeIfAbsent(
                player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (seen.add(region)) {
            questService.recordEvent(player.getUniqueId(), QuestObjectiveType.WILD_EXPLORE,
                    null, seen.size(), true);
        }
    }

    public void handleMobDeathDropsAndBestiary(
            @NotNull LivingEntity deadEntity,
            @Nullable Player killer,
            @NotNull List<ItemStack> existingDrops
    ) {
        String mobId = deadEntity.getPersistentDataContainer().get(WILD_MOB_ID_KEY, PersistentDataType.STRING);
        activeWildMobs.remove(deadEntity.getUniqueId());
        aiCooldowns.remove(deadEntity.getUniqueId());
        if (mobId == null) {
            return;
        }
        EndMobArchetype mob = EndMobArchetype.byId(mobId);
        if (mob == null) {
            return;
        }
        boolean elite = Byte.valueOf((byte) 1).equals(
                deadEntity.getPersistentDataContainer().get(WILD_ELITE_KEY, PersistentDataType.BYTE));

        existingDrops.clear();
        existingDrops.addAll(lootTable.materializeDrops(mob, elite, rng));

        if (killer != null) {
            try {
                KillRecordOutcome out = bestiaryRepository.recordKill(killer.getUniqueId(), mob.id(), elite || mob.isFieldBoss());
                if (out.newlyDiscovered()) {
                    killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
                    killer.sendMessage(Component.text(
                            "✦ Bestiary Entry Unlocked: " + mob.displayName() + " (" + mob.region().displayName() + ")",
                            NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD
                    ));
                }
            } catch (SQLException ex) {
                logger.warn("Failed updating bestiary for {}", killer.getUniqueId(), ex);
            }
        }
    }

    @NotNull
    public NpcInteractionHandler wildernessWarpNpcHandler() {
        return (player, npc) -> {
            player.sendMessage(Component.text("━━━ End Wilderness Hunting Expeditions ━━━",
                    NamedTextColor.GOLD, TextDecoration.BOLD));
            for (WildernessRegion region : WildernessRegion.values()) {
                TextColor accent = TextColor.fromHexString(region.accentHex());
                Component row = Component.text(" [WARP] ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .append(Component.text(region.displayName() + " (Lv." + region.minLevel() + "-" + region.maxLevel() + ")",
                                accent, TextDecoration.BOLD))
                        .append(Component.text(" — " + region.description(), NamedTextColor.GRAY))
                        .clickEvent(ClickEvent.runCommand("/wilds warp " + region.id()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to travel to " + region.displayName(), NamedTextColor.YELLOW)));
                player.sendMessage(row);
            }
        };
    }

    public void showBestiary(@NotNull Player player) {
        try {
            Map<String, BestiaryEntry> entries = bestiaryRepository.loadAll(player.getUniqueId());
            player.sendMessage(Component.text("━━━ End Wilderness Bestiary (" + entries.size() + "/"
                    + EndMobArchetype.values().length + " Discovered) ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
            for (EndMobArchetype mob : EndMobArchetype.values()) {
                BestiaryEntry entry = entries.get(mob.id());
                if (entry == null) {
                    player.sendMessage(Component.text(" • ??? [" + mob.region().displayName() + "]", NamedTextColor.DARK_GRAY));
                } else {
                    String prefix = mob.isFieldBoss() ? "☠ " : "• ";
                    player.sendMessage(Component.text(prefix + mob.displayName(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                            .append(Component.text(" — Kills: " + entry.kills()
                                    + (entry.elitesKilled() > 0 ? " (Elites/Boss: " + entry.elitesKilled() + ")" : ""),
                                    NamedTextColor.GRAY)));
                }
            }
        } catch (SQLException ex) {
            player.sendMessage(Component.text("Unable to load Bestiary right now.", NamedTextColor.RED));
        }
    }

    private void tickWilderness() {
        World wildWorld = Bukkit.getWorld(wildernessWorldName);
        if (wildWorld == null) {
            return;
        }

        // Purge invalid entities
        activeWildMobs.entrySet().removeIf(e -> {
            Entity ent = Bukkit.getEntity(e.getKey());
            return ent == null || !ent.isValid() || ent.isDead();
        });

        long now = System.currentTimeMillis();
        // Execute unique AI behaviors for engaged mobs
        for (Map.Entry<UUID, EndMobArchetype> entry : new ArrayList<>(activeWildMobs.entrySet())) {
            Entity ent = Bukkit.getEntity(entry.getKey());
            if (!(ent instanceof Mob mobEntity) || !(mobEntity.getTarget() instanceof Player target)) {
                continue;
            }
            Long lastUse = aiCooldowns.get(mobEntity.getUniqueId());
            if (lastUse != null && (now - lastUse) < 5500L) {
                continue;
            }
            if (mobEntity.getLocation().distanceSquared(target.getLocation()) > 196.0) {
                continue;
            }
            aiCooldowns.put(mobEntity.getUniqueId(), now);
            triggerAiAbility(mobEntity, target, entry.getValue());
        }

        // Bounded regional ambient spawns near online hunters inside skyrealms_end_wilds
        List<Player> wildPlayers = wildWorld.getPlayers();
        if (!wildPlayers.isEmpty() && activeWildMobs.size() < MAX_TRACKED_MOBS) {
            Player anchorPlayer = wildPlayers.get(rng.nextInt(wildPlayers.size()));
            WildernessRegion region = WildernessRegion.resolveAt(
                    anchorPlayer.getLocation().getX(), anchorPlayer.getLocation().getZ());
            announceRegionDiscovery(anchorPlayer, region);

            if (activeWildMobs.size() < Math.min(MAX_TRACKED_MOBS, wildPlayers.size() * 8)) {
                spawnNearHunter(wildWorld, anchorPlayer.getLocation(), region);
            }
        }
    }

    private void spawnNearHunter(World world, Location playerLoc, WildernessRegion region) {
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dist = 10.0 + rng.nextDouble() * 12.0;
        int x = (int) Math.floor(playerLoc.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(playerLoc.getZ() + Math.sin(angle) * dist);
        int highest = world.getHighestBlockYAt(x, z);
        if (highest < 45) {
            return; // Avoid spawning over empty void gaps
        }
        List<EndMobArchetype> pool = EndMobArchetype.normalMobsFor(region);
        if (pool.isEmpty()) {
            return;
        }
        EndMobArchetype choice = pool.get(rng.nextInt(pool.size()));
        spawnCustomMob(choice, new Location(world, x + 0.5, highest + 1.0, z + 0.5), false);
    }

    private void triggerAiAbility(Mob mob, Player target, EndMobArchetype archetype) {
        Location mLoc = mob.getLocation();
        Location tLoc = target.getLocation();
        World w = mob.getWorld();

        switch (archetype.aiAbility()) {
            case PACK_DASH -> {
                Vector lunge = tLoc.toVector().subtract(mLoc.toVector()).normalize().multiply(0.85).setY(0.28);
                mob.setVelocity(lunge);
                w.spawnParticle(Particle.CLOUD, mLoc.add(0, 0.5, 0), 8, 0.3, 0.2, 0.3, 0.03);
            }
            case SHADOW_BLINK -> {
                Vector behind = tLoc.getDirection().normalize().multiply(-2.2);
                Location blinkDest = tLoc.clone().add(behind);
                blinkDest.setY(Math.max(tLoc.getY(), w.getHighestBlockYAt(blinkDest) + 1.0));
                w.spawnParticle(Particle.PORTAL, mLoc.add(0, 1.0, 0), 15, 0.3, 0.5, 0.3, 0.1);
                mob.teleport(blinkDest);
                w.playSound(blinkDest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            }
            case CRYSTAL_PULSE, VOID_NOVA -> {
                w.spawnParticle(Particle.WITCH, mLoc.clone().add(0, 1.2, 0), 18, 1.6, 0.5, 1.6, 0.04);
                w.playSound(mLoc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 0.8f);
                if (mLoc.distanceSquared(tLoc) <= 25.0) {
                    Vector push = tLoc.toVector().subtract(mLoc.toVector()).normalize().multiply(0.65).setY(0.3);
                    target.setVelocity(push);
                    target.damage(archetype.isFieldBoss() ? 5.0 : 2.5, mob);
                }
            }
            case WITHER_VOLLEY -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, true, true));
                w.spawnParticle(Particle.SMOKE, tLoc.clone().add(0, 1.0, 0), 10, 0.4, 0.4, 0.4, 0.02);
            }
            case EARTHSHAKER -> {
                w.spawnParticle(Particle.EXPLOSION, mLoc.clone().add(0, 0.6, 0), 2, 0.6, 0.2, 0.6, 0.0);
                w.playSound(mLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.9f, 0.65f);
                if (mLoc.distanceSquared(tLoc) <= 36.0) {
                    target.setVelocity(new Vector(0, 0.45, 0));
                    target.damage(archetype.isFieldBoss() ? 6.0 : 3.0, mob);
                }
            }
        }
    }

    private static void equipMobVisuals(LivingEntity living, EndMobArchetype mob) {
        EntityEquipment eq = living.getEquipment();
        if (eq == null) {
            return;
        }
        Material helmet = switch (mob.region()) {
            case SHATTERED_FIELDS -> Material.CHAINMAIL_HELMET;
            case CRYSTAL_WILDS -> Material.DIAMOND_HELMET;
            case FORGOTTEN_RUINS, ABYSSAL_GROUNDS -> Material.NETHERITE_HELMET;
        };
        eq.setHelmet(new ItemStack(helmet));
        eq.setHelmetDropChance(0f);
    }

    private static void buildArrivalPlatform(World world, int cx, int y, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Material mat = (Math.abs(dx) == 2 || Math.abs(dz) == 2) ? Material.OBSIDIAN : Material.END_STONE_BRICKS;
                world.getBlockAt(cx + dx, y, cz + dz).setType(mat, false);
            }
        }
        world.getBlockAt(cx, y, cz).setType(Material.CRYING_OBSIDIAN, false);
    }

    private static void setBaseAttr(LivingEntity living, Attribute attr, double value) {
        AttributeInstance inst = living.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }
}

