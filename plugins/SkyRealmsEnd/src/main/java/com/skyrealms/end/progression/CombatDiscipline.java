package com.skyrealms.end.progression;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The three independent End Civilization combat disciplines.
 * Players may switch freely at any time without losing XP or levels in any discipline.
 */
public enum CombatDiscipline {
    ARCHERY(
            "Archery",
            "#55FF55",
            "Ranged Damage, Arrow Velocity, Crit Draw & Agility",
            "master_kaelen"
    ),
    MELEE(
            "Melee",
            "#FF5555",
            "Attack Damage, Max Health, Armor Toughness & Parry",
            "grandmaster_rovan"
    ),
    MAGE(
            "Mage",
            "#AA55FF",
            "Max Mana, Mana Regen, Spell Power & Cooldown Haste",
            "archmage_eryndor"
    );

    private final String displayName;
    private final String accentHex;
    private final String attributeSummary;
    private final String guildMasterNpcId;

    CombatDiscipline(
            @NotNull String displayName,
            @NotNull String accentHex,
            @NotNull String attributeSummary,
            @NotNull String guildMasterNpcId
    ) {
        this.displayName = displayName;
        this.accentHex = accentHex;
        this.attributeSummary = attributeSummary;
        this.guildMasterNpcId = guildMasterNpcId;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }

    @NotNull
    public String accentHex() {
        return accentHex;
    }

    @NotNull
    public String attributeSummary() {
        return attributeSummary;
    }

    @NotNull
    public String guildMasterNpcId() {
        return guildMasterNpcId;
    }

    @NotNull
    public static CombatDiscipline fromString(@Nullable String raw, @NotNull CombatDiscipline fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /**
     * Infers the matching combat discipline from a held weapon's material and optional
     * SkyRealms custom item identifier, supporting seamless free weapon switching.
     */
    @NotNull
    public static CombatDiscipline detectFromWeapon(
            @Nullable Material material,
            @Nullable String customItemId,
            @NotNull CombatDiscipline fallback
    ) {
        if (customItemId != null && !customItemId.isBlank()) {
            String normalized = customItemId.toLowerCase(Locale.ROOT);
            if (normalized.contains("bow") || normalized.contains("voidpiercer") || normalized.contains("fletcher")) {
                return ARCHERY;
            }
            if (normalized.contains("staff") || normalized.contains("voidcaller") || normalized.contains("conduit")
                    || normalized.contains("wand") || normalized.contains("tome") || normalized.contains("arcane")) {
                return MAGE;
            }
            if (normalized.contains("sword") || normalized.contains("blade") || normalized.contains("cleaver")
                    || normalized.contains("riftbreaker") || normalized.contains("executioner") || normalized.contains("axe")) {
                return MELEE;
            }
        }
        if (material == null || material == Material.AIR) {
            return fallback;
        }
        return switch (material) {
            case BOW, CROSSBOW -> ARCHERY;
            case BLAZE_ROD, BREEZE_ROD, END_ROD, AMETHYST_SHARD, ENCHANTED_BOOK, BOOK -> MAGE;
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD,
                 WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE,
                 TRIDENT, MACE -> MELEE;
            default -> fallback;
        };
    }
}

