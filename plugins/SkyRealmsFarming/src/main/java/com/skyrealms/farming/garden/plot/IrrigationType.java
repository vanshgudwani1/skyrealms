package com.skyrealms.farming.garden.plot;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Functional and visual irrigation channel styles for customizable plots.
 */
public enum IrrigationType {
    STANDARD("Standard Channels", Material.STONE_SLAB, Material.SEA_LANTERN, "Recessed waterlogged stone slabs lit by sea lanterns"),
    PRISMARINE_CANAL("Prismarine Canal", Material.PRISMARINE_SLAB, Material.SEA_LANTERN, "Open dark prismarine water troughs with luminous accents"),
    BAMBOO_AQUEDUCT("Bamboo Aqueduct", Material.BAMBOO_SLAB, Material.LANTERN, "Warm bamboo-trimmed garden water channels");

    private final String displayName;
    private final Material trimMaterial;
    private final Material lightMaterial;
    private final String description;

    IrrigationType(@NotNull String displayName, @NotNull Material trimMaterial, @NotNull Material lightMaterial, @NotNull String description) {
        this.displayName = displayName;
        this.trimMaterial = trimMaterial;
        this.lightMaterial = lightMaterial;
        this.description = description;
    }

    @NotNull public String displayName() { return displayName; }
    @NotNull public Material trimMaterial() { return trimMaterial; }
    @NotNull public Material lightMaterial() { return lightMaterial; }
    @NotNull public String description() { return description; }

    @NotNull
    public static IrrigationType fromName(@Nullable String name) {
        if (name == null) return STANDARD;
        for (IrrigationType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return STANDARD;
    }
}

