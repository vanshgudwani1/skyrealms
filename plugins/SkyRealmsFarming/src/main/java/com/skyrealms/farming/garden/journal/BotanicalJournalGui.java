package com.skyrealms.farming.garden.journal;

import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropMasteryService;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.farming.model.FarmingCropCollection;
import com.skyrealms.farming.repository.FarmingRepository;
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

import java.util.*;

/**
 * 54-slot read-only Botanical Journal GUI displaying crop collections,
 * mastery variety discoveries, mutation discoveries, streaks, and achievements.
 */
public final class BotanicalJournalGui {

    public static final String TITLE = "Botanical Journal";
    public static final int SLOT_NAV_MASTERY = 46;
    public static final int SLOT_NAV_ABILITIES = 47;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_NAV_PLOTS = 51;

    private static final int[] CROP_ARCHIVE_SLOTS = { 10, 11, 12, 13, 14, 15, 16, 19, 21, 23 };
    private static final int[] ACHIEVEMENT_SLOTS = { 28, 29, 30, 31, 32, 33, 34, 37 };

    public static final class JournalHolder implements InventoryHolder {
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

    private BotanicalJournalGui() {}

    public static void open(
            @NotNull Player player,
            @NotNull BotanicalJournalService journalService,
            @NotNull CropMasteryService masteryService,
            @NotNull McMMOFarmingService mcMmoService
    ) {
        JournalHolder holder = new JournalHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE, NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        holder.setInventory(inv);

        ItemStack border = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack accent = pane(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, accent);
            inv.setItem(45 + col, accent);
        }

        UUID uuid = player.getUniqueId();
        BotanicalJournalEntry entry = journalService.getJournal(uuid);
        Map<String, FarmingCropCollection> collections = journalService.getCollections(uuid);
        List<FarmingRepository.MutationDiscoveryRecord> mutations = journalService.getMutationDiscoveries(uuid);
        int farmingLevel = mcMmoService.getFarmingLevel(player);

        // Slot 4: Journal Summary
        long totalCollected = collections.values().stream().mapToLong(FarmingCropCollection::amountCollected).sum();
        ItemStack summary = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta sumMeta = summary.getItemMeta();
        if (sumMeta != null) {
            sumMeta.displayName(Component.text("✦ " + player.getName() + "'s Botanical Journal ✦", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("mcMMO Farming Level: ", NamedTextColor.GRAY)
                    .append(Component.text("Lv. " + farmingLevel, NamedTextColor.GREEN, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Lifetime Crop Collection: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%,d", totalCollected), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Highest Harvest Streak: ", NamedTextColor.GRAY)
                    .append(Component.text(entry.highestHarvestStreak() + " crops", NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Best Single Harvest: ", NamedTextColor.GRAY)
                    .append(Component.text(entry.bestSingleHarvest() + " items", NamedTextColor.LIGHT_PURPLE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Discovered Strains: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(entry.discoveredVarieties().size()), NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Rare Mutations Recorded: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(mutations.size()), NamedTextColor.LIGHT_PURPLE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Future Archives Ready: Equipment, Soil Science & Research", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            sumMeta.lore(lore);
            sumMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            summary.setItemMeta(sumMeta);
        }
        inv.setItem(4, summary);

        // Crop Collection & Discovery Archive
        CropType[] crops = CropType.values();
        for (int i = 0; i < crops.length && i < CROP_ARCHIVE_SLOTS.length; i++) {
            CropType crop = crops[i];
            CropMastery mastery = masteryService.getMastery(uuid, crop);
            FarmingCropCollection col = collections.get(crop.naturalCropId());
            long collected = col != null ? col.amountCollected() : 0L;
            boolean discovered = collected > 0 || mastery.harvestCount() > 0;

            ItemStack item = new ItemStack(discovered ? crop.icon() : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text((discovered ? "✦ " : "🔒 ") + crop.displayName() + " [" + crop.botanicalCategory() + "]",
                                discovered ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Identity: ", NamedTextColor.GRAY)
                        .append(Component.text(crop.masteryIdentity(), NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Lifetime Collection: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%,d", collected), NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Mastery Tier: ", NamedTextColor.GRAY)
                        .append(Component.text("Tier " + mastery.tier() + " / " + CropMastery.MAX_TIER + " (" + mastery.getCurrentTierTitle() + ")", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, false));
                List<String> varieties = mastery.getUnlockedDiscoveries();
                lore.add(Component.text("Discovered Strains: ", NamedTextColor.GRAY)
                        .append(Component.text(varieties.isEmpty() ? "None yet (Tier III+)" : String.join(", ", varieties),
                                varieties.isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.LIGHT_PURPLE))
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(CROP_ARCHIVE_SLOTS[i], item);
        }

        // Botanical Achievements
        BotanicalAchievement[] achs = BotanicalAchievement.values();
        for (int i = 0; i < achs.length && i < ACHIEVEMENT_SLOTS.length; i++) {
            BotanicalAchievement ach = achs[i];
            boolean unlocked = entry.hasAchievement(ach);
            ItemStack item = new ItemStack(unlocked ? Material.NETHER_STAR : Material.COAL);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text((unlocked ? "[✓] " : "[LOCKED] ") + ach.title(),
                                unlocked ? NamedTextColor.GOLD : NamedTextColor.GRAY, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text(ach.description(), unlocked ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text(unlocked ? "Status: UNLOCKED" : "Status: LOCKED",
                                        unlocked ? NamedTextColor.GREEN : NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(ACHIEVEMENT_SLOTS[i], item);
        }

        // Navigation Row
        inv.setItem(SLOT_NAV_MASTERY, navButton(Material.WHEAT, "✦ Crop Mastery", "View 10 crops & 7 mastery tiers"));
        inv.setItem(SLOT_NAV_ABILITIES, navButton(Material.CHERRY_SAPLING, "✦ Garden Abilities", "View ability scaling & cooldowns"));
        inv.setItem(SLOT_CLOSE, navButton(Material.BARRIER, "[X] Close Menu", "Close Botanical Journal"));
        inv.setItem(SLOT_NAV_PLOTS, navButton(Material.GRASS_BLOCK, "✦ Garden Plots", "Print plot status in chat"));

        player.openInventory(inv);
    }

    public static ItemStack navButton(@NotNull Material mat, @NotNull String title, @NotNull String desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
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

