package com.skyrealms.end.quest;

import com.skyrealms.end.progression.XpSource;

/**
 * Categories of quests in the SkyRealms End Civilization progression.
 */
public enum QuestCategory {
    MAIN_STORY("Main Story", "#FFD166", XpSource.GUILD_QUEST),
    GUILD_ARCHERY("Archery Guild", "#55FF55", XpSource.GUILD_QUEST),
    GUILD_MELEE("Melee Guild", "#FF5555", XpSource.GUILD_QUEST),
    GUILD_MAGE("Arcane Guild", "#AA55FF", XpSource.GUILD_QUEST),
    MASTERY_TRIAL("Mastery Trial", "#FF55FF", XpSource.MASTERY_CHALLENGE),
    DAILY_CONTRACT("Daily Contract", "#4ECDC4", XpSource.DAILY_CONTRACT),
    WEEKLY_CHALLENGE("Weekly Challenge", "#F7B267", XpSource.WEEKLY_ACTIVITY),
    COLLECTION("Collection", "#A8DADC", XpSource.GUILD_QUEST),
    EXPLORATION("Exploration", "#90BE6D", XpSource.GUILD_QUEST);

    private final String displayName;
    private final String hexColor;
    private final XpSource xpSource;

    QuestCategory(String displayName, String hexColor, XpSource xpSource) {
        this.displayName = displayName;
        this.hexColor = hexColor;
        this.xpSource = xpSource;
    }

    public String displayName() {
        return displayName;
    }

    public String hexColor() {
        return hexColor;
    }

    public XpSource xpSource() {
        return xpSource;
    }
}

