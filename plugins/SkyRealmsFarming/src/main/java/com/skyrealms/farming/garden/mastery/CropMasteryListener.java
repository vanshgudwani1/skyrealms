package com.skyrealms.farming.garden.mastery;

import com.skyrealms.farming.event.GardenCropHarvestEvent;
import com.skyrealms.platform.api.event.EventBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Event listener integrating Crop Mastery harvest triggers, double drop bonuses,
 * milestone perks, and authoritative InventoryHolder GUI navigation.
 */
public final class CropMasteryListener implements Listener {

    private final CropMasteryService masteryService;

    public CropMasteryListener(
            @NotNull CropMasteryService masteryService,
            @NotNull EventBus eventBus
    ) {
        this.masteryService = Objects.requireNonNull(masteryService, "masteryService cannot be null");

        // Subscribe to GardenCropHarvestEvent
        eventBus.subscribe(GardenCropHarvestEvent.class, this::handleHarvest);
    }

    private void handleHarvest(@NotNull GardenCropHarvestEvent event) {
        if (event.isCancelled()) return;

        CropType cropType = CropType.fromCropId(event.cropType());
        if (cropType == null) return;

        Player player = event.player();

        // 1. Award Mastery XP
        masteryService.awardMasteryXp(player, cropType, cropType.baseMasteryXp());
        CropMastery updated = masteryService.getMastery(player.getUniqueId(), cropType);

        // 2. Evaluate Double Drop Perk
        double doubleDropChance = updated.getTotalDoubleDropBonus();
        boolean doubleDropped = false;
        if (doubleDropChance > 0.0 && Math.random() < doubleDropChance) {
            doubleDropped = true;
            for (ItemStack drop : event.drops()) {
                if (drop != null && drop.getAmount() > 0) {
                    ItemStack extra = drop.clone();
                    HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(extra);
                    if (!overflow.isEmpty()) {
                        for (ItemStack rem : overflow.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), rem);
                        }
                    }
                }
            }

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.6f);
        }

        // 3. Crop-Specific Active Perks
        int level = updated.level();
        if (cropType == CropType.CARROT && level >= 5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0, false, false, false));
        } else if (cropType == CropType.SUGAR_CANE && level >= 5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, false));
        } else if (cropType == CropType.COCOA && level >= 10) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0, false, false, false));
        } else if (cropType == CropType.NETHER_WART && level >= 15) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, false, false));
        }

        // 4. Send clear, comprehensive action bar feedback on every single harvest
        Component actionBarMsg = Component.text("+" + event.farmingXpAwarded() + " Farming XP", TextColor.fromHexString("#77DD77"))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("+" + cropType.baseMasteryXp() + " " + cropType.displayName() + " Mastery", NamedTextColor.GOLD))
                .append(Component.text(" [Lvl " + updated.level() + " - " + Math.round(updated.progressToNextLevel() * 100) + "%]", NamedTextColor.YELLOW));

        if (doubleDropped) {
            actionBarMsg = actionBarMsg.append(Component.text(" [DOUBLE DROP!]", NamedTextColor.AQUA));
        }

        player.sendActionBar(actionBarMsg);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();

        // 1. Overview GUI Handling
        if (holder instanceof CropMasteryGui.CropOverviewHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int rawSlot = event.getRawSlot();
            if (clicked.getType() == Material.BARRIER || rawSlot == 49) {
                player.closeInventory();
                return;
            }

            // Map raw slot to CropType
            for (int i = 0; i < CropMasteryGui.CROP_SLOTS.length && i < CropType.values().length; i++) {
                if (rawSlot == CropMasteryGui.CROP_SLOTS[i]) {
                    CropType type = CropType.values()[i];
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                    masteryService.openCropDetailGui(player, type);
                    return;
                }
            }
        }
        // 2. Crop Detail GUI Handling
        else if (holder instanceof CropMasteryGui.CropDetailHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int rawSlot = event.getRawSlot();
            if (clicked.getType() == Material.ARROW || rawSlot == 49) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                masteryService.openMasteryGui(player);
            }
        }
    }
}
