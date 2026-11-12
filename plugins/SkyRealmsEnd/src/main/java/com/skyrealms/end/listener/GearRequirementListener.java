package com.skyrealms.end.listener;

import com.skyrealms.end.district.EndVillageBounds;
import com.skyrealms.end.gear.EndGearService;
import com.skyrealms.end.gear.EndGearService.RequirementCheckResult;
import com.skyrealms.end.npc.EndNpcManager;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.training.EndTrainingService;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces End equipment discipline level requirements and drives animated weapon skills
 * across Melee blades, Archery bows, and Mage staffs using the universal {@link ItemRegistryService}.
 */
public final class GearRequirementListener implements Listener {

    private final EndGearService gearService;
    private final ItemRegistryService itemService;
    private final EndVillageBounds villageBounds;
    private final CombatProgressionService progressionService;
    private final CombatContributionTracker contributionTracker;
    private final Map<UUID, Long> abilityCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> firingVolley = ConcurrentHashMap.newKeySet();

    public GearRequirementListener(
            @NotNull EndGearService gearService,
            @NotNull ItemRegistryService itemService,
            @NotNull EndVillageBounds villageBounds,
            @NotNull CombatProgressionService progressionService,
            @NotNull CombatContributionTracker contributionTracker
    ) {
        this.gearService = Objects.requireNonNull(gearService, "gearService cannot be null");
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
        this.villageBounds = Objects.requireNonNull(villageBounds, "villageBounds cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
        this.contributionTracker = Objects.requireNonNull(contributionTracker, "contributionTracker cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        RequirementCheckResult res = gearService.checkRequirement(shooter.getUniqueId(), bow);
        if (!res.allowed()) {
            event.setCancelled(true);
            sendRequirementDenied(shooter, res);
            return;
        }
        Optional<String> idOpt = itemService.getCustomItemId(bow);
        if (idOpt.isPresent() && idOpt.get().startsWith("end_")) {
            Location eye = shooter.getEyeLocation();
            shooter.getWorld().spawnParticle(Particle.END_ROD, eye.add(eye.getDirection().multiply(1.0)), 6, 0.15, 0.15, 0.15, 0.03);
            shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.35f);
            if (event.getEntity() instanceof AbstractArrow arrow) {
                spawnArrowTrail(arrow);
                if ("end_galewing_longbow".equals(idOpt.get())
                        && arrow instanceof Arrow mainArrow
                        && firingVolley.add(shooter.getUniqueId())) {
                    try {
                        Vector baseVel = mainArrow.getVelocity();
                        double speed = baseVel.length();
                        if (speed > 0.2) {
                            for (double angleDeg : new double[]{-10.0, 10.0}) {
                                Vector rotated = baseVel.clone().rotateAroundY(Math.toRadians(angleDeg));
                                Arrow volley = shooter.launchProjectile(Arrow.class, rotated);
                                volley.setDamage(Math.max(4.0, mainArrow.getDamage() * 0.7));
                                volley.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                                spawnArrowTrail(volley);
                            }
                        }
                    } finally {
                        firingVolley.remove(shooter.getUniqueId());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWeaponAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        ItemStack held = attacker.getInventory().getItemInMainHand();
        RequirementCheckResult res = gearService.checkRequirement(attacker.getUniqueId(), held);
        if (!res.allowed()) {
            event.setCancelled(true);
            sendRequirementDenied(attacker, res);
            return;
        }

        SkyItemStats stats = itemService.getPlayerEquippedStats(attacker);
        if (stats.slayerDamageBonus() > 0.0 && isTargetableEnemy(event.getEntity())) {
            event.setDamage(event.getDamage() * (1.0 + stats.slayerDamageBonus()));
        }

        Optional<String> idOpt = itemService.getCustomItemId(held);
        if (idOpt.isPresent() && idOpt.get().startsWith("end_") && isTargetableEnemy(event.getEntity())) {
            Location hit = event.getEntity().getLocation().add(0, 1.0, 0);
            hit.getWorld().spawnParticle(Particle.SWEEP_ATTACK, hit, 2, 0.3, 0.2, 0.3, 0.0);
            hit.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hit, 8, 0.3, 0.3, 0.3, 0.04);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEndArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)
                || !(arrow.getShooter() instanceof Player shooter)
                || !(event.getEntity() instanceof LivingEntity primary)
                || !isTargetableEnemy(primary)) {
            return;
        }
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        Optional<String> idOpt = itemService.getCustomItemId(bow);
        if (idOpt.isEmpty() || !idOpt.get().startsWith("end_")) {
            return;
        }

        Location hitLoc = primary.getLocation().add(0, 1.0, 0);
        hitLoc.getWorld().spawnParticle(Particle.END_ROD, hitLoc, 10, 0.35, 0.35, 0.35, 0.05);
        hitLoc.getWorld().spawnParticle(Particle.CRIT, hitLoc, 8, 0.3, 0.3, 0.3, 0.05);

        if (!"end_galewing_longbow".equals(idOpt.get()) || !tryCooldown(shooter.getUniqueId(), 4000L)) {
            return;
        }
        int chained = 0;
        for (Entity nearby : primary.getWorld().getNearbyEntities(primary.getLocation(), 6.0, 4.0, 6.0)) {
            if (isTargetableEnemy(nearby) && nearby != primary) {
                LivingEntity target = (LivingEntity) nearby;
                target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 14, 0.3, 0.4, 0.3, 0.05);
                target.damage(14.0, shooter);
                if (++chained >= 2) break;
            }
        }
        if (chained > 0) {
            primary.getWorld().playSound(primary.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.5f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorEquipClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getSlotType() != InventoryType.SlotType.ARMOR) {
            return;
        }
        RequirementCheckResult res = gearService.checkRequirement(player.getUniqueId(), event.getCursor());
        if (!res.allowed()) {
            event.setCancelled(true);
            sendRequirementDenied(player, res);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWeaponInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (held == null) {
            return;
        }
        Optional<String> idOpt = itemService.getCustomItemId(held);
        if (idOpt.isEmpty()) {
            return;
        }
        String customId = idOpt.get();
        if (!customId.startsWith("end_")) {
            return;
        }

        RequirementCheckResult res = gearService.checkRequirement(player.getUniqueId(), held);
        if (!res.allowed()) {
            event.setCancelled(true);
            sendRequirementDenied(player, res);
            return;
        }

        boolean isRight = event.getAction().name().startsWith("RIGHT_CLICK");
        boolean isLeft = event.getAction().name().startsWith("LEFT_CLICK");
        CombatDiscipline disc = CombatDiscipline.detectFromWeapon(held.getType(), customId, CombatDiscipline.MELEE);
        SkyItemStats stats = itemService.readItemStats(held);

        if (isRight && disc == CombatDiscipline.MELEE) {
            if ("end_stormrake_fangblade".equals(customId)) {
                triggerTempestCleave(player);
            } else {
                triggerMeleeDashCleave(player, stats);
            }
            return;
        }

        if (isLeft && disc == CombatDiscipline.ARCHERY) {
            triggerArcheryQuickShot(player, stats);
            return;
        }

        if (isRight && disc == CombatDiscipline.MAGE) {
            // Skip if player is inside an active Mage Training Courtyard session (handled by TrainingListener)
            if (!tryCooldown(player.getUniqueId(), 550L)) {
                return;
            }
            if ("end_tempest_conduit_staff".equals(customId)) {
                triggerSkyfallBolt(player);
                return;
            }
            if (!progressionService.tryConsumeMana(player.getUniqueId(), 8.0)) {
                player.sendActionBar(Component.text("Insufficient Mana!", NamedTextColor.BLUE));
                return;
            }
            progressionService.switchDiscipline(player, CombatDiscipline.MAGE, false);
            fireAnimatedArcaneBolt(player, stats);
        }
    }

    private void triggerMeleeDashCleave(Player player, SkyItemStats stats) {
        if (!tryCooldown(player.getUniqueId(), 2500L)) {
            return;
        }
        progressionService.switchDiscipline(player, CombatDiscipline.MELEE, false);
        Vector dash = player.getLocation().getDirection().setY(0.18).normalize().multiply(1.05);
        player.setVelocity(dash);

        World w = player.getWorld();
        Location c = player.getLocation().add(0, 1.1, 0).add(player.getLocation().getDirection().multiply(1.7));
        w.spawnParticle(Particle.SWEEP_ATTACK, c, 6, 1.5, 0.35, 1.5, 0.0);
        w.spawnParticle(Particle.END_ROD, c, 18, 1.4, 0.45, 1.4, 0.06);
        w.spawnParticle(Particle.ELECTRIC_SPARK, c, 14, 1.4, 0.4, 1.4, 0.05);
        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.1f);

        double dmg = 10.0 + (stats.combatDamage() * 0.65);
        for (Entity nearby : w.getNearbyEntities(c, 4.5, 2.8, 4.5)) {
            if (isTargetableEnemy(nearby)) {
                LivingEntity target = (LivingEntity) nearby;
                contributionTracker.recordDamage(target.getUniqueId(), player.getUniqueId(), CombatDiscipline.MELEE, dmg, System.currentTimeMillis());
                target.damage(dmg, player);
            }
        }
        player.sendActionBar(Component.text("✦ Void Dash Cleave!", NamedTextColor.RED));
    }

    private void triggerArcheryQuickShot(Player player, SkyItemStats stats) {
        if (!tryCooldown(player.getUniqueId(), 1500L)) {
            return;
        }
        progressionService.switchDiscipline(player, CombatDiscipline.ARCHERY, false);
        World w = player.getWorld();
        Location eye = player.getEyeLocation();
        Arrow arrow = player.launchProjectile(Arrow.class, eye.getDirection().multiply(2.9));
        arrow.setDamage(Math.max(4.0, 5.0 + stats.combatDamage() * 0.45));
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        spawnArrowTrail(arrow);

        // Light hop back with wind gust animation
        Vector hop = eye.getDirection().setY(0).normalize().multiply(-0.45).setY(0.25);
        player.setVelocity(hop);
        w.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 10, 0.4, 0.2, 0.4, 0.04);
        w.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.5f);
        player.sendActionBar(Component.text("✦ Gale Quick-Shot!", NamedTextColor.GREEN));
    }

    private void spawnArrowTrail(AbstractArrow arrow) {
        Plugin providingPlugin = JavaPlugin.getProvidingPlugin(getClass());
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 20 || !arrow.isValid() || arrow.isInBlock()) {
                    cancel();
                    return;
                }
                Location loc = arrow.getLocation();
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.03, 0.03, 0.03, 0.0);
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 2, 0.06, 0.06, 0.06, 0.01);
            }
        }.runTaskTimer(providingPlugin, 1L, 2L);
    }

    private void fireAnimatedArcaneBolt(Player player, SkyItemStats itemStats) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        if (side.lengthSquared() < 0.01) {
            side = new Vector(1, 0, 0);
        }
        Vector up = side.clone().crossProduct(dir).normalize();

        World world = player.getWorld();
        world.playSound(eye, Sound.ENTITY_EVOKER_CAST_SPELL, 0.65f, 1.45f);

        LivingEntity struck = null;
        Location endPoint = eye.clone().add(dir.clone().multiply(18.0));
        for (double d = 1.0; d <= 18.0; d += 0.55) {
            double angle = d * 1.6;
            Location center = eye.clone().add(dir.clone().multiply(d));
            Location spiral = center.clone()
                    .add(side.clone().multiply(Math.cos(angle) * 0.22))
                    .add(up.clone().multiply(Math.sin(angle) * 0.22));
            world.spawnParticle(Particle.WITCH, center, 1, 0.02, 0.02, 0.02, 0.0);
            world.spawnParticle(Particle.END_ROD, spiral, 1, 0.0, 0.0, 0.0, 0.0);

            for (Entity nearby : world.getNearbyEntities(center, 1.5, 1.6, 1.5)) {
                if (isTargetableEnemy(nearby)) {
                    struck = (LivingEntity) nearby;
                    endPoint = center;
                    break;
                }
            }
            if (struck != null || center.getBlock().getType().isSolid()) {
                endPoint = center;
                break;
            }
        }

        world.spawnParticle(Particle.EXPLOSION, endPoint, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, endPoint, 18, 0.6, 0.6, 0.6, 0.06);
        world.spawnParticle(Particle.END_ROD, endPoint, 18, 0.5, 0.5, 0.5, 0.06);

        double spellMult = progressionService.activeAttributes(player.getUniqueId()).spellPowerMultiplier();
        double dmg = Math.max(6.0, (8.0 + itemStats.combatDamage()) * spellMult * (1.0 + itemStats.slayerDamageBonus()));
        boolean hitAny = false;
        for (Entity nearby : world.getNearbyEntities(endPoint, 2.6, 2.6, 2.6)) {
            if (isTargetableEnemy(nearby)) {
                LivingEntity target = (LivingEntity) nearby;
                contributionTracker.recordDamage(target.getUniqueId(), player.getUniqueId(),
                        CombatDiscipline.MAGE, dmg, System.currentTimeMillis());
                target.damage(dmg, player);
                hitAny = true;
            }
        }
        if (hitAny) {
            world.playSound(endPoint, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
        }
    }

    private void triggerTempestCleave(Player player) {
        if (!tryCooldown(player.getUniqueId(), 6000L)) {
            return;
        }
        progressionService.switchDiscipline(player, CombatDiscipline.MELEE, false);
        World w = player.getWorld();
        Location c = player.getLocation().add(0, 1.0, 0);
        w.spawnParticle(Particle.SWEEP_ATTACK, c, 6, 1.8, 0.3, 1.8, 0.0);
        w.spawnParticle(Particle.ELECTRIC_SPARK, c, 32, 2.2, 0.5, 2.2, 0.06);
        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.75f);
        for (Entity nearby : w.getNearbyEntities(c, 5.0, 2.5, 5.0)) {
            if (isTargetableEnemy(nearby)) {
                LivingEntity target = (LivingEntity) nearby;
                contributionTracker.recordDamage(target.getUniqueId(), player.getUniqueId(), CombatDiscipline.MELEE, 24.0, System.currentTimeMillis());
                target.damage(24.0, player);
            }
        }
        player.sendActionBar(Component.text("✦ Tempest Cleave!", NamedTextColor.LIGHT_PURPLE));
    }

    private void triggerSkyfallBolt(Player player) {
        if (!tryCooldown(player.getUniqueId(), 5000L)) {
            return;
        }
        if (!progressionService.tryConsumeMana(player.getUniqueId(), 15.0)) {
            player.sendActionBar(Component.text("Insufficient Mana!", NamedTextColor.BLUE));
            return;
        }
        progressionService.switchDiscipline(player, CombatDiscipline.MAGE, false);
        var hit = player.rayTraceBlocks(22.0);
        Location targetLoc = hit != null && hit.getHitBlock() != null
                ? hit.getHitBlock().getLocation().add(0.5, 1.0, 0.5)
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(14.0));
        World w = targetLoc.getWorld();
        w.strikeLightningEffect(targetLoc);
        w.spawnParticle(Particle.ELECTRIC_SPARK, targetLoc, 35, 2.0, 0.8, 2.0, 0.07);
        for (Entity nearby : w.getNearbyEntities(targetLoc, 5.0, 3.5, 5.0)) {
            if (isTargetableEnemy(nearby)) {
                LivingEntity target = (LivingEntity) nearby;
                contributionTracker.recordDamage(target.getUniqueId(), player.getUniqueId(), CombatDiscipline.MAGE, 28.0, System.currentTimeMillis());
                target.damage(28.0, player);
            }
        }
        player.sendActionBar(Component.text("✦ Skyfall Bolt!", NamedTextColor.AQUA));
    }

    private boolean tryCooldown(UUID uuid, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = abilityCooldowns.get(uuid);
        if (last != null && (now - last) < cooldownMs) {
            return false;
        }
        abilityCooldowns.put(uuid, now);
        return true;
    }

    private static boolean isTargetableEnemy(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity instanceof Player) {
            return false;
        }
        var pdc = living.getPersistentDataContainer();
        if (pdc.has(EndNpcManager.NPC_ID_KEY, PersistentDataType.STRING)) {
            return false;
        }
        if (pdc.has(EndTrainingService.TRAINING_TARGET_KEY, PersistentDataType.BYTE)) {
            return false;
        }
        return true;
    }

    private static void sendRequirementDenied(Player player, RequirementCheckResult res) {
        String disc = res.discipline() != null ? res.discipline().displayName() : "Required Discipline";
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
        player.sendActionBar(Component.text(
                "Locked: " + res.displayName() + " requires " + disc + " Lv." + res.requiredLevel()
                        + " (Yours: Lv." + res.playerLevel() + ")",
                NamedTextColor.RED
        ));
    }
}
