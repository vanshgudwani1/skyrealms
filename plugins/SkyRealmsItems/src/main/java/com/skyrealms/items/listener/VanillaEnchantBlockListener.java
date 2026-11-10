package com.skyrealms.items.listener;

import com.skyrealms.items.factory.SkyRealmsItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Prevents vanilla enchantments from being applied to or remaining on ANY SkyRealms custom item
 * (matching {@code skyrealms:custom_item_id}), while allowing EliteEnchantments custom enchants.
 */
public final class VanillaEnchantBlockListener implements Listener {

    private static final Component BLOCKED_MSG = Component.text(
            "✦ Custom items cannot receive vanilla enchantments! Use custom enchants instead.",
            NamedTextColor.RED
    );

    private final SkyRealmsItemFactory factory;
    private final boolean enabled;

    public VanillaEnchantBlockListener(@NotNull SkyRealmsItemFactory factory, boolean enabled) {
        this.factory = Objects.requireNonNull(factory, "factory cannot be null");
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemEnchant(@NotNull PrepareItemEnchantEvent event) {
        if (!enabled) {
            return;
        }
        if (factory.isCustomItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(@NotNull EnchantItemEvent event) {
        if (!enabled) {
            return;
        }
        if (factory.isCustomItem(event.getItem())) {
            event.setCancelled(true);
            event.getEnchanter().sendMessage(BLOCKED_MSG);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(@NotNull PrepareAnvilEvent event) {
        if (!enabled) {
            return;
        }
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();

        if (!factory.isCustomItem(first) && !factory.isCustomItem(second)) {
            return;
        }

        // Block combining a custom item with any vanilla enchanted item or vanilla enchanted book.
        if (hasVanillaEnchantsOrStoredEnchants(second) || hasVanillaEnchantsOrStoredEnchants(first)) {
            if (second != null && second.getType() != Material.AIR) {
                event.setResult(null);
                return;
            }
        }

        ItemStack result = event.getResult();
        if (result != null && factory.isCustomItem(result) && !result.getEnchantments().isEmpty()) {
            ItemStack sanitized = result.clone();
            factory.sanitizeVanillaEnchants(sanitized);
            event.setResult(sanitized);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(@NotNull PrepareSmithingEvent event) {
        if (!enabled) {
            return;
        }
        ItemStack equipment = event.getInventory().getInputEquipment();
        if (factory.isCustomItem(equipment)) {
            // Protect custom hoes/items from accidentally converting via vanilla netherite smithing templates.
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            sanitizeIfNeeded(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(@NotNull PlayerItemHeldEvent event) {
        if (!enabled) {
            return;
        }
        ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
        sanitizeIfNeeded(next);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (sanitizeIfNeeded(stack)) {
            event.getItem().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!enabled || !(event.getWhoClicked() instanceof Player)) {
            return;
        }
        sanitizeIfNeeded(event.getCurrentItem());
        sanitizeIfNeeded(event.getCursor());
    }

    private boolean sanitizeIfNeeded(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getEnchantments().isEmpty()) {
            return false;
        }
        return factory.sanitizeVanillaEnchants(stack);
    }

    private static boolean hasVanillaEnchantsOrStoredEnchants(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        if (!stack.getEnchantments().isEmpty()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta instanceof EnchantmentStorageMeta storage && !storage.getStoredEnchants().isEmpty();
    }
}

