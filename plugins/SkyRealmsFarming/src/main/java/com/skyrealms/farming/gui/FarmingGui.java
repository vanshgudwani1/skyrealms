package com.skyrealms.farming.gui;

import com.skyrealms.farming.model.*;
import com.skyrealms.farming.service.FarmingProgressionService;
import com.skyrealms.farming.service.FarmingService;
import com.skyrealms.farming.service.FarmingUpgradeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;

/**
 * 54-slot interactive Cherry Farming GUI.
 */
public final class FarmingGui implements InventoryHolder, Listener {

    private final FarmingService farmingService;
    private final Inventory inventory;
    private final GuiType type;
    private final FarmingGenerator targetGenerator;

    public enum GuiType {
        MAIN_MENU,
        COLLECTIONS_MENU,
        GENERATORS_MENU,
        GENERATOR_DETAILS
    }

    public FarmingGui(@NotNull FarmingService farmingService, @NotNull GuiType type, @NotNull Component title, FarmingGenerator targetGenerator) {
        this.farmingService = farmingService;
        this.type = type;
        this.targetGenerator = targetGenerator;
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Opens the main 54-slot /farm menu.
     */
    public static void openMainMenu(@NotNull Player player, @NotNull FarmingService farmingService) {
        Component title = Component.text("🌸 SkyRealms Farming Sanctuary", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
        FarmingGui gui = new FarmingGui(farmingService, GuiType.MAIN_MENU, title, null);
        gui.populateMainMenu(player);
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_CHERRY_SAPLING_PLACE, 1.0f, 1.2f);
    }

    /**
     * Opens generator details & upgrade menu.
     */
    public static void openGeneratorDetails(@NotNull Player player, @NotNull FarmingGenerator generator, @NotNull FarmingService farmingService) {
        Component title = Component.text("⚡ " + generator.tier().displayName(), NamedTextColor.GOLD, TextDecoration.BOLD);
        FarmingGui gui = new FarmingGui(farmingService, GuiType.GENERATOR_DETAILS, title, generator);
        gui.populateGeneratorDetails(player, generator);
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
    }

    /**
     * Opens player collections menu.
     */
    public static void openCollectionsMenu(@NotNull Player player, @NotNull FarmingService farmingService) {
        Component title = Component.text("📚 Crop Collections", NamedTextColor.GOLD, TextDecoration.BOLD);
        FarmingGui gui = new FarmingGui(farmingService, GuiType.COLLECTIONS_MENU, title, null);
        gui.populateCollections(player);
        player.openInventory(gui.getInventory());
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
    }

    private void fillBorder(Material glass) {
        ItemStack item = new ItemStack(glass);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        for (int i = 0; i < 9; i++) inventory.setItem(i, item);
        for (int i = 45; i < 54; i++) inventory.setItem(i, item);
        for (int i = 9; i < 45; i += 9) {
            inventory.setItem(i, item);
            inventory.setItem(i + 8, item);
        }
    }

    private void populateMainMenu(Player player) {
        fillBorder(Material.PINK_STAINED_GLASS_PANE);

        FarmingPlayerProgress progress = farmingService.getPlayerProgress(player.getUniqueId());
        long currentLvlXp = FarmingProgressionService.getRequiredXpForLevel(progress.farmingLevel());
        long nextLvlXp = FarmingProgressionService.getRequiredXpForLevel(progress.farmingLevel() + 1);
        long inLevel = progress.farmingXp() - currentLvlXp;
        long needed = Math.max(1, nextLvlXp - currentLvlXp);

        // Player Head at Slot 4
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) skull.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(player);
            sm.displayName(Component.text(player.getName() + " — Farming Mastery", NamedTextColor.YELLOW, TextDecoration.BOLD));
            sm.lore(List.of(
                    Component.empty(),
                    Component.text("• Farming Level: ", NamedTextColor.GRAY).append(Component.text("Level " + progress.farmingLevel(), NamedTextColor.GREEN, TextDecoration.BOLD)),
                    Component.text("• Total Farming XP: ", NamedTextColor.GRAY).append(Component.text(String.format("%,d XP", progress.farmingXp()), NamedTextColor.AQUA)),
                    Component.text("• Progress: ", NamedTextColor.GRAY).append(Component.text(inLevel + " / " + needed + " XP", NamedTextColor.LIGHT_PURPLE)),
                    Component.text("• Total Crops Harvested: ", NamedTextColor.GRAY).append(Component.text(String.format("%,d", progress.totalHarvests()), NamedTextColor.GOLD)),
                    Component.empty()
            ));
            skull.setItemMeta(sm);
        }
        inventory.setItem(4, skull);

        // Slot 20: Crop Collections
        inventory.setItem(20, createIcon(
                Material.WHEAT,
                Component.text("🌾 Crop Collections", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(
                        Component.text("Track your lifetime harvests and milestone", NamedTextColor.GRAY),
                        Component.text("rewards for each unique crop variety.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to browse collections", NamedTextColor.YELLOW)
                )
        ));

        // Slot 22: Island Farming Generators
        inventory.setItem(22, createIcon(
                Material.LODESTONE,
                Component.text("⚡ Island Generators", NamedTextColor.GOLD, TextDecoration.BOLD),
                List.of(
                        Component.text("Inspect and manage your active physical", NamedTextColor.GRAY),
                        Component.text("farming generators on this island.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to view active generators", NamedTextColor.YELLOW)
                )
        ));

        // Slot 24: Generator Guide & Recipe
        inventory.setItem(24, createIcon(
                Material.KNOWLEDGE_BOOK,
                Component.text("📖 Construction Blueprint", NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(
                        Component.text("How to construct a Farming Generator:", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("1. Place a Lodestone as the central Core.", NamedTextColor.WHITE),
                        Component.text("2. Surround with Farmland in a 5x5 area.", NamedTextColor.WHITE),
                        Component.text("3. Place Water within 2 blocks for irrigation.", NamedTextColor.WHITE),
                        Component.text("4. Right-click Core to activate your generator!", NamedTextColor.WHITE),
                        Component.empty(),
                        Component.text("▶ Automatic crop regeneration is active!", NamedTextColor.GREEN)
                )
        ));

        // Close Icon at Slot 49
        inventory.setItem(49, createIcon(Material.BARRIER, Component.text("✖ Close Menu", NamedTextColor.RED, TextDecoration.BOLD), List.of()));
    }

    private void populateGeneratorDetails(Player player, FarmingGenerator generator) {
        fillBorder(Material.MAGENTA_STAINED_GLASS_PANE);

        FarmingUpgradeService upgradeService = farmingService.upgradeService();

        // Info at Slot 4
        inventory.setItem(4, createIcon(
                generator.tier().icon(),
                Component.text(generator.tier().displayName(), NamedTextColor.GOLD, TextDecoration.BOLD),
                List.of(
                        Component.text("• Status: ", NamedTextColor.GRAY).append(Component.text(generator.status().name(), NamedTextColor.GREEN)),
                        Component.text("• Location: ", NamedTextColor.GRAY).append(Component.text(generator.coreX() + ", " + generator.coreY() + ", " + generator.coreZ(), NamedTextColor.AQUA)),
                        Component.text("• Total Harvests: ", NamedTextColor.GRAY).append(Component.text(String.format("%,d", generator.totalHarvests()), NamedTextColor.YELLOW)),
                        Component.text("• Active Crop Nodes: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(generator.cropNodes().size()), NamedTextColor.LIGHT_PURPLE))
                )
        ));

        // Slot 19: SPEED
        addUpgradeButton(19, GeneratorUpgrade.KEY_SPEED, "⚡ Production Speed", Material.SUGAR, generator, upgradeService);

        // Slot 21: YIELD
        addUpgradeButton(21, GeneratorUpgrade.KEY_YIELD, "🌿 Crop Yield", Material.BONE_MEAL, generator, upgradeService);

        // Slot 23: MUTATION
        addUpgradeButton(23, GeneratorUpgrade.KEY_MUTATION, "✦ Mutation Chance", Material.AMETHYST_SHARD, generator, upgradeService);

        // Slot 25: XP BOOST
        addUpgradeButton(25, GeneratorUpgrade.KEY_XP_BOOST, "⭐ Farming XP Booster", Material.EXPERIENCE_BOTTLE, generator, upgradeService);

        // Slot 38: Evolve Generator Tier
        FarmingGeneratorTier nextTier = generator.tier().nextTier();
        if (nextTier != null) {
            inventory.setItem(38, createIcon(
                    Material.NETHER_STAR,
                    Component.text("🌟 Evolve to " + nextTier.displayName(), NamedTextColor.GOLD, TextDecoration.BOLD),
                    List.of(
                            Component.text("• Required Level: ", NamedTextColor.GRAY).append(Component.text("Level " + nextTier.requiredFarmingLevel(), NamedTextColor.YELLOW, TextDecoration.BOLD)),
                            Component.text("• Evolution Cost: ", NamedTextColor.GRAY).append(Component.text(nextTier.evolutionCost() + " Coins", NamedTextColor.GOLD, TextDecoration.BOLD)),
                            Component.text("• Transforms all farmland crop nodes to " + nextTier.displayName() + "!", NamedTextColor.AQUA),
                            Component.empty(),
                            Component.text("▶ Click to evolve generator tier", NamedTextColor.GREEN)
                    )
            ));
        } else {
            inventory.setItem(38, createIcon(
                    Material.NETHER_STAR,
                    Component.text("★ MAXIMUM TIER (MYSTIC) ★", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    List.of(
                            Component.text("This generator has achieved maximum arcane mastery.", NamedTextColor.GRAY)
                    )
            ));
        }

        // Slot 42: Dismantle & Refund
        inventory.setItem(42, createIcon(
                Material.TNT_MINECART,
                Component.text("✖ Dismantle & Refund Core", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(
                        Component.text("Safely deactivates this generator.", NamedTextColor.GRAY),
                        Component.text("Your Farming Core will be returned to your", NamedTextColor.GRAY),
                        Component.text("inventory with all upgrades preserved.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("▶ Click to dismantle generator", NamedTextColor.RED)
                )
        ));

        // Return button at Slot 49
        inventory.setItem(49, createIcon(Material.ARROW, Component.text("⬅ Back to Farming Menu", NamedTextColor.GRAY, TextDecoration.BOLD), List.of()));
    }

    private void addUpgradeButton(int slot, String key, String title, Material icon, FarmingGenerator generator, FarmingUpgradeService upgradeService) {
        int lvl = generator.getUpgradeLevel(key);
        int maxLvl = upgradeService.getMaxUpgradeLevel(key);
        boolean isMax = lvl >= maxLvl;
        BigDecimal cost = upgradeService.calculateUpgradeCost(key, lvl);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("• Current Level: ", NamedTextColor.GRAY).append(Component.text(lvl + " / " + maxLvl, NamedTextColor.GREEN, TextDecoration.BOLD)));
        if (!isMax) {
            lore.add(Component.text("• Next Level: ", NamedTextColor.GRAY).append(Component.text("Level " + (lvl + 1), NamedTextColor.YELLOW)));
            lore.add(Component.text("• Upgrade Cost: ", NamedTextColor.GRAY).append(Component.text(cost + " Coins", NamedTextColor.GOLD, TextDecoration.BOLD)));
            lore.add(Component.empty());
            lore.add(Component.text("▶ Click to purchase upgrade", NamedTextColor.GREEN));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("★ MAXIMUM LEVEL REACHED ★", NamedTextColor.AQUA, TextDecoration.BOLD));
        }

        inventory.setItem(slot, createIcon(icon, Component.text(title, NamedTextColor.YELLOW, TextDecoration.BOLD), lore));
    }

    private void populateCollections(Player player) {
        fillBorder(Material.PURPLE_STAINED_GLASS_PANE);

        Material[] crops = {
                Material.WHEAT, Material.CARROT, Material.POTATO, Material.MELON_SLICE,
                Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS, Material.NETHER_WART
        };

        int slot = 19;
        for (Material crop : crops) {
            String name = crop.name();
            inventory.setItem(slot++, createIcon(
                    crop,
                    Component.text(formatName(crop) + " Collection", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    List.of(
                            Component.text("Harvest from your farming generators to unlock milestones!", NamedTextColor.GRAY),
                            Component.empty(),
                            Component.text("• Milestones: ", NamedTextColor.GRAY).append(Component.text("100 / 500 / 2,500 / 10,000 / 50,000", NamedTextColor.LIGHT_PURPLE))
                    )
            ));
            if (slot == 26) slot = 28;
        }

        inventory.setItem(49, createIcon(Material.ARROW, Component.text("⬅ Back to Farming Menu", NamedTextColor.GRAY, TextDecoration.BOLD), List.of()));
    }

    private static ItemStack createIcon(Material material, Component title, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(title.decoration(TextDecoration.ITALIC, false));
            List<Component> cleanLore = new ArrayList<>();
            for (Component c : lore) {
                cleanLore.add(c.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(cleanLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FarmingGui gui)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (gui.type == GuiType.MAIN_MENU) {
            if (slot == 20) {
                openCollectionsMenu(player, gui.farmingService);
            } else if (slot == 22) {
                // Open first active generator on island if available
                var island = gui.farmingService.generatorManager().getAllActiveGenerators().stream()
                        .filter(g -> g.ownerUuid().equals(player.getUniqueId()))
                        .findFirst();
                if (island.isPresent()) {
                    openGeneratorDetails(player, island.get(), gui.farmingService);
                } else {
                    player.sendMessage(Component.text("⚠ You have no active farming generators on this island yet! Build one with a Lodestone and farmland.", NamedTextColor.YELLOW));
                    player.closeInventory();
                }
            } else if (slot == 49) {
                player.closeInventory();
            }
        } else if (gui.type == GuiType.GENERATOR_DETAILS) {
            if (slot == 49) {
                openMainMenu(player, gui.farmingService);
                return;
            }

            FarmingGenerator gen = gui.targetGenerator;
            if (gen == null) return;

            if (slot == 38) {
                gui.farmingService.evolveGenerator(player, gen.generatorId()).thenAccept(success -> {
                    Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SkyRealmsFarming"), () -> {
                        if (success) {
                            openGeneratorDetails(player, gen, gui.farmingService);
                        }
                    });
                });
                return;
            } else if (slot == 42) {
                gui.farmingService.dismantleGenerator(player, gen.generatorId()).thenAccept(success -> {
                    Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SkyRealmsFarming"), () -> player.closeInventory());
                });
                return;
            }

            String upgradeKey = switch (slot) {
                case 19 -> GeneratorUpgrade.KEY_SPEED;
                case 21 -> GeneratorUpgrade.KEY_YIELD;
                case 23 -> GeneratorUpgrade.KEY_MUTATION;
                case 25 -> GeneratorUpgrade.KEY_XP_BOOST;
                default -> null;
            };

            if (upgradeKey != null) {
                gui.farmingService.upgradeGenerator(player, gen.generatorId(), upgradeKey).thenAccept(res -> {
                    Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SkyRealmsFarming"), () -> {
                        openGeneratorDetails(player, gen, gui.farmingService);
                    });
                });
            }
        } else if (gui.type == GuiType.COLLECTIONS_MENU) {
            if (slot == 49) {
                openMainMenu(player, gui.farmingService);
            }
        }
    }
}
