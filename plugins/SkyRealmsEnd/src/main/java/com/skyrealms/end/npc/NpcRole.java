package com.skyrealms.end.npc;

/**
 * High-level functional archetypes for configurable End Civilization NPCs.
 */
public enum NpcRole {
    STEWARD,
    HERALD,
    GUIDE,
    ARCHIVIST,
    GUILDMASTER,
    BLACKSMITH,
    TRAINER,
    FLETCHER,
    HUNTER,
    COLLECTOR,
    RESEARCHER,
    ALCHEMIST,
    ENCHANTER,
    MERCHANT,
    AUCTION,
    QUEST,
    LICENSE,
    GUARD;

    public static NpcRole fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MERCHANT;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MERCHANT;
        }
    }
}

