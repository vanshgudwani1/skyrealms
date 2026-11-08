package com.skyrealms.farming.listener;

import com.skyrealms.farming.gui.FarmingGui;
import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import com.skyrealms.farming.service.FarmingGeneratorManager;
import com.skyrealms.farming.service.FarmingGeneratorProcessor;
import com.skyrealms.farming.service.FarmingProgressionService;
import com.skyrealms.farming.service.FarmingService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Optional;

/**
 * Event listener enforcing anti-exploit protections, harvest routing, and GUI interactions.
 */
public final class FarmingListener implements Listener {

    private final FarmingService farmingService;
    private final FarmingGeneratorManager generatorManager;
    private final FarmingGeneratorProcessor processor;
    private final FarmingProgressionService progressionService;
    private final IslandService islandService;

    public FarmingListener(
            @NotNull FarmingService farmingService,
            @NotNull FarmingGeneratorProcessor processor,
            @NotNull IslandService islandService
    ) {
        this.farmingService = farmingService;
        this.generatorManager = farmingService.generatorManager();
        this.processor = processor;
        this.progressionService = farmingService.progressionService();
        this.islandService = islandService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 1. Check if broken block is a Generator Crop Node
        Optional<FarmingGeneratorManager.NodeEntry> optNode = generatorManager.findNodeAt(block.getLocation());
        if (optNode.isPresent()) {
            FarmingGeneratorManager.NodeEntry entry = optNode.get();

            // Verify Island Permission
            Optional<Island> optIsland = islandService.getIslandAt(block.getLocation());
            if (optIsland.isPresent()) {
                Island island = optIsland.get();
                if (!island.hasPermission(player.getUniqueId(), IslandPermission.BLOCK_BREAK) && !player.hasPermission("skyrealms.admin")) {
                    player.sendMessage(Component.text("⛔ You do not have permission to harvest on this island.", NamedTextColor.RED));
                    event.setCancelled(true);
                    return;
                }
            }

            // Always cancel vanilla drop items to prevent duplicate drops
            event.setDropItems(false);
            event.setExpToDrop(0);

            // Delegate to authoritative processor
            boolean success = processor.handleHarvest(player, entry.generator(), entry.node(), block);
            if (!success) {
                event.setCancelled(true);
            }
            return;
        }

        // 2. Check if broken block is a Farming Core
        Optional<FarmingGenerator> optCore = generatorManager.getGeneratorAtCore(block.getLocation());
        if (optCore.isPresent()) {
            FarmingGenerator gen = optCore.get();
            Optional<Island> optIsland = islandService.getIslandAt(block.getLocation());
            if (optIsland.isPresent()) {
                Island island = optIsland.get();
                if (!island.isMember(player.getUniqueId()) && !player.hasPermission("skyrealms.admin")) {
                    player.sendMessage(Component.text("⛔ Only island members can remove a Farming Core.", NamedTextColor.RED));
                    event.setCancelled(true);
                    return;
                }
            }

            // Deactivate and delete generator
            generatorManager.removeGenerator(gen.generatorId());
            player.sendMessage(Component.text("✦ Farming Generator removed successfully.", NamedTextColor.GOLD));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();

        // Check if player clicked a Farming Core
        Optional<FarmingGenerator> optCore = generatorManager.getGeneratorAtCore(clicked.getLocation());
        if (optCore.isPresent()) {
            event.setCancelled(true);
            FarmingGenerator gen = optCore.get();
            FarmingGui.openGeneratorDetails(player, gen, farmingService);
            return;
        }

        // Check if player is attempting to activate a new Farming Core on Lodestone
        if (clicked.getType() == Material.LODESTONE) {
            Optional<Island> optIsland = islandService.getIslandAt(clicked.getLocation());
            if (optIsland.isPresent() && optIsland.get().isMember(player.getUniqueId())) {
                // If not already active, attempt activation of Tier 1 Wheat Generator
                if (generatorManager.getGeneratorAtCore(clicked.getLocation()).isEmpty()) {
                    event.setCancelled(true);
                    farmingService.createGenerator(player, clicked.getLocation(), FarmingGeneratorTier.TIER_1_WHEAT)
                            .thenAccept(res -> {
                                if (res.valid()) {
                                    player.sendMessage(Component.text("🌱 Farming Generator activated successfully!", NamedTextColor.GREEN));
                                    player.playSound(clicked.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                                } else {
                                    player.sendMessage(Component.text("⚠ Generator activation failed: " + res.message(), NamedTextColor.RED));
                                    player.playSound(clicked.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                }
                            });
                }
            }
        }
    }

    // --- Anti-Exploit Handlers ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        // If fluid flows into an active generator node, cancel flow to prevent automatic washing
        if (generatorManager.findNodeAt(toBlock.getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (generatorManager.findNodeAt(b.getLocation()).isPresent() || generatorManager.getGeneratorAtCore(b.getLocation()).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (generatorManager.findNodeAt(b.getLocation()).isPresent() || generatorManager.getGeneratorAtCore(b.getLocation()).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> generatorManager.findNodeAt(b.getLocation()).isPresent() || generatorManager.getGeneratorAtCore(b.getLocation()).isPresent());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> generatorManager.findNodeAt(b.getLocation()).isPresent() || generatorManager.getGeneratorAtCore(b.getLocation()).isPresent());
    }

    // --- Player Lifecycle ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        progressionService.preloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        progressionService.evictPlayer(event.getPlayer().getUniqueId());
    }
}
