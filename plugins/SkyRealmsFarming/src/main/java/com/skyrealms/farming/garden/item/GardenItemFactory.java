package com.skyrealms.farming.garden.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Factory for custom agricultural items, enchanted offering components, and starter farming tools.
 */
public final class GardenItemFactory {

    public static final NamespacedKey CUSTOM_ITEM_KEY = new NamespacedKey("skyrealms", "custom_item_id");
    public static final String ID_ENCHANTED_HAY_BALE = "enchanted_hay_bale";
    public static final String ID_ENCHANTED_CARROT = "enchanted_carrot";
    public static final String ID_ENCHANTED_SUGAR_CANE = "enchanted_sugar_cane";
    public static final String ID_NOVICE_HOE = "novice_cultivator_hoe";

    private GardenItemFactory() {}

    /**
     * Creates an Enchanted Hay Bale required for the Divine Offering.
     */
    @NotNull
    public static ItemStack createEnchantedHayBale(int amount) {
        ItemStack item = new ItemStack(Material.HAY_BLOCK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("✦ Enchanted Hay Bale ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✦ Divine Agricultural Offering ✦", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Tightly bound golden wheat infused with", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("the essence of celestial fertility.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Required: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("3x for Offering to the Gods", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, ID_ENCHANTED_HAY_BALE);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates an Enchanted Carrot required for the Divine Offering.
     */
    @NotNull
    public static ItemStack createEnchantedCarrot(int amount) {
        ItemStack item = new ItemStack(Material.CARROT, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("✦ Enchanted Carrot ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✦ Divine Agricultural Offering ✦", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("A luminous crisp root pulsating with", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("abundant sweetness and divine blessing.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Required: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("3x for Offering to the Gods", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, ID_ENCHANTED_CARROT);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates an Enchanted Sugar Cane required for the Divine Offering.
     */
    @NotNull
    public static ItemStack createEnchantedSugarCane(int amount) {
        ItemStack item = new ItemStack(Material.SUGAR_CANE, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("✦ Enchanted Sugar Cane ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✦ Divine Agricultural Offering ✦", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Crystallized reeds radiating celestial", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("vitality and ancient botanical magic.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Required: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("3x for Offering to the Gods", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, ID_ENCHANTED_SUGAR_CANE);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates Elarion's Novice Cultivator Hoe given as starter supply.
     */
    @NotNull
    public static ItemStack createNoviceHoe() {
        ItemStack item = new ItemStack(Material.STONE_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("🌱 Novice Cultivator Hoe", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✦ Elarion's Starter Implement ✦", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("A balanced, durable farming hoe blessed", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("by the Keeper of the Sakura Garden.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Ability: ", NamedTextColor.GOLD)
                .append(Component.text("Quick Till", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("• Prepares farmland with 100% moisture retention.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, ID_NOVICE_HOE);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Registers vanilla crafting recipes for the Enchanted Offering components.
     */
    public static void registerRecipes(@NotNull org.bukkit.plugin.Plugin plugin) {
        org.bukkit.NamespacedKey keyHay = new org.bukkit.NamespacedKey(plugin, "enchanted_hay_bale");
        if (org.bukkit.Bukkit.getRecipe(keyHay) == null) {
            org.bukkit.inventory.ShapedRecipe hayRecipe = new org.bukkit.inventory.ShapedRecipe(keyHay, createEnchantedHayBale(1));
            hayRecipe.shape("HHH", "HHH", "HHH");
            hayRecipe.setIngredient('H', Material.HAY_BLOCK);
            org.bukkit.Bukkit.addRecipe(hayRecipe);
        }

        org.bukkit.NamespacedKey keyCarrot = new org.bukkit.NamespacedKey(plugin, "enchanted_carrot");
        if (org.bukkit.Bukkit.getRecipe(keyCarrot) == null) {
            org.bukkit.inventory.ShapedRecipe carrotRecipe = new org.bukkit.inventory.ShapedRecipe(keyCarrot, createEnchantedCarrot(1));
            carrotRecipe.shape("CCC", "CGC", "CCC");
            carrotRecipe.setIngredient('C', Material.CARROT);
            carrotRecipe.setIngredient('G', Material.GOLDEN_CARROT);
            org.bukkit.Bukkit.addRecipe(carrotRecipe);
        }

        org.bukkit.NamespacedKey keyCane = new org.bukkit.NamespacedKey(plugin, "enchanted_sugar_cane");
        if (org.bukkit.Bukkit.getRecipe(keyCane) == null) {
            org.bukkit.inventory.ShapedRecipe caneRecipe = new org.bukkit.inventory.ShapedRecipe(keyCane, createEnchantedSugarCane(1));
            caneRecipe.shape("SSS", "SSS", "SSS");
            caneRecipe.setIngredient('S', Material.SUGAR_CANE);
            org.bukkit.Bukkit.addRecipe(caneRecipe);
        }
    }

    /**
     * Checks if an item stack matches a specific custom item identifier via PersistentDataContainer.
     */
    public static boolean isCustomItem(@Nullable ItemStack item, @NotNull String expectedId) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        return Objects.equals(id, expectedId);
    }

    /**
     * Retrieves the custom item identifier if present.
     */
    @Nullable
    public static String getCustomItemId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(CUSTOM_ITEM_KEY, PersistentDataType.STRING);
    }
}
