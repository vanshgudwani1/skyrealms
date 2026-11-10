package com.skyrealms.farming.garden.plot;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decorative border and non-intrusive plot styling options.
 * Placed strictly along paths/borders so crop harvesting is never obstructed.
 */
public enum DecorationStyle {
    NONE("Minimalist Lawn", Material.GRASS_BLOCK, "Clean open lawn around your crop beds"),
    CHERRY_LANTERNS("Cherry Lanterns", Material.PINK_PETALS, "Sakura petal accents and hanging corner lanterns"),
    ZEN_STONES("Zen Moss & Azalea", Material.FLOWERING_AZALEA, "Mossy stone accents and flowering azalea shrubs"),
    TRELLIS_ARCH("Garden Trellis", Material.CHERRY_FENCE, "Decorative timber border posts along walkways");

    private final String displayName;
    private final Material iconMaterial;
    private final String description;

    DecorationStyle(@NotNull String displayName, @NotNull Material iconMaterial, @NotNull String description) {
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.description = description;
    }

    @NotNull public String displayName() { return displayName; }
    @NotNull public Material iconMaterial() { return iconMaterial; }
    @NotNull public String description() { return description; }

    @NotNull
    public static DecorationStyle fromName(@Nullable String name) {
        if (name == null) return NONE;
        for (DecorationStyle s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        return NONE;
    }
}

