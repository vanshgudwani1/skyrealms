package com.skyrealms.islands.gui;

import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Native inventory GUI fallback for island management.
 */
public final class IslandGui implements Listener {

    private static final Component TITLE = Component.text("SkyRealms — Island Menu", NamedTextColor.DARK_AQUA, TextDecoration.BOLD);
    private final IslandService islandService;

    public IslandGui(@NotNull IslandService islandService) {
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
    }

    public void openMenu(@NotNull Player player) {
        Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage(Component.text("You do not have an island yet! Use /island create to begin.", NamedTextColor.YELLOW));
            return;
        }

        Island island = opt.get();
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Fill background with border glass
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.GRAY), List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Slot 10: Home
        inv.setItem(10, createItem(
                Material.COMPASS,
                Component.text("Island Home", NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(
                        Component.text("Click to teleport to your island spawn.", NamedTextColor.GRAY)
                )
        ));

        // Slot 12: Info & Stats
        inv.setItem(12, createItem(
                Material.OAK_SAPLING,
                Component.text("Island Information", NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(
                        Component.text("Name: " + (island.name() != null ? island.name() : "Island"), NamedTextColor.WHITE),
                        Component.text("Size: " + island.size() + "x" + island.size(), NamedTextColor.YELLOW),
                        Component.text("Level: " + island.level(), NamedTextColor.GOLD),
                        Component.text("Members: " + (island.members().size()), NamedTextColor.GRAY)
                )
        ));

        // Slot 14: Upgrades
        inv.setItem(14, createItem(
                Material.ANVIL,
                Component.text("Island Upgrades", NamedTextColor.GOLD, TextDecoration.BOLD),
                List.of(
                        Component.text("Click to purchase size upgrades.", NamedTextColor.GRAY)
                )
        ));

        // Slot 16: Settings
        inv.setItem(16, createItem(
                Material.COMPARATOR,
                Component.text("Island Settings", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(
                        Component.text("Public Visits: " + (island.settings().publicVisits() ? "Enabled" : "Disabled"), NamedTextColor.WHITE),
                        Component.text("PvP: " + (island.settings().pvpEnabled() ? "Enabled" : "Disabled"), NamedTextColor.WHITE)
                )
        ));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().title())) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 10) {
            player.closeInventory();
            islandService.teleportHome(player);
        } else if (slot == 14) {
            player.closeInventory();
            Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
            if (opt.isPresent()) {
                player.sendMessage(Component.text("Attempting size upgrade...", NamedTextColor.GRAY));
                islandService.upgradeIsland(opt.get().id(), "size").thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("✔ Island size upgraded successfully!", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("✖ Upgrade failed. Check funds or maximum tier reached.", NamedTextColor.RED));
                    }
                });
            }
        }
    }

    private ItemStack createItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
