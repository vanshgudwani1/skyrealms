package com.skyrealms.farming.garden.mastery;

import com.skyrealms.farming.garden.ability.GardenAbility;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.journal.BotanicalJournalGui;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 54-slot interactive chest GUI for mcMMO-driven Farming, Garden Abilities, and Crop Mastery.
 */
public final class CropMasteryGui {

    public static final String TITLE_OVERVIEW = "Garden Farming & Crop Mastery";
    public static final String TITLE_DETAIL_PREFIX = "Crop Mastery: ";

    public static final int[] CROP_SLOTS = { 19, 20, 21, 22, 23, 24, 25, 29, 31, 33 };
    public static final int[] MILESTONE_SLOTS = { 19, 21, 23, 25, 29, 31, 33 };

    public static final int SLOT_ABILITIES_HEADER = 6;
    public static final int SLOT_NAV_ABILITIES = 46;
    public static final int SLOT_NAV_JOURNAL = 47;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_NAV_PLOTS = 51;

    public static final class CropOverviewHolder implements InventoryHolder {
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

    public static final class CropDetailHolder implements InventoryHolder {
        private final CropType cropType;
        private Inventory inventory;

        public CropDetailHolder(@NotNull CropType cropType) {
            this.cropType = cropType;
        }

        @NotNull
        public CropType getCropType() {
            return cropType;
        }

        @Override
        @NotNull
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private CropMasteryGui() {}

    public static void openOverview(
            @NotNull Player player,
            @NotNull CropMasteryService service,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull GardenAbilityService abilityService
    ) {
        CropOverviewHolder holder = new CropOverviewHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE_OVERVIEW, NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        holder.setInventory(inv);

        ItemStack border = createPane(Material.PINK_STAINED_GLASS_PANE, " ");
        ItemStack accent = createPane(Material.MAGENTA_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, border);
        }
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, accent);
            inv.setItem(45 + col, accent);
        }

        int farmingLevel = mcMmoService.getFarmingLevel(player);
        int farmingXp = mcMmoService.getFarmingXp(player);
        int xpToNext = mcMmoService.getFarmingXpToNextLevel(player);
        List<CropMastery> all = service.getAllMasteries(player.getUniqueId());
        int totalTiers = all.stream().mapToInt(CropMastery::tier).sum();
        long totalHarvested = all.stream().mapToLong(CropMastery::harvestCount).sum();

        // Slot 2: mcMMO Farming Skill Status
        ItemStack skillItem = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta skillMeta = skillItem.getItemMeta();
        if (skillMeta != null) {
            skillMeta.displayName(Component.text("✦ mcMMO Farming Skill ✦", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Authoritative Farming Progression", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Farming Level: ", NamedTextColor.GRAY)
                    .append(Component.text("Lv. " + farmingLevel, NamedTextColor.GOLD, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Level XP: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%,d / %,d", farmingXp, xpToNext), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Harvest mature crops in your Garden", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("to level Farming & unlock Abilities!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            skillMeta.lore(lore);
            skillMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            skillItem.setItemMeta(skillMeta);
        }
        inv.setItem(2, skillItem);

        // Slot 4: Player Mastery Profile Skull
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.displayName(Component.text("✦ " + player.getName() + "'s Garden Profile ✦", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("mcMMO Farming + Crop Mastery", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Total Mastery Tiers: ", NamedTextColor.GRAY)
                    .append(Component.text(totalTiers + " / " + (CropType.values().length * CropMastery.MAX_TIER), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Total Crops Harvested: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%,d", totalHarvested), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(String.format("Reward Caps: Max %.0f%% Bonus Drops | Max %.1fx XP",
                    abilityService.config().maxCombinedBonusDropChance() * 100.0,
                    abilityService.config().maxXpMultiplier()), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Click any crop below to inspect its 7 Mastery Tiers!", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            skullMeta.lore(lore);
            skull.setItemMeta(skullMeta);
        }
        inv.setItem(4, skull);

        // Slot 6: Garden Abilities Summary (clickable shortcut to Abilities GUI)
        ItemStack abilityItem = new ItemStack(Material.CHERRY_SAPLING);
        ItemMeta abMeta = abilityItem.getItemMeta();
        if (abMeta != null) {
            abMeta.displayName(Component.text("✦ Garden Farming Abilities ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Unlocked via mcMMO Farming Level:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            for (GardenAbility ability : GardenAbility.values()) {
                boolean unlocked = abilityService.isUnlocked(ability, farmingLevel);
                String status = unlocked ? "§a[✓] " : "§c[Lv." + ability.unlockLevel() + "] ";
                lore.add(Component.text(status + ability.displayName(), unlocked ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text(String.format("Bountiful: %.1f%% | Golden Sprout: %.1f%%",
                    abilityService.getBountifulHarvestChance(farmingLevel) * 100.0,
                    abilityService.getGoldenSproutChance(farmingLevel) * 100.0), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("▶ Click to open Abilities Menu!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            abMeta.lore(lore);
            abilityItem.setItemMeta(abMeta);
        }
        inv.setItem(SLOT_ABILITIES_HEADER, abilityItem);

        // Populate 10 crops
        CropType[] types = CropType.values();
        for (int i = 0; i < types.length && i < CROP_SLOTS.length; i++) {
            CropType crop = types[i];
            CropMastery mastery = service.getMastery(player.getUniqueId(), crop);
            inv.setItem(CROP_SLOTS[i], createCropItem(crop, mastery, farmingLevel));
        }

        // Navigation bar
        inv.setItem(SLOT_NAV_ABILITIES, BotanicalJournalGui.navButton(Material.CHERRY_SAPLING, "✦ Garden Abilities", "Inspect ability scaling & cooldowns"));
        inv.setItem(SLOT_NAV_JOURNAL, BotanicalJournalGui.navButton(Material.WRITTEN_BOOK, "✦ Botanical Journal", "Collections, strains & achievements"));
        inv.setItem(SLOT_CLOSE, BotanicalJournalGui.navButton(Material.BARRIER, "[X] Close Menu", "Close Crop Mastery menu"));
        inv.setItem(SLOT_NAV_PLOTS, BotanicalJournalGui.navButton(Material.GRASS_BLOCK, "✦ Garden Plots", "Print plot status in chat"));

        player.openInventory(inv);
    }

    public static void openCropDetail(
            @NotNull Player player,
            @NotNull CropType crop,
            @NotNull CropMasteryService service,
            @NotNull McMMOFarmingService mcMmoService
    ) {
        CropDetailHolder holder = new CropDetailHolder(crop);
        Inventory inv = Bukkit.createInventory(
                holder,
                54,
                Component.text(TITLE_DETAIL_PREFIX + crop.displayName(), TextColor.fromHexString("#77DD77"), TextDecoration.BOLD)
        );
        holder.setInventory(inv);

        ItemStack border = createPane(Material.LIME_STAINED_GLASS_PANE, " ");
        ItemStack accent = createPane(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, border);
        }
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, accent);
            inv.setItem(45 + col, accent);
        }

        int farmingLevel = mcMmoService.getFarmingLevel(player);
        CropMastery mastery = service.getMastery(player.getUniqueId(), crop);
        inv.setItem(4, createCropItem(crop, mastery, farmingLevel));

        List<MasteryMilestone> milestones = MasteryMilestone.getMilestonesFor(crop);
        for (int i = 0; i < milestones.size() && i < MILESTONE_SLOTS.length; i++) {
            inv.setItem(MILESTONE_SLOTS[i], createMilestoneItem(milestones.get(i), mastery, farmingLevel));
        }

        inv.setItem(SLOT_NAV_JOURNAL, BotanicalJournalGui.navButton(Material.WRITTEN_BOOK, "✦ Botanical Journal", "Open Botanical Journal"));
        inv.setItem(SLOT_CLOSE, BotanicalJournalGui.navButton(Material.ARROW, "<< Return to Mastery Overview", "Back to all 10 crops"));

        player.openInventory(inv);
    }

    private static ItemStack createCropItem(@NotNull CropType crop, @NotNull CropMastery mastery, int farmingLevel) {
        ItemStack item = new ItemStack(crop.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("◆ " + crop.displayName() + " Mastery", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Tier " + mastery.tier() + " / " + CropMastery.MAX_TIER + " — " + mastery.getCurrentTierTitle(),
                        NamedTextColor.YELLOW, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Identity: " + crop.masteryIdentity(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        MasteryMilestone next = mastery.getNextMilestone();
        if (next != null) {
            boolean levelMet = farmingLevel >= next.requiredMcMmoLevel();
            lore.add(Component.text("Farming Level: ", NamedTextColor.GRAY)
                    .append(Component.text(farmingLevel + " / " + next.requiredMcMmoLevel(),
                            levelMet ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Harvests: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%,d / %,d", mastery.harvestCount(), next.requiredHarvests()), NamedTextColor.AQUA))
                    .append(Component.text(String.format(" (%,d left)", mastery.getRemainingHarvestsForNextTier()), NamedTextColor.DARK_GRAY))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Progress: ", NamedTextColor.GRAY)
                    .append(Component.text(mastery.getProgressBar(12), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Harvests: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%,d", mastery.harvestCount()), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("✦ GRANDMASTER ACHIEVED ✦", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Current Rewards:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Crop Bonus Chance: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("+%.0f%%", mastery.getTotalDoubleDropBonus() * 100.0), NamedTextColor.AQUA, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Farming XP Multiplier: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("+%.0f%%", mastery.getTotalMcMmoXpMultiplier() * 100.0), NamedTextColor.GREEN, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));

        if (next != null) {
            lore.add(Component.empty());
            lore.add(Component.text("Next Tier: Tier " + next.tier() + " — " + next.title(), NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Reward: " + next.description() + " (+" + next.coinReward() + " Coins)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("▶ Click to inspect all 7 Mastery Tiers!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        if (mastery.tier() > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createMilestoneItem(@NotNull MasteryMilestone m, @NotNull CropMastery mastery, int farmingLevel) {
        boolean unlocked = mastery.tier() >= m.tier();
        ItemStack item = new ItemStack(unlocked ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text((unlocked ? "[✓] " : "[LOCKED] ") + "Tier " + m.tier() + ": " + m.title(),
                        unlocked ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(m.description(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Requirements:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • mcMMO Farming Lv.: ", NamedTextColor.GRAY)
                .append(Component.text(farmingLevel + " / " + m.requiredMcMmoLevel(),
                        farmingLevel >= m.requiredMcMmoLevel() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Crops Harvested: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%,d / %,d", mastery.harvestCount(), m.requiredHarvests()),
                        mastery.harvestCount() >= m.requiredHarvests() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Rewards:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Crop Bonus Drop: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("+%.0f%%", m.doubleDropBonus() * 100.0), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Farming XP Bonus: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("+%.0f%%", m.mcMmoXpBonusMultiplier() * 100.0), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Coin Milestone Bonus: ", NamedTextColor.GRAY)
                .append(Component.text("+" + m.coinReward() + " Coins", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        if (m.discoveryUnlock() != null) {
            lore.add(Component.text("  • Botanical Discovery: ", NamedTextColor.GRAY)
                    .append(Component.text(m.discoveryUnlock(), NamedTextColor.LIGHT_PURPLE))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (m.cosmeticUnlock() != null) {
            lore.add(Component.text("  • Cosmetic Unlock: ", NamedTextColor.GRAY)
                    .append(Component.text(m.cosmeticUnlock(), NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (m.equipmentHook() != null) {
            lore.add(Component.text("  • Equipment Attunement: ", NamedTextColor.GRAY)
                    .append(Component.text(m.equipmentHook(), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}
