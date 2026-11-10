package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.mastery.CropType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Animated, server-lightweight seed planter for placing crops into valid farmland/soil inside Garden plots,
 * strictly consuming real seeds/crops from the player's inventory (or creative mode).
 */
public final class PlotSeedPlanter {

    public record PlantSpot(int x, int y, int z, @Nullable BlockFace cocoaFacing) {}

    private static final BlockFace[] CARDINALS = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };

    private PlotSeedPlanter() {}

    /**
     * Resolves which CropType to plant based on preference, held item, active preset, or available inventory seeds.
     */
    @Nullable
    public static CropType resolveCropToPlant(
            @NotNull Player player,
            @NotNull PlotConfiguration config,
            @Nullable CropType explicitCrop
    ) {
        if (explicitCrop != null) return explicitCrop;

        CropType held = CropType.fromSeedItemMaterial(player.getInventory().getItemInMainHand().getType());
        if (held != null) return held;

        CropType presetCrop = config.selectedLayout().targetCrop();
        if (presetCrop != null && countSeeds(player, presetCrop) > 0) {
            return presetCrop;
        }

        for (CropType type : CropType.values()) {
            if (countSeeds(player, type) > 0) {
                return type;
            }
        }
        return presetCrop;
    }

    public static int countSeeds(@NotNull Player player, @NotNull CropType cropType) {
        if (player.getGameMode() == GameMode.CREATIVE) return Integer.MAX_VALUE;
        Material seedMat = cropType.seedItemMaterial();
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == seedMat) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static boolean consumeOneSeed(@NotNull Player player, @NotNull Material seedMaterial) {
        if (player.getGameMode() == GameMode.CREATIVE) return true;
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() == seedMaterial && item.getAmount() > 0) {
                if (item.getAmount() == 1) {
                    inv.setItem(slot, null);
                } else {
                    item.setAmount(item.getAmount() - 1);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Scans the plot's 62x62 interior for all valid empty spots for the given crop,
     * respecting Specialized CropBed crop locks.
     */
    @NotNull
    public static List<PlantSpot> findPlantableSpots(
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull PlotConfiguration config,
            @NotNull CropType cropType
    ) {
        List<PlantSpot> spots = new ArrayList<>();
        int pcx = plot.getCenterX(garden.centerX());
        int pcz = plot.getCenterZ(garden.centerZ());
        int cy = garden.centerY();

        for (int lz = CropBed.INTERIOR_MIN; lz <= CropBed.INTERIOR_MAX; lz++) {
            for (int lx = CropBed.INTERIOR_MIN; lx <= CropBed.INTERIOR_MAX; lx++) {
                Optional<CropBed> optBed = config.findBedAtLocal(lx, lz);
                if (optBed.isPresent()) {
                    CropBed bed = optBed.get();
                    if (bed.bedType() == BedType.SPECIALIZED && bed.targetCrop() != null && bed.targetCrop() != cropType) {
                        continue;
                    }
                }
                int wx = pcx + lx;
                int wz = pcz + lz;
                if (cropType == CropType.COCOA) {
                    for (int y = cy + 1; y <= cy + 4; y++) {
                        Block b = world.getBlockAt(wx, y, wz);
                        BlockFace logFace = findAdjacentJungleLog(b);
                        if (b.getType() == Material.AIR && logFace != null) {
                            spots.add(new PlantSpot(wx, y, wz, logFace));
                        }
                    }
                } else {
                    int maxScanY = config.selectedLayout() == PlotLayoutPreset.TERRACED_GARDEN ? cy + 2 : cy;
                    for (int y = cy; y <= maxScanY; y++) {
                        if (isValidGroundPlantSpot(world.getBlockAt(wx, y, wz), world.getBlockAt(wx, y + 1, wz), cropType)) {
                            spots.add(new PlantSpot(wx, y + 1, wz, null));
                            break;
                        }
                    }
                }
            }
        }
        return spots;
    }

    /**
     * Launches a low-overhead animated wave planting up to 120 seeds/tick from the player's inventory.
     */
    public static GardenPlotCustomizationService.ActionResult startAnimatedPlotPlanting(
            @NotNull Plugin plugin,
            @NotNull Player player,
            @NotNull World world,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull PlotConfiguration config,
            @Nullable CropType explicitCrop
    ) {
        CropType crop = resolveCropToPlant(player, config, explicitCrop);
        if (crop == null) {
            return GardenPlotCustomizationService.ActionResult.fail("Hold or carry seeds/crops in your inventory to plant on farmland!");
        }

        int available = countSeeds(player, crop);
        if (available <= 0) {
            return GardenPlotCustomizationService.ActionResult.fail("You have no " + crop.displayName()
                    + " (" + crop.seedItemMaterial().name() + ") in your inventory.");
        }

        List<PlantSpot> spots = findPlantableSpots(world, garden, plot, config, crop);
        if (spots.isEmpty()) {
            return GardenPlotCustomizationService.ActionResult.fail("No empty valid " + crop.displayName()
                    + " farmland/soil spots found in Plot #" + plot.plotIndex() + ".");
        }

        if (!plugin.isEnabled()) {
            int planted = plantBatchSync(player, world, spots, crop, spots.size());
            return GardenPlotCustomizationService.ActionResult.ok("Planted " + planted + "x " + crop.displayName() + " across Plot #" + plot.plotIndex() + ".");
        }

        Material seedItemMat = crop.seedItemMaterial();
        Material placedMat = crop.plantedBlockMaterial();

        new BukkitRunnable() {
            int idx = 0;
            int planted = 0;
            int tick = 0;

            @Override
            public void run() {
                if (!plugin.isEnabled() || !player.isOnline() || !player.getWorld().equals(world)) {
                    cancel();
                    return;
                }

                int budget = 100;
                PlantSpot lastWave = null;
                while (idx < spots.size() && budget > 0) {
                    PlantSpot spot = spots.get(idx++);
                    Block target = world.getBlockAt(spot.x(), spot.y(), spot.z());
                    if (target.getType() != Material.AIR) continue;
                    if (!consumeOneSeed(player, seedItemMat)) {
                        idx = spots.size();
                        break;
                    }
                    placeSingleCropBlock(target, placedMat, spot.cocoaFacing());
                    planted++;
                    budget--;
                    lastWave = spot;
                }

                if (lastWave != null) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, lastWave.x() + 0.5, lastWave.y() + 0.3, lastWave.z() + 0.5,
                            4, 0.6, 0.1, 0.6, 0.0);
                    if (tick % 3 == 0) {
                        player.playSound(player.getLocation(), Sound.ITEM_CROP_PLANT, 0.55f, 1.0f + Math.min(0.5f, tick * 0.02f));
                    }
                    player.sendActionBar(Component.text("✦ Planting " + crop.displayName() + ": " + planted + " placed...", NamedTextColor.GREEN));
                }
                tick++;

                if (idx >= spots.size()) {
                    cancel();
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
                    player.sendMessage(Component.text("✦ Planted " + planted + "x " + crop.displayName()
                            + " onto Plot #" + plot.plotIndex() + " farmland!", NamedTextColor.GREEN));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return GardenPlotCustomizationService.ActionResult.ok("Planting " + crop.displayName()
                + " across empty farmland in Plot #" + plot.plotIndex() + "...");
    }

    /**
     * Quick row/area plant when sneak-right-clicking farmland/soil while holding seeds (up to 64 contiguous spots).
     */
    public static int plantQuickAreaFromClickedSoil(
            @NotNull Player player,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull PlotConfiguration config,
            @NotNull Block clickedBlock,
            @NotNull CropType crop
    ) {
        World world = clickedBlock.getWorld();
        int pcx = plot.getCenterX(garden.centerX());
        int pcz = plot.getCenterZ(garden.centerZ());
        int clickLx = clickedBlock.getX() - pcx;
        int clickLz = clickedBlock.getZ() - pcz;
        int baseY = clickedBlock.getY();

        List<PlantSpot> rowSpots = new ArrayList<>();
        // Scan a 7-wide strip along Z around the clicked coordinate first so rows fill intuitively
        for (int dz = -31; dz <= 31; dz++) {
            int lz = clickLz + dz;
            if (lz < CropBed.INTERIOR_MIN || lz > CropBed.INTERIOR_MAX) continue;
            for (int dx = -2; dx <= 2; dx++) {
                int lx = clickLx + dx;
                if (lx < CropBed.INTERIOR_MIN || lx > CropBed.INTERIOR_MAX) continue;
                Optional<CropBed> optBed = config.findBedAtLocal(lx, lz);
                if (optBed.isPresent()) {
                    CropBed bed = optBed.get();
                    if (bed.bedType() == BedType.SPECIALIZED && bed.targetCrop() != null && bed.targetCrop() != crop) {
                        continue;
                    }
                }
                int wx = pcx + lx;
                int wz = pcz + lz;
                if (crop == CropType.COCOA && clickedBlock.getType() == Material.JUNGLE_LOG) {
                    for (BlockFace face : CARDINALS) {
                        Block airSide = world.getBlockAt(wx, baseY, wz).getRelative(face);
                        if (airSide.getType() == Material.AIR && world.getBlockAt(wx, baseY, wz).getType() == Material.JUNGLE_LOG) {
                            rowSpots.add(new PlantSpot(airSide.getX(), airSide.getY(), airSide.getZ(), face.getOppositeFace()));
                        }
                    }
                } else if (crop != CropType.COCOA) {
                    Block soil = world.getBlockAt(wx, baseY, wz);
                    Block above = world.getBlockAt(wx, baseY + 1, wz);
                    if (isValidGroundPlantSpot(soil, above, crop)) {
                        rowSpots.add(new PlantSpot(wx, baseY + 1, wz, null));
                    }
                }
            }
        }
        return plantBatchSync(player, world, rowSpots, crop, 64);
    }

    private static int plantBatchSync(
            @NotNull Player player, @NotNull World world, @NotNull List<PlantSpot> spots,
            @NotNull CropType crop, int limit
    ) {
        Material seedMat = crop.seedItemMaterial();
        Material placedMat = crop.plantedBlockMaterial();
        int planted = 0;
        for (PlantSpot spot : spots) {
            if (planted >= limit) break;
            Block b = world.getBlockAt(spot.x(), spot.y(), spot.z());
            if (b.getType() != Material.AIR) continue;
            if (!consumeOneSeed(player, seedMat)) break;
            placeSingleCropBlock(b, placedMat, spot.cocoaFacing());
            planted++;
        }
        return planted;
    }

    private static void placeSingleCropBlock(@NotNull Block target, @NotNull Material placedMat, @Nullable BlockFace cocoaFacing) {
        if (placedMat == Material.COCOA && cocoaFacing != null) {
            BlockData data = Material.COCOA.createBlockData();
            if (data instanceof Directional dir) {
                dir.setFacing(cocoaFacing);
            }
            target.setBlockData(data, false);
        } else {
            target.setType(placedMat, false);
        }
    }

    public static boolean isValidGroundPlantSpot(@NotNull Block soil, @NotNull Block above, @NotNull CropType crop) {
        if (above.getType() != Material.AIR) return false;
        Material soilType = soil.getType();
        return switch (crop) {
            case WHEAT, CARROT, POTATO, BEETROOT, MELON, PUMPKIN -> soilType == Material.FARMLAND;
            case NETHER_WART -> soilType == Material.SOUL_SAND;
            case SUGAR_CANE -> (soilType == Material.SAND || soilType == Material.RED_SAND
                    || soilType == Material.GRASS_BLOCK || soilType == Material.DIRT
                    || soilType == Material.COARSE_DIRT || soilType == Material.PODZOL)
                    && hasAdjacentWater(soil);
            case CACTUS -> (soilType == Material.SAND || soilType == Material.RED_SAND)
                    && hasClearCardinalNeighbors(above);
            case COCOA -> false;
        };
    }

    private static boolean hasAdjacentWater(@NotNull Block soil) {
        for (BlockFace face : CARDINALS) {
            Block adj = soil.getRelative(face);
            if (adj.getType() == Material.WATER) return true;
            if (adj.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged()) return true;
        }
        return false;
    }

    private static boolean hasClearCardinalNeighbors(@NotNull Block airAboveSand) {
        for (BlockFace face : CARDINALS) {
            if (!airAboveSand.getRelative(face).isEmpty()) return false;
        }
        return true;
    }

    @Nullable
    private static BlockFace findAdjacentJungleLog(@NotNull Block airBlock) {
        for (BlockFace face : CARDINALS) {
            if (airBlock.getRelative(face).getType() == Material.JUNGLE_LOG) {
                return face;
            }
        }
        return null;
    }
}

