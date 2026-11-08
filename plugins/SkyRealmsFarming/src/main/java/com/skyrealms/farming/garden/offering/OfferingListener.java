package com.skyrealms.farming.garden.offering;

import com.skyrealms.farming.garden.cinematic.DivineCinematicService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Event listener managing offering chest interactions, hopper protection, and automated submission triggers.
 */
public final class OfferingListener implements Listener {

    public static final String OFFERING_CHEST_NAME = "Offering to the Gods";

    private final OfferingService offeringService;
    private final IslandService islandService;
    private final DivineCinematicService cinematicService;

    public OfferingListener(
            @NotNull OfferingService offeringService,
            @NotNull IslandService islandService,
            @NotNull DivineCinematicService cinematicService
    ) {
        this.offeringService = Objects.requireNonNull(offeringService, "offeringService cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.cinematicService = Objects.requireNonNull(cinematicService, "cinematicService cannot be null");
    }

    private boolean isOfferingChest(InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            String name = PlainTextComponentSerializer.plainText().serialize(chest.customName() != null ? chest.customName() : Component.empty());
            return name.toLowerCase().contains(OFFERING_CHEST_NAME.toLowerCase());
        }
        if (holder instanceof DoubleChest doubleChest) {
            if (doubleChest.getLeftSide() instanceof Chest left) {
                String name = PlainTextComponentSerializer.plainText().serialize(left.customName() != null ? left.customName() : Component.empty());
                if (name.toLowerCase().contains(OFFERING_CHEST_NAME.toLowerCase())) return true;
            }
        }
        return false;
    }

    private Block getChestBlock(InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            return chest.getBlock();
        }
        if (holder instanceof DoubleChest doubleChest && doubleChest.getLeftSide() instanceof Chest left) {
            return left.getBlock();
        }
        return null;
    }

    /**
     * When a player closes an Offering to the Gods chest, check and trigger submission.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (!isOfferingChest(holder)) return;

        if (!(event.getPlayer() instanceof Player player)) return;

        Block chestBlock = getChestBlock(holder);
        if (chestBlock == null) return;

        Optional<Island> optIsland = islandService.getIslandByPlayer(player.getUniqueId());
        if (optIsland.isEmpty()) return;

        Island island = optIsland.get();
        if (offeringService.isGardenUnlocked(island.id())) {
            player.sendMessage(Component.text("✦ The Gods have already accepted your offering! Access your Garden with /garden.", NamedTextColor.GREEN));
            return;
        }

        OfferingService.OfferingValidationResult result = offeringService.validateInventory(inv);
        if (result.valid()) {
            player.sendMessage(Component.text("✦ The Gods acknowledge your sacred tribute! Beginning ritual...", NamedTextColor.GOLD));
            offeringService.processOffering(player, chestBlock, island.id());
        } else {
            player.sendMessage(Component.text("✦ Offering to the Gods: Deposit 3x Enchanted Hay Bales, 3x Enchanted Carrots, and 3x Enchanted Sugar Cane.", NamedTextColor.YELLOW));
        }
    }

    /**
     * Hopper Anti-Exploit: Prevents hoppers from piping items in or out of an Offering chest.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMoveItem(InventoryMoveItemEvent event) {
        if (isOfferingChest(event.getSource().getHolder()) || isOfferingChest(event.getDestination().getHolder())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents breaking the offering chest while the cinematic is running.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof Chest chest) {
            if (isOfferingChest(chest)) {
                Optional<Island> optIsland = islandService.getIslandByPlayer(event.getPlayer().getUniqueId());
                if (optIsland.isPresent() && cinematicService.isCinematicActive(optIsland.get().id())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(Component.text("⛔ You cannot disturb the Offering to the Gods during the divine ritual!", NamedTextColor.RED));
                }
            }
        }
    }
}
