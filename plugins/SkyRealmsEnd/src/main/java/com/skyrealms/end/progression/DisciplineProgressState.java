package com.skyrealms.end.progression;

import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshots and per-player runtime state for End Civilization combat disciplines.
 */
public final class DisciplineProgressState {

    private DisciplineProgressState() {
    }

    public record DisciplineEntry(
            @NotNull CombatDiscipline discipline,
            int level,
            long totalXp,
            boolean masteryCompleted,
            int trainingBestScore,
            long updatedAt
    ) {
        public static DisciplineEntry initial(@NotNull CombatDiscipline discipline, long nowMillis) {
            return new DisciplineEntry(discipline, EndXpCurve.MIN_LEVEL, 0L, false, 0, nowMillis);
        }

        public DisciplineEntry withXp(long newTotalXp, long nowMillis) {
            long clamped = EndXpCurve.clampTotalXp(newTotalXp);
            int computedLevel = EndXpCurve.levelForTotalXp(clamped);
            return new DisciplineEntry(discipline, computedLevel, clamped, masteryCompleted, trainingBestScore, nowMillis);
        }

        public DisciplineEntry withMastery(boolean completed, long nowMillis) {
            return new DisciplineEntry(discipline, level, totalXp, completed, trainingBestScore, nowMillis);
        }

        public DisciplineEntry withTrainingBestScore(int bestScore, long nowMillis) {
            int updatedBest = Math.max(this.trainingBestScore, bestScore);
            return new DisciplineEntry(discipline, level, totalXp, masteryCompleted, updatedBest, nowMillis);
        }
    }

    public record PlayerDisciplineSnapshot(
            @NotNull UUID playerUuid,
            @NotNull CombatDiscipline activeDiscipline,
            @NotNull Map<CombatDiscipline, DisciplineEntry> entries
    ) {
        public PlayerDisciplineSnapshot {
            Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
            Objects.requireNonNull(activeDiscipline, "activeDiscipline cannot be null");
            entries = Map.copyOf(entries);
        }

        @NotNull
        public DisciplineEntry get(@NotNull CombatDiscipline discipline) {
            return entries.getOrDefault(discipline, DisciplineEntry.initial(discipline, 0L));
        }

        public boolean allMasteriesUnlockedAndMaxed() {
            for (CombatDiscipline d : CombatDiscipline.values()) {
                DisciplineEntry entry = get(d);
                if (entry.level() < EndXpCurve.MAX_LEVEL || !entry.masteryCompleted()) {
                    return false;
                }
            }
            return true;
        }
    }

    public record XpAwardResult(
            @NotNull CombatDiscipline discipline,
            long requestedXp,
            long grantedXp,
            int oldLevel,
            int newLevel,
            long newTotalXp,
            boolean leveledUp,
            boolean suppressedByAntiAfk,
            @NotNull String note
    ) {}

    public record LeaderboardEntry(
            @NotNull UUID playerUuid,
            @NotNull CombatDiscipline discipline,
            int level,
            long totalXp,
            boolean masteryCompleted
    ) {}

    /**
     * Thread-safe mutable runtime state held for online players.
     */
    public static final class MutablePlayerState {
        private final UUID playerUuid;
        private CombatDiscipline activeDiscipline;
        private final EnumMap<CombatDiscipline, DisciplineEntry> entries = new EnumMap<>(CombatDiscipline.class);
        private double currentMana;
        private boolean dirty;

        public MutablePlayerState(
                @NotNull UUID playerUuid,
                @NotNull CombatDiscipline activeDiscipline,
                @NotNull Map<CombatDiscipline, DisciplineEntry> loaded
        ) {
            this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
            this.activeDiscipline = Objects.requireNonNull(activeDiscipline, "activeDiscipline cannot be null");
            long now = System.currentTimeMillis();
            for (CombatDiscipline d : CombatDiscipline.values()) {
                this.entries.put(d, loaded.getOrDefault(d, DisciplineEntry.initial(d, now)));
            }
            this.currentMana = EndXpCurve.attributesFor(
                    CombatDiscipline.MAGE,
                    this.entries.get(CombatDiscipline.MAGE).level()
            ).maxMana();
        }

        @NotNull
        public synchronized PlayerDisciplineSnapshot snapshot() {
            return new PlayerDisciplineSnapshot(playerUuid, activeDiscipline, new EnumMap<>(entries));
        }

        @NotNull
        public synchronized CombatDiscipline activeDiscipline() {
            return activeDiscipline;
        }

        public synchronized boolean setActiveDiscipline(@NotNull CombatDiscipline target) {
            if (this.activeDiscipline == target) {
                return false;
            }
            this.activeDiscipline = target;
            this.dirty = true;
            return true;
        }

        @NotNull
        public synchronized DisciplineEntry entry(@NotNull CombatDiscipline discipline) {
            return entries.get(discipline);
        }

        public synchronized void putEntry(@NotNull DisciplineEntry entry) {
            entries.put(entry.discipline(), entry);
            this.dirty = true;
        }

        public synchronized double currentMana() {
            return currentMana;
        }

        public synchronized double maxMana() {
            return EndXpCurve.attributesFor(
                    CombatDiscipline.MAGE,
                    entries.get(CombatDiscipline.MAGE).level()
            ).maxMana();
        }

        public synchronized void regenerateMana(double amount) {
            double max = maxMana();
            this.currentMana = Math.min(max, Math.max(0.0, this.currentMana + amount));
        }

        public synchronized boolean consumeMana(double cost) {
            if (cost <= 0.0) {
                return true;
            }
            if (this.currentMana < cost) {
                return false;
            }
            this.currentMana -= cost;
            return true;
        }

        public synchronized boolean isDirty() {
            return dirty;
        }

        public synchronized void clearDirty() {
            this.dirty = false;
        }
    }
}

