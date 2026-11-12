package com.skyrealms.end.listener;

import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.training.EndTrainingService;
import com.skyrealms.end.training.EndTrainingService.ActiveSession;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Event listener routing outdoor training courtyard interactions, bow shots, melee/parry timing,
 * and Mage Arcane Bolt casts cleanly into {@link EndTrainingService} without entity grief or fatal damage.
 */
public final class TrainingListener implements Listener {

    private static final NamespacedKey CUSTOM_ITEM_ID_KEY = new NamespacedKey("skyrealms", "custom_item_id");

    private final EndTrainingService trainingService;

    public TrainingListener(@NotNull EndTrainingService trainingService) {
        this.trainingService = Objects.requireNonNull(trainingService, "trainingService cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTrainingEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        // Guard sparring constructs attacking players: strictly non-lethal and capped!
        if (victim instanceof Player player
                && damager.getPersistentDataContainer().has(EndTrainingService.TRAINING_TARGET_KEY, PersistentDataType.BYTE)) {
            if (event.getFinalDamage() >= player.getHealth() - 1.0) {
                event.setDamage(0.0);
                trainingService.endSession(player.getUniqueId(), true, "Sparring round concluded.");
            } else {
                event.setDamage(Math.min(2.0, event.getDamage()));
            }
            return;
        }

        if (!victim.getPersistentDataContainer().has(EndTrainingService.TRAINING_TARGET_KEY, PersistentDataType.BYTE)) {
            return;
        }

        // Prevent armor stand breaking inside safe-zone; resolve attacker cleanly
        event.setCancelled(true);

        Player attacker = null;
        boolean isRanged = false;
        org.bukkit.Location impactPoint = null;

        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) {
            attacker = shooter;
            isRanged = true;
            impactPoint = arrow.getLocation();
            arrow.remove();
        }

        if (attacker == null) {
            return;
        }

        trainingService.handleTargetHit(attacker, victim, Math.max(4.0, event.getDamage()), impactPoint, isRanged, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHitBlock(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        if (event.getHitEntity() != null) {
            return; // Handled cleanly by EntityDamageByEntityEvent
        }
        ActiveSession session = trainingService.activeSession(shooter.getUniqueId());
        if (session != null && session.discipline() == CombatDiscipline.ARCHERY) {
            trainingService.recordMissedArcheryShot(shooter);
            arrow.remove();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!trainingService.hasActiveSession(player.getUniqueId())) {
            return;
        }
        ActiveSession session = trainingService.activeSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        boolean isRightClick = event.getAction().name().startsWith("RIGHT_CLICK");
        if (isRightClick && trainingService.handleParryBlock(player)) {
            return;
        }

        ItemStack held = event.getItem();
        if (held != null && sessionUsesMage(player, held)) {
            event.setCancelled(true);
            trainingService.handleMageBoltCast(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTrainingEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(EndTrainingService.TRAINING_TARGET_KEY, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        trainingService.endSession(event.getPlayer().getUniqueId(), false, null);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!trainingService.isConfiguredWorld(event.getPlayer().getWorld().getName())) {
            trainingService.endSession(event.getPlayer().getUniqueId(), false, "Left End Village world.");
        }
    }

    private boolean sessionUsesMage(Player player, ItemStack held) {
        ActiveSession s = trainingService.activeSession(player.getUniqueId());
        if (s == null) {
            return false;
        }
        String customId = readCustomItemId(held);
        return CombatDiscipline.detectFromWeapon(held.getType(), customId, CombatDiscipline.MELEE) == CombatDiscipline.MAGE;
    }

    private static @Nullable String readCustomItemId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(CUSTOM_ITEM_ID_KEY, PersistentDataType.STRING);
    }
}
