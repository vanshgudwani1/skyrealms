package com.skyrealms.farming.garden.mastery;

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

import java.util.*;

/**
 * 54-slot interactive chest inventory GUI for Crop Mastery overview and milestone progression.
 * Uses authoritative InventoryHolder markers for guaranteed click cancellation and navigation.
 */
public final class CropMasteryGui {

    public static final String TITLE_OVERVIEW = "Crop Mastery Progression";
    public static final String TITLE_DETAIL_PREFIX = "Mastery: ";

    public static final int[] CROP_SLOTS = { 19, 20, 21, 22, 23, 24, 25, 29, 31, 33 };
    public static final int[] MILESTONE_SLOTS = { 19, 21, 23, 25, 29, 31, 33 };

    /**
     * Authoritative InventoryHolder marker for the 10-crop overview GUI.
     */
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

    /**
     * Authoritative InventoryHolder marker for an individual crop's milestone progression GUI.
     */
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

    /**
     * Opens the 54-slot overview GUI showing all 10 crops.
     */
    public static void openOverview(@NotNull Player player, @NotNull CropMasteryService service) {
        CropOverviewHolder holder = new CropOverviewHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE_OVERVIEW, NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        holder.setInventory(inv);

        // Decorative background panes
        ItemStack border = createPane(Material.PINK_STAINED_GLASS_PANE, " ");
        ItemStack accent = createPane(Material.MAGENTA_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, border);
        }
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, accent);
            inv.setItem(45 + col, accent);
        }

        // Top Header: Player Skull with total mastery stats
        List<CropMastery> all = service.getAllMasteries(player.getUniqueId());
        int totalLevel = all.stream().mapToInt(CropMastery::level).sum();
        long totalXp = all.stream().mapToLong(CropMastery::masteryXp).sum();

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.displayName(Component.text("✦ " + player.getName() + "'s Mastery Profile ✦", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Overall Agricultural Mastery", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Total Mastery Level: ", NamedTextColor.GRAY)
                    .append(Component.text(totalLevel + " / 300", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Total Mastery XP: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%,d XP", totalXp), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Click any crop below to view all 30 levels & milestone perks!", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            skullMeta.lore(lore);
            skull.setItemMeta(skullMeta);
        }
        inv.setItem(4, skull);

        // Populate the 10 crops
        CropType[] types = CropType.values();
        for (int i = 0; i < types.length && i < CROP_SLOTS.length; i++) {
            CropType crop = types[i];
            CropMastery mastery = service.getMastery(player.getUniqueId(), crop);
            inv.setItem(CROP_SLOTS[i], createCropItem(crop, mastery));
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("[X] Close Menu", NamedTextColor.RED, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            close.setItemMeta(closeMeta);
        }
        inv.setItem(49, close);

        player.openInventory(inv);
    }

    /**
     * Opens the detailed 7-milestone view for a specific crop.
     */
    public static void openCropDetail(@NotNull Player player, @NotNull CropType crop, @NotNull CropMasteryService service) {
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

        CropMastery mastery = service.getMastery(player.getUniqueId(), crop);

        // Crop Overview in slot 4
        inv.setItem(4, createCropItem(crop, mastery));

        // The 7 Milestones in MILESTONE_SLOTS
        List<MasteryMilestone> milestones = MasteryMilestone.getMilestonesFor(crop);
        for (int i = 0; i < milestones.size() && i < MILESTONE_SLOTS.length; i++) {
            MasteryMilestone m = milestones.get(i);
            inv.setItem(MILESTONE_SLOTS[i], createMilestoneItem(m, mastery));
        }

        // Back button in slot 49
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(Component.text("<< Return to Overview", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    private static ItemStack createCropItem(@NotNull CropType crop, @NotNull CropMastery mastery) {
        ItemStack item = new ItemStack(crop.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("◆ " + crop.displayName() + " Mastery", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Mastery Level: ", NamedTextColor.GRAY)
                .append(Component.text(mastery.level() + " / 30", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));

        if (mastery.level() < CropMastery.MAX_LEVEL) {
            lore.add(Component.text("Progress: ", NamedTextColor.GRAY)
                    .append(Component.text(mastery.getProgressBar(12) + " ", NamedTextColor.WHITE))
                    .append(Component.text(Math.round(mastery.progressToNextLevel() * 100) + "%", NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("XP: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(String.format("%,d", mastery.masteryXp()), NamedTextColor.AQUA))
                    .append(Component.text(" / " + String.format("%,d", mastery.xpForNextLevel()) + " XP", NamedTextColor.DARK_GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("✦ MAXIMUM MASTERY ACHIEVED ✦", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Perk Bonuses:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Double Drop Chance: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("+%.1f%%", mastery.getTotalDoubleDropBonus() * 100.0), NamedTextColor.AQUA, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));

        MasteryMilestone next = mastery.getNextMilestone();
        if (next != null) {
            lore.add(Component.empty());
            lore.add(Component.text("Next Milestone (Level " + next.level() + "):", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  " + next.title() + ": " + next.description(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("▶ Click to inspect all milestones & perks!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        if (mastery.level() > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createMilestoneItem(@NotNull MasteryMilestone milestone, @NotNull CropMastery mastery) {
        boolean unlocked = mastery.level() >= milestone.level();
        Material mat = unlocked ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (unlocked) {
            meta.displayName(Component.text("[✓] Level " + milestone.level() + ": " + milestone.title(), NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            meta.displayName(Component.text("[LOCKED] Level " + milestone.level() + ": " + milestone.title(), NamedTextColor.RED, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(milestone.description(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Milestone Rewards:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Double Drop: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("+%.1f%%", milestone.doubleDropBonus() * 100.0), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Coins Reward: ", NamedTextColor.GRAY)
                .append(Component.text("+" + milestone.bonusCoinsPerHarvest() + " Coins", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (unlocked) {
            lore.add(Component.text("Status: UNLOCKED & ACTIVE", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        } else {
            int needed = milestone.level() - mastery.level();
            lore.add(Component.text("Requires " + needed + " more levels", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
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
