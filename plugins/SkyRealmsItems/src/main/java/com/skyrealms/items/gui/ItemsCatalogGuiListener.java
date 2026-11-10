package com.skyrealms.items.gui;

import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.items.service.ItemRegistryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Authoritative InventoryHolder click handler for the SkyRealms Farming Hoes & Upgrade GUI.
 */
public final class ItemsCatalogGuiListener implements Listener {

    private final ItemRegistryService itemService;

    public ItemsCatalogGuiListener(@NotNull ItemRegistryService itemService) {
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ItemsCatalogGui.CatalogHolder)) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == ItemsCatalogGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (rawSlot == ItemsCatalogGui.SLOT_UPGRADE_HOE) {
            ItemStack held = player.getInventory().getItemInMainHand();
            HoeUpgradeService.UpgradeAttemptResult result = itemService.upgrades().upgradeHeldHoe(player, held, false);
            if (result.success()) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.3f);
                player.sendMessage(Component.text("✦ " + result.message(), NamedTextColor.GREEN));
                ItemsCatalogGui.open(player, itemService);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                player.sendMessage(Component.text("✖ " + result.message(), NamedTextColor.RED));
            }
            return;
        }

        if (!player.hasPermission("skyrealms.items.admin") && !player.isOp()) {
            return;
        }

        List<CustomItemDefinition> hoes = List.copyOf(itemService.catalog().all());
        for (int i = 0; i < ItemsCatalogGui.HOE_SLOTS.length && i < hoes.size(); i++) {
            if (rawSlot == ItemsCatalogGui.HOE_SLOTS[i]) {
                CustomItemDefinition def = hoes.get(i);
                ItemStack granted = itemService.factory().createItem(def, 1);
                player.getInventory().addItem(granted).values()
                        .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.3f);
                player.sendMessage(Component.text("✦ Claimed 1x " + def.displayName() + " [Lv.1].", NamedTextColor.GREEN));
                return;
            }
        }
    }
}
