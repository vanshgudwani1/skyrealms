package com.skyrealms.items.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Gameplay disciplines covered by SkyRealms custom items and gear.
 */
public enum ItemDomain {
    FARMING("Farming", Material.DIAMOND_HOE, NamedTextColor.GREEN),
    MINING("Mining", Material.DIAMOND_PICKAXE, NamedTextColor.AQUA),
    SLAYER("Slayer & Combat", Material.DIAMOND_SWORD, NamedTextColor.RED),
    FORAGING("Foraging", Material.DIAMOND_AXE, NamedTextColor.DARK_GREEN),
    FISHING("Fishing", Material.FISHING_ROD, NamedTextColor.BLUE),
    ARMOR("Armor Sets", Material.DIAMOND_CHESTPLATE, NamedTextColor.GOLD),
    UTILITY("Relics & Utility", Material.EMERALD, NamedTextColor.LIGHT_PURPLE);

    private final String displayName;
    private final Material icon;
    private final NamedTextColor color;

    ItemDomain(@NotNull String displayName, @NotNull Material icon, @NotNull NamedTextColor color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }

    @NotNull
    public Material icon() {
        return icon;
    }

    @NotNull
    public NamedTextColor color() {
        return color;
    }
}

