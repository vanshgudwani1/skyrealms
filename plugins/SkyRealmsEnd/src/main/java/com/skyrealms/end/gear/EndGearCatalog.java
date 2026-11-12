package com.skyrealms.end.gear;

import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.items.model.ArmorSetBonus;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.model.GearSlot;
import com.skyrealms.items.model.ItemDomain;
import com.skyrealms.items.model.ItemRarity;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.SkyItemStats;
import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Defines all End Civilization weapons, armor sets, and crafting reagents,
 * registering them directly into the universal {@link ItemRegistryService} catalog.
 */
public final class EndGearCatalog {

    // Crafting reagents
    public static final String MAT_SHATTERED_SHARD = "end_shattered_shard";
    public static final String MAT_CRYSTAL_ESSENCE = "end_crystal_essence";
    public static final String MAT_FORGOTTEN_CORE = "end_forgotten_core";
    public static final String MAT_ABYSSAL_FRAGMENT = "end_abyssal_fragment";

    // Stormrake Wyvern & Enderloper boss reagents
    public static final String MAT_PHASE_SINEW = "end_phase_sinew";
    public static final String MAT_STORMRAKE_SCALE = "end_stormrake_scale";
    public static final String MAT_TEMPEST_FANG = "end_tempest_fang";
    public static final String MAT_WYVERN_HEART = "end_wyvern_heart";
    public static final String RELIC_TEMPEST_WARD = "end_tempest_ward";

    public record EndGearSpec(
            @NotNull String itemId,
            @NotNull CombatDiscipline requiredDiscipline,
            int requiredLevel,
            @NotNull String forgeCategory,
            @NotNull Map<String, Integer> materialCosts
    ) {}

    private final Map<String, EndGearSpec> specs = new LinkedHashMap<>();
    private final List<CustomItemDefinition> definitions = new ArrayList<>();
    private final List<ArmorSetBonus> armorSets = new ArrayList<>();

    public EndGearCatalog() {
        int cmd = 2001;
        // 1. Crafting Reagents (Materials)
        addDef(CustomItemDefinition.builder(MAT_SHATTERED_SHARD, "Shattered End Shard", Material.AMETHYST_SHARD)
                .rarity(ItemRarity.COMMON).domain(ItemDomain.UTILITY).slot(GearSlot.MATERIAL)
                .lore(List.of("Recovered from Shattered Fields fissures."))
                .customModelData(cmd++).build());
        addDef(CustomItemDefinition.builder(MAT_CRYSTAL_ESSENCE, "Crystal Wilds Essence", Material.PRISMARINE_CRYSTALS)
                .rarity(ItemRarity.UNCOMMON).domain(ItemDomain.UTILITY).slot(GearSlot.MATERIAL)
                .lore(List.of("Stabilized resonance condensed in the Crystal Wilds."))
                .customModelData(cmd++).build());
        addDef(CustomItemDefinition.builder(MAT_FORGOTTEN_CORE, "Forgotten Void Core", Material.ENDER_EYE)
                .rarity(ItemRarity.EPIC).domain(ItemDomain.UTILITY).slot(GearSlot.MATERIAL)
                .lore(List.of("Ancient relic guarded in the Forgotten Ruins."))
                .customModelData(cmd++).build());
        addDef(CustomItemDefinition.builder(MAT_ABYSSAL_FRAGMENT, "Abyssal Rift Fragment", Material.NETHER_STAR)
                .rarity(ItemRarity.LEGENDARY).domain(ItemDomain.UTILITY).slot(GearSlot.MATERIAL)
                .lore(List.of("High-density rift shard forged near the Abyssal Grounds."))
                .customModelData(cmd++).build());

        // 2. ARCHERY WEAPONS (6 Tiers)
        cmd = registerWeapon(cmd, "end_apprentice_bow", "Apprentice Bow", Material.BOW,
                ItemRarity.COMMON, CombatDiscipline.ARCHERY, 1, "archery_bows",
                SkyItemStats.builder().combatDamage(3.0).speedBonus(0.02).build(),
                Map.of(MAT_SHATTERED_SHARD, 3),
                "Simple recurve fitted at the Archer Quarter range.");
        cmd = registerWeapon(cmd, "end_hunter_bow", "Hunter Bow", Material.BOW,
                ItemRarity.UNCOMMON, CombatDiscipline.ARCHERY, 5, "archery_bows",
                SkyItemStats.builder().combatDamage(6.0).slayerDamageBonus(0.08).speedBonus(0.03).build(),
                Map.of(MAT_SHATTERED_SHARD, 6, MAT_CRYSTAL_ESSENCE, 2),
                "Strung with sinew for swift wilderness tracking.");
        cmd = registerWeapon(cmd, "end_endwood_longbow", "Endwood Longbow", Material.BOW,
                ItemRarity.RARE, CombatDiscipline.ARCHERY, 10, "archery_bows",
                SkyItemStats.builder().combatDamage(9.5).slayerDamageBonus(0.14).speedBonus(0.05).build(),
                Map.of(MAT_CRYSTAL_ESSENCE, 6),
                "Carved from petrified chorus trunk and oiled in resin.");
        cmd = registerWeapon(cmd, "end_voidpiercer", "Voidpiercer", Material.BOW,
                ItemRarity.EPIC, CombatDiscipline.ARCHERY, 15, "archery_bows",
                SkyItemStats.builder().combatDamage(13.5).slayerDamageBonus(0.22).speedBonus(0.07).build(),
                Map.of(MAT_CRYSTAL_ESSENCE, 8, MAT_FORGOTTEN_CORE, 2),
                "Arrows punch cleanly through obsidian hide.");
        cmd = registerWeapon(cmd, "end_rift_hunter_bow", "Rift Hunter Bow", Material.BOW,
                ItemRarity.LEGENDARY, CombatDiscipline.ARCHERY, 20, "archery_bows",
                SkyItemStats.builder().combatDamage(18.0).slayerDamageBonus(0.30).speedBonus(0.10).build(),
                Map.of(MAT_FORGOTTEN_CORE, 5, MAT_ABYSSAL_FRAGMENT, 2),
                "Twinned rift limbs hum before every release.");
        cmd = registerWeapon(cmd, "end_astral_longbow", "Astral Longbow", Material.BOW,
                ItemRarity.MYTHIC, CombatDiscipline.ARCHERY, 30, "archery_bows",
                SkyItemStats.builder().combatDamage(24.0).slayerDamageBonus(0.45).speedBonus(0.14).build(),
                Map.of(MAT_FORGOTTEN_CORE, 8, MAT_ABYSSAL_FRAGMENT, 5),
                "Masterwork of Master Kaelen. Qualified for Sanctuary trials.");

        // 3. MELEE WEAPONS (6 Tiers)
        cmd = registerWeapon(cmd, "end_apprentice_sword", "Apprentice Sword", Material.IRON_SWORD,
                ItemRarity.COMMON, CombatDiscipline.MELEE, 1, "melee_weapons",
                SkyItemStats.builder().combatDamage(3.5).defense(1.0).build(),
                Map.of(MAT_SHATTERED_SHARD, 3),
                "Balanced iron blade issued by Grandmaster Rovan.");
        cmd = registerWeapon(cmd, "end_endsteel_blade", "Endsteel Blade", Material.IRON_SWORD,
                ItemRarity.UNCOMMON, CombatDiscipline.MELEE, 5, "melee_weapons",
                SkyItemStats.builder().combatDamage(6.5).defense(2.0).maxHealthBonus(4.0).build(),
                Map.of(MAT_SHATTERED_SHARD, 6, MAT_CRYSTAL_ESSENCE, 2),
                "Forged with End Stone flux for crisp parrying.");
        cmd = registerWeapon(cmd, "end_void_cleaver", "Void Cleaver", Material.DIAMOND_AXE,
                ItemRarity.RARE, CombatDiscipline.MELEE, 10, "melee_weapons",
                SkyItemStats.builder().combatDamage(10.0).slayerDamageBonus(0.12).maxHealthBonus(6.0).build(),
                Map.of(MAT_CRYSTAL_ESSENCE, 6),
                "Heavy crescent edge engineered for armored End beasts.");
        cmd = registerWeapon(cmd, "end_riftbreaker", "Riftbreaker", Material.DIAMOND_SWORD,
                ItemRarity.EPIC, CombatDiscipline.MELEE, 15, "melee_weapons",
                SkyItemStats.builder().combatDamage(14.5).slayerDamageBonus(0.20).defense(3.0).build(),
                Map.of(MAT_CRYSTAL_ESSENCE, 8, MAT_FORGOTTEN_CORE, 2),
                "Tempered in Weaponsmith Doran's white-hot forge.");
        cmd = registerWeapon(cmd, "end_ancient_greatblade", "Ancient Greatblade", Material.NETHERITE_SWORD,
                ItemRarity.LEGENDARY, CombatDiscipline.MELEE, 20, "melee_weapons",
                SkyItemStats.builder().combatDamage(19.0).slayerDamageBonus(0.28).maxHealthBonus(10.0).defense(4.0).build(),
                Map.of(MAT_FORGOTTEN_CORE, 5, MAT_ABYSSAL_FRAGMENT, 2),
                "An heirloom greatsword unearthed from pre-fall vaults.");
        cmd = registerWeapon(cmd, "end_astral_executioner", "Astral Executioner", Material.NETHERITE_AXE,
                ItemRarity.MYTHIC, CombatDiscipline.MELEE, 30, "melee_weapons",
                SkyItemStats.builder().combatDamage(25.0).slayerDamageBonus(0.45).maxHealthBonus(14.0).defense(5.0).build(),
                Map.of(MAT_FORGOTTEN_CORE, 8, MAT_ABYSSAL_FRAGMENT, 5),
                "The apex Warrior blade. Only Lv.30 masters may wield it.");

        // 4. MAGE WEAPONS (6 Tiers)
        cmd = registerWeapon(cmd, "end_apprentice_staff", "Apprentice Staff", Material.STICK,
                ItemRarity.COMMON, CombatDiscipline.MAGE, 1, "mage_staffs",
                SkyItemStats.builder().combatDamage(2.5).speedBonus(0.02).build(),
                Map.of(MAT_SHATTERED_SHARD, 3),
                "Channels concentrated Arcane Bolts outside safe-zones.");
        cmd = registerWeapon(cmd, "end_crystal_staff", "Crystal Staff", Material.AMETHYST_SHARD,
                ItemRarity.UNCOMMON, CombatDiscipline.MAGE, 5, "mage_staffs",
                SkyItemStats.builder().combatDamage(5.5).slayerDamageBonus(0.08).speedBonus(0.03).build(),
                Map.of(MAT_SHATTERED_SHARD, 6, MAT_CRYSTAL_ESSENCE, 2),
                "Resonates cleanly with Crystal Wilds ley lines.");
        cmd = registerWeapon(cmd, "end_voidcaller", "Voidcaller", Material.BREEZE_ROD,
                ItemRarity.RARE, CombatDiscipline.MAGE, 10, "mage_staffs",
                SkyItemStats.builder().combatDamage(9.0).slayerDamageBonus(0.15).speedBonus(0.05).build(),
                Map.of(MAT_CRYSTAL_ESSENCE, 6),
                "Bends void currents into piercing bolts.");
        cmd = registerWeapon(cmd, "end_rift_conduit", "Rift Conduit", Material.BLAZE_ROD,
                ItemRarity.EPIC, CombatDiscipline.MAGE, 15, "mage_staffs",
                SkyItemStats.builder().combatDamage(13.0).slayerDamageBonus(0.24).maxHealthBonus(4.0).build(),
                Map.of(MAT_CRYSTAL_ESSENCE, 8, MAT_FORGOTTEN_CORE, 2),
                "Runed core woven by Rune Enchanter.");
        cmd = registerWeapon(cmd, "end_ancient_arcane_staff", "Ancient Arcane Staff", Material.END_ROD,
                ItemRarity.LEGENDARY, CombatDiscipline.MAGE, 20, "mage_staffs",
                SkyItemStats.builder().combatDamage(18.0).slayerDamageBonus(0.32).speedBonus(0.08).build(),
                Map.of(MAT_FORGOTTEN_CORE, 5, MAT_ABYSSAL_FRAGMENT, 2),
                "Recovered from the Astral Observatory upper spire.");
        cmd = registerWeapon(cmd, "end_astral_archmage_staff", "Astral Archmage Staff", Material.END_ROD,
                ItemRarity.MYTHIC, CombatDiscipline.MAGE, 30, "mage_staffs",
                SkyItemStats.builder().combatDamage(24.0).slayerDamageBonus(0.45).maxHealthBonus(8.0).speedBonus(0.10).build(),
                Map.of(MAT_FORGOTTEN_CORE, 8, MAT_ABYSSAL_FRAGMENT, 5),
                "Eryndor's supreme focus. Unlocks peak spell conduit velocity.");

        // 5. ARMOR PROGRESSION (2 full 4-piece sets per Discipline = 6 sets / 24 armor pieces)
        cmd = registerArmorProgression(cmd, "end_scout", "End Scout", CombatDiscipline.ARCHERY, 5,
                ItemRarity.UNCOMMON, Color.fromRGB(107, 203, 119), false,
                SkyItemStats.builder().defense(2.0).maxHealthBonus(2.0).speedBonus(0.025).build(),
                SkyItemStats.builder().speedBonus(0.08).slayerDamageBonus(0.12).build(),
                "4pc Bonus: +8% Agility & +12% End Slayer Damage",
                Map.of(MAT_SHATTERED_SHARD, 4, MAT_CRYSTAL_ESSENCE, 1));

        cmd = registerArmorProgression(cmd, "end_voidstalker", "Voidstalker", CombatDiscipline.ARCHERY, 18,
                ItemRarity.LEGENDARY, Color.fromRGB(34, 139, 94), false,
                SkyItemStats.builder().defense(4.0).maxHealthBonus(5.0).speedBonus(0.04).combatDamage(1.5).build(),
                SkyItemStats.builder().speedBonus(0.12).combatDamage(6.0).slayerDamageBonus(0.20).build(),
                "4pc Bonus: +12% Agility, +6 Damage & +20% End Slayer",
                Map.of(MAT_FORGOTTEN_CORE, 2, MAT_ABYSSAL_FRAGMENT, 1));

        cmd = registerArmorProgression(cmd, "end_endsteel_vanguard", "Endsteel Vanguard", CombatDiscipline.MELEE, 5,
                ItemRarity.UNCOMMON, Color.fromRGB(220, 90, 90), true,
                SkyItemStats.builder().defense(3.5).maxHealthBonus(4.0).build(),
                SkyItemStats.builder().defense(6.0).maxHealthBonus(8.0).build(),
                "4pc Bonus: +6 Defense & +8 Max Health",
                Map.of(MAT_SHATTERED_SHARD, 4, MAT_CRYSTAL_ESSENCE, 1));

        cmd = registerArmorProgression(cmd, "end_riftwarden", "Riftwarden Plate", CombatDiscipline.MELEE, 18,
                ItemRarity.LEGENDARY, Color.fromRGB(150, 35, 65), false,
                SkyItemStats.builder().defense(5.5).maxHealthBonus(7.0).combatDamage(1.5).build(),
                SkyItemStats.builder().defense(10.0).maxHealthBonus(14.0).combatDamage(5.0).build(),
                "4pc Bonus: +10 Defense, +14 Max Health & +5 Damage",
                Map.of(MAT_FORGOTTEN_CORE, 2, MAT_ABYSSAL_FRAGMENT, 1));

        cmd = registerArmorProgression(cmd, "end_voidweave", "Voidweave", CombatDiscipline.MAGE, 5,
                ItemRarity.UNCOMMON, Color.fromRGB(142, 94, 255), false,
                SkyItemStats.builder().defense(2.0).maxHealthBonus(3.0).speedBonus(0.02).build(),
                SkyItemStats.builder().combatDamage(4.0).slayerDamageBonus(0.12).speedBonus(0.05).build(),
                "4pc Bonus: +4 Spell Damage & +12% End Slayer",
                Map.of(MAT_SHATTERED_SHARD, 4, MAT_CRYSTAL_ESSENCE, 1));

        cmd = registerArmorProgression(cmd, "end_astral_regalia", "Astral Archmage", CombatDiscipline.MAGE, 18,
                ItemRarity.LEGENDARY, Color.fromRGB(214, 110, 255), false,
                SkyItemStats.builder().defense(3.5).maxHealthBonus(5.5).combatDamage(2.0).speedBonus(0.03).build(),
                SkyItemStats.builder().combatDamage(8.0).slayerDamageBonus(0.24).maxHealthBonus(10.0).build(),
                "4pc Bonus: +8 Spell Damage, +24% End Slayer & +10 Health",
                Map.of(MAT_FORGOTTEN_CORE, 2, MAT_ABYSSAL_FRAGMENT, 1));

        // 6. STORMRAKE WYVERN BOSS REAGENTS, PREP WARD & FORGED BOSS GEAR
        addDef(CustomItemDefinition.builder(MAT_PHASE_SINEW, "Phase Sinew", Material.PHANTOM_MEMBRANE)
                .rarity(ItemRarity.UNCOMMON).domain(ItemDomain.UTILITY).slot(GearSlot.MATERIAL)
                .lore(List.of("Shifting tendon harvested from Enderloper heralds."))
                .customModelData(cmd++).build());
        addDef(CustomItemDefinition.builder(MAT_STORMRAKE_SCALE, "Stormrake Scale", Material.PRISMARINE_SHARD)
                .rarity(ItemRarity.RARE).domain(ItemDomain.UTILITY).slot(GearSlot.MATERIAL)
                .lore(List.of("Charged wyvern scale shed by Vaelkor."))
                .customModelData(cmd++).build());
        addDef(CustomItemDefinition.builder(MAT_TEMPEST_FANG, "Tempest Fang", Material.GHAST_TEAR)
                .rarity(ItemRarity.EPIC).domain(ItemDomain.UTILITY).slot(GearSlot.MATERIAL)
                .lore(List.of("Lightning-tempered fang torn from the Stormrake Wyvern."))
                .customModelData(cmd++).build());
        addDef(CustomItemDefinition.builder(MAT_WYVERN_HEART, "Wyvern Heart", Material.HEART_OF_THE_SEA)
                .rarity(ItemRarity.LEGENDARY).domain(ItemDomain.UTILITY).slot(GearSlot.MATERIAL)
                .lore(List.of("Pulsing storm core of Vaelkor, the Stormrake Wyvern."))
                .customModelData(cmd++).build());

        addDef(CustomItemDefinition.builder(RELIC_TEMPEST_WARD, "Tempest-Ward Charm", Material.AMETHYST_CLUSTER)
                .rarity(ItemRarity.EPIC).domain(ItemDomain.UTILITY).slot(GearSlot.OFF_HAND)
                .stats(SkyItemStats.builder().defense(4.0).maxHealthBonus(6.0).slayerDamageBonus(0.10).build())
                .lore(List.of("Hold in off-hand to reduce Vaelkor Breath/Lightning damage by 30%.",
                        "Crafted before the fight at Armorsmith Haldor."))
                .customModelData(cmd++).build());
        specs.put(RELIC_TEMPEST_WARD, new EndGearSpec(RELIC_TEMPEST_WARD, CombatDiscipline.MELEE, 1,
                "melee_armor", Map.of(MAT_CRYSTAL_ESSENCE, 3, MAT_FORGOTTEN_CORE, 1)));

        addDef(CustomItemDefinition.builder("end_stormrake_fangblade", "Stormrake Fangblade", Material.NETHERITE_SWORD)
                .rarity(ItemRarity.MYTHIC).domain(ItemDomain.SLAYER).slot(GearSlot.MAIN_HAND)
                .stats(SkyItemStats.builder().combatDamage(22.0).slayerDamageBonus(0.35).maxHealthBonus(8.0).defense(3.0).build())
                .ability("tempest_cleave", "Tempest Cleave", "Unleash a crackling shockwave arc hitting nearby foes.", 8)
                .lore(List.of("Forged from Vaelkor's Tempest Fangs.", "Requires Melee Level 18"))
                .customModelData(cmd++).build());
        specs.put("end_stormrake_fangblade", new EndGearSpec("end_stormrake_fangblade", CombatDiscipline.MELEE, 18,
                "melee_weapons", Map.of(MAT_STORMRAKE_SCALE, 6, MAT_TEMPEST_FANG, 2, MAT_WYVERN_HEART, 1)));

        addDef(CustomItemDefinition.builder("end_galewing_longbow", "Galewing Longbow", Material.BOW)
                .rarity(ItemRarity.MYTHIC).domain(ItemDomain.SLAYER).slot(GearSlot.MAIN_HAND)
                .stats(SkyItemStats.builder().combatDamage(21.0).slayerDamageBonus(0.35).speedBonus(0.10).build())
                .ability("chain_lightning", "Chain Lightning", "Charged shots arc lightning to up to 2 nearby enemies.", 4)
                .lore(List.of("Strung with Enderloper Phase Sinew & Stormrake Scales.", "Requires Archery Level 18"))
                .customModelData(cmd++).build());
        specs.put("end_galewing_longbow", new EndGearSpec("end_galewing_longbow", CombatDiscipline.ARCHERY, 18,
                "archery_bows", Map.of(MAT_STORMRAKE_SCALE, 6, MAT_PHASE_SINEW, 4, MAT_WYVERN_HEART, 1)));

        addDef(CustomItemDefinition.builder("end_tempest_conduit_staff", "Tempest Conduit Staff", Material.BREEZE_ROD)
                .rarity(ItemRarity.MYTHIC).domain(ItemDomain.SLAYER).slot(GearSlot.MAIN_HAND)
                .stats(SkyItemStats.builder().combatDamage(21.0).slayerDamageBonus(0.35).speedBonus(0.08).maxHealthBonus(6.0).build())
                .ability("skyfall_bolt", "Skyfall Bolt", "Calls down an AoE storm strike on your targeted area.", 6)
                .lore(List.of("Channels the Stormrake Wyvern's skyfall fury.", "Requires Mage Level 18"))
                .customModelData(cmd++).build());
        specs.put("end_tempest_conduit_staff", new EndGearSpec("end_tempest_conduit_staff", CombatDiscipline.MAGE, 18,
                "mage_staffs", Map.of(MAT_STORMRAKE_SCALE, 6, MAT_TEMPEST_FANG, 2, MAT_WYVERN_HEART, 1)));

        cmd = registerArmorProgression(cmd, "end_stormrake", "Stormrake Scale", CombatDiscipline.MELEE, 18,
                ItemRarity.MYTHIC, Color.fromRGB(92, 56, 175), false,
                SkyItemStats.builder().defense(5.0).maxHealthBonus(6.0).combatDamage(2.0).speedBonus(0.03).build(),
                SkyItemStats.builder().defense(10.0).maxHealthBonus(14.0).slayerDamageBonus(0.20).speedBonus(0.12).build(),
                "4pc Bonus: Stormborn (+10 Def, +14 HP, +12% Speed, +20% Boss Slayer)",
                Map.of(MAT_STORMRAKE_SCALE, 4, MAT_PHASE_SINEW, 2));
    }

    public void registerAllInto(@NotNull ItemRegistryService itemService) {
        Objects.requireNonNull(itemService, "itemService cannot be null");
        for (ArmorSetBonus set : armorSets) {
            itemService.registerArmorSet(set);
        }
        for (CustomItemDefinition def : definitions) {
            itemService.registerCustomItem(def);
        }
    }

    @Nullable
    public EndGearSpec spec(@NotNull String itemId) {
        return specs.get(itemId.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Collection<EndGearSpec> allSpecs() {
        return Collections.unmodifiableCollection(specs.values());
    }

    @NotNull
    public List<EndGearSpec> byForgeCategory(@NotNull String forgeCategory) {
        String norm = forgeCategory.toLowerCase(Locale.ROOT);
        List<EndGearSpec> out = new ArrayList<>();
        for (EndGearSpec s : specs.values()) {
            if (s.forgeCategory().equals(norm)) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Rolls an authoritative item quality level (1..5) for dungeon-free loot drops & forge crits.
     */
    public static int rollQualityLevel(@NotNull Random rng, boolean eliteOrBoss) {
        double p = rng.nextDouble();
        if (eliteOrBoss) {
            if (p < 0.12) return 5;
            if (p < 0.35) return 4;
            if (p < 0.70) return 3;
            return 2;
        }
        if (p < 0.04) return 4;
        if (p < 0.22) return 3;
        if (p < 0.58) return 2;
        return 1;
    }

    private int registerWeapon(
            int cmd,
            String id,
            String name,
            Material material,
            ItemRarity rarity,
            CombatDiscipline discipline,
            int reqLevel,
            String forgeCat,
            SkyItemStats stats,
            Map<String, Integer> costs,
            String lore
    ) {
        String usageHint = switch (discipline) {
            case MELEE -> "Left-Click: Strike | Right-Click: Void Dash Cleave";
            case ARCHERY -> "Hold Right-Click: Bow Shot | Left-Click: Gale Quick-Shot";
            case MAGE -> "Right-Click: Cast Arcane Bolt";
        };
        addDef(CustomItemDefinition.builder(id, name, material)
                .rarity(rarity)
                .domain(ItemDomain.SLAYER)
                .slot(GearSlot.MAIN_HAND)
                .stats(stats)
                .lore(List.of(lore, usageHint, "Requires " + discipline.displayName() + " Level " + reqLevel))
                .customModelData(cmd)
                .build());
        specs.put(id, new EndGearSpec(id, discipline, reqLevel, forgeCat, costs));
        return cmd + 1;
    }

    private int registerArmorProgression(
            int cmd,
            String setId,
            String setPrefix,
            CombatDiscipline discipline,
            int reqLevel,
            ItemRarity rarity,
            Color dyeColor,
            boolean useIronOrNetheriteBase,
            SkyItemStats pieceStats,
            SkyItemStats setBonusStats,
            String bonusSummary,
            Map<String, Integer> costs
    ) {
        armorSets.add(new ArmorSetBonus(setId, setPrefix + " Set", bonusSummary, setBonusStats, 0.0, false));

        GearSlot[] slots = {GearSlot.HELMET, GearSlot.CHESTPLATE, GearSlot.LEGGINGS, GearSlot.BOOTS};
        String[] suffixes = {"_helmet", "_chestplate", "_leggings", "_boots"};
        String[] labels = {"Helm", "Chestplate", "Leggings", "Boots"};
        Material[] ironMats = {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS};
        Material[] leatherMats = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};

        for (int i = 0; i < 4; i++) {
            String pieceId = setId + suffixes[i];
            Material mat = useIronOrNetheriteBase ? ironMats[i] : leatherMats[i];
            CustomItemDefinition.Builder b = CustomItemDefinition.builder(pieceId, setPrefix + " " + labels[i], mat)
                    .rarity(rarity)
                    .domain(ItemDomain.ARMOR)
                    .slot(slots[i])
                    .armorSet(setId)
                    .stats(pieceStats)
                    .lore(List.of(bonusSummary, "Requires " + discipline.displayName() + " Level " + reqLevel))
                    .customModelData(cmd++);
            if (!useIronOrNetheriteBase) {
                b.leatherColor(dyeColor);
            }
            addDef(b.build());
            specs.put(pieceId, new EndGearSpec(pieceId, discipline, reqLevel, "melee_armor", costs));
        }
        return cmd;
    }

    private void addDef(CustomItemDefinition def) {
        definitions.add(def);
    }
}

