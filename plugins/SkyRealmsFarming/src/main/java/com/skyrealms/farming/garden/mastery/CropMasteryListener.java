package com.skyrealms.farming.garden.mastery;

import com.skyrealms.farming.event.GardenCropHarvestEvent;
import com.skyrealms.platform.api.event.EventBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Event listener integrating Crop Mastery harvest triggers, double drop bonuses,
 * milestone perks, and interactive GUI navigation.
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
        CropMastery mastery = masteryService.getMastery(player.getUniqueId(), cropType);

        // 1. Award Mastery XP
        masteryService.awardMasteryXp(player, cropType, cropType.baseMasteryXp());

        // 2. Evaluate Double Drop Perk
        double doubleDropChance = mastery.getTotalDoubleDropBonus();
        if (doubleDropChance > 0.0 && Math.random() < doubleDropChance) {
            List<ItemStack> extraDrops = new ArrayList<>();
            for (ItemStack drop : event.drops()) {
                if (drop != null && drop.getAmount() > 0) {
                    ItemStack extra = drop.clone();
                    extraDrops.add(extra);
                    HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(extra);
                    if (!overflow.isEmpty()) {
                        for (ItemStack rem : overflow.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), rem);
                        }
                    }
                }
            }

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.6f);
            player.sendActionBar(Component.text("✦ Double Drop Triggered! (+" + Math.round(doubleDropChance * 100) + "% Mastery Bonus)", NamedTextColor.GOLD));
        }

        // 3. Crop-Specific Active Perks
        int level = mastery.level();
        if (cropType == CropType.CARROT && level >= 5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0, false, false, false));
        } else if (cropType == CropType.SUGAR_CANE && level >= 5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, false));
        } else if (cropType == CropType.COCOA && level >= 10) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 0, false, false, false));
        } else if (cropType == CropType.NETHER_WART && level >= 15) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, false, false));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.contains(CropMasteryGui.TITLE_OVERVIEW)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                return;
            }

            // Check if clicked item corresponds to a CropType
            for (CropType type : CropType.values()) {
                if (clicked.getType() == type.icon()) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                    masteryService.openCropDetailGui(player, type);
                    return;
                }
            }
        } else if (title.contains(CropMasteryGui.TITLE_DETAIL_PREFIX)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.ARROW) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                masteryService.openMasteryGui(player);
            }
        }
    }
}
