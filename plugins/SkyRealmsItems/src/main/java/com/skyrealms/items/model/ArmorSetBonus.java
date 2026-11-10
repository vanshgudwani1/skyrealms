package com.skyrealms.items.model;

import com.skyrealms.platform.integrations.SkyItemStats;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Defines a 4-piece armor set bonus activated when wearing all matching pieces.
 */
public record ArmorSetBonus(
        @NotNull String setId,
        @NotNull String displayName,
        @NotNull String bonusSummary,
        @NotNull SkyItemStats bonusStats,
        double lifestealPercent,
        boolean grantsPassiveHaste
) {
    public ArmorSetBonus {
        Objects.requireNonNull(setId, "setId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(bonusSummary, "bonusSummary cannot be null");
        Objects.requireNonNull(bonusStats, "bonusStats cannot be null");
    }
}

