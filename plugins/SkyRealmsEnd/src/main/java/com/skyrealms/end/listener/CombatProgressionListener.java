package com.skyrealms.end.listener;

import com.skyrealms.end.district.EndVillageBounds;
import com.skyrealms.end.npc.EndNpcManager;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.EndXpCurve.DisciplineAttributes;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Listens for End Civilization combat activity:
 * - Free weapon switching across Archery, Melee, and Mage
 * - Passive discipline damage scaling outside village safe-zone
 * - Multi-contributor damage tracking & kill XP attribution (excluding safe-zone & non-eligible spawns)
 */
public final class CombatProgressionListener implements Listener {

    public static final NamespacedKey CUSTOM_ITEM_ID_KEY = new NamespacedKey("skyrealms", "custom_item_id");
    public static final NamespacedKey END_MOB_XP_KEY = new NamespacedKey("skyrealms", "end_mob_xp");
    public static final NamespacedKey NO_XP_KEY = new NamespacedKey("skyrealms", "no_xp");

    private final EndVillageBounds villageBounds;
    private final CombatProgressionService progressionService;
    private final CombatContributionTracker contributionTracker;

    public CombatProgressionListener(
            @NotNull EndVillageBounds villageBounds,
            @NotNull CombatProgressionService progressionService,
            @NotNull CombatContributionTracker contributionTracker
    ) {
        this.villageBounds = Objects.requireNonNull(villageBounds, "villageBounds cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
        this.contributionTracker = Objects.requireNonNull(contributionTracker, "contributionTracker cannot be null");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        progressionService.loadOnlinePlayerAsync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        progressionService.unloadOnlinePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!progressionService.isConfiguredWorld(player.getWorld().getName())) {
            return;
        }
        ItemStack nextItem = player.getInventory().getItem(event.getNewSlot());
        handleWeaponSwitch(player, nextItem);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!progressionService.isConfiguredWorld(player.getWorld().getName())) {
            return;
        }
        handleWeaponSwitch(player, event.getMainHandItem());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByPlayer(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity livingVictim) || victim instanceof Player) {
            return;
        }
        if (livingVictim.getPersistentDataContainer().has(EndNpcManager.NPC_ID_KEY, PersistentDataType.STRING)) {
            return;
        }
        if (isSafeZone(victim.getLocation())) {
            return;
        }
        // Only track End monsters or custom tagged End mobs outside village safe-zone
        if (!isEligibleMob(livingVictim)) {
            return;
        }

        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        CombatDiscipline usedDiscipline = resolveAttackDiscipline(attacker, event.getDamager());
        progressionService.switchDiscipline(attacker, usedDiscipline, false);

        DisciplineAttributes attrs = progressionService.activeAttributes(attacker.getUniqueId());
        if (usedDiscipline == CombatDiscipline.ARCHERY && attrs.bonusRangedDamagePercent() > 0.0) {
            event.setDamage(event.getDamage() * (1.0 + attrs.bonusRangedDamagePercent() / 100.0));
        } else if (usedDiscipline == CombatDiscipline.MAGE && attrs.spellPowerMultiplier() > 1.0) {
            event.setDamage(event.getDamage() * attrs.spellPowerMultiplier());
        }

        contributionTracker.recordDamage(
                livingVictim.getUniqueId(),
                attacker.getUniqueId(),
                usedDiscipline,
                event.getFinalDamage(),
                System.currentTimeMillis()
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof AbstractArrow) || !(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        if (!progressionService.isConfiguredWorld(shooter.getWorld().getName())
                || isSafeZone(shooter.getLocation())) {
            return;
        }
        progressionService.switchDiscipline(shooter, CombatDiscipline.ARCHERY, false);
        DisciplineAttributes attrs = progressionService.activeAttributes(shooter.getUniqueId());
        if (attrs.bonusArrowVelocityPercent() > 0.0) {
            double multiplier = 1.0 + (attrs.bonusArrowVelocityPercent() / 100.0);
            projectile.setVelocity(projectile.getVelocity().multiply(multiplier));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        if (entity.getPersistentDataContainer().has(EndNpcManager.NPC_ID_KEY, PersistentDataType.STRING)) {
            return;
        }
        if (isSafeZone(entity.getLocation())) {
            contributionTracker.consumeKillShares(entity.getUniqueId(), 0L, false);
            return;
        }

        long baseXp = resolveBaseXpReward(entity);
        if (baseXp <= 0L) {
            contributionTracker.consumeKillShares(entity.getUniqueId(), 0L, false);
            return;
        }

        String label = entity.getType().name();
        progressionService.distributeMobKillXp(entity.getUniqueId(), baseXp, label);
    }

    private boolean isSafeZone(org.bukkit.Location loc) {
        return loc.getWorld() != null && villageBounds.isInSafeZone(
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ()
        );
    }

    private void handleWeaponSwitch(Player player, @Nullable ItemStack item) {
        if (item == null) {
            return;
        }
        String customId = readCustomItemId(item);
        CombatDiscipline current = progressionService.getActiveDiscipline(player.getUniqueId());
        CombatDiscipline detected = CombatDiscipline.detectFromWeapon(item.getType(), customId, current);
        if (detected != current) {
            progressionService.switchDiscipline(player, detected, true);
        }
    }

    private static @Nullable Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private static CombatDiscipline resolveAttackDiscipline(Player attacker, Entity damager) {
        if (damager instanceof AbstractArrow) {
            return CombatDiscipline.ARCHERY;
        }
        ItemStack held = attacker.getInventory().getItemInMainHand();
        CombatDiscipline heldDisc = CombatDiscipline.detectFromWeapon(held.getType(), readCustomItemId(held), CombatDiscipline.MELEE);
        // Punching with a bow counts as Melee rather than ranged Archery
        return heldDisc == CombatDiscipline.ARCHERY ? CombatDiscipline.MELEE : heldDisc;
    }

    private static @Nullable String readCustomItemId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(CUSTOM_ITEM_ID_KEY, PersistentDataType.STRING);
    }

    private static boolean isEligibleMob(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(NO_XP_KEY, PersistentDataType.BYTE)) {
            return false;
        }
        if (entity.getEntitySpawnReason() == SpawnReason.SPAWNER) {
            return false;
        }
        return entity instanceof Monster || entity instanceof Shulker || pdc.has(END_MOB_XP_KEY, PersistentDataType.LONG);
    }

    private static long resolveBaseXpReward(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(NO_XP_KEY, PersistentDataType.BYTE)) {
            return 0L;
        }
        if (entity.getEntitySpawnReason() == SpawnReason.SPAWNER) {
            return 0L;
        }
        Long taggedXp = pdc.get(END_MOB_XP_KEY, PersistentDataType.LONG);
        if (taggedXp != null && taggedXp > 0L) {
            return taggedXp;
        }
        if (entity instanceof Enderman) {
            return 28L;
        }
        if (entity instanceof Shulker) {
            return 45L;
        }
        if (entity instanceof Endermite) {
            return 14L;
        }
        if (entity instanceof Monster) {
            return 20L;
        }
        return 0L;
    }
}
