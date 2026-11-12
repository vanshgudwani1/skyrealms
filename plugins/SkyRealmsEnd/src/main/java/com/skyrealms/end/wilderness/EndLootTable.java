package com.skyrealms.end.wilderness;

import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.end.gear.EndGearService;
import com.skyrealms.items.service.ItemRegistryService;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Authoritative loot tables rolling End crafting reagents and gear through {@link ItemRegistryService}
 * based on wilderness region tier, Elite status, and Field Boss encounters.
 */
public final class EndLootTable {

    public record RolledLootEntry(@NotNull String itemId, int quantity, boolean rolledGear, boolean eliteOrBoss) {}

    private final ItemRegistryService itemService;
    private final EndGearService gearService;

    public EndLootTable(@NotNull ItemRegistryService itemService, @NotNull EndGearService gearService) {
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
        this.gearService = Objects.requireNonNull(gearService, "gearService cannot be null");
    }

    /**
     * Pure deterministic loot roller returning item IDs & quantities for unit tests and live drops.
     */
    @NotNull
    public List<RolledLootEntry> rollEntries(
            @NotNull EndMobArchetype mob,
            boolean elite,
            @NotNull Random rng
    ) {
        Objects.requireNonNull(mob, "mob cannot be null");
        Objects.requireNonNull(rng, "rng cannot be null");
        List<RolledLootEntry> out = new ArrayList<>();
        boolean highTier = elite || mob.isFieldBoss();

        String primaryMat = switch (mob.region()) {
            case SHATTERED_FIELDS -> EndGearCatalog.MAT_SHATTERED_SHARD;
            case CRYSTAL_WILDS -> EndGearCatalog.MAT_CRYSTAL_ESSENCE;
            case FORGOTTEN_RUINS -> EndGearCatalog.MAT_FORGOTTEN_CORE;
            case ABYSSAL_GROUNDS -> EndGearCatalog.MAT_ABYSSAL_FRAGMENT;
        };

        int matQty = mob.isFieldBoss() ? 3 + rng.nextInt(3) : (elite ? 2 + rng.nextInt(2) : (rng.nextDouble() < 0.72 ? 1 : 0));
        if (matQty > 0) {
            out.add(new RolledLootEntry(primaryMat, matQty, false, highTier));
        }

        // Secondary regional reagent drop
        if (highTier && mob.region() != WildernessRegion.SHATTERED_FIELDS) {
            String prevMat = switch (mob.region()) {
                case CRYSTAL_WILDS -> EndGearCatalog.MAT_SHATTERED_SHARD;
                case FORGOTTEN_RUINS -> EndGearCatalog.MAT_CRYSTAL_ESSENCE;
                case ABYSSAL_GROUNDS -> EndGearCatalog.MAT_FORGOTTEN_CORE;
                default -> EndGearCatalog.MAT_SHATTERED_SHARD;
            };
            out.add(new RolledLootEntry(prevMat, 2, false, true));
        }

        // Equipment drop roll on Elites & Field Bosses
        double gearChance = mob.isFieldBoss() ? 0.85 : (elite ? 0.25 : 0.03);
        if (rng.nextDouble() < gearChance) {
            String gearId = pickGearPool(mob.region(), highTier, rng);
            out.add(new RolledLootEntry(gearId, 1, true, highTier));
        }

        return out;
    }

    @NotNull
    public List<ItemStack> materializeDrops(
            @NotNull EndMobArchetype mob,
            boolean elite,
            @NotNull Random rng
    ) {
        List<ItemStack> stacks = new ArrayList<>();
        for (RolledLootEntry entry : rollEntries(mob, elite, rng)) {
            if (entry.rolledGear()) {
                gearService.mintRolledGear(entry.itemId(), rng, entry.eliteOrBoss()).ifPresent(stacks::add);
            } else {
                itemService.createCustomItem(entry.itemId(), entry.quantity()).ifPresent(stacks::add);
            }
        }
        return stacks;
    }

    private static String pickGearPool(WildernessRegion region, boolean highTier, Random rng) {
        String[] pool = switch (region) {
            case SHATTERED_FIELDS -> new String[]{
                    "end_apprentice_bow", "end_apprentice_sword", "end_apprentice_staff",
                    "end_hunter_bow", "end_endsteel_blade", "end_crystal_staff"
            };
            case CRYSTAL_WILDS -> highTier
                    ? new String[]{"end_endwood_longbow", "end_void_cleaver", "end_voidcaller",
                    "end_scout_chestplate", "end_endsteel_vanguard_chestplate", "end_voidweave_chestplate"}
                    : new String[]{"end_hunter_bow", "end_endsteel_blade", "end_crystal_staff"};
            case FORGOTTEN_RUINS -> new String[]{
                    "end_voidpiercer", "end_riftbreaker", "end_rift_conduit",
                    "end_rift_hunter_bow", "end_ancient_greatblade", "end_ancient_arcane_staff"
            };
            case ABYSSAL_GROUNDS -> new String[]{
                    "end_rift_hunter_bow", "end_ancient_greatblade", "end_ancient_arcane_staff",
                    "end_voidstalker_chestplate", "end_riftwarden_chestplate", "end_astral_regalia_chestplate"
            };
        };
        return pool[rng.nextInt(pool.length)];
    }
}

