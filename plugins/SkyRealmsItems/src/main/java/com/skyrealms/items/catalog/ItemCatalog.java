package com.skyrealms.items.catalog;

import com.skyrealms.items.model.ArmorSetBonus;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.model.GearSlot;
import com.skyrealms.items.model.ItemDomain;
import com.skyrealms.items.model.ItemRarity;
import com.skyrealms.platform.integrations.SkyItemStats;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative registry of the 3 upgradable SkyRealms Farming Hoes.
 */
public final class ItemCatalog {

    public static final String SPROUT_HOE = "sprout_hoe";
    public static final String SAKURA_SCYTHE = "sakura_scythe";
    public static final String VERDANT_REAPER = "verdant_reaper";

    private final Map<String, CustomItemDefinition> items;
    private final Map<String, ArmorSetBonus> armorSets;

    public ItemCatalog() {
        Map<String, CustomItemDefinition> map = Collections.synchronizedMap(new LinkedHashMap<>());

        register(map, CustomItemDefinition.builder(SPROUT_HOE, "Sprout Harvester Hoe", Material.IRON_HOE)
                .rarity(ItemRarity.UNCOMMON)
                .domain(ItemDomain.FARMING)
                .slot(GearSlot.MAIN_HAND)
                .stats(SkyItemStats.builder().farmingFortune(0.08).farmingXpBonus(0.05).build())
                .lore(List.of("Balanced starter scythe tuned for Garden plots."))
                .customModelData(1001)
                .build());

        register(map, CustomItemDefinition.builder(SAKURA_SCYTHE, "Sakura Blossom Scythe", Material.DIAMOND_HOE)
                .rarity(ItemRarity.EPIC)
                .domain(ItemDomain.FARMING)
                .slot(GearSlot.MAIN_HAND)
                .stats(SkyItemStats.builder().farmingFortune(0.20).farmingXpBonus(0.12).speedBonus(0.05).build())
                .ability("petal_gust", "Petal Gust", "Grants Haste I and Speed I for 8s while harvesting.", 20)
                .lore(List.of("Forged beneath the Sakura Conservatory canopy."))
                .customModelData(1002)
                .build());

        register(map, CustomItemDefinition.builder(VERDANT_REAPER, "Verdant Grand Reaper", Material.NETHERITE_HOE)
                .rarity(ItemRarity.LEGENDARY)
                .domain(ItemDomain.FARMING)
                .slot(GearSlot.MAIN_HAND)
                .stats(SkyItemStats.builder().farmingFortune(0.35).farmingXpBonus(0.20).speedBonus(0.08).build())
                .ability("bountiful_bloom", "Bountiful Bloom", "Harvests a 3x3 cluster of mature crops around the target.", 10)
                .lore(List.of("Masterwork reaper channeling peak conservatory yield."))
                .customModelData(1003)
                .build());

        this.items = map;
        this.armorSets = Collections.synchronizedMap(new LinkedHashMap<>());
    }

    /**
     * Registers an additional domain item (e.g., End Civilization weapons, armor, or materials)
     * into the single universal SkyRealms ItemCatalog.
     */
    public void register(@NotNull CustomItemDefinition def) {
        Objects.requireNonNull(def, "def cannot be null");
        register(items, def);
    }

    public void registerArmorSet(@NotNull ArmorSetBonus setBonus) {
        Objects.requireNonNull(setBonus, "setBonus cannot be null");
        armorSets.put(setBonus.setId().toLowerCase(Locale.ROOT), setBonus);
    }

    private static void register(Map<String, CustomItemDefinition> map, CustomItemDefinition def) {
        map.put(def.id().toLowerCase(Locale.ROOT), def);
    }

    @Nullable
    public CustomItemDefinition find(@NotNull String id) {
        Objects.requireNonNull(id, "id cannot be null");
        return items.get(id.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Collection<CustomItemDefinition> all() {
        synchronized (items) {
            return List.copyOf(items.values());
        }
    }

    @NotNull
    public List<CustomItemDefinition> byDomain(@NotNull ItemDomain domain) {
        Objects.requireNonNull(domain, "domain cannot be null");
        synchronized (items) {
            return items.values().stream()
                    .filter(def -> def.domain() == domain)
                    .toList();
        }
    }

    @Nullable
    public ArmorSetBonus findArmorSet(@NotNull String setId) {
        Objects.requireNonNull(setId, "setId cannot be null");
        return armorSets.get(setId.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Collection<ArmorSetBonus> allArmorSets() {
        synchronized (armorSets) {
            return List.copyOf(armorSets.values());
        }
    }
}
