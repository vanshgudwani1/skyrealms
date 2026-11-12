package com.skyrealms.end.training;

import com.skyrealms.end.progression.CombatDiscipline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Outdoor training challenges hosted across the three End Village courtyards
 * (Archery Range, Warrior Courtyard, and Arcane Courtyard).
 */
public enum TrainingChallengeType {
    // Archer Quarter Courtyard
    ARCHERY_STATIONARY(
            "archery_stationary",
            "Stationary Targets",
            CombatDiscipline.ARCHERY,
            "archery",
            1,
            30,
            45L,
            "Hit fixed range posts across the Archery Courtyard."
    ),
    ARCHERY_MOVING(
            "archery_moving",
            "Moving Targets",
            CombatDiscipline.ARCHERY,
            "archery",
            3,
            35,
            65L,
            "Track and strike laterally gliding bullseye targets."
    ),
    ARCHERY_FLYING(
            "archery_flying",
            "Flying Targets",
            CombatDiscipline.ARCHERY,
            "archery",
            5,
            35,
            85L,
            "Shoot hovering wing-lantern drones above the range."
    ),
    ARCHERY_DISTANCE(
            "archery_distance",
            "Distance Challenge",
            CombatDiscipline.ARCHERY,
            "archery",
            8,
            40,
            100L,
            "Land long-range shots (14m+) for escalating distance multipliers."
    ),
    ARCHERY_ACCURACY(
            "archery_accuracy",
            "Accuracy Scoring",
            CombatDiscipline.ARCHERY,
            "archery",
            1,
            35,
            75L,
            "Maintain an unbroken hit streak; missed arrows reduce accuracy multiplier."
    ),

    // Warrior Quarter Courtyard
    MELEE_DUMMIES(
            "melee_dummies",
            "Combat Dummies",
            CombatDiscipline.MELEE,
            "melee",
            1,
            25,
            45L,
            "Test weapon combos and raw DPS against reinforced dummies."
    ),
    MELEE_COMBO(
            "melee_combo",
            "Combo Challenge",
            CombatDiscipline.MELEE,
            "melee",
            3,
            35,
            70L,
            "Strike highlighted dummies in rapid sequence for chain multipliers."
    ),
    MELEE_PARRY(
            "melee_parry",
            "Parry Trials",
            CombatDiscipline.MELEE,
            "melee",
            5,
            35,
            85L,
            "Right-click block or counter-strike during the red telegraph window."
    ),
    MELEE_SPARRING(
            "melee_sparring",
            "Sparring Match",
            CombatDiscipline.MELEE,
            "melee",
            8,
            45,
            100L,
            "Controlled duel against an Endblade Construct (non-lethal)."
    ),
    MELEE_SURVIVAL(
            "melee_survival",
            "Survival Waves",
            CombatDiscipline.MELEE,
            "melee",
            10,
            50,
            125L,
            "Clear escalating waves of sparring constructs in the Warrior Courtyard."
    ),

    // Arcane Quarter Courtyard
    MAGE_SPELL_TARGETS(
            "mage_targets",
            "Spell Targets",
            CombatDiscipline.MAGE,
            "arcane",
            1,
            30,
            45L,
            "Channel Arcane Bolts with a Mage catalyst at hovering crystals."
    ),
    MAGE_MANA_CHALLENGE(
            "mage_mana",
            "Mana Challenge",
            CombatDiscipline.MAGE,
            "arcane",
            3,
            35,
            70L,
            "Shatter high-density orbs while pacing your mana regeneration."
    ),
    MAGE_OBSTACLES(
            "mage_obstacles",
            "Magical Obstacles",
            CombatDiscipline.MAGE,
            "arcane",
            5,
            35,
            85L,
            "Thread Arcane Bolts past orbiting Void Barrier shields."
    ),
    MAGE_ELEMENTAL(
            "mage_elemental",
            "Elemental Challenge",
            CombatDiscipline.MAGE,
            "arcane",
            8,
            40,
            105L,
            "Strike the resonant Flame, Frost, or Void crystal matching the active sigil."
    ),
    MAGE_COMBO(
            "mage_combo",
            "Spell Combination Trial",
            CombatDiscipline.MAGE,
            "arcane",
            10,
            45,
            125L,
            "Chain Ignis -> Frost -> Void casts in sequence across resonant foci."
    );

    private final String key;
    private final String displayName;
    private final CombatDiscipline discipline;
    private final String arenaId;
    private final int minLevel;
    private final int durationSeconds;
    private final long baseXpReward;
    private final String description;

    TrainingChallengeType(
            @NotNull String key,
            @NotNull String displayName,
            @NotNull CombatDiscipline discipline,
            @NotNull String arenaId,
            int minLevel,
            int durationSeconds,
            long baseXpReward,
            @NotNull String description
    ) {
        this.key = key;
        this.displayName = displayName;
        this.discipline = discipline;
        this.arenaId = arenaId;
        this.minLevel = minLevel;
        this.durationSeconds = durationSeconds;
        this.baseXpReward = baseXpReward;
        this.description = description;
    }

    @NotNull
    public String key() {
        return key;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }

    @NotNull
    public CombatDiscipline discipline() {
        return discipline;
    }

    @NotNull
    public String arenaId() {
        return arenaId;
    }

    public int minLevel() {
        return minLevel;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public long baseXpReward() {
        return baseXpReward;
    }

    @NotNull
    public String description() {
        return description;
    }

    @Nullable
    public static TrainingChallengeType byKey(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        for (TrainingChallengeType type : values()) {
            if (type.key.equals(norm) || type.name().equalsIgnoreCase(norm)) {
                return type;
            }
        }
        return null;
    }

    @NotNull
    public static List<TrainingChallengeType> byDiscipline(@NotNull CombatDiscipline discipline) {
        List<TrainingChallengeType> out = new ArrayList<>();
        for (TrainingChallengeType type : values()) {
            if (type.discipline == discipline) {
                out.add(type);
            }
        }
        return out;
    }
}

