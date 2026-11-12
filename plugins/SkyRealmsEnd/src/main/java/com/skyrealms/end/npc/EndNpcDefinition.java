package com.skyrealms.end.npc;

import com.skyrealms.end.config.EndVillageConfig.AnchorPoint;
import com.skyrealms.end.district.EndDistrict;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Complete declarative definition for a persistent configurable End Civilization NPC.
 */
public record EndNpcDefinition(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull String titleTag,
        @NotNull EndDistrict district,
        @NotNull NpcRole role,
        @NotNull AnimationProfile animationProfile,
        @NotNull NpcActionType actionType,
        @NotNull String actionTarget,
        @NotNull AnchorPoint anchor,
        @NotNull List<AnchorPoint> patrolRoute,
        @NotNull List<String> dialogue,
        @Nullable String modelId,
        double proximityRadius
) {
    public boolean hasPatrol() {
        return patrolRoute.size() >= 2;
    }
}

