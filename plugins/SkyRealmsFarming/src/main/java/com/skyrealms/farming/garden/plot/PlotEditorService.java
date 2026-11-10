package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player-friendly Plot Edit Mode sessions using vanilla block-click interactions.
 * Strictly enforces plot boundaries, co-op permissions, and edit session isolation.
 */
public final class PlotEditorService {

    public static final class PlotEditSession {
        private final UUID islandId;
        private final int plotIndex;
        private int[] corner1Local; // [lx, lz]
        private int[] corner2Local; // [lx, lz]
        private BedType selectedBedType = BedType.BASIC;
        private CropType selectedTargetCrop = null;
        private PlotLayoutPreset pendingConfirmationPreset = null;
        private String pendingSavedLayoutName = null;

        public PlotEditSession(@NotNull UUID islandId, int plotIndex) {
            this.islandId = islandId;
            this.plotIndex = plotIndex;
        }

        public UUID islandId() { return islandId; }
        public int plotIndex() { return plotIndex; }
        public int @Nullable [] corner1() { return corner1Local; }
        public int @Nullable [] corner2() { return corner2Local; }
        public BedType selectedBedType() { return selectedBedType; }
        @Nullable public CropType selectedTargetCrop() { return selectedTargetCrop; }
        @Nullable public PlotLayoutPreset pendingPreset() { return pendingConfirmationPreset; }
        @Nullable public String pendingSavedLayout() { return pendingSavedLayoutName; }

        public void setCorner1(int lx, int lz) { this.corner1Local = new int[]{lx, lz}; }
        public void setCorner2(int lx, int lz) { this.corner2Local = new int[]{lx, lz}; }
        public void setSelectedBedType(@NotNull BedType type) { this.selectedBedType = type; }
        public void setSelectedTargetCrop(@Nullable CropType crop) { this.selectedTargetCrop = crop; }
        public void setPendingPreset(@Nullable PlotLayoutPreset preset) {
            this.pendingConfirmationPreset = preset;
            this.pendingSavedLayoutName = null;
        }
        public void setPendingSavedLayout(@Nullable String name) {
            this.pendingSavedLayoutName = name;
            this.pendingConfirmationPreset = null;
        }
        public void clearCorners() {
            this.corner1Local = null;
            this.corner2Local = null;
        }
    }

    private final GardenService gardenService;
    private final IslandService islandService;
    private final GardenPlotCustomizationService customizationService;
    private final McMMOFarmingService mcMmoService;

    private final Map<UUID, PlotEditSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BedType> preferredBedTypes = new ConcurrentHashMap<>();

    public PlotEditorService(
            @NotNull GardenService gardenService,
            @NotNull IslandService islandService,
            @NotNull GardenPlotCustomizationService customizationService,
            @NotNull McMMOFarmingService mcMmoService
    ) {
        this.gardenService = gardenService;
        this.islandService = islandService;
        this.customizationService = customizationService;
        this.mcMmoService = mcMmoService;
    }

    public boolean isEditing(@NotNull UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    @Nullable
    public PlotEditSession getSession(@NotNull UUID playerUuid) {
        return activeSessions.get(playerUuid);
    }

    @NotNull
    public BedType getPreferredBedType(@NotNull UUID playerUuid) {
        PlotEditSession session = activeSessions.get(playerUuid);
        if (session != null) {
            return session.selectedBedType();
        }
        return preferredBedTypes.getOrDefault(playerUuid, BedType.BASIC);
    }

    @NotNull
    public BedType cyclePreferredBedType(@NotNull UUID playerUuid) {
        BedType[] types = BedType.values();
        BedType current = getPreferredBedType(playerUuid);
        BedType next = types[(current.ordinal() + 1) % types.length];
        preferredBedTypes.put(playerUuid, next);
        PlotEditSession session = activeSessions.get(playerUuid);
        if (session != null) {
            session.setSelectedBedType(next);
        }
        return next;
    }

    public boolean hasEditPermission(@NotNull Player player, @NotNull UUID islandId) {
        if (player.isOp() || player.hasPermission("skyrealms.garden.admin")) return true;
        Optional<Island> opt = islandService.getIsland(islandId);
        return opt.isPresent()
                && opt.get().hasPermission(player.getUniqueId(), IslandPermission.BLOCK_PLACE)
                && opt.get().hasPermission(player.getUniqueId(), IslandPermission.BLOCK_BREAK);
    }

    public boolean enterEditMode(@NotNull Player player, @NotNull Garden garden, @NotNull GardenPlot plot) {
        if (!plot.unlocked()) {
            player.sendMessage(Component.text("🔒 Plot #" + plot.plotIndex() + " is locked.", NamedTextColor.RED));
            return false;
        }
        if (!hasEditPermission(player, garden.islandId())) {
            player.sendMessage(Component.text("⛔ You do not have co-op build permission in this Garden.", NamedTextColor.RED));
            return false;
        }

        PlotEditSession session = new PlotEditSession(garden.islandId(), plot.plotIndex());
        session.setSelectedBedType(preferredBedTypes.getOrDefault(player.getUniqueId(), BedType.BASIC));
        activeSessions.put(player.getUniqueId(), session);

        PlotConfiguration cfg = customizationService.getPlotConfiguration(garden.islandId(), plot.plotIndex());
        PlotLayoutEngine.previewLayout(player, garden, plot, cfg);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        player.sendMessage(Component.text("✎ Plot Edit Mode ENABLED for Plot #" + plot.plotIndex(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("  Empty Hand / Stick: Left-Click = Corner 1 | Right-Click = Corner 2 | Sneak+Right-Click = Create Bed", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  (Holding blocks or tools mines/builds normally. Use /garden plot edit to exit.)", NamedTextColor.DARK_GRAY));
        return true;
    }

    public void exitEditMode(@NotNull Player player, boolean notify) {
        if (activeSessions.remove(player.getUniqueId()) != null && notify && player.isOnline()) {
            player.sendMessage(Component.text("✎ Exited Plot Edit Mode.", NamedTextColor.YELLOW));
        }
    }

    public void clearSession(@NotNull UUID playerUuid) {
        activeSessions.remove(playerUuid);
        preferredBedTypes.remove(playerUuid);
    }

    public boolean tryCreateBedFromSelection(@NotNull Player player) {
        PlotEditSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.corner1() == null || session.corner2() == null) {
            player.sendActionBar(Component.text("Select Corner 1 (Left-Click) and Corner 2 (Right-Click) inside the plot first.", NamedTextColor.RED));
            return false;
        }

        Optional<Garden> optGarden = gardenService.getGarden(session.islandId());
        if (optGarden.isEmpty()) return false;
        Garden garden = optGarden.get();

        GardenPlot plot = gardenService.getPlots(garden.islandId()).stream()
                .filter(p -> p.plotIndex() == session.plotIndex())
                .findFirst().orElse(null);
        if (plot == null || !hasEditPermission(player, garden.islandId())) {
            exitEditMode(player, true);
            return false;
        }

        int[] c1 = session.corner1();
        int[] c2 = session.corner2();
        int level = mcMmoService.getFarmingLevel(player);
        GardenPlotCustomizationService.ActionResult res = customizationService.createCropBed(
                garden, plot, session.selectedBedType(), session.selectedTargetCrop(),
                c1[0], c1[1], c2[0], c2[1], level, player.getWorld()
        );

        if (res.success()) {
            session.clearCorners();
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.3f);
            player.sendMessage(Component.text("✦ " + res.message(), NamedTextColor.GREEN));
            PlotLayoutEngine.previewLayout(player, garden, plot, customizationService.getPlotConfiguration(garden.islandId(), plot.plotIndex()));
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            player.sendMessage(Component.text("✖ " + res.message(), NamedTextColor.RED));
        }
        return res.success();
    }

    public void showCornerParticle(@NotNull Player player, @NotNull Garden garden, @NotNull GardenPlot plot, int lx, int lz, boolean isFirst) {
        int wx = plot.getCenterX(garden.centerX()) + lx;
        int wz = plot.getCenterZ(garden.centerZ()) + lz;
        player.spawnParticle(isFirst ? Particle.END_ROD : Particle.HAPPY_VILLAGER,
                wx + 0.5, garden.centerY() + 1.4, wz + 0.5, 8, 0.1, 0.2, 0.1, 0.0);
    }
}

