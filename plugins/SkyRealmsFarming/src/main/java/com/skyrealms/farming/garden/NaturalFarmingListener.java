package com.skyrealms.farming.garden;

import com.skyrealms.farming.event.GardenCropPlantEvent;
import com.skyrealms.platform.api.event.EventBus;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Listener integrating vanilla Minecraft farming mechanics inside SkyRealms Gardens:
 * right-click fast harvest, natural growth acceleration, planting event dispatching,
 * and anti-exploit placed block tracking.
 */
public final class NaturalFarmingListener implements Listener {

    private final GardenService gardenService;
    private final NaturalFarmingEngine engine;
    private final EventBus eventBus;

    public NaturalFarmingListener(
            @NotNull GardenService gardenService,
            @NotNull NaturalFarmingEngine engine,
            @NotNull EventBus eventBus
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
    }

    private boolean isGardenWorld(Location loc) {
        return loc != null && loc.getWorld() != null && gardenService.isGardenWorld(loc.getWorld());
    }

    /**
     * Right-Click Fast Harvest for fully mature crops.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (!isGardenWorld(clicked.getLocation())) return;
        if (!engine.isCropMaterial(clicked.getType())) return;

        Player player = event.getPlayer();
        if (engine.isMature(clicked)) {
            boolean harvested = engine.handleRightClickHarvest(player, clicked);
            if (harvested) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Normal manual crop harvesting via BlockBreakEvent.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isGardenWorld(block.getLocation())) return;
        if (!engine.isCropMaterial(block.getType())) return;

        Player player = event.getPlayer();
        engine.handleBreakHarvest(player, block);
    }

    /**
     * Track manual block placement to prevent placed-crop XP exploit.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isGardenWorld(block.getLocation())) return;

        Material mat = block.getType();
        if (mat == Material.MELON || mat == Material.PUMPKIN || mat == Material.SUGAR_CANE || mat == Material.CACTUS) {
            engine.markPlayerPlaced(block);
        } else if (engine.isCropMaterial(mat)) {
            // Planting seeds (Wheat, Carrots, Potatoes, Beetroots, Nether Wart, Cocoa)
            engine.clearPlayerPlaced(block);

            Optional<Garden> optGarden = gardenService.getGardenAt(block.getLocation());
            Optional<GardenPlot> optPlot = gardenService.getPlotAt(block.getLocation());
            if (optGarden.isPresent() && optPlot.isPresent() && optPlot.get().unlocked()) {
                GardenCropPlantEvent plantEvent = new GardenCropPlantEvent(
                        event.getPlayer(),
                        optGarden.get(),
                        optPlot.get(),
                        engine.getCropId(mat),
                        block.getLocation()
                );
                eventBus.publish(plantEvent);
                if (plantEvent.isCancelled()) {
                    event.setCancelled(true);
                    return;
                }
                event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_GRASS_PLACE, 0.8f, 1.2f);
            }
        }
    }

    /**
     * Clear player placed tag on natural growth (pumpkins, melons).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getNewState().getBlock();
        if (!isGardenWorld(block.getLocation())) return;

        engine.clearPlayerPlaced(block);

        // Soil fertility bonus growth acceleration
        Optional<GardenPlot> optPlot = gardenService.getPlotAt(block.getLocation());
        if (optPlot.isPresent()) {
            GardenPlot plot = optPlot.get();
            if (plot.fertility() > 1.2) {
                // High fertility soil can trigger bonus growth
                if (Math.random() < 0.25) {
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.5f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Block block = event.getNewState().getBlock();
        if (isGardenWorld(block.getLocation())) {
            engine.clearPlayerPlaced(block);
        }
    }
}
