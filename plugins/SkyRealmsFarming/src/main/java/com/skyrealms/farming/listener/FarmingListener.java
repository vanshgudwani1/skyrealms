package com.skyrealms.farming.listener;

import com.skyrealms.farming.gui.FarmingGui;
import com.skyrealms.farming.model.*;
import com.skyrealms.farming.service.*;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandPermission;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Event listener enforcing 5x5 ground validation on placement, fast right-click harvest,
 * Bio-Fuel overclocking, and safe core dismantling.
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

    /**
     * Handles custom Farming Core placement with strict 5x5 ground validation & auto-terraforming.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!FarmingCoreItemFactory.isFarmingCore(item)) {
            return;
        }

        Player player = event.getPlayer();
        Block placed = event.getBlockPlaced();

        FarmingGeneratorTier tier = FarmingCoreItemFactory.extractTier(item);
        Map<String, Integer> upgrades = FarmingCoreItemFactory.extractUpgrades(item);

        generatorManager.createAndTerraformGenerator(player, placed.getLocation(), tier, upgrades)
                .thenAccept(res -> {
                    if (!res.valid()) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("⚠ Placement Failed: ", NamedTextColor.RED, TextDecoration.BOLD)
                                .append(Component.text(res.message(), NamedTextColor.YELLOW)));
                        player.playSound(placed.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 1. Check if broken block is a Generator Crop Node
        Optional<FarmingGeneratorManager.NodeEntry> optNode = generatorManager.findNodeAt(block.getLocation());
        if (optNode.isPresent()) {
            FarmingGeneratorManager.NodeEntry entry = optNode.get();

            Optional<Island> optIsland = islandService.getIslandAt(block.getLocation());
            if (optIsland.isPresent()) {
                Island island = optIsland.get();
                if (!island.hasPermission(player.getUniqueId(), IslandPermission.BLOCK_BREAK) && !player.hasPermission("skyrealms.admin")) {
                    player.sendMessage(Component.text("⛔ You do not have permission to harvest on this island.", NamedTextColor.RED));
                    event.setCancelled(true);
                    return;
                }
            }

            event.setDropItems(false);
            event.setExpToDrop(0);

            boolean success = processor.handleHarvest(player, entry.generator(), entry.node(), block);
            if (!success) {
                event.setCancelled(true);
            }
            return;
        }

        // 2. Check if broken block is Farmland / Soil beneath an active Generator Crop Node
        Optional<FarmingGeneratorManager.NodeEntry> optNodeAbove = generatorManager.findNodeAt(block.getLocation().clone().add(0, 1, 0));
        if (optNodeAbove.isPresent()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("⛔ You cannot break the farmland of an active Farming Generator! Dismantle the Core with sneak-break or via the /farm menu to remove it.", NamedTextColor.RED));
            player.playSound(block.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 3. Check if broken block is the sub-surface irrigation block beneath the Core
        Optional<FarmingGenerator> optCoreAbove = generatorManager.getGeneratorAtCore(block.getLocation().clone().add(0, 1, 0));
        if (optCoreAbove.isPresent()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("⛔ You cannot break the irrigation water beneath an active Farming Generator!", NamedTextColor.RED));
            player.playSound(block.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 4. Check if broken block is a Farming Core (Dismantle & Refund)
        Optional<FarmingGenerator> optCore = generatorManager.getGeneratorAtCore(block.getLocation());
        if (optCore.isPresent()) {
            FarmingGenerator gen = optCore.get();
            Optional<Island> optIsland = islandService.getIslandAt(block.getLocation());
            if (optIsland.isPresent()) {
                Island island = optIsland.get();
                if (!island.isMember(player.getUniqueId()) && !player.hasPermission("skyrealms.admin")) {
                    player.sendMessage(Component.text("⛔ Only island members can dismantle a Farming Core.", NamedTextColor.RED));
                    event.setCancelled(true);
                    return;
                }
            }

            event.setDropItems(false);
            event.setExpToDrop(0);

            // Extract upgrades to preserve in item
            Map<String, Integer> preservedUpgrades = new HashMap<>();
            for (GeneratorUpgrade u : gen.upgrades().values()) {
                preservedUpgrades.put(u.key(), u.level());
            }

            // Refund custom core item
            ItemStack refundedCore = FarmingCoreItemFactory.createCoreItem(gen.tier(), preservedUpgrades);
            player.getWorld().dropItemNaturally(block.getLocation(), refundedCore);

            generatorManager.removeGenerator(gen.generatorId());
            player.sendMessage(Component.text("✦ Farming Generator dismantled! Core returned with upgrades preserved.", NamedTextColor.GOLD));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();

        // 1. Check if right-clicking a crop node (Fast-Harvest)
        Optional<FarmingGeneratorManager.NodeEntry> optNode = generatorManager.findNodeAt(clicked.getLocation());
        if (optNode.isPresent()) {
            if (clicked.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
                event.setCancelled(true);
                processor.handleHarvest(player, optNode.get().generator(), optNode.get().node(), clicked);
                return;
            }
        }

        // 2. Check if right-clicking a Farming Core
        Optional<FarmingGenerator> optCore = generatorManager.getGeneratorAtCore(clicked.getLocation());
        if (optCore.isPresent()) {
            event.setCancelled(true);
            FarmingGenerator gen = optCore.get();

            ItemStack handItem = player.getInventory().getItemInMainHand();

            // Bio-Fuel / Bone Meal Overclocking
            if (handItem.getType() == Material.BONE_MEAL) {
                handItem.subtract(1);
                generatorManager.overclockManager().activateOverclock(player, gen, 300); // 5 minutes
                return;
            }

            // Otherwise open GUI
            FarmingGui.openGeneratorDetails(player, gen, farmingService);
            return;
        }

        // 3. Prevent shovel dirt path creation on generator farmland
        if (clicked.getType() == Material.FARMLAND && player.getInventory().getItemInMainHand().getType().name().endsWith("_SHOVEL")) {
            if (generatorManager.findNodeAt(clicked.getLocation().clone().add(0, 1, 0)).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Block b = event.getClickedBlock();
            if (b != null && b.getType() == Material.FARMLAND) {
                if (generatorManager.findNodeAt(b.getLocation().clone().add(0, 1, 0)).isPresent()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        Block b = event.getBlock();
        if (b.getType() == Material.FARMLAND) {
            if (generatorManager.findNodeAt(b.getLocation().clone().add(0, 1, 0)).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandFade(BlockFadeEvent event) {
        Block b = event.getBlock();
        if (b.getType() == Material.FARMLAND) {
            if (generatorManager.findNodeAt(b.getLocation().clone().add(0, 1, 0)).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    // --- Anti-Exploit Handlers ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
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
