package com.skyrealms.pets.gui;

import com.skyrealms.pets.model.PetDefinition;
import com.skyrealms.pets.model.PlayerPetData;
import com.skyrealms.pets.service.PetService;
import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Custom 45-slot Cubees Companion Pets GUI (/pets).
 */
public final class PetsGui {

    public static final int[] PET_SLOTS = {11, 12, 14, 15, 20, 21, 23, 24};
    public static final int SLOT_ACTIVE_INFO = 4;
    public static final int SLOT_DISMISS = 38;
    public static final int SLOT_CLOSE = 40;
    public static final int SLOT_RENAME_HINT = 42;

    public static final class PetsHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        @NotNull
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private PetsGui() {}

    public static void open(@NotNull Player player, @NotNull PetService petService) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(petService, "petService cannot be null");

        PetsHolder holder = new PetsHolder();
        Inventory inv = Bukkit.createInventory(
                holder, 45, Component.text("Cubees Pets Collection", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        holder.setInventory(inv);

        ItemStack border = pane(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack accent = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, border);
        }
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, accent);
            inv.setItem(36 + col, accent);
        }

        Map<String, PlayerPetData> unlocked = petService.getUnlockedPets(player.getUniqueId());
        PlayerPetData activeData = petService.getActivePetData(player.getUniqueId()).orElse(null);

        inv.setItem(SLOT_ACTIVE_INFO, buildActiveBanner(petService, activeData));

        List<PetDefinition> defs = List.copyOf(petService.catalog().all());
        for (int i = 0; i < defs.size() && i < PET_SLOTS.length; i++) {
            PetDefinition def = defs.get(i);
            PlayerPetData data = unlocked.get(def.id());
            inv.setItem(PET_SLOTS[i], buildPetIcon(def, data));
        }

        inv.setItem(SLOT_DISMISS, namedItem(
                Material.LEAD,
                "Recall Active Pet",
                NamedTextColor.YELLOW,
                List.of(Component.text(activeData != null ? "Click to recall your current Cubee." : "No pet is currently summoned.",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
        ));

        inv.setItem(SLOT_CLOSE, namedItem(
                Material.BARRIER,
                "Close",
                NamedTextColor.RED,
                List.of(Component.text("Close Pets Menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
        ));

        inv.setItem(SLOT_RENAME_HINT, namedItem(
                Material.NAME_TAG,
                "Rename Active Pet",
                NamedTextColor.AQUA,
                List.of(
                        Component.text("Use ", NamedTextColor.GRAY)
                                .append(Component.text("/pets rename <name>", NamedTextColor.YELLOW))
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Active pets gain XP when harvesting crops!", NamedTextColor.GREEN)
                                .decoration(TextDecoration.ITALIC, false)
                )
        ));

        player.openInventory(inv);
    }

    private static ItemStack buildActiveBanner(PetService service, PlayerPetData activeData) {
        if (activeData == null) {
            return namedItem(
                    Material.NETHER_STAR,
                    "No Active Pet",
                    NamedTextColor.GRAY,
                    List.of(Component.text("Click any unlocked Cubee below to summon!", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false))
            );
        }
        PetDefinition def = service.catalog().find(activeData.petId());
        if (def == null) {
            return pane(Material.NETHER_STAR);
        }
        SkyItemStats stats = def.statsForLevel(activeData.level());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Active: ", NamedTextColor.GRAY)
                .append(Component.text(def.displayName() + " [Lv." + activeData.level() + "/" + PetDefinition.MAX_LEVEL + "]",
                        def.color(), TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        appendStatLines(lore, stats);
        return namedItem(def.iconMaterial(), "Active Cubee Bonuses", NamedTextColor.GREEN, lore);
    }

    private static ItemStack buildPetIcon(PetDefinition def, PlayerPetData data) {
        boolean isUnlocked = data != null;
        boolean isActive = data != null && data.active();
        int level = isUnlocked ? data.level() : 1;
        SkyItemStats stats = def.statsForLevel(level);

        Material mat = isUnlocked ? def.iconMaterial() : Material.GRAY_DYE;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String titlePrefix = isActive ? "✦ [ACTIVE] " : (isUnlocked ? "✦ " : "🔒 ");
        meta.displayName(Component.text(titlePrefix + def.displayName() + (isUnlocked ? " [Lv." + level + "]" : ""),
                        isUnlocked ? def.color() : NamedTextColor.GRAY, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(def.rarityLabel() + " • " + def.element(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(def.flavor(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        lore.add(Component.text(isUnlocked ? "Current Perks (Lv." + level + "):" : "Starter Perks (Lv.1):", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        appendStatLines(lore, stats);

        if (isUnlocked) {
            lore.add(Component.empty());
            if (level >= PetDefinition.MAX_LEVEL) {
                lore.add(Component.text("MAX LEVEL (" + PetDefinition.MAX_LEVEL + "/" + PetDefinition.MAX_LEVEL + ")",
                        NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                double req = PlayerPetData.xpRequiredForNextLevel(level);
                lore.add(Component.text(String.format("Pet XP: %.0f / %.0f", data.xp(), req), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text(isActive ? "▶ Click to Recall" : "▶ Click to Summon",
                    isActive ? NamedTextColor.YELLOW : NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text(String.format("Unlock Cost: %,d Coins", def.unlockCostCoins()), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("▶ Click to Unlock", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        if (isActive) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private static void appendStatLines(List<Component> lore, SkyItemStats stats) {
        if (stats.farmingFortune() > 0.0) {
            lore.add(Component.text("  Farming Fortune: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("+%.0f%%", stats.farmingFortune() * 100.0), NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (stats.farmingXpBonus() > 0.0) {
            lore.add(Component.text("  Farming XP: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("+%.0f%%", stats.farmingXpBonus() * 100.0), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (stats.speedBonus() > 0.0) {
            lore.add(Component.text("  Walk Speed: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("+%.0f%%", stats.speedBonus() * 100.0), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack namedItem(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }
}

