package com.skyrealms.items.gui;

import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.items.service.ItemRegistryService;
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
import java.util.Objects;
import java.util.Optional;

/**
 * SkyRealms Farming Hoes & Upgrade GUI (/items).
 */
public final class ItemsCatalogGui {

    public static final int[] HOE_SLOTS = {11, 13, 15};
    public static final int SLOT_UPGRADE_HOE = 31;
    public static final int SLOT_PLAYER_STATS = 38;
    public static final int SLOT_CLOSE = 40;

    public static final class CatalogHolder implements InventoryHolder {
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

    private ItemsCatalogGui() {}

    public static void open(@NotNull Player player, @NotNull ItemRegistryService itemService) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(itemService, "itemService cannot be null");

        CatalogHolder holder = new CatalogHolder();
        Inventory inv = Bukkit.createInventory(
                holder, 45, Component.text("SkyRealms Farming Hoes", NamedTextColor.GOLD, TextDecoration.BOLD)
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

        inv.setItem(4, infoBanner());

        List<CustomItemDefinition> hoes = List.copyOf(itemService.catalog().all());
        boolean canGive = player.hasPermission("skyrealms.items.admin") || player.isOp();
        for (int i = 0; i < hoes.size() && i < HOE_SLOTS.length; i++) {
            CustomItemDefinition def = hoes.get(i);
            ItemStack preview = itemService.factory().createItem(def, 1);
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("Item ID: " + def.id() + " (Lv.1–Lv.5)", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                if (canGive) {
                    lore.add(Component.text("▶ Click to claim 1x (Admin)", NamedTextColor.YELLOW, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
                preview.setItemMeta(meta);
            }
            inv.setItem(HOE_SLOTS[i], preview);
        }

        inv.setItem(SLOT_UPGRADE_HOE, buildUpgradeButton(player, itemService));
        inv.setItem(SLOT_PLAYER_STATS, buildStatsButton(player, itemService));
        inv.setItem(SLOT_CLOSE, namedItem(Material.BARRIER, "Close", NamedTextColor.RED,
                List.of(Component.text("Close menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

        player.openInventory(inv);
    }

    private static ItemStack infoBanner() {
        return namedItem(
                Material.WHEAT,
                "✦ Custom Farming Hoes",
                NamedTextColor.GREEN,
                List.of(
                        Component.text("3 upgradable tiers (Lv.1 to Lv.5 each).", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("• Supports EliteEnchantments custom enchants", NamedTextColor.AQUA)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("• Vanilla enchants are blocked on all custom items", NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)
                )
        );
    }

    private static ItemStack buildUpgradeButton(Player player, ItemRegistryService itemService) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<CustomItemDefinition> defOpt = itemService.factory().readDefinition(held);
        if (defOpt.isEmpty()) {
            return namedItem(
                    Material.ANVIL,
                    "Upgrade Held Hoe",
                    NamedTextColor.GOLD,
                    List.of(
                            Component.text("Hold a SkyRealms Farming Hoe in your", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("main hand to upgrade or evolve it!", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false)
                    )
            );
        }

        CustomItemDefinition currentDef = defOpt.get();
        int currentLevel = itemService.factory().readItemLevel(held);
        Optional<HoeUpgradeService.UpgradeStep> nextOpt = itemService.upgrades().nextUpgrade(currentDef, currentLevel);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Held: ", NamedTextColor.GRAY)
                .append(Component.text(currentDef.displayName() + " [Lv." + currentLevel + "]", currentDef.rarity().color()))
                .decoration(TextDecoration.ITALIC, false));

        if (nextOpt.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("✦ MAXIMUM TIER & LEVEL REACHED!", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            return namedItem(Material.NETHER_STAR, "Max Level Hoe", NamedTextColor.GOLD, lore);
        }

        HoeUpgradeService.UpgradeStep step = nextOpt.get();
        SkyItemStats nextStats = HoeUpgradeService.scaleStats(step.targetDefinition(), step.targetLevel());
        lore.add(Component.empty());
        lore.add(Component.text(step.isEvolution() ? "Next Evolution: " : "Next Level: ", NamedTextColor.YELLOW)
                .append(Component.text(step.targetDefinition().displayName() + " [Lv." + step.targetLevel() + "]",
                        step.targetDefinition().rarity().color()))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(String.format("  Farming Fortune: +%.0f%%", nextStats.farmingFortune() * 100.0), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(String.format("  Farming XP: +%.1f%%", nextStats.farmingXpBonus() * 100.0), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text(String.format("Cost: %,d Coins", step.coinCost()), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Preserves all custom enchants!", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("▶ Click to Upgrade", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        return namedItem(Material.ANVIL,
                step.isEvolution() ? "Evolve Held Hoe" : "Upgrade Held Hoe",
                NamedTextColor.GREEN, lore);
    }

    private static ItemStack buildStatsButton(Player player, ItemRegistryService itemService) {
        SkyItemStats stats = itemService.getPlayerEquippedStats(player);
        List<Component> lore = List.of(
                Component.text("Farming Fortune: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("+%.0f%%", stats.farmingFortune() * 100.0), NamedTextColor.GREEN))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Farming XP Bonus: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("+%.1f%%", stats.farmingXpBonus() * 100.0), NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Walk Speed Bonus: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("+%.0f%%", stats.speedBonus() * 100.0), NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false)
        );
        return namedItem(Material.PLAYER_HEAD, "Your Equipped Hoe Stats", NamedTextColor.AQUA, lore);
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
