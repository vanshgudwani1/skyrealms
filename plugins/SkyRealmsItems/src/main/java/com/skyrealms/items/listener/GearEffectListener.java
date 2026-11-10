package com.skyrealms.items.listener;

import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.service.ItemRegistryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles active right-click abilities for SkyRealms Farming Hoes.
 */
public final class GearEffectListener implements Listener {

    private final ItemRegistryService itemService;
    private final ArmorAttributeTask attributeTask;

    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> recursiveBreakGuard = ConcurrentHashMap.newKeySet();

    public GearEffectListener(
            @NotNull ItemRegistryService itemService,
            @NotNull ArmorAttributeTask attributeTask
    ) {
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
        this.attributeTask = Objects.requireNonNull(attributeTask, "attributeTask cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (recursiveBreakGuard.contains(player.getUniqueId())) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<CustomItemDefinition> defOpt = itemService.factory().readDefinition(held);
        if (defOpt.isEmpty()) {
            return;
        }

        CustomItemDefinition def = defOpt.get();
        // Prevent custom farming hoes from tilling soil on right-click block
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
        }

        String abilityId = def.abilityId();
        if (abilityId == null) {
            return;
        }

        switch (abilityId) {
            case "petal_gust" -> {
                if (tryConsumeCooldown(player, def)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 160, 0, true, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 0, true, false, true));
                    player.playSound(player.getLocation(), Sound.BLOCK_CHERRY_SAPLING_PLACE, 0.9f, 1.3f);
                    player.spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 1, 0), 20, 0.6, 0.4, 0.6, 0.04);
                    player.sendActionBar(Component.text("✦ Petal Gust activated (8s)!", NamedTextColor.LIGHT_PURPLE));
                }
            }
            case "bountiful_bloom" -> {
                Block clicked = event.getClickedBlock();
                if (clicked == null) {
                    return;
                }
                if (tryConsumeCooldown(player, def)) {
                    int broken = 0;
                    if (recursiveBreakGuard.add(player.getUniqueId())) {
                        try {
                            for (int dx = -1; dx <= 1; dx++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    Block target = clicked.getRelative(dx, 0, dz);
                                    if (isMatureCrop(target) && player.breakBlock(target)) {
                                        broken++;
                                    }
                                }
                            }
                        } finally {
                            recursiveBreakGuard.remove(player.getUniqueId());
                        }
                    }
                    player.playSound(clicked.getLocation(), Sound.ITEM_CROP_PLANT, 0.9f, 1.2f);
                    player.sendActionBar(Component.text("✦ Bountiful Bloom harvested " + broken + " crops!", NamedTextColor.GREEN));
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        attributeTask.clearPlayerModifiers(event.getPlayer());
        recursiveBreakGuard.remove(uuid);
        cooldowns.keySet().removeIf(key -> key.startsWith(uuid + ":"));
    }

    private boolean tryConsumeCooldown(Player player, CustomItemDefinition def) {
        if (def.abilityCooldownSeconds() <= 0 || def.abilityId() == null) {
            return true;
        }
        String key = player.getUniqueId() + ":" + def.abilityId();
        long now = System.currentTimeMillis();
        Long expiresAt = cooldowns.get(key);
        if (expiresAt != null && expiresAt > now) {
            long remSec = Math.max(1L, (expiresAt - now + 999L) / 1000L);
            player.sendActionBar(Component.text("⏳ " + def.abilityName() + " on cooldown: " + remSec + "s", NamedTextColor.YELLOW));
            return false;
        }
        cooldowns.put(key, now + def.abilityCooldownSeconds() * 1000L);
        return true;
    }

    private static boolean isMatureCrop(Block block) {
        org.bukkit.Material type = block.getType();
        if (type == org.bukkit.Material.MELON_STEM
                || type == org.bukkit.Material.PUMPKIN_STEM
                || type == org.bukkit.Material.ATTACHED_MELON_STEM
                || type == org.bukkit.Material.ATTACHED_PUMPKIN_STEM) {
            return false;
        }
        if (!Tag.CROPS.isTagged(type) && type != org.bukkit.Material.NETHER_WART) {
            return false;
        }
        return block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }
}
