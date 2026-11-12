package com.skyrealms.end.quest;

import com.skyrealms.end.progression.CombatDiscipline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable blueprint defining an End Civilization quest, prerequisite, objective, and authoritative rewards.
 */
public record QuestDefinition(
        @NotNull String id,
        @NotNull String title,
        @NotNull String description,
        @NotNull QuestCategory category,
        @NotNull String npcBoardTag,
        @Nullable String prerequisiteQuestId,
        @Nullable CombatDiscipline disciplineFilter,
        int requiredDisciplineLevel,
        @NotNull QuestObjectiveType objectiveType,
        @Nullable String targetItemOrKey,
        int targetValue,
        @NotNull CycleType cycleType,
        long xpReward,
        @Nullable CombatDiscipline xpDiscipline,
        long coinReward,
        int reputationReward,
        @Nullable String itemRewardId,
        int itemRewardQty,
        boolean grantsMastery
) {
    public enum CycleType {
        PERMANENT,
        DAILY,
        WEEKLY
    }

    public QuestDefinition {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(npcBoardTag, "npcBoardTag cannot be null");
        Objects.requireNonNull(objectiveType, "objectiveType cannot be null");
        Objects.requireNonNull(cycleType, "cycleType cannot be null");
        if (targetValue <= 0) {
            throw new IllegalArgumentException("targetValue must be positive for quest " + id);
        }
    }
}

