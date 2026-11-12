package com.skyrealms.end.listener;

import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.items.service.ItemRegistryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integrates the Stormrake Wyvern (Vaelkor) & Enderloper MythicMobs encounter with
 * real Phase 2 airborne flight steering, SkyRealmsEnd XP attribution, Tempest-Ward Charm
 * mitigation, and authoritative boss gear drops.
 */
public final class VaelkorBossListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Plugin plugin;
    private final ItemRegistryService itemService;
    private final CombatProgressionService progressionService;
    private final CombatContributionTracker contributionTracker;
    private final Random rng = new Random();
    private final Map<UUID, Long> trackedWyverns = new ConcurrentHashMap<>();
    private BukkitTask flightTask;
    private int flightTick;

    public VaelkorBossListener(
            @NotNull Plugin plugin,
            @NotNull ItemRegistryService itemService,
            @NotNull CombatProgressionService progressionService,
            @NotNull CombatContributionTracker contributionTracker
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
        this.contributionTracker = Objects.requireNonNull(contributionTracker, "contributionTracker cannot be null");
    }

    public void start() {
        stop();
        this.flightTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAirborneWyverns, 4L, 4L);
    }

    public void stop() {
        if (flightTask != null) {
            flightTask.cancel();
            flightTask = null;
        }
        trackedWyverns.clear();
    }

    private void tickAirborneWyverns() {
        flightTick++;
        double angle = flightTick * 0.14;
        for (UUID bossId : trackedWyverns.keySet()) {
            Entity ent = Bukkit.getEntity(bossId);
            if (!(ent instanceof LivingEntity boss) || !boss.isValid() || boss.isDead()) {
                trackedWyverns.remove(bossId);
                continue;
            }
            AttributeInstance maxAttr = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp = maxAttr != null ? maxAttr.getValue() : 1000.0;
            double ratio = boss.getHealth() / Math.max(1.0, maxHp);

            if (ratio <= 0.65 && ratio > 0.30) {
                // Phase 2: Real airborne circling flight 6.5 blocks above nearest player
                boss.setGravity(false);
                boss.setFallDistance(0f);
                Player target = findNearestPlayer(boss, 36.0);
                if (target != null) {
                    Location desired = target.getLocation().clone().add(
                            Math.cos(angle) * 8.5,
                            6.5 + Math.sin(angle * 2.0) * 1.2,
                            Math.sin(angle) * 8.5
                    );
                    Vector step = desired.toVector().subtract(boss.getLocation().toVector());
                    double dist = step.length();
                    if (dist > 0.2) {
                        boss.setVelocity(step.normalize().multiply(Math.min(0.65, dist * 0.25)));
                    }
                    boss.getWorld().spawnParticle(Particle.CLOUD, boss.getLocation().add(0, 1.2, 0), 3, 0.8, 0.2, 0.8, 0.02);
                    boss.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, boss.getLocation().add(0, 1.4, 0), 2, 0.9, 0.3, 0.9, 0.01);
                }
            } else if (!boss.hasGravity()) {
                // Phase 3 landing or Phase 1 reset
                boss.setGravity(true);
                boss.setFallDistance(0f);
            }
        }
    }

    private static Player findNearestPlayer(LivingEntity boss, double maxRadius) {
        Player best = null;
        double bestSq = maxRadius * maxRadius;
        for (Player p : boss.getWorld().getPlayers()) {
            double dSq = p.getLocation().distanceSquared(boss.getLocation());
            if (dSq < bestSq) {
                bestSq = dSq;
                best = p;
            }
        }
        return best;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        String attackerName = plainName(event.getDamager());
        if (!attackerName.contains("Vaelkor")) {
            return;
        }

        double reduction = 0.0;
        ItemStack offHand = victim.getInventory().getItemInOffHand();
        Optional<String> offId = itemService.getCustomItemId(offHand);
        if (offId.isPresent() && EndGearCatalog.RELIC_TEMPEST_WARD.equals(offId.get())) {
            reduction += 0.30;
        }
        var setBonus = itemService.getActiveArmorSet(victim);
        if (setBonus.isPresent() && "end_stormrake".equals(setBonus.get().setId())) {
            reduction += 0.25;
        }
        if (reduction > 0.0) {
            event.setDamage(event.getDamage() * Math.max(0.35, 1.0 - reduction));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageBoss(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        String name = plainName(living);
        if (!name.contains("Vaelkor")) {
            return;
        }
        if (name.contains("Stormrake Wyvern")) {
            trackedWyverns.putIfAbsent(living.getUniqueId(), System.currentTimeMillis());
        }
        Player attacker = event.getDamager() instanceof Player p ? p
                : (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player sh ? sh : null);
        if (attacker == null) {
            return;
        }
        CombatDiscipline disc = event.getDamager() instanceof org.bukkit.entity.AbstractArrow
                ? CombatDiscipline.ARCHERY
                : CombatDiscipline.detectFromWeapon(attacker.getInventory().getItemInMainHand().getType(),
                itemService.getCustomItemId(attacker.getInventory().getItemInMainHand()).orElse(null),
                CombatDiscipline.MELEE);
        contributionTracker.recordDamage(living.getUniqueId(), attacker.getUniqueId(), disc,
                event.getFinalDamage(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        trackedWyverns.remove(entity.getUniqueId());
        String name = plainName(entity);
        if (name.contains("Vaelkor, the Stormrake Wyvern")) {
            event.getDrops().clear();
            event.setDroppedExp(150);
            itemService.createCustomItem(EndGearCatalog.MAT_STORMRAKE_SCALE, 5).ifPresent(event.getDrops()::add);
            itemService.createCustomItem(EndGearCatalog.MAT_TEMPEST_FANG, 2).ifPresent(event.getDrops()::add);
            if (rng.nextDouble() < 0.65) {
                itemService.createCustomItem(EndGearCatalog.MAT_WYVERN_HEART, 1).ifPresent(event.getDrops()::add);
            }
            progressionService.distributeMobKillXp(entity.getUniqueId(), 800L, "vaelkor_stormrake_wyvern");

            for (Player p : entity.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(entity.getLocation()) <= 48.0 * 48.0) {
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
                    p.sendMessage(Component.text(
                            "✦ Vaelkor, the Stormrake Wyvern defeated! Bring Scales, Fangs & Wyvern Heart to Doran, Elira, Haldor, or Rune Enchanter!",
                            NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD
                    ));
                }
            }
        } else if (name.contains("Vaelkor's Enderloper")) {
            event.getDrops().clear();
            event.setDroppedExp(20);
            itemService.createCustomItem(EndGearCatalog.MAT_PHASE_SINEW, 1 + rng.nextInt(2)).ifPresent(event.getDrops()::add);
            progressionService.distributeMobKillXp(entity.getUniqueId(), 75L, "vaelkor_enderloper");
        }
    }

    private static String plainName(Entity entity) {
        Component custom = entity.customName();
        return custom != null ? PLAIN.serialize(custom) : entity.getName();
    }
}
