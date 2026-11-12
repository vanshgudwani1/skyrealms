package com.skyrealms.end.progression;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic XP curve, attribute scaling, and unlock milestone definitions for End combat disciplines.
 *
 * <p>Formula: {@code xpRequiredForNextLevel(level) = 80 + 22 * level + 4 * level * level}
 * for {@code level in [1, 29]}. Summing steps 1 through 29 yields exactly {@code 46,110} XP for Level 30.</p>
 */
public final class EndXpCurve {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 30;

    private static final long[] CUMULATIVE_XP = new long[MAX_LEVEL + 1];

    static {
        CUMULATIVE_XP[1] = 0L;
        for (int level = 1; level < MAX_LEVEL; level++) {
            CUMULATIVE_XP[level + 1] = CUMULATIVE_XP[level] + xpRequiredForNextLevel(level);
        }
    }

    public static final long TOTAL_XP_FOR_MAX_LEVEL = CUMULATIVE_XP[MAX_LEVEL]; // 46,110 XP

    private EndXpCurve() {
    }

    /**
     * Scaled passive bonuses granted at a given level for a discipline.
     */
    public record DisciplineAttributes(
            double bonusMeleeDamage,
            double bonusMaxHealth,
            double bonusArmorToughness,
            double bonusRangedDamagePercent,
            double bonusArrowVelocityPercent,
            double critDrawChancePercent,
            double walkSpeedBonusScalar,
            double maxMana,
            double manaRegenPerSecond,
            double spellPowerMultiplier,
            double cooldownHastePercent
    ) {}

    public record UnlockMilestone(
            int level,
            @NotNull String label
    ) {}

    /**
     * Exact formula: {@code 80 + 22 * level + 4 * level * level}. Returns 0 at or above {@link #MAX_LEVEL}.
     */
    public static long xpRequiredForNextLevel(int level) {
        if (level < MIN_LEVEL || level >= MAX_LEVEL) {
            return 0L;
        }
        long l = level;
        return 80L + 22L * l + 4L * l * l;
    }

    /**
     * Total cumulative XP required from level 1 (0 XP) to reach {@code level} (clamped to [1, 30]).
     */
    public static long totalXpForLevel(int level) {
        int clamped = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        return CUMULATIVE_XP[clamped];
    }

    /**
     * Computes the discipline level [1..30] corresponding to {@code totalXp}.
     */
    public static int levelForTotalXp(long totalXp) {
        if (totalXp <= 0L) {
            return MIN_LEVEL;
        }
        if (totalXp >= TOTAL_XP_FOR_MAX_LEVEL) {
            return MAX_LEVEL;
        }
        int level = MIN_LEVEL;
        for (int candidate = 2; candidate <= MAX_LEVEL; candidate++) {
            if (totalXp >= CUMULATIVE_XP[candidate]) {
                level = candidate;
            } else {
                break;
            }
        }
        return level;
    }

    public static long clampTotalXp(long totalXp) {
        return Math.max(0L, Math.min(TOTAL_XP_FOR_MAX_LEVEL, totalXp));
    }

    public static long xpIntoCurrentLevel(long totalXp) {
        long clamped = clampTotalXp(totalXp);
        int level = levelForTotalXp(clamped);
        return clamped - CUMULATIVE_XP[level];
    }

    public static long xpRemainingToNextLevel(long totalXp) {
        long clamped = clampTotalXp(totalXp);
        int level = levelForTotalXp(clamped);
        if (level >= MAX_LEVEL) {
            return 0L;
        }
        return CUMULATIVE_XP[level + 1] - clamped;
    }

    public static double progressFraction(long totalXp) {
        long clamped = clampTotalXp(totalXp);
        int level = levelForTotalXp(clamped);
        if (level >= MAX_LEVEL) {
            return 1.0;
        }
        long step = xpRequiredForNextLevel(level);
        if (step <= 0L) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.0, (double) xpIntoCurrentLevel(clamped) / (double) step));
    }

    @NotNull
    public static DisciplineAttributes attributesFor(@NotNull CombatDiscipline discipline, int level) {
        Objects.requireNonNull(discipline, "discipline cannot be null");
        int steps = Math.max(0, Math.min(MAX_LEVEL, level) - 1);
        return switch (discipline) {
            case ARCHERY -> new DisciplineAttributes(
                    0.0,
                    0.0,
                    0.0,
                    steps * 2.0,              // +58% ranged damage at lv 30
                    steps * 1.5,              // +43.5% arrow velocity at lv 30
                    5.0 + steps * 1.0,        // 34% crit draw at lv 30
                    steps * 0.003,            // +8.7% walk agility when Archery active
                    100.0,
                    2.0,
                    1.0,
                    0.0
            );
            case MELEE -> new DisciplineAttributes(
                    steps * 0.35,             // +10.15 melee damage at lv 30
                    (steps / 2) * 1.0,        // +14.0 max health (+7 hearts) at lv 30
                    steps * 0.25,             // +7.25 armor toughness at lv 30
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    100.0,
                    2.0,
                    1.0,
                    0.0
            );
            case MAGE -> new DisciplineAttributes(
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    steps * 0.0015,           // +4.35% arcane glide speed at lv 30
                    100.0 + steps * 8.0,      // 332 max mana at lv 30
                    2.0 + steps * 0.25,       // 9.25 mana/s at lv 30
                    1.0 + steps * 0.03,       // 1.87x spell power at lv 30
                    steps * 1.0               // 29% cooldown haste at lv 30
            );
        };
    }

    @NotNull
    public static List<UnlockMilestone> milestonesFor(@NotNull CombatDiscipline discipline) {
        return switch (discipline) {
            case ARCHERY -> List.of(
                    new UnlockMilestone(1, "Apprentice Bow & Stationary Targets"),
                    new UnlockMilestone(5, "Hunter Bow & Moving Target Drills"),
                    new UnlockMilestone(10, "Endwood Longbow & Wind Compensation"),
                    new UnlockMilestone(15, "Voidpiercer Bow & Flying Target Trials"),
                    new UnlockMilestone(20, "Rift Hunter Bow & Long-Range Accuracy"),
                    new UnlockMilestone(25, "Archery Mastery Trial Eligibility"),
                    new UnlockMilestone(30, "Astral Longbow & Dragon Archery Seal")
            );
            case MELEE -> List.of(
                    new UnlockMilestone(1, "Apprentice Sword & Combat Dummies"),
                    new UnlockMilestone(5, "Endsteel Blade & Combo Drills"),
                    new UnlockMilestone(10, "Void Cleaver & Parry Trials"),
                    new UnlockMilestone(15, "Riftbreaker & Knight Sparring"),
                    new UnlockMilestone(20, "Ancient Greatblade & Survival Waves"),
                    new UnlockMilestone(25, "Melee Mastery Trial Eligibility"),
                    new UnlockMilestone(30, "Astral Executioner & Dragon Melee Seal")
            );
            case MAGE -> List.of(
                    new UnlockMilestone(1, "Apprentice Staff & Resonance Targets"),
                    new UnlockMilestone(5, "Crystal Staff & Mana Flow Trials"),
                    new UnlockMilestone(10, "Voidcaller & Barrier Obstacles"),
                    new UnlockMilestone(15, "Rift Conduit & Elemental Trials"),
                    new UnlockMilestone(20, "Ancient Arcane Staff & Spell Combos"),
                    new UnlockMilestone(25, "Mage Mastery Trial Eligibility"),
                    new UnlockMilestone(30, "Astral Archmage Staff & Dragon Arcane Seal")
            );
        };
    }

    @NotNull
    public static List<String> unlockedFeaturesAt(@NotNull CombatDiscipline discipline, int level) {
        List<String> unlocked = new ArrayList<>();
        for (UnlockMilestone m : milestonesFor(discipline)) {
            if (level >= m.level()) {
                unlocked.add("Lv " + m.level() + ": " + m.label());
            }
        }
        return unlocked;
    }
}

