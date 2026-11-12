package com.skyrealms.end.npc;

/**
 * Centralized animation profiles driven by the End Civilization Animation Scheduler.
 */
public enum AnimationProfile {
    IDLE,
    BLACKSMITH,
    MERCHANT,
    MAGE,
    ARCHER,
    WARRIOR,
    GUARD,
    QUEST;

    public static AnimationProfile fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return IDLE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return IDLE;
        }
    }
}

