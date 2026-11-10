package com.skyrealms.farming.garden.plot;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Full customizable configuration state for one Garden plot (1-16).
 */
public final class PlotConfiguration {

    private final UUID islandId;
    private final int plotIndex;
    private PlotLayoutPreset selectedLayout;
    private IrrigationType irrigationType;
    private DecorationStyle decorationStyle;
    private int upgradeLevel;
    private int expandedRadius;
    private final List<CropBed> beds;

    public PlotConfiguration(
            @NotNull UUID islandId,
            int plotIndex,
            @NotNull PlotLayoutPreset selectedLayout,
            @NotNull IrrigationType irrigationType,
            @NotNull DecorationStyle decorationStyle,
            int upgradeLevel,
            int expandedRadius,
            @NotNull List<CropBed> beds
    ) {
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.plotIndex = plotIndex;
        this.selectedLayout = Objects.requireNonNull(selectedLayout, "selectedLayout cannot be null");
        this.irrigationType = Objects.requireNonNull(irrigationType, "irrigationType cannot be null");
        this.decorationStyle = Objects.requireNonNull(decorationStyle, "decorationStyle cannot be null");
        this.upgradeLevel = Math.max(1, Math.min(4, upgradeLevel));
        this.expandedRadius = PlotUpgradeTier.FULL_INTERIOR_RADIUS;
        this.beds = new ArrayList<>(Objects.requireNonNull(beds, "beds cannot be null"));
    }

    @NotNull
    public static PlotConfiguration defaultFor(@NotNull UUID islandId, int plotIndex) {
        return new PlotConfiguration(
                islandId,
                plotIndex,
                PlotLayoutPreset.CLEAN_CANVAS,
                IrrigationType.STANDARD,
                DecorationStyle.NONE,
                1,
                PlotUpgradeTier.TIER_1.expandedRadius(),
                List.of()
        );
    }

    @NotNull public UUID islandId() { return islandId; }
    public int plotIndex() { return plotIndex; }
    @NotNull public PlotLayoutPreset selectedLayout() { return selectedLayout; }
    @NotNull public IrrigationType irrigationType() { return irrigationType; }
    @NotNull public DecorationStyle decorationStyle() { return decorationStyle; }
    public int upgradeLevel() { return upgradeLevel; }
    public int expandedRadius() { return expandedRadius; }
    @NotNull public List<CropBed> beds() { return Collections.unmodifiableList(beds); }

    public int maxBedsAllowed() {
        return PlotUpgradeTier.forLevel(upgradeLevel).maxBeds();
    }

    public boolean isLocalCoordInActiveZone(int localX, int localZ) {
        int minAllowed = Math.max(CropBed.INTERIOR_MIN, -expandedRadius);
        int maxAllowed = Math.min(CropBed.INTERIOR_MAX, expandedRadius - 1);
        return localX >= minAllowed && localX <= maxAllowed && localZ >= minAllowed && localZ <= maxAllowed;
    }

    @NotNull
    public Optional<CropBed> findBedAtLocal(int localX, int localZ) {
        for (CropBed bed : beds) {
            if (bed.containsLocal(localX, localZ)) {
                return Optional.of(bed);
            }
        }
        return Optional.empty();
    }

    public void setSelectedLayout(@NotNull PlotLayoutPreset selectedLayout) {
        this.selectedLayout = Objects.requireNonNull(selectedLayout);
    }

    public void setIrrigationType(@NotNull IrrigationType irrigationType) {
        this.irrigationType = Objects.requireNonNull(irrigationType);
    }

    public void setDecorationStyle(@NotNull DecorationStyle decorationStyle) {
        this.decorationStyle = Objects.requireNonNull(decorationStyle);
    }

    public void setUpgradeTier(@NotNull PlotUpgradeTier tier) {
        this.upgradeLevel = tier.level();
        this.expandedRadius = tier.expandedRadius();
    }

    public void replaceAllBeds(@NotNull List<CropBed> newBeds) {
        this.beds.clear();
        this.beds.addAll(newBeds);
    }

    public void addBed(@NotNull CropBed bed) {
        this.beds.add(bed);
    }

    public boolean removeBed(@NotNull UUID bedId) {
        return this.beds.removeIf(b -> b.bedId().equals(bedId));
    }
}

