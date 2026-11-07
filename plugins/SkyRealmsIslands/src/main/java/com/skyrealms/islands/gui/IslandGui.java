package com.skyrealms.islands.gui;

import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandMember;
import com.skyrealms.islands.model.IslandRole;
import com.skyrealms.islands.model.IslandSettings;
import com.skyrealms.islands.service.IslandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Premium, production-grade Celestial Cherry Blossom Island GUI Hub.
 * Features an aesthetic 54-slot main control center, animated audio feedback,
 * multi-tier upgrades catalog, dynamic settings toggles, team roster, and public island browser.
 */
public final class IslandGui implements Listener {

    private static final Component TITLE_MAIN = Component.text("🌸 SkyRealms ✦ Cherry Island Hub", TextColor.fromHexString("#FF77A8"), TextDecoration.BOLD);
    private static final Component TITLE_UPGRADES = Component.text("🌸 Island Upgrades ✦ Celestial Tiers", TextColor.fromHexString("#FFAAA6"), TextDecoration.BOLD);
    private static final Component TITLE_SETTINGS = Component.text("🌸 Island Settings ✦ Privacy & Flags", TextColor.fromHexString("#FFB7B2"), TextDecoration.BOLD);
    private static final Component TITLE_MEMBERS = Component.text("🌸 Island Members ✦ Team Co-Op", TextColor.fromHexString("#E2F0CB"), TextDecoration.BOLD);
    private static final Component TITLE_WARPS = Component.text("🌸 Public Islands ✦ Celestial Warps", TextColor.fromHexString("#B5EAD7"), TextDecoration.BOLD);

    private final IslandManager islandManager;

    public IslandGui(@NotNull IslandManager islandManager) {
        this.islandManager = Objects.requireNonNull(islandManager, "islandManager cannot be null");
    }

    public void openMenu(@NotNull Player player) {
        openMainMenu(player);
    }

    // =============================================================================================
    // 1. MAIN ISLAND HUB (54 Slots)
    // =============================================================================================

    public void openMainMenu(@NotNull Player player) {
        Optional<Island> opt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage(Component.text("🌸 You do not have an island yet! Use ", TextColor.fromHexString("#FF77A8"))
                    .append(Component.text("/island create", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" to begin your celestial adventure.", TextColor.fromHexString("#FF77A8"))));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Island island = opt.get();
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MAIN);

        // Frame decorative glass
        fillBorders(inv, Material.PINK_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE);

        // Slot 4: Player Island Overview (Custom Player Skull)
        ItemStack profile = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) profile.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.displayName(Component.text("✦ " + (island.name() != null ? island.name() : "Cherry Island") + " ✦", TextColor.fromHexString("#FF77A8"), TextDecoration.BOLD));
            skullMeta.lore(List.of(
                    Component.text("● Status: ", NamedTextColor.GRAY).append(Component.text("ACTIVE", NamedTextColor.GREEN, TextDecoration.BOLD)),
                    Component.text("● Owner: ", NamedTextColor.GRAY).append(Component.text(getOwnerName(island.ownerUuid()), NamedTextColor.GOLD)),
                    Component.text("● Island Level: ", NamedTextColor.GRAY).append(Component.text("Lv. " + island.level(), TextColor.fromHexString("#FFAAA6"), TextDecoration.BOLD)),
                    Component.text("● Boundary Size: ", NamedTextColor.GRAY).append(Component.text(island.size() + "x" + island.size() + " blocks", NamedTextColor.YELLOW)),
                    Component.text("● Co-Op Members: ", NamedTextColor.GRAY).append(Component.text(island.members().size() + " players", NamedTextColor.AQUA)),
                    Component.text("● Coordinates: ", NamedTextColor.GRAY).append(Component.text(island.location().centerX() + ", " + island.location().centerZ(), NamedTextColor.WHITE)),
                    Component.empty(),
                    Component.text("🌸 Celestial Cherry Blossom Sanctuary", NamedTextColor.LIGHT_PURPLE, TextDecoration.ITALIC)
            ));
            profile.setItemMeta(skullMeta);
        }
        inv.setItem(4, profile);

        // Slot 20: Teleport Home
        inv.setItem(20, createItem(
                Material.CHERRY_DOOR,
                Component.text("✦ Warp to Island Home ✦", NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(
                        Component.text("Instantly teleport to your floating cherry sanctuary.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to Teleport!", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                true
        ));

        // Slot 22: Island Upgrades
        inv.setItem(22, createItem(
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                Component.text("✦ Celestial Upgrades ✦", TextColor.fromHexString("#FFAAA6"), TextDecoration.BOLD),
                List.of(
                        Component.text("Expand your island borders and unlock co-op tiers.", NamedTextColor.GRAY),
                        Component.text("Current Size: ", NamedTextColor.GRAY).append(Component.text(island.size() + "x" + island.size(), NamedTextColor.YELLOW)),
                        Component.empty(),
                        Component.text("▶ Click to View Upgrades Catalog", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                true
        ));

        // Slot 24: Island Settings
        inv.setItem(24, createItem(
                Material.COMPARATOR,
                Component.text("✦ Privacy & Settings ✦", NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(
                        Component.text("Configure visitor permissions, PvP, and privacy flags.", NamedTextColor.GRAY),
                        Component.text("● Public Visits: ", NamedTextColor.GRAY).append(island.settings().publicVisits() ? Component.text("ENABLED", NamedTextColor.GREEN) : Component.text("DISABLED", NamedTextColor.RED)),
                        Component.text("● Island PvP: ", NamedTextColor.GRAY).append(island.settings().pvpEnabled() ? Component.text("ENABLED", NamedTextColor.GREEN) : Component.text("DISABLED", NamedTextColor.RED)),
                        Component.empty(),
                        Component.text("▶ Click to Configure Settings", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                false
        ));

        // Slot 30: Team Roster
        inv.setItem(30, createItem(
                Material.PLAYER_HEAD,
                Component.text("✦ Island Team Roster ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                List.of(
                        Component.text("Manage team members, permissions, and invitations.", NamedTextColor.GRAY),
                        Component.text("Team Size: ", NamedTextColor.GRAY).append(Component.text(island.members().size() + " members", NamedTextColor.WHITE)),
                        Component.empty(),
                        Component.text("▶ Click to View Members", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                false
        ));

        // Slot 32: Boundary Visualizer
        inv.setItem(32, createItem(
                Material.SPECTRAL_ARROW,
                Component.text("✦ Boundary Border Visualizer ✦", TextColor.fromHexString("#FF77A8"), TextDecoration.BOLD),
                List.of(
                        Component.text("Projects an animated glowing pink particle perimeter", NamedTextColor.GRAY),
                        Component.text("around your exact island boundary line for 20 seconds.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to Toggle Border Particles!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                ),
                true
        ));

        // Slot 38: Public Warps Browser
        inv.setItem(38, createItem(
                Material.ENDER_EYE,
                Component.text("✦ Explore Public Islands ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                List.of(
                        Component.text("Browse and visit other players' celestial sanctuaries.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to Explore Warps", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                true
        ));

        // Slot 40: Rename Island
        inv.setItem(40, createItem(
                Material.NAME_TAG,
                Component.text("✦ Rename Island ✦", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(
                        Component.text("Give your celestial island a custom name.", NamedTextColor.GRAY),
                        Component.text("Current: ", NamedTextColor.GRAY).append(Component.text(island.name() != null ? island.name() : "Island", NamedTextColor.WHITE)),
                        Component.empty(),
                        Component.text("Use /is name <new_name> in chat!", NamedTextColor.GRAY, TextDecoration.ITALIC)
                ),
                false
        ));

        // Slot 42: Reset Island
        inv.setItem(42, createItem(
                Material.BARRIER,
                Component.text("✦ Reset Island ✦", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(
                        Component.text("Resets and regenerates your starter cherry sanctuary.", NamedTextColor.GRAY),
                        Component.text("⚠ Warning: Deletes all placed blocks on current island!", NamedTextColor.RED),
                        Component.empty(),
                        Component.text("Use /is reset in chat to confirm.", NamedTextColor.GRAY, TextDecoration.ITALIC)
                ),
                false
        ));

        // Slot 49: Close
        inv.setItem(49, createItem(
                Material.STRUCTURE_VOID,
                Component.text("Close Menu", NamedTextColor.RED),
                List.of(),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    // =============================================================================================
    // 2. UPGRADES MENU (54 Slots)
    // =============================================================================================

    public void openUpgradesMenu(@NotNull Player player) {
        Optional<Island> opt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) return;

        Island island = opt.get();
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_UPGRADES);
        fillBorders(inv, Material.PINK_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        int currentSize = island.size();
        int[] tiers = {100, 120, 140, 160, 200};
        double[] costs = {0, 1000, 5000, 15000, 50000};
        int currentTier = 1;
        for (int i = 0; i < tiers.length; i++) {
            if (currentSize >= tiers[i]) {
                currentTier = i + 1;
            }
        }

        // Header info at slot 4
        inv.setItem(4, createItem(
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                Component.text("✦ Island Boundary Size Expansion ✦", TextColor.fromHexString("#FFAAA6"), TextDecoration.BOLD),
                List.of(
                        Component.text("Current Size: ", NamedTextColor.GRAY).append(Component.text(currentSize + "x" + currentSize + " blocks", NamedTextColor.YELLOW, TextDecoration.BOLD)),
                        Component.text("Tier: ", NamedTextColor.GRAY).append(Component.text("Tier " + currentTier + " / 5", NamedTextColor.GOLD)),
                        Component.empty(),
                        Component.text("Expand your protected building and automation radius!", NamedTextColor.GRAY)
                ),
                true
        ));

        // Size tiers display on slots 20, 21, 22, 23, 24
        int[] slots = {20, 21, 22, 23, 24};
        for (int i = 0; i < tiers.length; i++) {
            int tierNum = i + 1;
            int size = tiers[i];
            double cost = costs[i];
            int slot = slots[i];

            Material mat;
            Component name;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Size: " + size + "x" + size + " blocks", NamedTextColor.YELLOW));

            if (tierNum < currentTier) {
                mat = Material.EMERALD_BLOCK;
                name = Component.text("✔ Tier " + tierNum + " (Unlocked)", NamedTextColor.GREEN, TextDecoration.BOLD);
                lore.add(Component.text("Status: Already Unlocked", NamedTextColor.GRAY));
            } else if (tierNum == currentTier) {
                mat = Material.BEACON;
                name = Component.text("★ Tier " + tierNum + " (Current Active)", TextColor.fromHexString("#FF77A8"), TextDecoration.BOLD);
                lore.add(Component.text("Status: Current Active Tier", NamedTextColor.LIGHT_PURPLE));
            } else if (tierNum == currentTier + 1) {
                mat = Material.GOLD_BLOCK;
                name = Component.text("⚡ Upgrade to Tier " + tierNum, NamedTextColor.GOLD, TextDecoration.BOLD);
                lore.add(Component.text("Cost: " + String.format("%,.0f", cost) + " Coins", NamedTextColor.WHITE));
                lore.add(Component.empty());
                lore.add(Component.text("▶ Click to Purchase Upgrade!", NamedTextColor.GREEN, TextDecoration.BOLD));
            } else {
                mat = Material.GRAY_CONCRETE;
                name = Component.text("🔒 Tier " + tierNum + " (Locked)", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
                lore.add(Component.text("Requires previous tier first.", NamedTextColor.RED));
            }

            inv.setItem(slot, createItem(mat, name, lore, tierNum == currentTier + 1));
        }

        // Back button at slot 45
        inv.setItem(45, createItem(
                Material.ARROW,
                Component.text("◀ Return to Main Menu", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    // =============================================================================================
    // 3. SETTINGS MENU (27 Slots)
    // =============================================================================================

    public void openSettingsMenu(@NotNull Player player) {
        Optional<Island> opt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) return;

        Island island = opt.get();
        IslandSettings s = island.settings();
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_SETTINGS);
        fillBorders27(inv, Material.PINK_STAINED_GLASS_PANE);

        // Slot 10: Public Visits
        inv.setItem(10, createItem(
                s.publicVisits() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                Component.text("✦ Public Visits: ", NamedTextColor.WHITE, TextDecoration.BOLD)
                        .append(s.publicVisits() ? Component.text("[ENABLED]", NamedTextColor.GREEN, TextDecoration.BOLD) : Component.text("[DISABLED]", NamedTextColor.RED, TextDecoration.BOLD)),
                List.of(
                        Component.text("Allows non-members to teleport and view your island.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to Toggle!", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                s.publicVisits()
        ));

        // Slot 12: Island PvP
        inv.setItem(12, createItem(
                Material.DIAMOND_SWORD,
                Component.text("✦ Island PvP: ", NamedTextColor.WHITE, TextDecoration.BOLD)
                        .append(s.pvpEnabled() ? Component.text("[ENABLED]", NamedTextColor.GREEN, TextDecoration.BOLD) : Component.text("[DISABLED]", NamedTextColor.RED, TextDecoration.BOLD)),
                List.of(
                        Component.text("Toggle player-versus-player combat on this island.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to Toggle!", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                s.pvpEnabled()
        ));

        // Slot 14: Visitor Block Break
        inv.setItem(14, createItem(
                Material.IRON_PICKAXE,
                Component.text("✦ Visitor Block Break: ", NamedTextColor.WHITE, TextDecoration.BOLD)
                        .append(s.visitorBlockBreak() ? Component.text("[ALLOWED]", NamedTextColor.GREEN, TextDecoration.BOLD) : Component.text("[BLOCKED]", NamedTextColor.RED, TextDecoration.BOLD)),
                List.of(
                        Component.text("Allow visitors to break blocks on your island.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to Toggle!", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                s.visitorBlockBreak()
        ));

        // Slot 16: Visitor Container Access
        inv.setItem(16, createItem(
                Material.CHEST,
                Component.text("✦ Visitor Container Access: ", NamedTextColor.WHITE, TextDecoration.BOLD)
                        .append(s.visitorContainerAccess() ? Component.text("[ALLOWED]", NamedTextColor.GREEN, TextDecoration.BOLD) : Component.text("[BLOCKED]", NamedTextColor.RED, TextDecoration.BOLD)),
                List.of(
                        Component.text("Allow visitors to open chests, barrels, and hoppers.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to Toggle!", NamedTextColor.YELLOW, TextDecoration.BOLD)
                ),
                s.visitorContainerAccess()
        ));

        // Back button at slot 18
        inv.setItem(18, createItem(
                Material.ARROW,
                Component.text("◀ Return to Main Menu", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    // =============================================================================================
    // 4. MEMBERS MENU (54 Slots)
    // =============================================================================================

    public void openMembersMenu(@NotNull Player player) {
        Optional<Island> opt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) return;

        Island island = opt.get();
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MEMBERS);
        fillBorders(inv, Material.MAGENTA_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        int slot = 10;
        for (IslandMember member : island.members().values()) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2;

            OfflinePlayer op = Bukkit.getOfflinePlayer(member.playerUuid());
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(op);
                meta.displayName(Component.text(op.getName() != null ? op.getName() : "Member", NamedTextColor.GOLD, TextDecoration.BOLD));
                meta.lore(List.of(
                        Component.text("● Role: ", NamedTextColor.GRAY).append(getRoleBadge(member.role())),
                        Component.text("● Joined: ", NamedTextColor.GRAY).append(Component.text(member.joinedAt().toString().substring(0, 10), NamedTextColor.WHITE)),
                        Component.empty(),
                        Component.text(op.isOnline() ? "● Status: Online" : "● Status: Offline", op.isOnline() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                ));
                skull.setItemMeta(meta);
            }
            inv.setItem(slot++, skull);
        }

        // Slot 49: Back button
        inv.setItem(49, createItem(
                Material.ARROW,
                Component.text("◀ Return to Main Menu", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(),
                false
        ));

        // Slot 53: Invite button
        inv.setItem(53, createItem(
                Material.EMERALD,
                Component.text("✦ Invite Friend ✦", NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(
                        Component.text("Invite an online player to join your island co-op.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Use /is invite <player> in chat!", NamedTextColor.YELLOW)
                ),
                true
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    // =============================================================================================
    // 5. PUBLIC ISLANDS WARPS BROWSER (54 Slots)
    // =============================================================================================

    public void openPublicIslandsMenu(@NotNull Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_WARPS);
        fillBorders(inv, Material.PINK_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        int slot = 10;
        for (Island island : islandManager.getAllActiveIslands()) {
            if (!island.settings().publicVisits()) continue;
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2;

            OfflinePlayer owner = Bukkit.getOfflinePlayer(island.ownerUuid());
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(owner);
                meta.displayName(Component.text(island.name() != null ? island.name() : (owner.getName() + "'s Island"), TextColor.fromHexString("#FF77A8"), TextDecoration.BOLD));
                meta.lore(List.of(
                        Component.text("● Owner: ", NamedTextColor.GRAY).append(Component.text(owner.getName() != null ? owner.getName() : "Unknown", NamedTextColor.GOLD)),
                        Component.text("● Level: ", NamedTextColor.GRAY).append(Component.text("Lv. " + island.level(), TextColor.fromHexString("#FFAAA6"))),
                        Component.text("● Size: ", NamedTextColor.GRAY).append(Component.text(island.size() + "x" + island.size(), NamedTextColor.YELLOW)),
                        Component.empty(),
                        Component.text("▶ Click to Visit This Island!", NamedTextColor.GREEN, TextDecoration.BOLD)
                ));
                skull.setItemMeta(meta);
            }
            inv.setItem(slot++, skull);
        }

        // Slot 49: Back button
        inv.setItem(49, createItem(
                Material.ARROW,
                Component.text("◀ Return to Main Menu", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    // =============================================================================================
    // 6. EVENT HANDLER & INTERACTION ROUTER
    // =============================================================================================

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Component title = event.getView().title();

        if (TITLE_MAIN.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            handleMainMenuClick(player, slot);
        } else if (TITLE_UPGRADES.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            handleUpgradesMenuClick(player, slot);
        } else if (TITLE_SETTINGS.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            handleSettingsMenuClick(player, slot);
        } else if (TITLE_MEMBERS.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 49) {
                openMainMenu(player);
            }
        } else if (TITLE_WARPS.equals(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 49) {
                openMainMenu(player);
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    OfflinePlayer targetOwner = meta.getOwningPlayer();
                    Optional<Island> targetIsland = islandManager.getIslandByPlayer(targetOwner.getUniqueId());
                    if (targetIsland.isPresent()) {
                        player.closeInventory();
                        islandManager.visitIsland(player, targetIsland.get().id());
                    }
                }
            }
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        Optional<Island> opt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) return;
        Island island = opt.get();

        switch (slot) {
            case 20 -> { // Warp Home
                player.closeInventory();
                islandManager.teleportHome(player);
            }
            case 22 -> openUpgradesMenu(player); // Upgrades
            case 24 -> openSettingsMenu(player); // Settings
            case 30 -> openMembersMenu(player);  // Members
            case 32 -> { // Border Visualizer
                player.closeInventory();
                islandManager.effectsService().toggleBorderVisualizer(player, island);
            }
            case 38 -> openPublicIslandsMenu(player); // Public Warps
            case 40 -> { // Rename
                player.closeInventory();
                player.sendMessage(Component.text("🌸 To rename your island, type: ", TextColor.fromHexString("#FF77A8"))
                        .append(Component.text("/is name <New Name>", NamedTextColor.YELLOW, TextDecoration.BOLD)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
            }
            case 42 -> { // Reset
                player.closeInventory();
                player.sendMessage(Component.text("⚠ To reset your island back to starter cherry platform, type: ", NamedTextColor.RED)
                        .append(Component.text("/is reset", NamedTextColor.YELLOW, TextDecoration.BOLD)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            }
            case 49 -> player.closeInventory();
        }
    }

    private void handleUpgradesMenuClick(Player player, int slot) {
        if (slot == 45) {
            openMainMenu(player);
            return;
        }

        Optional<Island> opt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) return;
        Island island = opt.get();

        int[] slots = {20, 21, 22, 23, 24};
        int clickedIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                clickedIndex = i;
                break;
            }
        }

        if (clickedIndex != -1) {
            int targetTier = clickedIndex + 1;
            int currentSize = island.size();
            int[] tiers = {100, 120, 140, 160, 200};
            final int targetSize = tiers[clickedIndex];
            int currentTier = 1;
            for (int i = 0; i < tiers.length; i++) {
                if (currentSize >= tiers[i]) currentTier = i + 1;
            }

            if (targetTier == currentTier + 1) {
                player.sendMessage(Component.text("🌸 Purchasing Island Expansion...", TextColor.fromHexString("#FF77A8")));
                islandManager.upgradeIsland(island.id(), "size").thenAccept(success -> {
                    Bukkit.getScheduler().runTask(islandManager.effectsService() != null ? Bukkit.getPluginManager().getPlugin("SkyRealmsIslands") : null, () -> {
                        if (success) {
                            player.sendMessage(Component.text("✔ Island size expanded to " + targetSize + "x" + targetSize + " blocks!", NamedTextColor.GREEN, TextDecoration.BOLD));
                            openUpgradesMenu(player);
                        } else {
                            player.sendMessage(Component.text("✖ You do not have enough Coins to purchase this upgrade!", NamedTextColor.RED));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        }
                    });
                });
            } else if (targetTier <= currentTier) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.2f);
            } else {
                player.sendMessage(Component.text("✖ You must purchase the preceding tier first!", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    private void handleSettingsMenuClick(Player player, int slot) {
        if (slot == 18) {
            openMainMenu(player);
            return;
        }

        Optional<Island> opt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (opt.isEmpty()) return;
        Island island = opt.get();
        IslandSettings current = island.settings();

        IslandSettings updated = null;
        switch (slot) {
            case 10 -> updated = new IslandSettings(!current.publicVisits(), current.visitorBlockBreak(), current.visitorBlockPlace(), current.visitorContainerAccess(), current.pvpEnabled(), current.settingsJson());
            case 12 -> updated = new IslandSettings(current.publicVisits(), current.visitorBlockBreak(), current.visitorBlockPlace(), current.visitorContainerAccess(), !current.pvpEnabled(), current.settingsJson());
            case 14 -> updated = new IslandSettings(current.publicVisits(), !current.visitorBlockBreak(), current.visitorBlockPlace(), current.visitorContainerAccess(), current.pvpEnabled(), current.settingsJson());
            case 16 -> updated = new IslandSettings(current.publicVisits(), current.visitorBlockBreak(), current.visitorBlockPlace(), !current.visitorContainerAccess(), current.pvpEnabled(), current.settingsJson());
        }

        if (updated != null) {
            islandManager.updateSettings(island.id(), updated).thenRun(() -> {
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SkyRealmsIslands"), () -> {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    openSettingsMenu(player);
                });
            });
        }
    }

    // =============================================================================================
    // Helper Formatters & Item Creators
    // =============================================================================================

    private static ItemStack createItem(Material mat, Component name, List<Component> lore, boolean glowing) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            if (glowing) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void fillBorders(Inventory inv, Material primary, Material secondary) {
        ItemStack pItem = createItem(primary, Component.text(" "), List.of(), false);
        ItemStack sItem = createItem(secondary, Component.text(" "), List.of(), false);

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, (i % 2 == 0) ? pItem : sItem);
            }
        }
    }

    private static void fillBorders27(Inventory inv, Material mat) {
        ItemStack item = createItem(mat, Component.text(" "), List.of(), false);
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, item);
            }
        }
    }

    private static Component getRoleBadge(IslandRole role) {
        return switch (role) {
            case LEADER -> Component.text("LEADER", NamedTextColor.GOLD, TextDecoration.BOLD);
            case OFFICER -> Component.text("OFFICER", NamedTextColor.AQUA, TextDecoration.BOLD);
            case MEMBER -> Component.text("MEMBER", NamedTextColor.GREEN);
            default -> Component.text("VISITOR", NamedTextColor.GRAY);
        };
    }

    private static String getOwnerName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }
}
