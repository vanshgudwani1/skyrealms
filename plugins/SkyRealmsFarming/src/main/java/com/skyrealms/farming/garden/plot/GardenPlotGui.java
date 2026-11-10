package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 54-slot interactive Plot Studio GUI:
 * - 10 Crop-Specific Optimal Full-Plot Layouts
 * - Clean Canvas Reset
 * - Single Custom Plot Layout slot (Left-Click load / Right-Click save)
 * - Animated Farmland Seed Planter button
 * - Irrigation, Decoration, Crop Bed Editor & Upgrades
 */
public final class GardenPlotGui {

    // 10 Crop-Optimal Presets across Row 1 & Row 2
    public static final int[] CROP_PRESET_SLOTS = { 10, 11, 12, 13, 14, 19, 20, 21, 22, 23 };
    public static final int SLOT_CUSTOM_LAYOUT = 16;
    public static final int SLOT_CLEAN_CANVAS = 25;

    // Configured Crop Beds (14 slots)
    public static final int[] BED_SLOTS = { 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43 };

    // Bottom Controls (Row 5)
    public static final int SLOT_TOGGLE_EDIT = 45;
    public static final int SLOT_EDIT_BED_TYPE = 46;
    public static final int SLOT_UPGRADE_PLOT = 47;
    public static final int SLOT_IRRIGATION = 48;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_DECORATION = 50;
    public static final int SLOT_PREVIEW = 51;
    public static final int SLOT_PLANT_SEEDS = 52;

    public static final int SLOT_CONFIRM_YES = 29;
    public static final int SLOT_CONFIRM_NO = 33;

    public static final class PlotMenuHolder implements InventoryHolder {
        private final UUID islandId;
        private final int plotIndex;
        private Inventory inventory;

        public PlotMenuHolder(@NotNull UUID islandId, int plotIndex) {
            this.islandId = islandId;
            this.plotIndex = plotIndex;
        }

        public UUID islandId() { return islandId; }
        public int plotIndex() { return plotIndex; }
        @Override @NotNull public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    public static final class LayoutConfirmHolder implements InventoryHolder {
        private final UUID islandId;
        private final int plotIndex;
        private final PlotLayoutPreset preset;
        private final boolean customSlot;
        private Inventory inventory;

        public LayoutConfirmHolder(
                @NotNull UUID islandId,
                int plotIndex,
                @Nullable PlotLayoutPreset preset,
                boolean customSlot
        ) {
            this.islandId = islandId;
            this.plotIndex = plotIndex;
            this.preset = preset;
            this.customSlot = customSlot;
        }

        public UUID islandId() { return islandId; }
        public int plotIndex() { return plotIndex; }
        @Nullable public PlotLayoutPreset preset() { return preset; }
        public boolean isCustomSlot() { return customSlot; }
        @Override @NotNull public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    private GardenPlotGui() {}

    public static void openPlotMenu(
            @NotNull Player player,
            @NotNull UUID islandId,
            int plotIndex,
            @NotNull GardenPlotCustomizationService customizationService,
            @NotNull PlotEditorService editorService,
            @NotNull McMMOFarmingService mcMmoService
    ) {
        PlotMenuHolder holder = new PlotMenuHolder(islandId, plotIndex);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Garden Plot #" + plotIndex + " Studio", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        holder.setInventory(inv);

        ItemStack border = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack accent = pane(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, accent);
            inv.setItem(45 + col, accent);
        }

        PlotConfiguration cfg = customizationService.getPlotConfiguration(islandId, plotIndex);
        PlotUpgradeTier tier = PlotUpgradeTier.forLevel(cfg.upgradeLevel());
        PlotUpgradeTier nextTier = tier.nextTier();
        int farmingLevel = mcMmoService.getFarmingLevel(player);

        // Slot 4: Plot Summary
        inv.setItem(4, icon(Material.FARMLAND, NamedTextColor.GOLD, "✦ Plot #" + plotIndex + " Overview ✦", List.of(
                Component.text("Upgrade Tier: ", NamedTextColor.GRAY)
                        .append(Component.text("Lv." + tier.level() + " (" + tier.title() + ")", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false),
                Component.text("Editable Area: ", NamedTextColor.GRAY)
                        .append(Component.text("Full 4x4 Chunks (62x62 interior)", NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false),
                Component.text("Active Layout: ", NamedTextColor.GRAY)
                        .append(Component.text(cfg.selectedLayout().displayName(), NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false),
                Component.text("Crop Beds: ", NamedTextColor.GRAY)
                        .append(Component.text(cfg.beds().size() + " / " + cfg.maxBedsAllowed(), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false)
        )));

        // 10 Best Crop Layouts
        List<PlotLayoutPreset> cropPresets = PlotLayoutPreset.cropPresets();
        for (int i = 0; i < cropPresets.size() && i < CROP_PRESET_SLOTS.length; i++) {
            PlotLayoutPreset p = cropPresets.get(i);
            boolean active = cfg.selectedLayout() == p;
            inv.setItem(CROP_PRESET_SLOTS[i], icon(p.icon(), active ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                    (active ? "[ACTIVE] " : "") + p.displayName(), List.of(
                            Component.text(p.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("▶ Click to animate across full 4x4 plot", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                    )));
        }

        // Single Custom Plot Layout Slot
        Optional<SavedPlotLayout> customOpt = customizationService.getCustomLayout(islandId);
        List<Component> customLore = customOpt.map(saved -> List.<Component>of(
                Component.text("Base Preset: " + saved.presetBase().displayName(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Saved Beds: " + SavedPlotLayout.deserializeBeds(saved.bedsSerialized()).size(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Left-Click: Animate & load Custom Layout", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-Click: Overwrite Custom Layout slot", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        )).orElseGet(() -> List.<Component>of(
                Component.text("1 Custom Layout slot saved per Garden.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Currently empty.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Right-Click: Save current plot as Custom Layout", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        inv.setItem(SLOT_CUSTOM_LAYOUT, icon(Material.CHEST_MINECART, NamedTextColor.AQUA,
                customOpt.isPresent() ? "Custom Plot Layout [Saved]" : "Custom Plot Layout [Empty]", customLore));

        // Clean Canvas Slot
        boolean cleanActive = cfg.selectedLayout() == PlotLayoutPreset.CLEAN_CANVAS;
        inv.setItem(SLOT_CLEAN_CANVAS, icon(Material.GRASS_BLOCK, cleanActive ? NamedTextColor.GREEN : NamedTextColor.RED,
                (cleanActive ? "[ACTIVE] " : "") + "Clean Canvas (Reset Plot)", List.of(
                        Component.text("Animated reset to open lawn + starter bed", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Prompts before removing crops or containers", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                )));

        // Crop Beds
        List<CropBed> beds = cfg.beds();
        for (int i = 0; i < beds.size() && i < BED_SLOTS.length; i++) {
            CropBed bed = beds.get(i);
            Material mat = bed.targetCrop() != null ? bed.targetCrop().icon() : Material.PODZOL;
            if (bed.bedType() == BedType.BASIC && bed.targetCrop() == null) mat = Material.DIRT;
            if (bed.bedType() == BedType.SPECIALIZED && bed.targetCrop() == null) mat = Material.MYCELIUM;

            inv.setItem(BED_SLOTS[i], icon(mat, NamedTextColor.GREEN,
                    "Crop Bed #" + (i + 1) + " [" + bed.bedType().displayName() + "]", List.of(
                            Component.text("Size: ", NamedTextColor.GRAY)
                                    .append(Component.text(bed.width() + "x" + bed.depth() + " (" + bed.area() + " blocks)", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false),
                            Component.text("Assigned Crop: ", NamedTextColor.GRAY)
                                    .append(Component.text(bed.targetCrop() != null ? bed.targetCrop().displayName() : "Any Crop", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false),
                            Component.text(String.format("Bonus: +%.0f%% Drops | +%.0f%% XP",
                                    bed.bedType().bonusDropModifier() * 100.0, bed.bedType().xpMultiplierModifier() * 100.0), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("Left-Click: Cycle Bed Type", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                            Component.text("Right-Click: Cycle Assigned Crop", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                            Component.text("Shift+Right-Click: Remove Bed", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                    )));
        }

        // Bottom Controls
        boolean editing = editorService.isEditing(player.getUniqueId());
        inv.setItem(SLOT_TOGGLE_EDIT, icon(editing ? Material.LIME_DYE : Material.WOODEN_HOE,
                editing ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                editing ? "Plot Edit Mode: ON" : "Plot Edit Mode: OFF", List.of(
                        Component.text("Click to " + (editing ? "exit" : "enter") + " Full-Plot Edit Mode", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Empty Hand / Stick selects corners inside 62x62 interior", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                        Component.text("Blocks & mining tools always work normally", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                )));

        BedType editBedType = editorService.getPreferredBedType(player.getUniqueId());
        inv.setItem(SLOT_EDIT_BED_TYPE, icon(Material.COARSE_DIRT, NamedTextColor.GOLD,
                "Editor Bed Type: " + editBedType.displayName(), List.of(
                        Component.text(editBedType.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("▶ Click to cycle new bed type", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                )));

        if (nextTier != null) {
            boolean levelOk = farmingLevel >= nextTier.requiredFarmingLevel();
            inv.setItem(SLOT_UPGRADE_PLOT, icon(Material.EXPERIENCE_BOTTLE, levelOk ? NamedTextColor.GREEN : NamedTextColor.RED,
                    "Upgrade Plot -> Tier " + nextTier.level(), List.of(
                            Component.text(nextTier.title(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                            Component.text(nextTier.summary(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("Farming Lv.: " + farmingLevel + " / " + nextTier.requiredFarmingLevel(),
                                    levelOk ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                            Component.text(String.format("Cost: %,.0f Coins", nextTier.coinCost()), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                    )));
        } else {
            inv.setItem(SLOT_UPGRADE_PLOT, icon(Material.NETHER_STAR, NamedTextColor.GOLD,
                    "Plot Max Tier Reached", List.of(Component.text(tier.summary(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))));
        }

        inv.setItem(SLOT_IRRIGATION, icon(cfg.irrigationType().trimMaterial(), NamedTextColor.AQUA,
                "Irrigation: " + cfg.irrigationType().displayName(), List.of(
                        Component.text(cfg.irrigationType().description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("▶ Click to cycle & animate", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                )));

        inv.setItem(SLOT_CLOSE, icon(Material.BARRIER, NamedTextColor.RED, "[X] Close Menu", List.of()));

        inv.setItem(SLOT_DECORATION, icon(cfg.decorationStyle().iconMaterial(), NamedTextColor.LIGHT_PURPLE,
                "Decoration: " + cfg.decorationStyle().displayName(), List.of(
                        Component.text(cfg.decorationStyle().description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("▶ Click to cycle & animate", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                )));

        inv.setItem(SLOT_PREVIEW, icon(Material.ENDER_EYE, NamedTextColor.LIGHT_PURPLE,
                "Preview Plot Borders & Beds", List.of(Component.text("Shows particle outline of beds & 4x4 interior", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

        inv.setItem(SLOT_PLANT_SEEDS, icon(Material.WHEAT_SEEDS, NamedTextColor.GREEN,
                "Plant Seeds in Farmland", List.of(
                        Component.text("Animates planting seeds from your inventory", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("onto all valid empty farmland/soil in Plot #" + plotIndex, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Tip: Also Sneak+Right-Click soil with seeds!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                )));

        player.openInventory(inv);
    }

    public static void openConfirmLayout(
            @NotNull Player player,
            @NotNull UUID islandId,
            int plotIndex,
            @Nullable PlotLayoutPreset preset,
            boolean customSlot,
            int existingCrops,
            int existingContainers
    ) {
        LayoutConfirmHolder holder = new LayoutConfirmHolder(islandId, plotIndex, preset, customSlot);
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text("Confirm Destructive Layout Change", NamedTextColor.RED, TextDecoration.BOLD));
        holder.setInventory(inv);

        ItemStack pane = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, pane);

        String targetName = customSlot ? "Custom Plot Layout" : (preset != null ? preset.displayName() : "Layout");
        inv.setItem(13, icon(Material.WRITABLE_BOOK, NamedTextColor.GOLD, "Warning: Existing Plot Contents", List.of(
                Component.text("Applying: " + targetName, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Detected " + existingCrops + " crops and " + existingContainers + " containers.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Confirming will animate and replace the plot interior.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        )));

        inv.setItem(SLOT_CONFIRM_YES, icon(Material.LIME_WOOL, NamedTextColor.GREEN, "[✓] Confirm Layout Animation",
                List.of(Component.text("Apply " + targetName + " to Plot #" + plotIndex, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        inv.setItem(SLOT_CONFIRM_NO, icon(Material.RED_WOOL, NamedTextColor.RED, "[X] Cancel",
                List.of(Component.text("Return without modifying plot", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

        player.openInventory(inv);
    }

    private static ItemStack icon(Material mat, NamedTextColor color, String name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
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
