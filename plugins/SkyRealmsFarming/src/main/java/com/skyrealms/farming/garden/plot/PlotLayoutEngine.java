package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.mastery.CropType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Deterministic layout generator, conflict detector, particle previewer,
 * and lightweight tick-batched animated block builder for full 4x4 chunk Garden plot interiors (62x62).
 */
public final class PlotLayoutEngine {

    public record DestructiveScanResult(int existingCrops, int existingContainers) {
        public boolean hasConflicts() {
            return existingCrops > 0 || existingContainers > 0;
        }
    }

    public record GeneratedLayout(
            @NotNull PlotLayoutPreset preset,
            @NotNull List<CropBed> beds
    ) {}

    private PlotLayoutEngine() {}

    /**
     * Generates deterministic plot-local crop bed definitions spanning the full 4x4 chunk plot interior [-31, 30].
     */
    @NotNull
    public static GeneratedLayout generatePresetDefinition(
            @NotNull UUID islandId,
            int plotIndex,
            @NotNull PlotLayoutPreset preset,
            int expandedRadius,
            int maxBeds
    ) {
        int minAllowed = Math.max(CropBed.INTERIOR_MIN, -expandedRadius);
        int maxAllowed = Math.min(CropBed.INTERIOR_MAX, expandedRadius - 1);
        List<CropBed> beds = new ArrayList<>();
        CropType targetCrop = preset.targetCrop();

        switch (preset) {
            case CLEAN_CANVAS -> beds.add(new CropBed(
                    UUID.randomUUID(), islandId, plotIndex, BedType.BASIC, null,
                    Math.max(minAllowed, -4), Math.max(minAllowed, -4),
                    Math.min(maxAllowed, 4), Math.min(maxAllowed, 4)
            ));
            case WHEAT_OPTIMAL, CARROT_OPTIMAL, POTATO_OPTIMAL, BEETROOT_OPTIMAL, NETHER_WART_OPTIMAL, TRADITIONAL_ROWS -> {
                // Full 4x4 chunk symmetrical farming strips across [-30, 29] split by a central N-S boulevard (lx in [-1, 1])
                int[][] zBands = { {-30, -17}, {-15, -2}, {0, 13}, {15, 29} };
                for (int[] band : zBands) {
                    addClampedBed(beds, maxBeds, islandId, plotIndex, targetCrop, minAllowed, maxAllowed, -30, band[0], -3, band[1]);
                    addClampedBed(beds, maxBeds, islandId, plotIndex, targetCrop, minAllowed, maxAllowed, 3, band[0], 29, band[1]);
                }
            }
            case MELON_OPTIMAL, PUMPKIN_OPTIMAL, SUGAR_CANE_OPTIMAL, CACTUS_OPTIMAL, COCOA_OPTIMAL -> {
                // Full 4x4 chunk dual giant bays (West [-30, -3], East [3, 29], spanning [-30, 29]) for optimal row/grid crops
                addClampedBed(beds, maxBeds, islandId, plotIndex, targetCrop, minAllowed, maxAllowed, -30, -30, -3, 29);
                addClampedBed(beds, maxBeds, islandId, plotIndex, targetCrop, minAllowed, maxAllowed, 3, -30, 29, 29);
            }
            case JAPANESE_GARDEN -> {
                addClampedBed(beds, maxBeds, islandId, plotIndex, null, minAllowed, maxAllowed, -30, -30, -4, -4);
                addClampedBed(beds, maxBeds, islandId, plotIndex, null, minAllowed, maxAllowed, 4, -30, 29, -4);
                addClampedBed(beds, maxBeds, islandId, plotIndex, null, minAllowed, maxAllowed, -30, 4, -4, 29);
                addClampedBed(beds, maxBeds, islandId, plotIndex, null, minAllowed, maxAllowed, 4, 4, 29, 29);
            }
            case GREENHOUSE -> {
                int[][] zBays = { {-30, -12}, {-8, 7}, {11, 29} };
                for (int[] bay : zBays) {
                    addClampedBed(beds, maxBeds, islandId, plotIndex, null, minAllowed, maxAllowed, -30, bay[0], -4, bay[1]);
                    addClampedBed(beds, maxBeds, islandId, plotIndex, null, minAllowed, maxAllowed, 4, bay[0], 29, bay[1]);
                }
            }
            case TERRACED_GARDEN -> {
                int[][] zTiers = { {-30, -12}, {-10, 8}, {10, 29} };
                for (int[] tier : zTiers) {
                    addClampedBed(beds, maxBeds, islandId, plotIndex, null, minAllowed, maxAllowed, -30, tier[0], -3, tier[1]);
                    addClampedBed(beds, maxBeds, islandId, plotIndex, null, minAllowed, maxAllowed, 3, tier[0], 29, tier[1]);
                }
            }
        }

        return new GeneratedLayout(preset, beds);
    }

    private static void addClampedBed(
            List<CropBed> beds, int maxBeds, UUID islandId, int plotIndex, @Nullable CropType targetCrop,
            int minAllowed, int maxAllowed, int x1, int z1, int x2, int z2
    ) {
        if (beds.size() >= maxBeds) return;
        int cx1 = Math.max(minAllowed, x1);
        int cz1 = Math.max(minAllowed, z1);
        int cx2 = Math.min(maxAllowed, x2);
        int cz2 = Math.min(maxAllowed, z2);
        if (cx1 <= cx2 && cz1 <= cz2) {
            beds.add(new CropBed(UUID.randomUUID(), islandId, plotIndex, BedType.BASIC, targetCrop, cx1, cz1, cx2, cz2));
        }
    }

    /**
     * Scans only the 62x62 plot interior for existing player crops and containers before destructive replacement.
     */
    @NotNull
    public static DestructiveScanResult scanDestructiveConflicts(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot
    ) {
        int pcx = plot.getCenterX(garden.centerX());
        int pcz = plot.getCenterZ(garden.centerZ());
        int cy = garden.centerY();

        int crops = 0;
        int containers = 0;

        for (int lx = CropBed.INTERIOR_MIN; lx <= CropBed.INTERIOR_MAX; lx++) {
            for (int lz = CropBed.INTERIOR_MIN; lz <= CropBed.INTERIOR_MAX; lz++) {
                int wx = pcx + lx;
                int wz = pcz + lz;
                for (int y = cy; y <= cy + 4; y++) {
                    Material mat = world.getBlockAt(wx, y, wz).getType();
                    if (CropType.fromBlockMaterial(mat) != null) {
                        crops++;
                    } else if (isStorageContainer(mat)) {
                        containers++;
                    }
                }
            }
        }
        return new DestructiveScanResult(crops, containers);
    }

    /**
     * Non-destructive particle preview of crop beds and full interior boundary.
     */
    public static void previewLayout(@NotNull Player player, @NotNull Garden garden, @NotNull GardenPlot plot, @NotNull PlotConfiguration config) {
        World world = player.getWorld();
        int pcx = plot.getCenterX(garden.centerX());
        int pcz = plot.getCenterZ(garden.centerZ());
        int y = garden.centerY() + 1;

        for (CropBed bed : config.beds()) {
            for (int lx = bed.minLocalX(); lx <= bed.maxLocalX(); lx += 2) {
                spawnPreviewPoint(player, world, pcx + lx, y, pcz + bed.minLocalZ());
                spawnPreviewPoint(player, world, pcx + lx, y, pcz + bed.maxLocalZ());
            }
            for (int lz = bed.minLocalZ(); lz <= bed.maxLocalZ(); lz += 2) {
                spawnPreviewPoint(player, world, pcx + bed.minLocalX(), y, pcz + lz);
                spawnPreviewPoint(player, world, pcx + bed.maxLocalX(), y, pcz + lz);
            }
        }

        int minA = Math.max(CropBed.INTERIOR_MIN, -config.expandedRadius());
        int maxA = Math.min(CropBed.INTERIOR_MAX, config.expandedRadius() - 1);
        int[][] corners = { {minA, minA}, {maxA, minA}, {minA, maxA}, {maxA, maxA} };
        for (int[] c : corners) {
            player.spawnParticle(Particle.CHERRY_LEAVES,
                    new Location(world, pcx + c[0] + 0.5, y + 0.6, pcz + c[1] + 0.5), 6, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private static void spawnPreviewPoint(Player player, World world, int wx, int y, int wz) {
        player.spawnParticle(Particle.HAPPY_VILLAGER,
                new Location(world, wx + 0.5, y + 0.2, wz + 0.5), 1, 0, 0, 0, 0);
    }

    /**
     * Lightweight tick-batched animated plot paste (2 Z-rows = 124 columns per tick -> ~1.55s total for 62x62).
     * Avoids TPS spikes by skipping unchanged blocks and disabling heavy physics/relighting cascades.
     */
    public static void startAnimatedBuild(
            @NotNull Plugin plugin,
            @Nullable Player initiator,
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull PlotConfiguration config,
            @NotNull Set<GardenPlotCustomizationService.PlotKey> activeAnimations,
            @NotNull GardenPlotCustomizationService.PlotKey plotKey
    ) {
        if (!plugin.isEnabled()) {
            activeAnimations.remove(plotKey);
            buildPlotInterior(world, garden, plot, config);
            return;
        }

        int pcx = plot.getCenterX(garden.centerX());
        int pcz = plot.getCenterZ(garden.centerZ());
        int cy = garden.centerY();
        boolean isNorthRow = plot.plotIndex() <= 8;
        int totalRows = (CropBed.INTERIOR_MAX - CropBed.INTERIOR_MIN) + 1;

        new BukkitRunnable() {
            int currentLz = CropBed.INTERIOR_MIN;
            int tickStep = 0;

            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    cancel();
                    activeAnimations.remove(plotKey);
                    return;
                }

                // 2 Z-rows per tick = ~124 block columns/tick (~1.55 seconds for full 4x4 chunk plot)
                int rowsThisTick = 2;
                int waveEdgeLz = currentLz;
                for (int r = 0; r < rowsThisTick && currentLz <= CropBed.INTERIOR_MAX; r++, currentLz++) {
                    waveEdgeLz = currentLz;
                    for (int lx = CropBed.INTERIOR_MIN; lx <= CropBed.INTERIOR_MAX; lx++) {
                        applyPlotColumn(world, pcx, cy, pcz, isNorthRow, config, lx, currentLz);
                    }
                }

                // Lightweight sweeping wave particles + rising progress chime
                if (initiator != null && initiator.isOnline() && initiator.getWorld().equals(world)) {
                    double waveZ = pcz + waveEdgeLz + 0.5;
                    for (int px = -24; px <= 24; px += 8) {
                        world.spawnParticle(Particle.HAPPY_VILLAGER, pcx + px + 0.5, cy + 1.35, waveZ, 1, 0.3, 0.15, 0.2, 0.0);
                    }
                    int done = (currentLz - CropBed.INTERIOR_MIN);
                    int pct = Math.min(100, (done * 100) / totalRows);
                    initiator.sendActionBar(buildProgressBar(pct, config.selectedLayout().displayName()));
                    if (tickStep % 5 == 0) {
                        float pitch = 0.85f + (pct / 100.0f) * 0.75f;
                        initiator.playSound(initiator.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, pitch);
                    }
                }
                tickStep++;

                if (currentLz > CropBed.INTERIOR_MAX) {
                    applyDecorations(world, pcx, cy, pcz, config);
                    activeAnimations.remove(plotKey);
                    cancel();
                    if (initiator != null && initiator.isOnline()) {
                        initiator.playSound(initiator.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
                        initiator.sendActionBar(Component.text("✦ " + config.selectedLayout().displayName()
                                + " applied across full Plot #" + plot.plotIndex() + "!", NamedTextColor.GREEN));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Component buildProgressBar(int pct, String label) {
        int filled = pct / 10;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("] ").append(pct).append("% Pasting ").append(label);
        return Component.text(bar.toString(), NamedTextColor.AQUA);
    }

    /**
     * Synchronous full interior build (used in tests or non-animated fallback).
     */
    public static void buildPlotInterior(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull PlotConfiguration config
    ) {
        int pcx = plot.getCenterX(garden.centerX());
        int pcz = plot.getCenterZ(garden.centerZ());
        int cy = garden.centerY();
        boolean isNorthRow = plot.plotIndex() <= 8;

        for (int lx = CropBed.INTERIOR_MIN; lx <= CropBed.INTERIOR_MAX; lx++) {
            for (int lz = CropBed.INTERIOR_MIN; lz <= CropBed.INTERIOR_MAX; lz++) {
                applyPlotColumn(world, pcx, cy, pcz, isNorthRow, config, lx, lz);
            }
        }
        applyDecorations(world, pcx, cy, pcz, config);
    }

    private static void applyPlotColumn(
            @NotNull World world, int pcx, int cy, int pcz, boolean isNorthRow,
            @NotNull PlotConfiguration config, int lx, int lz
    ) {
        int wx = pcx + lx;
        int wz = pcz + lz;
        PlotLayoutPreset preset = config.selectedLayout();
        Optional<CropBed> bed = config.findBedAtLocal(lx, lz);
        int terraceOffset = getTerraceOffset(preset, lz, isNorthRow);
        int surfaceY = cy + terraceOffset;

        // Clear above surface up to cy + 4 unless Cocoa wall occupies cy+1..cy+3
        boolean isCocoaWall = preset == PlotLayoutPreset.COCOA_OPTIMAL && bed.isPresent() && Math.floorMod(lx, 3) == 0;

        for (int y = cy + 1; y <= cy + 4; y++) {
            if (isCocoaWall && y >= surfaceY + 1 && y <= surfaceY + 3) {
                setBlockIfDifferent(world, wx, y, wz, Material.JUNGLE_LOG);
            } else if (y > surfaceY) {
                setBlockIfDifferent(world, wx, y, wz, Material.AIR);
            }
        }

        setBlockIfDifferent(world, wx, cy - 1, wz,
                preset == PlotLayoutPreset.NETHER_WART_OPTIMAL ? Material.NETHERRACK : Material.DIRT);
        if (terraceOffset > 0) {
            for (int y = cy; y < surfaceY; y++) {
                setBlockIfDifferent(world, wx, y, wz, Material.DIRT);
            }
        }

        if (bed.isPresent()) {
            switch (preset) {
                case MELON_OPTIMAL, PUMPKIN_OPTIMAL -> {
                    boolean stemRow = Math.floorMod(lz, 2) == 0;
                    if (stemRow && Math.floorMod(lx, 8) == 0 && Math.floorMod(lz, 8) == 0) {
                        placeIrrigationBlock(world, wx, surfaceY, wz, config.irrigationType());
                    } else if (stemRow) {
                        setBlockIfDifferent(world, wx, surfaceY, wz, Material.FARMLAND);
                    } else {
                        setBlockIfDifferent(world, wx, surfaceY, wz, Material.GRASS_BLOCK);
                    }
                }
                case SUGAR_CANE_OPTIMAL -> {
                    if (Math.floorMod(lx, 3) == 0) {
                        placeIrrigationBlock(world, wx, surfaceY, wz, config.irrigationType());
                    } else {
                        setBlockIfDifferent(world, wx, surfaceY, wz, Material.SAND);
                    }
                }
                case CACTUS_OPTIMAL -> {
                    if (((lx + lz) & 1) == 0) {
                        setBlockIfDifferent(world, wx, surfaceY, wz, Material.SAND);
                    } else {
                        setBlockIfDifferent(world, wx, surfaceY, wz, Material.SMOOTH_SANDSTONE);
                    }
                }
                case COCOA_OPTIMAL -> {
                    if (Math.floorMod(lx, 3) == 0) {
                        setBlockIfDifferent(world, wx, surfaceY, wz, Material.MOSS_BLOCK);
                    } else {
                        setBlockIfDifferent(world, wx, surfaceY, wz, Material.JUNGLE_PLANKS);
                    }
                }
                case NETHER_WART_OPTIMAL -> {
                    setBlockIfDifferent(world, wx, surfaceY, wz, Material.SOUL_SAND);
                }
                default -> {
                    boolean isWaterSlot = (lx == 0 && lz == 0) || (Math.floorMod(lx, 6) == 0 && Math.floorMod(lz, 6) == 0);
                    if (isWaterSlot) {
                        placeIrrigationBlock(world, wx, surfaceY, wz, config.irrigationType());
                    } else {
                        setBlockIfDifferent(world, wx, surfaceY, wz, bed.get().bedType().baseSoilMaterial());
                    }
                }
            }
        } else if (isWalkwayTile(preset, lx, lz, isNorthRow)) {
            Material pathMat = switch (preset) {
                case JAPANESE_GARDEN -> Material.CHERRY_PLANKS;
                case GREENHOUSE -> Material.QUARTZ_BLOCK;
                case TERRACED_GARDEN -> Material.STONE_BRICKS;
                case NETHER_WART_OPTIMAL -> Material.NETHER_BRICKS;
                case SUGAR_CANE_OPTIMAL -> Material.SPRUCE_PLANKS;
                case CACTUS_OPTIMAL -> Material.SMOOTH_SANDSTONE;
                case COCOA_OPTIMAL -> Material.JUNGLE_PLANKS;
                default -> Material.SMOOTH_STONE;
            };
            setBlockIfDifferent(world, wx, surfaceY, wz, pathMat);
        } else {
            Material fill = switch (preset) {
                case NETHER_WART_OPTIMAL -> Material.POLISHED_BLACKSTONE_BRICKS;
                case CACTUS_OPTIMAL -> Material.SMOOTH_SANDSTONE;
                default -> Material.GRASS_BLOCK;
            };
            setBlockIfDifferent(world, wx, surfaceY, wz, fill);
        }
    }

    private static void setBlockIfDifferent(@NotNull World world, int x, int y, int z, @NotNull Material target) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != target) {
            block.setType(target, false);
        }
    }

    private static int getTerraceOffset(@NotNull PlotLayoutPreset preset, int lz, boolean isNorthRow) {
        if (preset != PlotLayoutPreset.TERRACED_GARDEN) return 0;
        if (isNorthRow) {
            if (lz <= -12) return 2;
            if (lz <= 8) return 1;
            return 0;
        } else {
            if (lz >= 10) return 2;
            if (lz >= -10) return 1;
            return 0;
        }
    }

    private static boolean isWalkwayTile(@NotNull PlotLayoutPreset preset, int lx, int lz, boolean isNorthRow) {
        boolean entrance = isNorthRow ? (Math.abs(lx) <= 1 && lz >= 5) : (Math.abs(lx) <= 1 && lz <= -5);
        if (entrance) return true;
        return switch (preset) {
            case WHEAT_OPTIMAL, CARROT_OPTIMAL, POTATO_OPTIMAL, BEETROOT_OPTIMAL, NETHER_WART_OPTIMAL, TRADITIONAL_ROWS ->
                    Math.abs(lx) <= 1 || lz == -16 || lz == -1 || lz == 14;
            case MELON_OPTIMAL, PUMPKIN_OPTIMAL, SUGAR_CANE_OPTIMAL, CACTUS_OPTIMAL, COCOA_OPTIMAL ->
                    Math.abs(lx) <= 1;
            case JAPANESE_GARDEN -> Math.abs(lx) <= 2 || Math.abs(lz) <= 2;
            case GREENHOUSE -> Math.abs(lx) <= 2 || (lz >= -11 && lz <= -9) || (lz >= 8 && lz <= 10);
            case TERRACED_GARDEN -> Math.abs(lx) <= 1 || lz == -11 || lz == 9;
            case CLEAN_CANVAS -> false;
        };
    }

    private static void placeIrrigationBlock(@NotNull World world, int x, int y, int z, @NotNull IrrigationType irrigation) {
        setBlockIfDifferent(world, x, y - 1, z, irrigation.lightMaterial());
        Block waterBlock = world.getBlockAt(x, y, z);
        if (irrigation == IrrigationType.PRISMARINE_CANAL) {
            if (waterBlock.getType() != Material.WATER) {
                waterBlock.setType(Material.WATER, false);
            }
        } else {
            waterBlock.setType(irrigation.trimMaterial(), false);
            BlockData data = waterBlock.getBlockData();
            if (data instanceof Waterlogged wl) {
                wl.setWaterlogged(true);
                waterBlock.setBlockData(wl, false);
            }
        }
    }

    private static void applyDecorations(@NotNull World world, int pcx, int cy, int pcz, @NotNull PlotConfiguration config) {
        if (config.decorationStyle() == DecorationStyle.NONE) return;

        int minC = CropBed.INTERIOR_MIN;
        int maxC = CropBed.INTERIOR_MAX;
        int[][] corners = { {minC, minC}, {maxC, minC}, {minC, maxC}, {maxC, maxC} };
        for (int[] c : corners) {
            if (config.findBedAtLocal(c[0], c[1]).isPresent()) continue;
            int wx = pcx + c[0];
            int wz = pcz + c[1];
            switch (config.decorationStyle()) {
                case CHERRY_LANTERNS -> {
                    setBlockIfDifferent(world, wx, cy, wz, Material.MOSS_BLOCK);
                    setBlockIfDifferent(world, wx, cy + 1, wz, Material.CHERRY_FENCE);
                    setBlockIfDifferent(world, wx, cy + 2, wz, Material.LANTERN);
                }
                case ZEN_STONES -> {
                    setBlockIfDifferent(world, wx, cy, wz, Material.MOSS_BLOCK);
                    setBlockIfDifferent(world, wx, cy + 1, wz, Material.FLOWERING_AZALEA);
                }
                case TRELLIS_ARCH -> {
                    setBlockIfDifferent(world, wx, cy + 1, wz, Material.DARK_OAK_FENCE);
                    setBlockIfDifferent(world, wx, cy + 2, wz, Material.CHERRY_LEAVES);
                }
                case NONE -> {}
            }
        }
    }

    public static boolean isStorageContainer(@NotNull Material mat) {
        return switch (mat) {
            case CHEST, TRAPPED_CHEST, BARREL, HOPPER, DISPENSER, DROPPER,
                 SHULKER_BOX, WHITE_SHULKER_BOX, PINK_SHULKER_BOX, CHERRY_SIGN -> true;
            default -> mat.name().endsWith("SHULKER_BOX");
        };
    }
}
