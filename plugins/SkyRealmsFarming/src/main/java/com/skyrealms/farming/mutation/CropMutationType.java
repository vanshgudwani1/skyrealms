package com.skyrealms.farming.mutation;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

public enum CropMutationType {
    GOLDEN("Golden", "Yields extreme coin value at market.", NamedTextColor.GOLD, 3.0, 1.0, 1.0),
    GIANT("Giant", "Super-sized specimen granting massive bonus Farming XP.", NamedTextColor.LIGHT_PURPLE, 1.0, 3.0, 1.0),
    ANCIENT("Ancient", "Relic crop variety sought after by researchers.", NamedTextColor.DARK_AQUA, 2.0, 1.5, 1.0),
    FERTILE("Fertile", "Overflowing vitality yields bonus drop quantities.", NamedTextColor.GREEN, 1.0, 1.2, 2.0),
    MAGICAL("Magical", "Radiating celestial arcane starlight for mystic craft.", TextColor.color(0xDA70D6), 2.5, 2.0, 1.5);

    private final String displayName;
    private final String description;
    private final TextColor color;
    private final double valueMultiplier;
    private final double xpMultiplier;
    private final double dropMultiplier;

    CropMutationType(
            @NotNull String displayName,
            @NotNull String description,
            @NotNull TextColor color,
            double valueMultiplier,
            double xpMultiplier,
            double dropMultiplier
    ) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.valueMultiplier = valueMultiplier;
        this.xpMultiplier = xpMultiplier;
        this.dropMultiplier = dropMultiplier;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }

    @NotNull
    public String description() {
        return description;
    }

    @NotNull
    public TextColor color() {
        return color;
    }

    public double valueMultiplier() {
        return valueMultiplier;
    }

    public double xpMultiplier() {
        return xpMultiplier;
    }

    public double dropMultiplier() {
        return dropMultiplier;
    }
}
