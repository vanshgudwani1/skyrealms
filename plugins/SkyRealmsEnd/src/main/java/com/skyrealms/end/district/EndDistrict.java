package com.skyrealms.end.district;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The seven architectural districts of the SkyRealms End Civilization village.
 */
public enum EndDistrict {
    CENTRAL_PLAZA("central_plaza", "Central Plaza", "Heart of the Ancient End Civilization", "#FFD166"),
    WARRIOR_QUARTER("warrior_quarter", "Warrior's Quarter", "Forges, Barracks & Melee Sparring Grounds", "#FF6B6B"),
    ARCHER_QUARTER("archer_quarter", "Archer's Quarter", "Fletchers, Scouts & Precision Range", "#6BCB77"),
    ARCANE_QUARTER("arcane_quarter", "Arcane Quarter", "Astral Observatory & Spell Sanctums", "#B983FF"),
    MERCHANT_DISTRICT("merchant_district", "Merchant District", "End Trading Plaza & Guild Bazaars", "#4ECDC4"),
    GRAND_QUEST_HALL("grand_quest_hall", "Grand Quest Hall", "Archives, Guild Contracts & Expeditions", "#F7B267"),
    DRAGON_SANCTUARY("dragon_sanctuary", "Dragon Sanctuary", "Ancient Shrine & Qualification Seal", "#E066FF");

    private final String id;
    private final String displayName;
    private final String subtitle;
    private final String hexColor;

    EndDistrict(
            @NotNull String id,
            @NotNull String displayName,
            @NotNull String subtitle,
            @NotNull String hexColor
    ) {
        this.id = id;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.hexColor = hexColor;
    }

    @NotNull
    public String id() {
        return id;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }

    @NotNull
    public String subtitle() {
        return subtitle;
    }

    @NotNull
    public String hexColor() {
        return hexColor;
    }

    @Nullable
    public static EndDistrict fromId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        for (EndDistrict d : values()) {
            if (d.id.equals(norm) || d.name().equalsIgnoreCase(norm)) {
                return d;
            }
        }
        return null;
    }
}

