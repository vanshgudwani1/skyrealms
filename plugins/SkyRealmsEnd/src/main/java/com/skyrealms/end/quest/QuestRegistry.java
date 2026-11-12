package com.skyrealms.end.quest;

import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.quest.QuestDefinition.CycleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Authoritative registry containing the Main End storyline, all three complete Guild progression
 * paths culminating in Archery/Melee/Mage Mastery Trials, Daily Contracts, Weekly Challenges,
 * Collection quests, and Exploration quests.
 */
public final class QuestRegistry {

    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();

    public QuestRegistry() {
        // ── 1. MAIN END STORYLINE (Quest Archivist — main_story) ─────────────────────────────
        add(new QuestDefinition(
                "story_1_old_pact",
                "Echoes of the Old Pact",
                "Explore 3 districts across the End Village to attune with its wards.",
                QuestCategory.MAIN_STORY, "main_story", null,
                null, 1, QuestObjectiveType.DISCOVER_DISTRICT, null, 3, CycleType.PERMANENT,
                150L, CombatDiscipline.MELEE, 250L, 25, "end_apprentice_sword", 1, false
        ));
        add(new QuestDefinition(
                "story_2_three_guilds",
                "Trial of the Courtyards",
                "Complete 2 outdoor courtyard training drills in any discipline.",
                QuestCategory.MAIN_STORY, "main_story", "story_1_old_pact",
                null, 1, QuestObjectiveType.TRAINING_COMPLETE, null, 2, CycleType.PERMANENT,
                220L, CombatDiscipline.ARCHERY, 400L, 40, "end_apprentice_bow", 1, false
        ));
        add(new QuestDefinition(
                "story_3_beyond_walls",
                "Beyond the Safe Walls",
                "Step beyond the village and defeat 12 wild End creatures.",
                QuestCategory.MAIN_STORY, "main_story", "story_2_three_guilds",
                null, 1, QuestObjectiveType.MOB_KILL, null, 12, CycleType.PERMANENT,
                380L, CombatDiscipline.MAGE, 750L, 60, "end_apprentice_staff", 1, false
        ));
        add(new QuestDefinition(
                "story_4_shards_of_wilds",
                "Shattered Reclamation",
                "Deliver 6 Shattered End Shards to fortify the Citadel smithies.",
                QuestCategory.MAIN_STORY, "main_story", "story_3_beyond_walls",
                null, 5, QuestObjectiveType.COLLECTION_DELIVER, EndGearCatalog.MAT_SHATTERED_SHARD, 6, CycleType.PERMANENT,
                600L, CombatDiscipline.MELEE, 1200L, 80, EndGearCatalog.MAT_CRYSTAL_ESSENCE, 3, false
        ));
        add(new QuestDefinition(
                "story_5_sanctuary_call",
                "Call of the Dragon Sanctuary",
                "Attain Level 15 in at least one combat discipline to earn Dragon Sanctuary audience.",
                QuestCategory.MAIN_STORY, "main_story", "story_4_shards_of_wilds",
                null, 1, QuestObjectiveType.DISCIPLINE_LEVEL, null, 15, CycleType.PERMANENT,
                1200L, CombatDiscipline.MAGE, 2500L, 120, EndGearCatalog.MAT_FORGOTTEN_CORE, 2, false
        ));

        // ── 2. ARCHERY GUILD PATH (Master Kaelen — guild_archery) ────────────────────────────
        add(new QuestDefinition(
                "archery_1_steady_draw",
                "Path of the Gale I: Steady Draw",
                "Complete 2 Archery Courtyard drills to prove bow control.",
                QuestCategory.GUILD_ARCHERY, "guild_archery", null,
                CombatDiscipline.ARCHERY, 1, QuestObjectiveType.TRAINING_COMPLETE, "ARCHERY", 2, CycleType.PERMANENT,
                180L, CombatDiscipline.ARCHERY, 300L, 30, "end_apprentice_bow", 1, false
        ));
        add(new QuestDefinition(
                "archery_2_eyes_on_wind",
                "Path of the Gale II: Eyes on the Wind",
                "Slay 15 wild End monsters using bows or crossbows.",
                QuestCategory.GUILD_ARCHERY, "guild_archery", "archery_1_steady_draw",
                CombatDiscipline.ARCHERY, 5, QuestObjectiveType.MOB_KILL, "ARCHERY", 15, CycleType.PERMANENT,
                550L, CombatDiscipline.ARCHERY, 900L, 60, "end_hunter_bow", 1, false
        ));
        add(new QuestDefinition(
                "archery_3_void_hunter",
                "Path of the Gale III: Voidpiercer Mark",
                "Slay 30 wild End monsters with Archery and train to Lv.15+.",
                QuestCategory.GUILD_ARCHERY, "guild_archery", "archery_2_eyes_on_wind",
                CombatDiscipline.ARCHERY, 12, QuestObjectiveType.MOB_KILL, "ARCHERY", 30, CycleType.PERMANENT,
                1600L, CombatDiscipline.ARCHERY, 2200L, 100, "end_voidpiercer", 1, false
        ));
        add(new QuestDefinition(
                "archery_4_mastery_trial",
                "Archery Mastery Trial",
                "Prove supreme precision at Archery Lv.25+: complete 3 Archery drills & 25 Archery kills.",
                QuestCategory.MASTERY_TRIAL, "guild_archery", "archery_3_void_hunter",
                CombatDiscipline.ARCHERY, 25, QuestObjectiveType.MASTERY_TRIAL_CLEAR, "ARCHERY", 5, CycleType.PERMANENT,
                3500L, CombatDiscipline.ARCHERY, 5000L, 250, "end_astral_longbow", 1, true
        ));

        // ── 3. MELEE GUILD PATH (Grandmaster Rovan — guild_melee) ────────────────────────────
        add(new QuestDefinition(
                "melee_1_bastion_blade",
                "Path of Iron I: First Blade",
                "Complete 2 Warrior Courtyard melee drills.",
                QuestCategory.GUILD_MELEE, "guild_melee", null,
                CombatDiscipline.MELEE, 1, QuestObjectiveType.TRAINING_COMPLETE, "MELEE", 2, CycleType.PERMANENT,
                180L, CombatDiscipline.MELEE, 300L, 30, "end_apprentice_sword", 1, false
        ));
        add(new QuestDefinition(
                "melee_2_vanguard_steel",
                "Path of Iron II: Vanguard Steel",
                "Slay 15 wild End creatures in close combat.",
                QuestCategory.GUILD_MELEE, "guild_melee", "melee_1_bastion_blade",
                CombatDiscipline.MELEE, 5, QuestObjectiveType.MOB_KILL, "MELEE", 15, CycleType.PERMANENT,
                550L, CombatDiscipline.MELEE, 900L, 60, "end_endsteel_blade", 1, false
        ));
        add(new QuestDefinition(
                "melee_3_rift_breaker",
                "Path of Iron III: Break the Line",
                "Slay 30 wild End monsters with Melee at Lv.12+.",
                QuestCategory.GUILD_MELEE, "guild_melee", "melee_2_vanguard_steel",
                CombatDiscipline.MELEE, 12, QuestObjectiveType.MOB_KILL, "MELEE", 30, CycleType.PERMANENT,
                1600L, CombatDiscipline.MELEE, 2200L, 100, "end_riftbreaker", 1, false
        ));
        add(new QuestDefinition(
                "melee_4_mastery_trial",
                "Melee Mastery Trial",
                "Pass Grandmaster Rovan's trial at Melee Lv.25+: clear 5 combat trials & slay wild threats.",
                QuestCategory.MASTERY_TRIAL, "guild_melee", "melee_3_rift_breaker",
                CombatDiscipline.MELEE, 25, QuestObjectiveType.MASTERY_TRIAL_CLEAR, "MELEE", 5, CycleType.PERMANENT,
                3500L, CombatDiscipline.MELEE, 5000L, 250, "end_astral_executioner", 1, true
        ));

        // ── 4. MAGE GUILD PATH (Archmage Eryndor — guild_mage) ───────────────────────────────
        add(new QuestDefinition(
                "mage_1_observatory_sparks",
                "Path of Stars I: Sparks Above",
                "Complete 2 Arcane Courtyard spell drills.",
                QuestCategory.GUILD_MAGE, "guild_mage", null,
                CombatDiscipline.MAGE, 1, QuestObjectiveType.TRAINING_COMPLETE, "MAGE", 2, CycleType.PERMANENT,
                180L, CombatDiscipline.MAGE, 300L, 30, "end_apprentice_staff", 1, false
        ));
        add(new QuestDefinition(
                "mage_2_chorus_currents",
                "Path of Stars II: Currents of Chorus",
                "Slay 15 wild End monsters with Mage catalysts.",
                QuestCategory.GUILD_MAGE, "guild_mage", "mage_1_observatory_sparks",
                CombatDiscipline.MAGE, 5, QuestObjectiveType.MOB_KILL, "MAGE", 15, CycleType.PERMANENT,
                550L, CombatDiscipline.MAGE, 900L, 60, "end_crystal_staff", 1, false
        ));
        add(new QuestDefinition(
                "mage_3_rift_scholar",
                "Path of Stars III: Conduit Attunement",
                "Slay 30 wild End threats using arcane bolts at Mage Lv.12+.",
                QuestCategory.GUILD_MAGE, "guild_mage", "mage_2_chorus_currents",
                CombatDiscipline.MAGE, 12, QuestObjectiveType.MOB_KILL, "MAGE", 30, CycleType.PERMANENT,
                1600L, CombatDiscipline.MAGE, 2200L, 100, "end_rift_conduit", 1, false
        ));
        add(new QuestDefinition(
                "mage_4_mastery_trial",
                "Mage Mastery Trial",
                "Complete Archmage Eryndor's trial at Mage Lv.25+ to unlock the Astral Archmage Staff.",
                QuestCategory.MASTERY_TRIAL, "guild_mage", "mage_3_rift_scholar",
                CombatDiscipline.MAGE, 25, QuestObjectiveType.MASTERY_TRIAL_CLEAR, "MAGE", 5, CycleType.PERMANENT,
                3500L, CombatDiscipline.MAGE, 5000L, 250, "end_astral_archmage_staff", 1, true
        ));

        // ── 5. DAILY CONTRACTS (Guild Registrar — daily_contracts) ───────────────────────────
        add(new QuestDefinition(
                "daily_wilderness_patrol",
                "Daily Contract: Rift Wardens",
                "Defeat 8 End creatures beyond the village walls today.",
                QuestCategory.DAILY_CONTRACT, "daily_contracts", null,
                null, 1, QuestObjectiveType.MOB_KILL, null, 8, CycleType.DAILY,
                220L, CombatDiscipline.MELEE, 450L, 20, EndGearCatalog.MAT_SHATTERED_SHARD, 2, false
        ));
        add(new QuestDefinition(
                "daily_courtyard_focus",
                "Daily Contract: Courtyard Focus",
                "Complete 2 outdoor training challenges across any discipline today.",
                QuestCategory.DAILY_CONTRACT, "daily_contracts", null,
                null, 1, QuestObjectiveType.TRAINING_COMPLETE, null, 2, CycleType.DAILY,
                200L, CombatDiscipline.ARCHERY, 400L, 20, EndGearCatalog.MAT_CRYSTAL_ESSENCE, 1, false
        ));
        add(new QuestDefinition(
                "daily_forge_requisition",
                "Daily Contract: Forge Supply",
                "Deliver 4 Shattered End Shards to the Guild Registrar.",
                QuestCategory.DAILY_CONTRACT, "daily_contracts", null,
                null, 1, QuestObjectiveType.COLLECTION_DELIVER, EndGearCatalog.MAT_SHATTERED_SHARD, 4, CycleType.DAILY,
                190L, CombatDiscipline.MAGE, 500L, 25, null, 0, false
        ));

        // ── 6. WEEKLY CHALLENGES (Guild Registrar — daily_contracts / weekly) ────────────────
        add(new QuestDefinition(
                "weekly_tri_discipline",
                "Weekly Challenge: Wilderness Purge",
                "Slay 40 wild End creatures this week across any combination of disciplines.",
                QuestCategory.WEEKLY_CHALLENGE, "daily_contracts", null,
                null, 1, QuestObjectiveType.MOB_KILL, null, 40, CycleType.WEEKLY,
                1400L, CombatDiscipline.MELEE, 3500L, 120, EndGearCatalog.MAT_FORGOTTEN_CORE, 2, false
        ));
        add(new QuestDefinition(
                "weekly_training_gauntlet",
                "Weekly Challenge: Courtyard Gauntlet",
                "Complete 8 outdoor training drills this week.",
                QuestCategory.WEEKLY_CHALLENGE, "daily_contracts", null,
                null, 1, QuestObjectiveType.TRAINING_COMPLETE, null, 8, CycleType.WEEKLY,
                1200L, CombatDiscipline.ARCHERY, 3000L, 100, EndGearCatalog.MAT_ABYSSAL_FRAGMENT, 1, false
        ));

        // ── 7. COLLECTION & EXPLORATION QUESTS ───────────────────────────────────────────────
        add(new QuestDefinition(
                "collect_relics_shattered",
                "Trophies of the Shattered Fields",
                "Deliver 10 Shattered End Shards to Relic Collector.",
                QuestCategory.COLLECTION, "archery_collections", null,
                null, 1, QuestObjectiveType.COLLECTION_DELIVER, EndGearCatalog.MAT_SHATTERED_SHARD, 10, CycleType.PERMANENT,
                350L, CombatDiscipline.ARCHERY, 850L, 50, EndGearCatalog.MAT_CRYSTAL_ESSENCE, 3, false
        ));
        add(new QuestDefinition(
                "explore_all_districts",
                "Surveyor of the Citadel",
                "Discover all 7 districts inside the End Village.",
                QuestCategory.EXPLORATION, "exploration_quests", null,
                null, 1, QuestObjectiveType.DISCOVER_DISTRICT, null, 7, CycleType.PERMANENT,
                300L, CombatDiscipline.MAGE, 900L, 75, EndGearCatalog.MAT_SHATTERED_SHARD, 4, false
        ));
        add(new QuestDefinition(
                "explore_wilds_frontiers",
                "Cartographer of the Outer End",
                "Explore all 4 End Wilderness hunting regions.",
                QuestCategory.EXPLORATION, "exploration_quests", "explore_all_districts",
                null, 5, QuestObjectiveType.WILD_EXPLORE, null, 4, CycleType.PERMANENT,
                950L, CombatDiscipline.ARCHERY, 2400L, 150, EndGearCatalog.MAT_FORGOTTEN_CORE, 2, false
        ));
    }

    private void add(QuestDefinition def) {
        quests.put(def.id().toLowerCase(Locale.ROOT), def);
    }

    @Nullable
    public QuestDefinition get(@NotNull String questId) {
        return quests.get(questId.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Collection<QuestDefinition> all() {
        return Collections.unmodifiableCollection(quests.values());
    }

    @NotNull
    public List<QuestDefinition> byBoardTag(@NotNull String boardTag) {
        String norm = boardTag.toLowerCase(Locale.ROOT);
        List<QuestDefinition> out = new ArrayList<>();
        for (QuestDefinition q : quests.values()) {
            if (q.npcBoardTag().equals(norm)) {
                out.add(q);
            }
        }
        return out;
    }

    @NotNull
    public static String cycleKeyFor(@NotNull QuestDefinition def, @NotNull LocalDate date) {
        return switch (def.cycleType()) {
            case PERMANENT -> "PERMANENT";
            case DAILY -> "DAILY_" + date;
            case WEEKLY -> {
                int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                int year = date.get(IsoFields.WEEK_BASED_YEAR);
                yield String.format(Locale.US, "WEEKLY_%d-W%02d", year, week);
            }
        };
    }
}

