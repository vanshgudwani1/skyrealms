package com.skyrealms.items.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

/**
 * Rarity tiers for SkyRealms custom items and gear.
 */
public enum ItemRarity {
    COMMON("COMMON", NamedTextColor.WHITE),
    UNCOMMON("UNCOMMON", NamedTextColor.GREEN),
    RARE("RARE", NamedTextColor.AQUA),
    EPIC("EPIC", NamedTextColor.DARK_PURPLE),
    LEGENDARY("LEGENDARY", NamedTextColor.GOLD),
    MYTHIC("MYTHIC", NamedTextColor.LIGHT_PURPLE);

    private final String label;
    private final NamedTextColor color;

    ItemRarity(@NotNull String label, @NotNull NamedTextColor color) {
        this.label = label;
        this.color = color;
    }

    @NotNull
    public String label() {
        return label;
    }

    @NotNull
    public NamedTextColor color() {
        return color;
    }
}

