package com.skyrealms.farming.garden.plot;

import com.skyrealms.economy.model.TransactionResult;
import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropMasteryService;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.farming.repository.FarmingRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Authoritative service managing customizable plot configurations, crop beds,
 * layouts, irrigation, decorations, upgrades, and saved layouts with async persistence.
 */
public final class GardenPlotCustomizationService {

    public static final String CUSTOM_LAYOUT_NAME = "CUSTOM";

    public record PlotKey(UUID islandId, int plotIndex) {}

    public record ActionResult(boolean success, @NotNull String message, boolean requiresConfirmation) {
        public static ActionResult ok(@NotNull String msg) { return new ActionResult(true, msg, false); }
        public static ActionResult fail(@NotNull String msg) { return new ActionResult(false, msg, false); }
        public static ActionResult confirm(@NotNull String msg) { return new ActionResult(false, msg, true); }
    }

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final GardenService gardenService;
    private final EconomyService economyService;
    private final McMMOFarmingService mcMmoService;
    private final CropMasteryService masteryService;
    private final Logger logger;

    private final Map<PlotKey, PlotConfiguration> configCache = new ConcurrentHashMap<>();
    private final Set<PlotKey> activeAnimations = ConcurrentHashMap.newKeySet();

    public boolean isAnimating(@NotNull UUID islandId, int plotIndex) {
        return activeAnimations.contains(new PlotKey(islandId, plotIndex));
    }

    public GardenPlotCustomizationService(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull GardenService gardenService,
            @NotNull EconomyService economyService,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull CropMasteryService masteryService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.mcMmoService = Objects.requireNonNull(mcMmoService, "mcMmoService cannot be null");
        this.masteryService = Objects.requireNonNull(masteryService, "masteryService cannot be null");
        this.logger = plugin.getLogger();
    }

    /**
     * Loads or retrieves cached PlotConfiguration. Ensures clean starter bed exists for CLEAN_CANVAS on first load.
     */
    @NotNull
    public PlotConfiguration getPlotConfiguration(@NotNull UUID islandId, int plotIndex) {
        PlotKey key = new PlotKey(islandId, plotIndex);
        PlotConfiguration cached = configCache.get(key);
        if (cached != null) return cached;

        try {
            Optional<PlotConfiguration> opt = repository.findPlotConfiguration(islandId, plotIndex);
            if (opt.isPresent()) {
                PlotConfiguration cfg = opt.get();
                // Ensure newly unlocked CLEAN_CANVAS plots have the starter 9x9 Basic Bed registered
                if (cfg.beds().isEmpty() && cfg.selectedLayout() == PlotLayoutPreset.CLEAN_CANVAS) {
                    PlotLayoutEngine.GeneratedLayout starter = PlotLayoutEngine.generatePresetDefinition(
                            islandId, plotIndex, PlotLayoutPreset.CLEAN_CANVAS, cfg.expandedRadius(), cfg.maxBedsAllowed()
                    );
                    cfg.replaceAllBeds(starter.beds());
                    saveAsync(cfg);
                }
                configCache.put(key, cfg);
                return cfg;
            }
        } catch (SQLException e) {
            logger.warning("Failed to load PlotConfiguration for " + islandId + " #" + plotIndex + ": " + e.getMessage());
        }

        PlotConfiguration def = PlotConfiguration.defaultFor(islandId, plotIndex);
        PlotLayoutEngine.GeneratedLayout starter = PlotLayoutEngine.generatePresetDefinition(
                islandId, plotIndex, PlotLayoutPreset.CLEAN_CANVAS, def.expandedRadius(), def.maxBedsAllowed()
        );
        def.replaceAllBeds(starter.beds());
        configCache.put(key, def);
        return def;
    }

    @NotNull
    public Optional<CropBed> getBedAtWorldCoords(@NotNull Garden garden, @NotNull GardenPlot plot, int worldX, int worldZ) {
        PlotConfiguration cfg = getPlotConfiguration(garden.islandId(), plot.plotIndex());
        int lx = worldX - plot.getCenterX(garden.centerX());
        int lz = worldZ - plot.getCenterZ(garden.centerZ());
        return cfg.findBedAtLocal(lx, lz);
    }

    @NotNull
    public ActionResult createCropBed(
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull BedType bedType,
            @Nullable CropType targetCrop,
            int minLx, int minLz, int maxLx, int maxLz,
            int farmingLevel,
            @Nullable World world
    ) {
        if (!plot.unlocked()) {
            return ActionResult.fail("Plot #" + plot.plotIndex() + " is locked.");
        }

        PlotConfiguration cfg = getPlotConfiguration(garden.islandId(), plot.plotIndex());
        if (cfg.beds().size() >= cfg.maxBedsAllowed()) {
            return ActionResult.fail("Max crop beds reached (" + cfg.maxBedsAllowed() + ") for Plot Upgrade Lv." + cfg.upgradeLevel() + ".");
        }
        if (farmingLevel < bedType.requiredFarmingLevel()) {
            return ActionResult.fail(bedType.displayName() + " requires mcMMO Farming Lv." + bedType.requiredFarmingLevel() + ".");
        }
        if (cfg.upgradeLevel() < bedType.requiredPlotUpgradeLevel()) {
            return ActionResult.fail(bedType.displayName() + " requires Plot Upgrade Lv." + bedType.requiredPlotUpgradeLevel() + ".");
        }

        CropBed candidate;
        try {
            candidate = new CropBed(UUID.randomUUID(), garden.islandId(), plot.plotIndex(), bedType, targetCrop, minLx, minLz, maxLx, maxLz);
        } catch (IllegalArgumentException ex) {
            return ActionResult.fail(ex.getMessage());
        }

        if (!candidate.isWithinRadius(cfg.expandedRadius())) {
            return ActionResult.fail("Bed extends outside plot interior [-31, 30].");
        }
        if (candidate.area() > 62 * 62) {
            return ActionResult.fail("Single crop bed cannot exceed the 62x62 plot interior (3,844 blocks).");
        }
        for (CropBed existing : cfg.beds()) {
            if (existing.overlaps(candidate)) {
                return ActionResult.fail("Bed overlaps an existing Crop Bed.");
            }
        }

        cfg.addBed(candidate);
        saveAsync(cfg);

        if (world != null) {
            int pcx = plot.getCenterX(garden.centerX());
            int pcz = plot.getCenterZ(garden.centerZ());
            int cy = garden.centerY();
            for (int lx = candidate.minLocalX(); lx <= candidate.maxLocalX(); lx++) {
                for (int lz = candidate.minLocalZ(); lz <= candidate.maxLocalZ(); lz++) {
                    world.getBlockAt(pcx + lx, cy - 1, pcz + lz).setType(Material.DIRT, false);
                    world.getBlockAt(pcx + lx, cy, pcz + lz).setType(bedType.baseSoilMaterial(), false);
                }
            }
        }

        return ActionResult.ok("Created " + bedType.displayName() + " (" + candidate.width() + "x" + candidate.depth() + ").");
    }

    @NotNull
    public ActionResult removeCropBed(@NotNull Garden garden, @NotNull GardenPlot plot, @NotNull UUID bedId, @Nullable World world) {
        PlotConfiguration cfg = getPlotConfiguration(garden.islandId(), plot.plotIndex());
        CropBed target = cfg.beds().stream().filter(b -> b.bedId().equals(bedId)).findFirst().orElse(null);
        if (target == null) {
            return ActionResult.fail("Crop bed not found.");
        }

        cfg.removeBed(bedId);
        saveAsync(cfg);

        if (world != null) {
            int pcx = plot.getCenterX(garden.centerX());
            int pcz = plot.getCenterZ(garden.centerZ());
            int cy = garden.centerY();
            for (int lx = target.minLocalX(); lx <= target.maxLocalX(); lx++) {
                for (int lz = target.minLocalZ(); lz <= target.maxLocalZ(); lz++) {
                    if (CropType.fromBlockMaterial(world.getBlockAt(pcx + lx, cy + 1, pcz + lz).getType()) != null) {
                        world.getBlockAt(pcx + lx, cy + 1, pcz + lz).setType(Material.AIR, false);
                    }
                    world.getBlockAt(pcx + lx, cy, pcz + lz).setType(Material.GRASS_BLOCK, false);
                }
            }
        }

        return ActionResult.ok("Removed Crop Bed.");
    }

    @NotNull
    public ActionResult updateBed(@NotNull UUID islandId, int plotIndex, @NotNull UUID bedId, @NotNull BedType newType, @Nullable CropType targetCrop, int farmingLevel, @Nullable UUID playerUuid) {
        PlotConfiguration cfg = getPlotConfiguration(islandId, plotIndex);
        if (farmingLevel < newType.requiredFarmingLevel()) {
            return ActionResult.fail(newType.displayName() + " requires mcMMO Farming Lv." + newType.requiredFarmingLevel() + ".");
        }
        if (cfg.upgradeLevel() < newType.requiredPlotUpgradeLevel()) {
            return ActionResult.fail(newType.displayName() + " requires Plot Upgrade Lv." + newType.requiredPlotUpgradeLevel() + ".");
        }
        if (newType == BedType.SPECIALIZED && targetCrop != null && playerUuid != null) {
            CropMastery mastery = masteryService.getMastery(playerUuid, targetCrop);
            if (mastery.tier() < 2) {
                return ActionResult.fail("Specialized " + targetCrop.displayName() + " Bed requires " + targetCrop.displayName() + " Mastery Tier II.");
            }
        }

        List<CropBed> updated = new ArrayList<>();
        boolean found = false;
        for (CropBed b : cfg.beds()) {
            if (b.bedId().equals(bedId)) {
                updated.add(new CropBed(b.bedId(), islandId, plotIndex, newType, targetCrop,
                        b.minLocalX(), b.minLocalZ(), b.maxLocalX(), b.maxLocalZ()));
                found = true;
            } else {
                updated.add(b);
            }
        }
        if (!found) return ActionResult.fail("Crop bed not found.");

        cfg.replaceAllBeds(updated);
        saveAsync(cfg);
        return ActionResult.ok("Updated bed to " + newType.displayName()
                + (targetCrop != null ? " [" + targetCrop.displayName() + "]" : "") + ".");
    }

    @NotNull
    public ActionResult applyPresetLayout(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull PlotLayoutPreset preset,
            boolean confirmed
    ) {
        return applyPresetLayout(world, garden, plot, preset, confirmed, null);
    }

    @NotNull
    public ActionResult applyPresetLayout(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull PlotLayoutPreset preset,
            boolean confirmed,
            @Nullable Player initiator
    ) {
        if (!plot.unlocked()) {
            return ActionResult.fail("Plot #" + plot.plotIndex() + " is locked.");
        }
        PlotKey key = new PlotKey(garden.islandId(), plot.plotIndex());
        if (!activeAnimations.add(key)) {
            return ActionResult.fail("Plot #" + plot.plotIndex() + " is currently animating a layout change. Please wait a second!");
        }

        try {
            PlotLayoutEngine.DestructiveScanResult scan = PlotLayoutEngine.scanDestructiveConflicts(world, garden, plot);
            if (scan.hasConflicts() && !confirmed) {
                activeAnimations.remove(key);
                return ActionResult.confirm("Plot contains " + scan.existingCrops() + " planted crops and "
                        + scan.existingContainers() + " containers. Confirm to apply " + preset.displayName() + ".");
            }

            PlotConfiguration cfg = getPlotConfiguration(garden.islandId(), plot.plotIndex());
            PlotLayoutEngine.GeneratedLayout generated = PlotLayoutEngine.generatePresetDefinition(
                    garden.islandId(), plot.plotIndex(), preset, cfg.expandedRadius(), cfg.maxBedsAllowed()
            );
            cfg.setSelectedLayout(preset);
            cfg.replaceAllBeds(generated.beds());
            saveAsync(cfg);
            PlotLayoutEngine.startAnimatedBuild(plugin, initiator, world, garden, plot, cfg, activeAnimations, key);

            return ActionResult.ok("Animating layout: " + preset.displayName() + " across full 4x4 chunk Plot #" + plot.plotIndex() + " (" + cfg.beds().size() + " beds).");
        } catch (RuntimeException ex) {
            activeAnimations.remove(key);
            throw ex;
        }
    }

    public void setIrrigationAndDecoration(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @Nullable IrrigationType irrigation,
            @Nullable DecorationStyle decoration
    ) {
        setIrrigationAndDecoration(world, garden, plot, irrigation, decoration, null);
    }

    public void setIrrigationAndDecoration(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @Nullable IrrigationType irrigation,
            @Nullable DecorationStyle decoration,
            @Nullable Player initiator
    ) {
        PlotKey key = new PlotKey(garden.islandId(), plot.plotIndex());
        if (!activeAnimations.add(key)) {
            return;
        }
        try {
            PlotConfiguration cfg = getPlotConfiguration(garden.islandId(), plot.plotIndex());
            if (irrigation != null) cfg.setIrrigationType(irrigation);
            if (decoration != null) cfg.setDecorationStyle(decoration);
            saveAsync(cfg);
            PlotLayoutEngine.startAnimatedBuild(plugin, initiator, world, garden, plot, cfg, activeAnimations, key);
        } catch (RuntimeException ex) {
            activeAnimations.remove(key);
            throw ex;
        }
    }

    @NotNull
    public CompletableFuture<ActionResult> upgradePlot(@NotNull Player player, @NotNull UUID islandId, int plotIndex) {
        PlotConfiguration cfg = getPlotConfiguration(islandId, plotIndex);
        PlotUpgradeTier current = PlotUpgradeTier.forLevel(cfg.upgradeLevel());
        PlotUpgradeTier next = current.nextTier();
        if (next == null) {
            return CompletableFuture.completedFuture(ActionResult.fail("Plot #" + plotIndex + " is already at maximum tier."));
        }

        int farmingLevel = mcMmoService.getFarmingLevel(player);
        if (farmingLevel < next.requiredFarmingLevel()) {
            return CompletableFuture.completedFuture(ActionResult.fail(
                    "Requires mcMMO Farming Lv." + next.requiredFarmingLevel() + " (Current: Lv." + farmingLevel + ")."));
        }

        return economyService.withdraw(
                player.getUniqueId(),
                "coins",
                BigDecimal.valueOf(next.coinCost()),
                "Plot #" + plotIndex + " Upgrade -> Tier " + next.level(),
                "plot-upgrade-" + islandId + "-" + plotIndex + "-t" + next.level() + "-" + UUID.randomUUID()
        ).thenApply(res -> {
            if (res == null || !res.isSuccess()) {
                return ActionResult.fail("Insufficient Coins (" + String.format("%,.0f", next.coinCost()) + " Coins required).");
            }
            cfg.setUpgradeTier(next);
            saveAsync(cfg);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.3f);
                    player.sendMessage(Component.text("✦ Plot #" + plotIndex + " upgraded to ", NamedTextColor.GREEN)
                            .append(Component.text(next.title() + " (Tier " + next.level() + ")", NamedTextColor.GOLD)));
                }
            });
            return ActionResult.ok("Upgraded Plot #" + plotIndex + " to " + next.title() + "!");
        });
    }

    /**
     * Saves the plot as the island's SINGLE Custom Plot Layout slot (replaces any prior custom layouts).
     */
    @NotNull
    public ActionResult saveCustomLayout(@NotNull UUID islandId, int plotIndex) {
        return saveLayout(islandId, plotIndex, CUSTOM_LAYOUT_NAME);
    }

    @NotNull
    public ActionResult saveLayout(@NotNull UUID islandId, int plotIndex, @NotNull String layoutName) {
        PlotConfiguration cfg = getPlotConfiguration(islandId, plotIndex);
        SavedPlotLayout saved = new SavedPlotLayout(
                UUID.randomUUID(),
                islandId,
                CUSTOM_LAYOUT_NAME,
                cfg.selectedLayout(),
                cfg.irrigationType(),
                cfg.decorationStyle(),
                SavedPlotLayout.serializeBeds(cfg.beds()),
                Instant.now()
        );
        try {
            // Enforce single custom layout rule: remove any legacy extra slots so only 1 Custom Layout exists
            for (SavedPlotLayout existing : repository.findSavedLayouts(islandId)) {
                if (!existing.layoutName().equalsIgnoreCase(CUSTOM_LAYOUT_NAME)) {
                    repository.deleteSavedLayout(islandId, existing.layoutName());
                }
            }
            repository.savePlotLayout(saved);
            return ActionResult.ok("Saved your Custom Plot Layout (" + cfg.beds().size() + " beds). Only 1 Custom Layout slot is stored.");
        } catch (SQLException e) {
            logger.warning("Failed to save custom plot layout: " + e.getMessage());
            return ActionResult.fail("Database error saving custom layout.");
        }
    }

    @NotNull
    public Optional<SavedPlotLayout> getCustomLayout(@NotNull UUID islandId) {
        List<SavedPlotLayout> all = getSavedLayouts(islandId);
        return all.stream()
                .filter(l -> l.layoutName().equalsIgnoreCase(CUSTOM_LAYOUT_NAME))
                .findFirst()
                .or(() -> all.isEmpty() ? Optional.empty() : Optional.of(all.get(0)));
    }

    @NotNull
    public List<SavedPlotLayout> getSavedLayouts(@NotNull UUID islandId) {
        try {
            return repository.findSavedLayouts(islandId);
        } catch (SQLException e) {
            logger.warning("Failed to load saved layouts: " + e.getMessage());
            return List.of();
        }
    }

    @NotNull
    public ActionResult loadCustomLayout(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            boolean confirmed,
            @Nullable Player initiator
    ) {
        Optional<SavedPlotLayout> opt = getCustomLayout(garden.islandId());
        if (opt.isEmpty()) {
            return ActionResult.fail("No Custom Plot Layout saved yet. Right-Click the Custom Layout icon or run /garden plot save!");
        }
        return loadSavedLayout(world, garden, plot, opt.get().layoutName(), confirmed, initiator);
    }

    @NotNull
    public ActionResult loadSavedLayout(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull String layoutName,
            boolean confirmed
    ) {
        return loadSavedLayout(world, garden, plot, layoutName, confirmed, null);
    }

    @NotNull
    public ActionResult loadSavedLayout(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull String layoutName,
            boolean confirmed,
            @Nullable Player initiator
    ) {
        List<SavedPlotLayout> list = getSavedLayouts(garden.islandId());
        SavedPlotLayout target = list.stream()
                .filter(l -> l.layoutName().equalsIgnoreCase(layoutName))
                .findFirst().orElse(null);
        if (target == null) {
            return ActionResult.fail("Saved Custom Layout not found.");
        }

        PlotKey key = new PlotKey(garden.islandId(), plot.plotIndex());
        if (!activeAnimations.add(key)) {
            return ActionResult.fail("Plot #" + plot.plotIndex() + " is currently animating a layout change. Please wait a second!");
        }

        try {
            PlotLayoutEngine.DestructiveScanResult scan = PlotLayoutEngine.scanDestructiveConflicts(world, garden, plot);
            if (scan.hasConflicts() && !confirmed) {
                activeAnimations.remove(key);
                return ActionResult.confirm("Plot contains " + scan.existingCrops() + " crops and "
                        + scan.existingContainers() + " containers. Confirm to load your Custom Layout.");
            }

            PlotConfiguration cfg = getPlotConfiguration(garden.islandId(), plot.plotIndex());
            List<CropBed> instantiated = target.instantiateBeds(garden.islandId(), plot.plotIndex());
            List<CropBed> validBeds = new ArrayList<>();
            for (CropBed b : instantiated) {
                if (b.isWithinRadius(cfg.expandedRadius()) && validBeds.size() < cfg.maxBedsAllowed()) {
                    validBeds.add(b);
                }
            }

            cfg.setSelectedLayout(target.presetBase());
            cfg.setIrrigationType(target.irrigationType());
            cfg.setDecorationStyle(target.decorationStyle());
            cfg.replaceAllBeds(validBeds);
            saveAsync(cfg);
            PlotLayoutEngine.startAnimatedBuild(plugin, initiator, world, garden, plot, cfg, activeAnimations, key);
            return ActionResult.ok("Animating your Custom Plot Layout onto Plot #" + plot.plotIndex() + " (" + validBeds.size() + " beds).");
        } catch (RuntimeException ex) {
            activeAnimations.remove(key);
            throw ex;
        }
    }

    @NotNull
    public ActionResult plantSeedsInPlot(
            @NotNull Player player,
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @Nullable CropType explicitCrop
    ) {
        if (!plot.unlocked()) {
            return ActionResult.fail("Plot #" + plot.plotIndex() + " is locked.");
        }
        if (isAnimating(garden.islandId(), plot.plotIndex())) {
            return ActionResult.fail("Wait ~1s for Plot #" + plot.plotIndex() + "'s layout animation to finish before planting seeds!");
        }
        PlotConfiguration cfg = getPlotConfiguration(garden.islandId(), plot.plotIndex());
        return PlotSeedPlanter.startAnimatedPlotPlanting(plugin, player, world, garden, plot, cfg, explicitCrop);
    }

    private void saveAsync(@NotNull PlotConfiguration cfg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.savePlotConfiguration(cfg);
            } catch (SQLException e) {
                logger.severe("Failed to save PlotConfiguration for " + cfg.islandId() + " #" + cfg.plotIndex() + ": " + e.getMessage());
            }
        });
    }
}

