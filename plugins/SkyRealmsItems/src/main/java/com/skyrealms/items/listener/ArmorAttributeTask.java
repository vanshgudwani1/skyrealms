package com.skyrealms.items.listener;

import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.SkyItemStats;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Periodically synchronizes walk speed attribute modifiers for online players holding SkyRealms Hoes.
 */
public final class ArmorAttributeTask {

    private static final NamespacedKey SPEED_MOD_KEY = new NamespacedKey("skyrealms", "gear_walk_speed");
    private static final NamespacedKey HEALTH_MOD_KEY = new NamespacedKey("skyrealms", "gear_max_health");
    private static final NamespacedKey DEFENSE_MOD_KEY = new NamespacedKey("skyrealms", "gear_defense");
    private static final NamespacedKey DAMAGE_MOD_KEY = new NamespacedKey("skyrealms", "gear_combat_damage");

    private final Plugin plugin;
    private final ItemRegistryService itemService;
    private BukkitTask task;

    public ArmorAttributeTask(@NotNull Plugin plugin, @NotNull ItemRegistryService itemService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
    }

    public void start(long intervalTicks) {
        stop();
        long period = Math.max(10L, intervalTicks);
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    syncPlayer(player);
                }
            }
        }.runTaskTimer(plugin, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayerModifiers(player);
        }
    }

    public void syncPlayer(@NotNull Player player) {
        SkyItemStats stats = itemService.getPlayerEquippedStats(player);
        syncModifier(player, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_MOD_KEY,
                stats.speedBonus(), AttributeModifier.Operation.ADD_SCALAR);
        syncModifier(player, Attribute.GENERIC_MAX_HEALTH, HEALTH_MOD_KEY,
                stats.maxHealthBonus(), AttributeModifier.Operation.ADD_NUMBER);
        syncModifier(player, Attribute.GENERIC_ARMOR, DEFENSE_MOD_KEY,
                stats.defense(), AttributeModifier.Operation.ADD_NUMBER);
        syncModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, DAMAGE_MOD_KEY,
                stats.combatDamage(), AttributeModifier.Operation.ADD_NUMBER);
    }

    public void clearPlayerModifiers(@NotNull Player player) {
        removeMod(player, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_MOD_KEY);
        removeMod(player, Attribute.GENERIC_MAX_HEALTH, HEALTH_MOD_KEY);
        removeMod(player, Attribute.GENERIC_ARMOR, DEFENSE_MOD_KEY);
        removeMod(player, Attribute.GENERIC_ATTACK_DAMAGE, DAMAGE_MOD_KEY);
    }

    private static void removeMod(Player player, Attribute attr, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attr);
        if (instance != null && instance.getModifier(key) != null) {
            instance.removeModifier(key);
        }
    }

    private static void syncModifier(
            Player player,
            Attribute attribute,
            NamespacedKey key,
            double amount,
            AttributeModifier.Operation op
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(key);
        if (amount <= 0.0) {
            if (existing != null) {
                instance.removeModifier(key);
            }
            return;
        }
        if (existing != null) {
            if (Double.compare(existing.getAmount(), amount) == 0 && existing.getOperation() == op) {
                return;
            }
            instance.removeModifier(key);
        }
        instance.addTransientModifier(new AttributeModifier(key, amount, op));
    }
}
