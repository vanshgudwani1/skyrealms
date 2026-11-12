package com.skyrealms.end.training;

import com.skyrealms.end.progression.CombatDiscipline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure deterministic scoring, accuracy evaluation, and anti-farm XP scaling for training trials.
 */
public final class TrainingScoringEngine {

    public static final long DIMINISHING_WINDOW_MILLIS = 180_000L; // 3-minute sliding window
    public static final int FULL_XP_SESSIONS_PER_WINDOW = 4;

    public record HitScoreBreakdown(
            int pointsAwarded,
            boolean bullseyeOrCritical,
            double accuracyOrComboMultiplier
    ) {}

    public record TrainingCompletionSummary(
            @NotNull TrainingChallengeType challenge,
            int finalScore,
            int hits,
            int misses,
            double accuracyPercent,
            int maxStreak,
            long xpAwarded,
            double xpScaleApplied
    ) {}

    private final Map<UUID, Deque<Long>> recentCompletions = new ConcurrentHashMap<>();

    /**
     * Scores an Archery hit using shooter-to-target distance, ring precision, and streak.
     */
    @NotNull
    public HitScoreBreakdown scoreArcheryHit(
            @NotNull TrainingChallengeType challenge,
            double distanceBlocks,
            double hitOffsetFromCenter,
            int currentStreak
    ) {
        int base = switch (challenge) {
            case ARCHERY_FLYING -> 35;
            case ARCHERY_MOVING -> 28;
            case ARCHERY_DISTANCE -> 30;
            default -> 20;
        };
        boolean bullseye = hitOffsetFromCenter <= 0.45;
        double distBonus = challenge == TrainingChallengeType.ARCHERY_DISTANCE
                ? Math.max(1.0, Math.min(2.6, distanceBlocks / 8.0))
                : 1.0 + Math.min(0.6, Math.max(0.0, (distanceBlocks - 6.0) * 0.05));
        double streakMult = 1.0 + Math.min(1.0, currentStreak * 0.10);
        int points = (int) Math.round(base * distBonus * streakMult * (bullseye ? 1.35 : 1.0));
        return new HitScoreBreakdown(Math.max(5, points), bullseye, streakMult);
    }

    /**
     * Scores a Melee dummy strike, combo step, or parry counter.
     */
    @NotNull
    public HitScoreBreakdown scoreMeleeHit(
            @NotNull TrainingChallengeType challenge,
            double damage,
            int comboStep,
            boolean timedParryCounter
    ) {
        int base = switch (challenge) {
            case MELEE_PARRY -> timedParryCounter ? 75 : 20;
            case MELEE_SURVIVAL, MELEE_SPARRING -> 35;
            case MELEE_COMBO -> 28;
            default -> 18;
        };
        double dmgScale = Math.max(0.75, Math.min(2.0, damage / 6.0));
        double comboMult = 1.0 + Math.min(1.2, comboStep * 0.12);
        int points = (int) Math.round(base * dmgScale * comboMult);
        return new HitScoreBreakdown(Math.max(5, points), timedParryCounter || comboStep >= 4, comboMult);
    }

    /**
     * Scores an Arcane spell target hit, obstacle pierce, or elemental/spell combination.
     */
    @NotNull
    public HitScoreBreakdown scoreMageHit(
            @NotNull TrainingChallengeType challenge,
            boolean matchingElementOrSequence,
            int comboStep,
            double spellPowerMultiplier
    ) {
        int base = switch (challenge) {
            case MAGE_COMBO -> matchingElementOrSequence ? 60 : 18;
            case MAGE_ELEMENTAL -> matchingElementOrSequence ? 55 : 12;
            case MAGE_OBSTACLES -> 38;
            default -> 24;
        };
        double comboMult = matchingElementOrSequence ? (1.0 + Math.min(1.2, comboStep * 0.15)) : 1.0;
        int points = (int) Math.round(base * comboMult * Math.max(1.0, Math.min(1.8, spellPowerMultiplier)));
        return new HitScoreBreakdown(Math.max(5, points), matchingElementOrSequence && comboStep >= 3, comboMult);
    }

    /**
     * Finalizes a training session, computing accuracy %, diminishing-returns anti-spam scaling, and XP payout.
     */
    @NotNull
    public TrainingCompletionSummary summarizeAndComputeXp(
            @NotNull UUID playerUuid,
            @NotNull TrainingChallengeType challenge,
            int rawScore,
            int hits,
            int misses,
            int maxStreak,
            long nowMillis
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(challenge, "challenge cannot be null");

        int safeHits = Math.max(0, hits);
        int safeMisses = Math.max(0, misses);
        int totalShots = safeHits + safeMisses;
        double accuracy = totalShots > 0 ? (safeHits * 100.0) / totalShots : 0.0;

        if (safeHits == 0 || rawScore <= 0) {
            return new TrainingCompletionSummary(challenge, Math.max(0, rawScore), safeHits, safeMisses, accuracy, maxStreak, 0L, 0.0);
        }

        // Apply accuracy weighting for Archery accuracy mode & performance scaling overall
        double performanceScale = Math.min(1.6, 0.65 + (rawScore / 450.0));
        if (challenge == TrainingChallengeType.ARCHERY_ACCURACY && accuracy < 50.0) {
            performanceScale *= 0.6;
        }

        double antiSpamScale = evaluateCompletionScale(playerUuid, nowMillis);
        long xp = Math.round(challenge.baseXpReward() * performanceScale * antiSpamScale);
        if (antiSpamScale > 0.0) {
            xp = Math.max(8L, xp);
        }

        return new TrainingCompletionSummary(
                challenge,
                rawScore,
                safeHits,
                safeMisses,
                accuracy,
                maxStreak,
                xp,
                antiSpamScale
        );
    }

    public double evaluateCompletionScale(@NotNull UUID playerUuid, long nowMillis) {
        Deque<Long> window = recentCompletions.computeIfAbsent(playerUuid, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && (nowMillis - window.peekFirst()) > DIMINISHING_WINDOW_MILLIS) {
                window.pollFirst();
            }
            int priorCount = window.size();
            window.addLast(nowMillis);

            if (priorCount < FULL_XP_SESSIONS_PER_WINDOW) {
                return 1.0;
            }
            if (priorCount == FULL_XP_SESSIONS_PER_WINDOW) {
                return 0.65;
            }
            if (priorCount == FULL_XP_SESSIONS_PER_WINDOW + 1) {
                return 0.35;
            }
            return 0.15;
        }
    }

    public void forgetPlayer(@NotNull UUID playerUuid) {
        recentCompletions.remove(playerUuid);
    }

    @NotNull
    public static String elementNameForPhase(int step) {
        return switch (Math.floorMod(step, 3)) {
            case 0 -> "IGNIS";
            case 1 -> "FROST";
            default -> "VOID";
        };
    }

    public static boolean matchesNpcTarget(@NotNull TrainingChallengeType challenge, @Nullable String npcActionTarget) {
        if (npcActionTarget == null || npcActionTarget.isBlank()) {
            return true;
        }
        return switch (npcActionTarget) {
            case "archery_range" -> challenge.discipline() == CombatDiscipline.ARCHERY;
            case "melee_combos" -> challenge == TrainingChallengeType.MELEE_DUMMIES
                    || challenge == TrainingChallengeType.MELEE_COMBO
                    || challenge == TrainingChallengeType.MELEE_PARRY;
            case "melee_arena" -> challenge == TrainingChallengeType.MELEE_SPARRING
                    || challenge == TrainingChallengeType.MELEE_SURVIVAL;
            case "arcane_trials" -> challenge == TrainingChallengeType.MAGE_SPELL_TARGETS
                    || challenge == TrainingChallengeType.MAGE_MANA_CHALLENGE
                    || challenge == TrainingChallengeType.MAGE_OBSTACLES;
            case "arcane_elements" -> challenge == TrainingChallengeType.MAGE_ELEMENTAL
                    || challenge == TrainingChallengeType.MAGE_COMBO;
            default -> true;
        };
    }
}
