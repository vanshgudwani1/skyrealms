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
     * Legacy Farming Core placement interceptor: completely prevents activating retired generator mechanics.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (FarmingCoreItemFactory.isFarmingCore(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage(Component.text("✖ Farming Generators have been retired in favor of the SkyRealms Garden!", NamedTextColor.RED, TextDecoration.BOLD));
            player.sendMessage(Component.text("Type /garden home or /is farm to enter your greenhouse dome.", NamedTextColor.YELLOW));
            player.playSound(event.getBlockPlaced().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if broken block is a legacy Farming Core (allow safe dismantle and cleanup)
        Optional<FarmingGenerator> optCore = generatorManager.getGeneratorAtCore(block.getLocation());
        if (optCore.isPresent()) {
            FarmingGenerator gen = optCore.get();
            Optional<Island> optIsland = islandService.getIslandAt(block.getLocation());
            if (optIsland.isPresent()) {
                Island island = optIsland.get();
                if (!island.isMember(player.getUniqueId()) && !player.hasPermission("skyrealms.admin")) {
                    player.sendMessage(Component.text("⛔ Only island members can dismantle a legacy Farming Core.", NamedTextColor.RED));
                    event.setCancelled(true);
                    return;
                }
            }

            generatorManager.removeGenerator(gen.generatorId());
            player.sendMessage(Component.text("✦ Legacy Farming Core removed and deregistered.", NamedTextColor.GOLD));
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

        // Check if right-clicking a legacy Farming Core
        Optional<FarmingGenerator> optCore = generatorManager.getGeneratorAtCore(clicked.getLocation());
        if (optCore.isPresent()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("🌾 Farming Generators have been retired in favor of SkyRealms Garden. Type /garden home to access your Garden!", NamedTextColor.GOLD));
        }
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
