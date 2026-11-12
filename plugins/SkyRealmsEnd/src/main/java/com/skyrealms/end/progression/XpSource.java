package com.skyrealms.end.progression;

/**
 * Valid authoritative sources of End Civilization combat discipline XP.
 */
public enum XpSource {
    MONSTER_KILL(true, "Monster Kill"),
    GUILD_QUEST(false, "Guild Quest"),
    TRAINING_CHALLENGE(true, "Training Challenge"),
    DAILY_CONTRACT(false, "Daily Contract"),
    WEEKLY_ACTIVITY(false, "Weekly Activity"),
    MASTERY_CHALLENGE(false, "Mastery Challenge"),
    ADMIN_GRANT(false, "Admin Grant");

    private final boolean subjectToAntiAfk;
    private final String displayName;

    XpSource(boolean subjectToAntiAfk, String displayName) {
        this.subjectToAntiAfk = subjectToAntiAfk;
        this.displayName = displayName;
    }

    public boolean subjectToAntiAfk() {
        return subjectToAntiAfk;
    }

    public String displayName() {
        return displayName;
    }
}

