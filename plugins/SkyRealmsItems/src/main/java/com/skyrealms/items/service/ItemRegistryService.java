package com.skyrealms.items.service;

import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.factory.SkyRealmsItemFactory;
import com.skyrealms.items.model.ArmorSetBonus;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.model.GearSlot;
import com.skyrealms.platform.integrations.CustomItemStatProvider;
import com.skyrealms.platform.integrations.SkyItemStats;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative service managing SkyRealms Farming Hoes, upgrade progression,
 * and level-scaled cross-discipline stat provisioning.
 */
public final class ItemRegistryService implements CustomItemStatProvider {

    private final ItemCatalog catalog;
    private final SkyRealmsItemFactory factory;
    private final HoeUpgradeService upgradeService;

    public ItemRegistryService(@NotNull ItemCatalog catalog, @NotNull SkyRealmsItemFactory factory) {
        this(catalog, factory, new HoeUpgradeService(catalog, factory));
    }

    public ItemRegistryService(
            @NotNull ItemCatalog catalog,
            @NotNull SkyRealmsItemFactory factory,
            @NotNull HoeUpgradeService upgradeService
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
        this.factory = Objects.requireNonNull(factory, "factory cannot be null");
        this.upgradeService = Objects.requireNonNull(upgradeService, "upgradeService cannot be null");
    }

    @NotNull
    public ItemCatalog catalog() {
        return catalog;
    }

    @NotNull
    public SkyRealmsItemFactory factory() {
        return factory;
    }

    @NotNull
    public HoeUpgradeService upgrades() {
        return upgradeService;
    }

    @Override
    @NotNull
    public Optional<String> getCustomItemId(@Nullable ItemStack item) {
        return factory.readCustomItemId(item);
    }

    @Override
    @NotNull
    public SkyItemStats readItemStats(@Nullable ItemStack item) {
        Optional<CustomItemDefinition> defOpt = factory.readDefinition(item);
        if (defOpt.isEmpty()) {
            return SkyItemStats.EMPTY;
        }
        int level = factory.readItemLevel(item);
        return HoeUpgradeService.scaleStats(defOpt.get(), level);
    }

    @Override
    @NotNull
    public SkyItemStats getPlayerEquippedStats(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");
        PlayerInventory inv = player.getInventory();
        return calculateEquippedStats(
                inv.getItemInMainHand(),
                inv.getItemInOffHand(),
                inv.getArmorContents()
        );
    }

    public void registerCustomItem(@NotNull CustomItemDefinition def) {
        catalog.register(def);
    }

    public void registerArmorSet(@NotNull ArmorSetBonus setBonus) {
        catalog.registerArmorSet(setBonus);
    }

    @NotNull
    public SkyItemStats calculateEquippedStats(
            @Nullable ItemStack mainHand,
            @Nullable ItemStack offHand,
            @Nullable ItemStack[] armorContents
    ) {
        SkyItemStats total = SkyItemStats.EMPTY;

        Optional<CustomItemDefinition> mainDef = factory.readDefinition(mainHand);
        if (mainDef.isPresent() && mainDef.get().slot() == GearSlot.MAIN_HAND) {
            total = total.add(readItemStats(mainHand));
        }
        Optional<CustomItemDefinition> offDef = factory.readDefinition(offHand);
        if (offDef.isPresent() && offDef.get().slot() == GearSlot.OFF_HAND) {
            total = total.add(readItemStats(offHand));
        }
        if (armorContents != null) {
            for (ItemStack armorPiece : armorContents) {
                Optional<CustomItemDefinition> armDef = factory.readDefinition(armorPiece);
                if (armDef.isPresent() && isArmorSlot(armDef.get().slot())) {
                    total = total.add(readItemStats(armorPiece));
                }
            }
            Optional<ArmorSetBonus> setBonus = getActiveArmorSet(armorContents);
            if (setBonus.isPresent()) {
                total = total.add(setBonus.get().bonusStats());
            }
        }
        return total;
    }

    @NotNull
    public Optional<ArmorSetBonus> getActiveArmorSet(@NotNull Player player) {
        return getActiveArmorSet(player.getInventory().getArmorContents());
    }

    @NotNull
    public Optional<ArmorSetBonus> getActiveArmorSet(@Nullable ItemStack[] armorContents) {
        if (armorContents == null || armorContents.length < 4) {
            return Optional.empty();
        }
        String matchedSetId = null;
        for (ItemStack piece : armorContents) {
            Optional<CustomItemDefinition> defOpt = factory.readDefinition(piece);
            if (defOpt.isEmpty() || !isArmorSlot(defOpt.get().slot())) {
                return Optional.empty();
            }
            String pieceSet = defOpt.get().armorSetId();
            if (pieceSet == null || pieceSet.isBlank()) {
                return Optional.empty();
            }
            if (matchedSetId == null) {
                matchedSetId = pieceSet;
            } else if (!matchedSetId.equalsIgnoreCase(pieceSet)) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(matchedSetId).map(catalog::findArmorSet);
    }

    private static boolean isArmorSlot(GearSlot slot) {
        return slot == GearSlot.HELMET || slot == GearSlot.CHESTPLATE || slot == GearSlot.LEGGINGS || slot == GearSlot.BOOTS;
    }

    @Override
    @NotNull
    public Optional<ItemStack> createCustomItem(@NotNull String id, int amount) {
        return createCustomItem(id, amount, HoeUpgradeService.MIN_LEVEL);
    }

    @NotNull
    public Optional<ItemStack> createCustomItem(@NotNull String id, int amount, int level) {
        Objects.requireNonNull(id, "id cannot be null");
        CustomItemDefinition def = catalog.find(id);
        if (def == null) {
            return Optional.empty();
        }
        return Optional.of(factory.createItem(def, amount, level));
    }
}
