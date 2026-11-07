package com.skyrealms.islands.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configurable visitor flags and protection rules for an island.
 */
public record IslandSettings(
        boolean publicVisits,
        boolean visitorBlockBreak,
        boolean visitorBlockPlace,
        boolean visitorContainerAccess,
        boolean pvpEnabled,
        @Nullable String settingsJson
) {
    @NotNull
    public static IslandSettings defaultSettings() {
        return new IslandSettings(
                true,   // public visits enabled by default
                false,  // visitor block breaking disabled
                false,  // visitor block placing disabled
                false,  // visitor container opening disabled
                false,  // pvp disabled
                "{}"
        );
    }
}
