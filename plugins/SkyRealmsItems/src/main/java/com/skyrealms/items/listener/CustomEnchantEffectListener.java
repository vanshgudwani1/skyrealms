package com.skyrealms.items.listener;

import com.skyrealms.items.service.EliteEnchantsService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces runtime behavior for EliteEnchantments custom tool enchants:
 * <ul>
 *     <li><b>replenish</b>: prevents breaking immature seeds/stems/roots and auto-replants mature crops at age 0.</li>
 *     <li><b>telekinesis</b>: deposits block drops directly into the player's inventory.</li>
 * </ul>
 */
public final class CustomEnchantEffectListener implements Listener {

    private record BlockCoord(String world, int x, int y, int z) {
        static BlockCoord of(Block block) {
            return new BlockCoord(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }

    private record PendingReplant(UUID playerId, BlockData harvestedData, Material seedMaterial) {
    }

    private final Plugin plugin;
    private final EliteEnchantsService enchants;
    private final Map<BlockCoord, PendingReplant> pendingReplants = new ConcurrentHashMap<>();

    public CustomEnchantEffectListener(@NotNull Plugin plugin, @NotNull EliteEnchantsService enchants) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.enchants = Objects.requireNonNull(enchants, "enchants cannot be null");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProtectImmatureSeed(@NotNull BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!enchants.hasReplenish(tool)) {
            return;
        }
        if (EliteEnchantsService.isProtectedSeedOrRoot(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatureCropBroken(@NotNull BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!enchants.hasReplenish(tool)) {
            return;
        }

        Block block = event.getBlock();
        Material seedMat = EliteEnchantsService.seedMaterialFor(block.getType());
        if (seedMat == null) {
            return;
        }
        if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        BlockCoord coord = BlockCoord.of(block);
        BlockData harvestedData = block.getBlockData().clone();
        pendingReplants.put(coord, new PendingReplant(player.getUniqueId(), harvestedData, seedMat));

        // Fallback cleanup if no BlockDropItemEvent fires (e.g. dropItems=false): still replant cleanly from inventory.
        Bukkit.getScheduler().runTask(plugin, () -> {
            PendingReplant remaining = pendingReplants.remove(coord);
            if (remaining == null) {
                return;
            }
            Player online = Bukkit.getPlayer(remaining.playerId());
            if (online != null && EliteEnchantsService.consumeOneSeed(online, remaining.seedMaterial(), null)) {
                replantBlockIfValid(block, remaining.harvestedData());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(@NotNull BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BlockCoord coord = BlockCoord.of(block);
        PendingReplant pending = pendingReplants.remove(coord);

        // 1. Handle Replenish seed consumption + scheduled age-0 replant
        if (pending != null && pending.playerId().equals(player.getUniqueId())) {
            boolean consumed = consumeOneSeedFromEventOrInventory(player, pending.seedMaterial(), event);
            if (consumed) {
                Bukkit.getScheduler().runTask(plugin, () -> replantBlockIfValid(block, pending.harvestedData()));
            }
        }

        // 2. Handle Telekinesis direct-to-inventory delivery
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!enchants.hasTelekinesis(tool)) {
            return;
        }

        Iterator<Item> it = event.getItems().iterator();
        while (it.hasNext()) {
            Item itemEntity = it.next();
            ItemStack stack = itemEntity.getItemStack();
            if (stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                it.remove();
                continue;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack.clone());
            if (overflow.isEmpty()) {
                it.remove();
            } else {
                ItemStack firstLeftover = overflow.values().iterator().next();
                if (firstLeftover == null || firstLeftover.getAmount() <= 0) {
                    it.remove();
                } else {
                    itemEntity.setItemStack(firstLeftover);
                }
            }
        }
    }

    private static boolean consumeOneSeedFromEventOrInventory(
            @NotNull Player player,
            @NotNull Material seedMat,
            @NotNull BlockDropItemEvent event
    ) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        Iterator<Item> it = event.getItems().iterator();
        while (it.hasNext()) {
            Item entity = it.next();
            ItemStack stack = entity.getItemStack();
            if (stack.getType() == seedMat && stack.getAmount() > 0) {
                if (stack.getAmount() == 1) {
                    it.remove();
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                    entity.setItemStack(stack);
                }
                return true;
            }
        }
        return EliteEnchantsService.consumeOneSeed(player, seedMat, null);
    }

    private static void replantBlockIfValid(@NotNull Block block, @NotNull BlockData harvestedData) {
        if (block.getType() != Material.AIR) {
            return;
        }
        if (!EliteEnchantsService.hasValidSupportForReplant(block, harvestedData)) {
            return;
        }
        block.setBlockData(EliteEnchantsService.createReplantedData(harvestedData), true);
        EliteEnchantsService.playReplantSound(block, harvestedData.getMaterial());
    }
}
