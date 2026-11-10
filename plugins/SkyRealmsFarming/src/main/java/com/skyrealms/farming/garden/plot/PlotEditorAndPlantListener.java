package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.ability.GardenHarvestAndAbilityListener;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.farming.repository.FarmingRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles manual planting & Sneak+Right-Click row planting in farmland/soil,
 * Plot Edit Mode full-plot corner selections, and Plot Studio GUI clicks.
 */
public final class PlotEditorAndPlantListener implements Listener {

    private final GardenService gardenService;
    private final GardenPlotCustomizationService customizationService;
    private final PlotEditorService editorService;
    private final McMMOFarmingService mcMmoService;
    private final FarmingRepository repository;

    public PlotEditorAndPlantListener(
            @NotNull GardenService gardenService,
            @NotNull GardenPlotCustomizationService customizationService,
            @NotNull PlotEditorService editorService,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull FarmingRepository repository
    ) {
        this.gardenService = java.util.Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.customizationService = java.util.Objects.requireNonNull(customizationService, "customizationService cannot be null");
        this.editorService = java.util.Objects.requireNonNull(editorService, "editorService cannot be null");
        this.mcMmoService = java.util.Objects.requireNonNull(mcMmoService, "mcMmoService cannot be null");
        this.repository = java.util.Objects.requireNonNull(repository, "repository cannot be null");
    }

    @Nullable
    public static CropType fromPlantingMaterial(@NotNull Material material) {
        CropType direct = CropType.fromBlockMaterial(material);
        if (direct != null) return direct;
        return switch (material) {
            case MELON_STEM, ATTACHED_MELON_STEM -> CropType.MELON;
            case PUMPKIN_STEM, ATTACHED_PUMPKIN_STEM -> CropType.PUMPKIN;
            default -> null;
        };
    }

    /**
     * Allows manual vanilla seed planting anywhere inside an unlocked plot's 62x62 interior,
     * while still enforcing Specialized CropBed target crop rules when planting on a Specialized bed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropPlant(@NotNull BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!gardenService.isGardenWorld(block.getWorld())) return;

        CropType plantedCrop = fromPlantingMaterial(block.getType());
        if (plantedCrop == null) return;

        Optional<Garden> optGarden = gardenService.getGardenAt(block.getLocation());
        Optional<GardenPlot> optPlot = gardenService.getPlotAt(block.getLocation());
        if (optGarden.isEmpty() || optPlot.isEmpty()) return;

        Garden garden = optGarden.get();
        GardenPlot plot = optPlot.get();
        Player player = event.getPlayer();
        if (!plot.unlocked()) {
            event.setCancelled(true);
            return;
        }

        PlotConfiguration cfg = customizationService.getPlotConfiguration(garden.islandId(), plot.plotIndex());
        int lx = block.getX() - plot.getCenterX(garden.centerX());
        int lz = block.getZ() - plot.getCenterZ(garden.centerZ());

        Optional<CropBed> optBed = cfg.findBedAtLocal(lx, lz);
        if (optBed.isPresent()) {
            CropBed bed = optBed.get();
            if (bed.bedType() == BedType.SPECIALIZED && bed.targetCrop() != null && bed.targetCrop() != plantedCrop) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("⛔ This Specialized Bed only accepts "
                        + bed.targetCrop().displayName() + "! (/garden plot)", NamedTextColor.RED));
                player.playSound(block.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.1f);
            }
        }
    }

    /**
     * Quick Farmland/Soil Row Planter: Sneak + Right-Click soil inside an unlocked plot while holding seeds
     * (and NOT holding a Hoe / NOT in Edit Mode) to batch-plant up to 64 adjacent empty spots.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSneakPlantInFarmland(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || editorService.isEditing(player.getUniqueId())) return;
        if (!gardenService.isGardenWorld(player.getWorld())) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (GardenHarvestAndAbilityListener.isHoe(held.getType())) return;

        CropType crop = CropType.fromSeedItemMaterial(held.getType());
        if (crop == null) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Optional<Garden> optGarden = gardenService.getGardenAt(clicked.getLocation());
        Optional<GardenPlot> optPlot = gardenService.getPlotAt(clicked.getLocation());
        if (optGarden.isEmpty() || optPlot.isEmpty()) return;

        Garden garden = optGarden.get();
        GardenPlot plot = optPlot.get();
        if (!plot.unlocked() || !editorService.hasEditPermission(player, garden.islandId())) return;

        int lx = clicked.getX() - plot.getCenterX(garden.centerX());
        int lz = clicked.getZ() - plot.getCenterZ(garden.centerZ());
        if (lx < CropBed.INTERIOR_MIN || lx > CropBed.INTERIOR_MAX || lz < CropBed.INTERIOR_MIN || lz > CropBed.INTERIOR_MAX) {
            return;
        }

        PlotConfiguration cfg = customizationService.getPlotConfiguration(garden.islandId(), plot.plotIndex());
        int planted = PlotSeedPlanter.plantQuickAreaFromClickedSoil(player, garden, plot, cfg, clicked, crop);
        if (planted > 0) {
            event.setCancelled(true);
            player.playSound(clicked.getLocation(), Sound.ITEM_CROP_PLANT, 0.8f, 1.2f);
            player.sendActionBar(Component.text("✦ Planted " + planted + "x " + crop.displayName() + " into farmland!", NamedTextColor.GREEN));
        }
    }

    /**
     * Handles Plot Edit Mode corner selections and bed creation across the full 62x62 interior.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEditorInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        PlotEditorService.PlotEditSession session = editorService.getSession(player.getUniqueId());
        if (session == null) return;

        if (!gardenService.isGardenWorld(player.getWorld())) {
            editorService.exitEditMode(player, true);
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isEditorSelectionItem(held.getType())) {
            // Allow normal mining, block placing, seed planting, and hoe abilities while Edit Mode is active
            return;
        }

        Action action = event.getAction();
        if (player.isSneaking() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            editorService.tryCreateBedFromSelection(player);
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        Optional<Garden> optGarden = gardenService.getGardenAt(clicked.getLocation());
        Optional<GardenPlot> optPlot = gardenService.getPlotAt(clicked.getLocation());
        if (optGarden.isEmpty() || optPlot.isEmpty()
                || !optGarden.get().islandId().equals(session.islandId())
                || optPlot.get().plotIndex() != session.plotIndex()) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("⛔ You cannot edit outside Plot #" + session.plotIndex() + "!", NamedTextColor.RED));
            return;
        }

        if (!editorService.hasEditPermission(player, session.islandId())) {
            event.setCancelled(true);
            editorService.exitEditMode(player, true);
            return;
        }

        Garden garden = optGarden.get();
        GardenPlot plot = optPlot.get();
        int lx = clicked.getX() - plot.getCenterX(garden.centerX());
        int lz = clicked.getZ() - plot.getCenterZ(garden.centerZ());
        if (lx < CropBed.INTERIOR_MIN || lx > CropBed.INTERIOR_MAX || lz < CropBed.INTERIOR_MIN || lz > CropBed.INTERIOR_MAX) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("⛔ Cannot modify plot perimeter walls or Torii gate.", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_BLOCK) {
            session.setCorner1(lx, lz);
            editorService.showCornerParticle(player, garden, plot, lx, lz, true);
            player.sendActionBar(Component.text("✎ Corner 1 set (" + lx + ", " + lz + ") | /garden plot edit to exit", NamedTextColor.AQUA));
        } else {
            session.setCorner2(lx, lz);
            editorService.showCornerParticle(player, garden, plot, lx, lz, false);
            player.sendActionBar(Component.text("✎ Corner 2 set (" + lx + ", " + lz + ") — Sneak+Right-Click to create bed | /garden plot edit to exit", NamedTextColor.GREEN));
        }
    }

    public static boolean isEditorSelectionItem(@NotNull Material type) {
        return type == Material.AIR || type == Material.STICK || type == Material.BLAZE_ROD;
    }

    /**
     * Handles Plot Studio & Layout Confirmation GUI clicks.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlotGuiClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        int rawSlot = event.getRawSlot();

        if (holder instanceof GardenPlotGui.PlotMenuHolder pm) {
            event.setCancelled(true);
            if (rawSlot < 0 || rawSlot >= 54 || event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            handlePlotMenuClick(player, pm, rawSlot, event.getClick());
        } else if (holder instanceof GardenPlotGui.LayoutConfirmHolder lc) {
            event.setCancelled(true);
            if (rawSlot == GardenPlotGui.SLOT_CONFIRM_NO) {
                player.closeInventory();
                openMenu(player, lc.islandId(), lc.plotIndex());
                return;
            }
            if (rawSlot == GardenPlotGui.SLOT_CONFIRM_YES) {
                applyLayoutConfirmed(player, lc);
            }
        }
    }

    private void handlePlotMenuClick(@NotNull Player player, @NotNull GardenPlotGui.PlotMenuHolder pm, int rawSlot, @NotNull ClickType click) {
        if (rawSlot == GardenPlotGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        Optional<Garden> optGarden = gardenService.getGarden(pm.islandId());
        if (optGarden.isEmpty()) return;
        Garden garden = optGarden.get();
        GardenPlot plot = findPlot(garden.islandId(), pm.plotIndex());
        if (plot == null) return;

        if (!editorService.hasEditPermission(player, garden.islandId())) {
            player.sendMessage(Component.text("⛔ You lack co-op build permission to modify this plot.", NamedTextColor.RED));
            return;
        }

        // 10 Crop-Specific Optimal Layouts
        List<PlotLayoutPreset> cropPresets = PlotLayoutPreset.cropPresets();
        for (int i = 0; i < GardenPlotGui.CROP_PRESET_SLOTS.length && i < cropPresets.size(); i++) {
            if (rawSlot == GardenPlotGui.CROP_PRESET_SLOTS[i]) {
                tryApplyPreset(player, garden, plot, cropPresets.get(i));
                return;
            }
        }

        if (rawSlot == GardenPlotGui.SLOT_CLEAN_CANVAS) {
            tryApplyPreset(player, garden, plot, PlotLayoutPreset.CLEAN_CANVAS);
            return;
        }

        if (rawSlot == GardenPlotGui.SLOT_CUSTOM_LAYOUT) {
            if (click.isRightClick()) {
                GardenPlotCustomizationService.ActionResult res = customizationService.saveCustomLayout(garden.islandId(), plot.plotIndex());
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
                player.sendMessage(Component.text((res.success() ? "✦ " : "✖ ") + res.message(),
                        res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                openMenu(player, garden.islandId(), plot.plotIndex());
            } else {
                GardenPlotCustomizationService.ActionResult res = customizationService.loadCustomLayout(
                        player.getWorld(), garden, plot, false, player);
                if (res.requiresConfirmation()) {
                    PlotLayoutEngine.DestructiveScanResult scan = PlotLayoutEngine.scanDestructiveConflicts(player.getWorld(), garden, plot);
                    GardenPlotGui.openConfirmLayout(player, garden.islandId(), plot.plotIndex(), null, true,
                            scan.existingCrops(), scan.existingContainers());
                } else {
                    player.closeInventory();
                    player.sendMessage(Component.text((res.success() ? "✦ " : "✖ ") + res.message(),
                            res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                }
            }
            return;
        }

        PlotConfiguration cfg = customizationService.getPlotConfiguration(garden.islandId(), plot.plotIndex());

        if (rawSlot == GardenPlotGui.SLOT_IRRIGATION) {
            IrrigationType[] vals = IrrigationType.values();
            IrrigationType next = vals[(cfg.irrigationType().ordinal() + 1) % vals.length];
            customizationService.setIrrigationAndDecoration(player.getWorld(), garden, plot, next, null, player);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            return;
        }

        if (rawSlot == GardenPlotGui.SLOT_DECORATION) {
            DecorationStyle[] vals = DecorationStyle.values();
            DecorationStyle next = vals[(cfg.decorationStyle().ordinal() + 1) % vals.length];
            customizationService.setIrrigationAndDecoration(player.getWorld(), garden, plot, null, next, player);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            return;
        }

        // Bed slots: Left = cycle BedType, Right = cycle TargetCrop, Shift+Right = remove bed
        List<CropBed> beds = cfg.beds();
        for (int i = 0; i < GardenPlotGui.BED_SLOTS.length && i < beds.size(); i++) {
            if (rawSlot == GardenPlotGui.BED_SLOTS[i]) {
                CropBed bed = beds.get(i);
                if (click == ClickType.SHIFT_RIGHT) {
                    GardenPlotCustomizationService.ActionResult res = customizationService.removeCropBed(garden, plot, bed.bedId(), player.getWorld());
                    player.sendMessage(Component.text(res.message(), res.success() ? NamedTextColor.YELLOW : NamedTextColor.RED));
                } else if (click.isRightClick()) {
                    CropType[] crops = CropType.values();
                    CropType nextCrop;
                    if (bed.targetCrop() == null) {
                        nextCrop = crops[0];
                    } else if (bed.targetCrop().ordinal() == crops.length - 1) {
                        nextCrop = null;
                    } else {
                        nextCrop = crops[bed.targetCrop().ordinal() + 1];
                    }
                    GardenPlotCustomizationService.ActionResult res = customizationService.updateBed(
                            garden.islandId(), plot.plotIndex(), bed.bedId(), bed.bedType(), nextCrop,
                            mcMmoService.getFarmingLevel(player), player.getUniqueId());
                    player.sendMessage(Component.text(res.message(), res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                } else {
                    BedType[] types = BedType.values();
                    BedType nextType = types[(bed.bedType().ordinal() + 1) % types.length];
                    GardenPlotCustomizationService.ActionResult res = customizationService.updateBed(
                            garden.islandId(), plot.plotIndex(), bed.bedId(), nextType, bed.targetCrop(),
                            mcMmoService.getFarmingLevel(player), player.getUniqueId());
                    player.sendMessage(Component.text(res.message(), res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                }
                openMenu(player, garden.islandId(), plot.plotIndex());
                return;
            }
        }

        if (rawSlot == GardenPlotGui.SLOT_TOGGLE_EDIT) {
            player.closeInventory();
            if (editorService.isEditing(player.getUniqueId())) {
                editorService.exitEditMode(player, true);
            } else {
                editorService.enterEditMode(player, garden, plot);
            }
        } else if (rawSlot == GardenPlotGui.SLOT_EDIT_BED_TYPE) {
            editorService.cyclePreferredBedType(player.getUniqueId());
            openMenu(player, garden.islandId(), plot.plotIndex());
        } else if (rawSlot == GardenPlotGui.SLOT_UPGRADE_PLOT) {
            customizationService.upgradePlot(player, garden.islandId(), plot.plotIndex()).thenAccept(res -> {
                if (!res.success()) {
                    player.sendMessage(Component.text("✖ " + res.message(), NamedTextColor.RED));
                } else {
                    Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SkyRealmsFarming"),
                            () -> openMenu(player, garden.islandId(), plot.plotIndex()));
                }
            });
        } else if (rawSlot == GardenPlotGui.SLOT_PREVIEW) {
            player.closeInventory();
            PlotLayoutEngine.previewLayout(player, garden, plot, cfg);
            player.sendMessage(Component.text("✦ Previewing Plot #" + plot.plotIndex() + " crop beds & 4x4 interior.", NamedTextColor.AQUA));
        } else if (rawSlot == GardenPlotGui.SLOT_PLANT_SEEDS) {
            player.closeInventory();
            GardenPlotCustomizationService.ActionResult res = customizationService.plantSeedsInPlot(
                    player, player.getWorld(), garden, plot, null);
            player.sendMessage(Component.text((res.success() ? "✦ " : "✖ ") + res.message(),
                    res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
    }

    private void tryApplyPreset(@NotNull Player player, @NotNull Garden garden, @NotNull GardenPlot plot, @NotNull PlotLayoutPreset preset) {
        GardenPlotCustomizationService.ActionResult res = customizationService.applyPresetLayout(player.getWorld(), garden, plot, preset, false, player);
        if (res.requiresConfirmation()) {
            PlotLayoutEngine.DestructiveScanResult scan = PlotLayoutEngine.scanDestructiveConflicts(player.getWorld(), garden, plot);
            GardenPlotGui.openConfirmLayout(player, garden.islandId(), plot.plotIndex(), preset, false, scan.existingCrops(), scan.existingContainers());
        } else if (res.success()) {
            player.closeInventory();
            player.sendMessage(Component.text("✦ " + res.message(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("✖ " + res.message(), NamedTextColor.RED));
        }
    }

    private void applyLayoutConfirmed(@NotNull Player player, @NotNull GardenPlotGui.LayoutConfirmHolder lc) {
        Optional<Garden> optGarden = gardenService.getGarden(lc.islandId());
        if (optGarden.isEmpty()) return;
        Garden garden = optGarden.get();
        GardenPlot plot = findPlot(garden.islandId(), lc.plotIndex());
        if (plot == null || !editorService.hasEditPermission(player, garden.islandId())) return;

        GardenPlotCustomizationService.ActionResult res = lc.isCustomSlot()
                ? customizationService.loadCustomLayout(player.getWorld(), garden, plot, true, player)
                : customizationService.applyPresetLayout(player.getWorld(), garden, plot, lc.preset(), true, player);

        player.closeInventory();
        if (res.success()) {
            player.sendMessage(Component.text("✦ " + res.message(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("✖ " + res.message(), NamedTextColor.RED));
        }
    }

    private void openMenu(@NotNull Player player, @NotNull UUID islandId, int plotIndex) {
        GardenPlotGui.openPlotMenu(player, islandId, plotIndex, customizationService, editorService, mcMmoService);
    }

    @Nullable
    private GardenPlot findPlot(@NotNull UUID islandId, int plotIndex) {
        for (GardenPlot p : gardenService.getPlots(islandId)) {
            if (p.plotIndex() == plotIndex) return p;
        }
        return null;
    }

    @EventHandler
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        if (!gardenService.isGardenWorld(event.getPlayer().getWorld())) {
            editorService.exitEditMode(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        editorService.clearSession(event.getPlayer().getUniqueId());
    }
}
