package com.skyrealms.end.npc;

/**
 * Extensible interaction routing actions for configurable End Village NPCs.
 */
public enum NpcActionType {
    DIALOGUE,
    SHOP,
    QUEST_BOARD,
    CRAFTING,
    TRAINING,
    REPUTATION,
    LEADERBOARD,
    AUCTION,
    WARP_WILDERNESS,
    DRAGON_LICENSE;

    public static NpcActionType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return DIALOGUE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DIALOGUE;
        }
    }
}

