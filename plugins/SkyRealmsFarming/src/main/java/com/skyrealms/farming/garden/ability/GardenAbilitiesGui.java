package com.skyrealms.farming.garden.ability;

import com.skyrealms.farming.garden.harvest.GardenBalanceConfig;
import com.skyrealms.farming.garden.journal.BotanicalJournalGui;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
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

/**
 * 54-slot interactive GUI for /garden abilities showing live mcMMO Farming stats,
 * all four Garden-only abilities, cooldowns, caps, and progression navigation.
 */
public final class GardenAbilitiesGui {

    public static final String TITLE = "Garden Farming Abilities";
    public static final int SLOT_NAV_MASTERY = 46;
    public static final int SLOT_NAV_JOURNAL = 47;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_NAV_PLOTS = 51;

    public static final class AbilitiesHolder implements InventoryHolder {
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

    private GardenAbilitiesGui() {}

    public static void open(
            @NotNull Player player,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull GardenAbilityService abilityService,
            @NotNull VerdantAuraManager auraManager
    ) {
        AbilitiesHolder holder = new AbilitiesHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        holder.setInventory(inv);

        ItemStack border = pane(Material.PINK_STAINED_GLASS_PANE, " ");
        ItemStack accent = pane(Material.MAGENTA_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, accent);
            inv.setItem(45 + col, accent);
        }

        int level = mcMmoService.getFarmingLevel(player);
        int xp = mcMmoService.getFarmingXp(player);
        int xpToNext = mcMmoService.getFarmingXpToNextLevel(player);
        GardenBalanceConfig cfg = abilityService.config();

        // Slot 4: mcMMO Farming Overview
        ItemStack overview = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta ovMeta = overview.getItemMeta();
        if (ovMeta != null) {
            ovMeta.displayName(Component.text("✦ mcMMO Farming Progression ✦", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            ovMeta.lore(List.of(
                    Component.text("Farming Level: ", NamedTextColor.GRAY)
                            .append(Component.text("Lv. " + level, NamedTextColor.GOLD, TextDecoration.BOLD))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Current XP: ", NamedTextColor.GRAY)
                            .append(Component.text(String.format("%,d / %,d XP", xp, xpToNext), NamedTextColor.AQUA))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Scope: ", NamedTextColor.GRAY)
                            .append(Component.text("SkyRealms Garden Exclusive", NamedTextColor.LIGHT_PURPLE))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(String.format("Reward Caps: Max Bonus %.0f%% | Max XP %.1fx",
                            cfg.maxCombinedBonusDropChance() * 100.0, cfg.maxXpMultiplier()), NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            ovMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            overview.setItemMeta(ovMeta);
        }
        inv.setItem(4, overview);

        // Slot 19: Bountiful Harvest
        boolean bUnlocked = abilityService.isUnlocked(GardenAbility.BOUNTIFUL_HARVEST, level);
        double bChance = abilityService.getBountifulHarvestChance(level);
        inv.setItem(19, abilityIcon(Material.WHEAT, GardenAbility.BOUNTIFUL_HARVEST, bUnlocked, cfg.bountifulUnlockLevel(), List.of(
                Component.text("Current Bonus Chance: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.1f%%", bChance * 100.0), NamedTextColor.AQUA, TextDecoration.BOLD))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(String.format("Range: %.0f%% (Lv.%d) -> %.0f%% (Lv.%d)",
                        cfg.bountifulMinChance() * 100.0, cfg.bountifulUnlockLevel(),
                        cfg.bountifulMaxChance() * 100.0, cfg.bountifulMaxScaleLevel()), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        )));

        // Slot 21: Verdant Aura
        boolean vUnlocked = abilityService.isUnlocked(GardenAbility.VERDANT_AURA, level);
        boolean vActive = auraManager.isAuraActive(player.getUniqueId());
        String vBuffs = level >= cfg.verdantHaste2Level() ? "Haste II + Speed I"
                : (level >= cfg.verdantSpeed1Level() ? "Haste I + Speed I"
                : (vUnlocked ? "Haste I" : "Locked"));
        inv.setItem(21, abilityIcon(Material.AZALEA_LEAVES, GardenAbility.VERDANT_AURA, vUnlocked, cfg.verdantUnlockLevel(), List.of(
                Component.text("Active Buffs: ", NamedTextColor.GRAY)
                        .append(Component.text(vBuffs, vUnlocked ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Status: ", NamedTextColor.GRAY)
                        .append(Component.text(vActive ? "ACTIVE IN GARDEN" : "Dormant (Garden Only)",
                                vActive ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(String.format("Speed I at Lv.%d | Haste II at Lv.%d",
                        cfg.verdantSpeed1Level(), cfg.verdantHaste2Level()), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        )));

        // Slot 23: Sakura Harvest Surge
        boolean sUnlocked = abilityService.isUnlocked(GardenAbility.SAKURA_HARVEST_SURGE, level);
        long remActive = abilityService.getSurgeRemainingActiveSeconds(player.getUniqueId());
        long remCd = abilityService.getSurgeRemainingCooldownSeconds(player.getUniqueId());
        String surgeStatus = !sUnlocked ? "Locked"
                : (remActive > 0 ? "ACTIVE (" + remActive + "s left)"
                : (remCd > 0 ? "Cooldown (" + remCd + "s)" : "READY (Sneak + Right-Click Hoe)"));
        inv.setItem(23, abilityIcon(Material.CHERRY_SAPLING, GardenAbility.SAKURA_HARVEST_SURGE, sUnlocked, cfg.surgeUnlockLevel(), List.of(
                Component.text("Status: ", NamedTextColor.GRAY)
                        .append(Component.text(surgeStatus, remActive > 0 ? NamedTextColor.LIGHT_PURPLE
                                : (remCd > 0 ? NamedTextColor.YELLOW : (sUnlocked ? NamedTextColor.GREEN : NamedTextColor.RED))))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Duration: ", NamedTextColor.GRAY)
                        .append(Component.text(abilityService.getSurgeDurationSeconds(level) + "s", NamedTextColor.AQUA))
                        .append(Component.text(" | Cooldown: ", NamedTextColor.GRAY))
                        .append(Component.text(abilityService.getSurgeCooldownSeconds(level) + "s", NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false)
        )));

        // Slot 25: Golden Sprout
        boolean gUnlocked = abilityService.isUnlocked(GardenAbility.GOLDEN_SPROUT, level);
        double gChance = abilityService.getGoldenSproutChance(level);
        double remDaily = abilityService.getRemainingDailyGoldenSproutCap(player.getUniqueId());
        inv.setItem(25, abilityIcon(Material.GOLD_INGOT, GardenAbility.GOLDEN_SPROUT, gUnlocked, cfg.goldenSproutUnlockLevel(), List.of(
                Component.text("Proc Chance: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.1f%%", gChance * 100.0), NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Max Per-Harvest: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.0f Coins", cfg.goldenSproutMaxCoinsPerHarvest()), NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Daily Cap Remaining: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%,.0f / %,.0f Coins", remDaily, cfg.goldenSproutDailyCoinCap()), NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false)
        )));

        // Navigation Row
        inv.setItem(SLOT_NAV_MASTERY, BotanicalJournalGui.navButton(Material.WHEAT, "✦ Crop Mastery", "Inspect 10 crops & 7 tiers"));
        inv.setItem(SLOT_NAV_JOURNAL, BotanicalJournalGui.navButton(Material.WRITTEN_BOOK, "✦ Botanical Journal", "Collections, streaks & achievements"));
        inv.setItem(SLOT_CLOSE, BotanicalJournalGui.navButton(Material.BARRIER, "[X] Close Menu", "Close Abilities menu"));
        inv.setItem(SLOT_NAV_PLOTS, BotanicalJournalGui.navButton(Material.GRASS_BLOCK, "✦ Garden Plots", "Print plot status in chat"));

        player.openInventory(inv);
    }

    private static ItemStack abilityIcon(
            @NotNull Material mat,
            @NotNull GardenAbility ability,
            boolean unlocked,
            int unlockLevel,
            @NotNull List<Component> extraLore
    ) {
        ItemStack item = new ItemStack(unlocked ? mat : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text((unlocked ? "[✓] " : "[Lv." + unlockLevel + "] ") + ability.displayName(),
                            unlocked ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(ability.triggerType(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(ability.shortSummary(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.addAll(extraLore);
            if (!unlocked) {
                lore.add(Component.empty());
                lore.add(Component.text("Requires mcMMO Farming Lv." + unlockLevel, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}

