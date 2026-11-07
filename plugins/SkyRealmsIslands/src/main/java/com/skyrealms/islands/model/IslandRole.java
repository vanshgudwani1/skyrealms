package com.skyrealms.islands.model;

import org.jetbrains.annotations.NotNull;

/**
 * 4-Tier member and visitor hierarchy for SkyRealms islands.
 */
public enum IslandRole {
    LEADER(4),
    OFFICER(3),
    MEMBER(2),
    VISITOR(1),
    NONE(0);

    private final int level;

    IslandRole(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean isAtLeast(@NotNull IslandRole required) {
        return this.level >= required.level;
    }
}
