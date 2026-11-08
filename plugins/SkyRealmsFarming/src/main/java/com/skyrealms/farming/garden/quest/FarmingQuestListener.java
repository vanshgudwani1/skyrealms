package com.skyrealms.farming.garden.quest;

import com.skyrealms.farming.event.GardenCropHarvestEvent;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.platform.api.event.EventBus;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Listener tracking player agricultural actions inside the Garden to advance active quests.
 */
public final class FarmingQuestListener implements Listener {

    private final GardenService gardenService;
    private final FarmingQuestService questService;

    public FarmingQuestListener(
            @NotNull GardenService gardenService,
            @NotNull FarmingQuestService questService,
            @NotNull EventBus eventBus
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.questService = Objects.requireNonNull(questService, "questService cannot be null");

        // Subscribe to GardenCropHarvestEvent from EventBus
        eventBus.subscribe(GardenCropHarvestEvent.class, event -> {
            if (!event.isCancelled()) {
                questService.recordHarvest(event.player(), event.cropType());
            }
        });
    }

    private boolean isHoe(Material mat) {
        return mat != null && mat.name().endsWith("_HOE");
    }

    private boolean isTillable(Material mat) {
        return mat == Material.DIRT || mat == Material.GRASS_BLOCK || mat == Material.DIRT_PATH || mat == Material.COARSE_DIRT || mat == Material.ROOTED_DIRT;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHoeUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (!gardenService.isGardenWorld(clicked.getWorld())) return;

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!isHoe(handItem.getType())) return;

        if (isTillable(clicked.getType())) {
            Optional<GardenPlot> optPlot = gardenService.getPlotAt(clicked.getLocation());
            if (optPlot.isPresent() && optPlot.get().unlocked()) {
                questService.recordTill(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSeedPlant(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!gardenService.isGardenWorld(placed.getWorld())) return;

        Material mat = placed.getType();
        if (mat == Material.WHEAT) {
            Optional<GardenPlot> optPlot = gardenService.getPlotAt(placed.getLocation());
            if (optPlot.isPresent() && optPlot.get().unlocked()) {
                questService.recordPlant(event.getPlayer(), mat);
            }
        }
    }
}
