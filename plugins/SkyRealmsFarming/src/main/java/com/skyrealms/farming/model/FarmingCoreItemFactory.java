package com.skyrealms.farming.model;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Factory and serialization engine for custom Farming Generator Core items.
 */
public final class FarmingCoreItemFactory {

    public static final NamespacedKey CORE_KEY = new NamespacedKey("skyrealms", "farming_core");
    public static final NamespacedKey TIER_KEY = new NamespacedKey("skyrealms", "core_tier");
    public static final NamespacedKey UPGRADES_KEY = new NamespacedKey("skyrealms", "core_upgrades");

    private FarmingCoreItemFactory() {}

    /**
     * Creates a new custom Farming Generator Core item.
     */
    @NotNull
    public static ItemStack createCoreItem(@NotNull FarmingGeneratorTier tier, @Nullable Map<String, Integer> upgrades) {
        ItemStack item = new ItemStack(Material.LODESTONE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Custom Title
        meta.displayName(Component.text("⚡ " + tier.displayName() + " Core", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✦ Arcane SkyRealms Farming Generator ✦", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("• Tier: ", NamedTextColor.GRAY)
                .append(Component.text(tier.displayName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("• Ground Requirement: ", NamedTextColor.GRAY)
                .append(Component.text("Flat 5x5 Dirt or Grass", NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("• Irrigation: ", NamedTextColor.GRAY)
                .append(Component.text("Automatic Sub-Surface Water", NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("• Display: ", NamedTextColor.GRAY)
                .append(Component.text("Animated 3D DecentHologram", NamedTextColor.LIGHT_PURPLE))
                .decoration(TextDecoration.ITALIC, false));

        if (upgrades != null && !upgrades.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("Preserved Upgrades:", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            for (Map.Entry<String, Integer> entry : upgrades.entrySet()) {
                lore.add(Component.text("  ✦ " + entry.getKey().toUpperCase() + ": ", NamedTextColor.GRAY)
                        .append(Component.text("Lvl " + entry.getValue(), NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("▶ Place on your island to trigger Earth Bloom!", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        // Persistent Data Container
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CORE_KEY, PersistentDataType.STRING, "true");
        pdc.set(TIER_KEY, PersistentDataType.STRING, tier.name());

        if (upgrades != null && !upgrades.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : upgrades.entrySet()) {
                if (!sb.isEmpty()) sb.append(";");
                sb.append(e.getKey()).append(":").append(e.getValue());
            }
            pdc.set(UPGRADES_KEY, PersistentDataType.STRING, sb.toString());
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Checks if an ItemStack is an authentic Farming Generator Core.
     */
    public static boolean isFarmingCore(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(CORE_KEY, PersistentDataType.STRING);
    }

    /**
     * Extracts the tier from a Farming Core item.
     */
    @NotNull
    public static FarmingGeneratorTier extractTier(@NotNull ItemStack item) {
        if (!isFarmingCore(item)) return FarmingGeneratorTier.TIER_1_WHEAT;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return FarmingGeneratorTier.TIER_1_WHEAT;
        String tierName = meta.getPersistentDataContainer().get(TIER_KEY, PersistentDataType.STRING);
        if (tierName == null) return FarmingGeneratorTier.TIER_1_WHEAT;
        try {
            return FarmingGeneratorTier.valueOf(tierName);
        } catch (IllegalArgumentException e) {
            return FarmingGeneratorTier.TIER_1_WHEAT;
        }
    }

    /**
     * Extracts preserved upgrades from a Farming Core item.
     */
    @NotNull
    public static Map<String, Integer> extractUpgrades(@NotNull ItemStack item) {
        Map<String, Integer> map = new HashMap<>();
        if (!isFarmingCore(item)) return map;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return map;

        String raw = meta.getPersistentDataContainer().get(UPGRADES_KEY, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return map;

        for (String pair : raw.split(";")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    map.put(kv[0].toLowerCase(), Integer.parseInt(kv[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }
}
