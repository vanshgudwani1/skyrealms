package com.skyrealms.end.wilderness;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom End Wilderness monsters and three non-dragon Field Bosses across all four hunting regions.
 */
public enum EndMobArchetype {
    // Region 1: Shattered Fields (1-8)
    VOID_MITE_SWARM(
            "void_mite_swarm",
            "Void Mite Swarm",
            WildernessRegion.SHATTERED_FIELDS,
            EntityType.ENDERMITE,
            24.0, 4.0, 0.34,
            20L, false,
            AiAbility.PACK_DASH
    ),
    SHATTERED_DRIFTER(
            "shattered_drifter",
            "Shattered Drifter",
            WildernessRegion.SHATTERED_FIELDS,
            EntityType.STRAY,
            38.0, 5.5, 0.27,
            34L, false,
            AiAbility.SHADOW_BLINK
    ),
    BOSS_RIFTSTALKER_ALPHA(
            "boss_riftstalker_alpha",
            "Riftstalker Alpha",
            WildernessRegion.SHATTERED_FIELDS,
            EntityType.ENDERMAN,
            280.0, 11.0, 0.33,
            340L, true,
            AiAbility.VOID_NOVA
    ),

    // Region 2: Crystal Wilds (9-15)
    CRYSTAL_SENTINEL(
            "crystal_sentinel",
            "Crystal Sentinel",
            WildernessRegion.CRYSTAL_WILDS,
            EntityType.WITHER_SKELETON,
            85.0, 8.5, 0.28,
            65L, false,
            AiAbility.CRYSTAL_PULSE
    ),
    PRISMATIC_LURKER(
            "prismatic_lurker",
            "Prismatic Lurker",
            WildernessRegion.CRYSTAL_WILDS,
            EntityType.ENDERMAN,
            95.0, 9.0, 0.32,
            78L, false,
            AiAbility.SHADOW_BLINK
    ),
    BOSS_CRYSTALLINE_WARD(
            "boss_crystalline_ward",
            "Crystalline Ward",
            WildernessRegion.CRYSTAL_WILDS,
            EntityType.IRON_GOLEM,
            520.0, 15.0, 0.25,
            620L, true,
            AiAbility.CRYSTAL_PULSE
    ),

    // Region 3: Forgotten Ruins (16-23)
    RUIN_WRAITH(
            "ruin_wraith",
            "Ruin Wraith",
            WildernessRegion.FORGOTTEN_RUINS,
            EntityType.SKELETON,
            150.0, 12.0, 0.30,
            115L, false,
            AiAbility.WITHER_VOLLEY
    ),
    OBSIDIAN_REVENANT(
            "obsidian_revenant",
            "Obsidian Revenant",
            WildernessRegion.FORGOTTEN_RUINS,
            EntityType.WITHER_SKELETON,
            185.0, 13.5, 0.29,
            135L, false,
            AiAbility.EARTHSHAKER
    ),
    BOSS_FORGOTTEN_HARVESTER(
            "boss_forgotten_harvester",
            "Forgotten Harvester",
            WildernessRegion.FORGOTTEN_RUINS,
            EntityType.WITHER_SKELETON,
            850.0, 20.0, 0.32,
            1050L, true,
            AiAbility.EARTHSHAKER
    ),

    // Region 4: Abyssal Grounds (24-30)
    ABYSSAL_RAVAGER(
            "abyssal_ravager",
            "Abyssal Ravager",
            WildernessRegion.ABYSSAL_GROUNDS,
            EntityType.RAVAGER,
            320.0, 18.0, 0.31,
            210L, false,
            AiAbility.PACK_DASH
    ),
    NULL_CONJURER(
            "null_conjurer",
            "Null Conjurer",
            WildernessRegion.ABYSSAL_GROUNDS,
            EntityType.EVOKER,
            260.0, 16.5, 0.29,
            230L, false,
            AiAbility.VOID_NOVA
    );

    public enum AiAbility {
        PACK_DASH,
        SHADOW_BLINK,
        CRYSTAL_PULSE,
        WITHER_VOLLEY,
        EARTHSHAKER,
        VOID_NOVA
    }

    private final String id;
    private final String displayName;
    private final WildernessRegion region;
    private final EntityType entityType;
    private final double maxHealth;
    private final double attackDamage;
    private final double moveSpeed;
    private final long baseXp;
    private final boolean fieldBoss;
    private final AiAbility aiAbility;

    EndMobArchetype(
            @NotNull String id,
            @NotNull String displayName,
            @NotNull WildernessRegion region,
            @NotNull EntityType entityType,
            double maxHealth,
            double attackDamage,
            double moveSpeed,
            long baseXp,
            boolean fieldBoss,
            @NotNull AiAbility aiAbility
    ) {
        this.id = id;
        this.displayName = displayName;
        this.region = region;
        this.entityType = entityType;
        this.maxHealth = maxHealth;
        this.attackDamage = attackDamage;
        this.moveSpeed = moveSpeed;
        this.baseXp = baseXp;
        this.fieldBoss = fieldBoss;
        this.aiAbility = aiAbility;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public WildernessRegion region() { return region; }
    public EntityType entityType() { return entityType; }
    public double maxHealth() { return maxHealth; }
    public double attackDamage() { return attackDamage; }
    public double moveSpeed() { return moveSpeed; }
    public long baseXp() { return baseXp; }
    public boolean isFieldBoss() { return fieldBoss; }
    public AiAbility aiAbility() { return aiAbility; }

    @Nullable
    public static EndMobArchetype byId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        for (EndMobArchetype mob : values()) {
            if (mob.id.equals(norm) || mob.name().equalsIgnoreCase(norm)) {
                return mob;
            }
        }
        return null;
    }

    @NotNull
    public static List<EndMobArchetype> normalMobsFor(@NotNull WildernessRegion region) {
        List<EndMobArchetype> out = new ArrayList<>();
        for (EndMobArchetype mob : values()) {
            if (mob.region == region && !mob.fieldBoss) {
                out.add(mob);
            }
        }
        return out;
    }
}

