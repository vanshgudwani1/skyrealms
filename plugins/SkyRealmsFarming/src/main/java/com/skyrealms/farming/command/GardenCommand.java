package com.skyrealms.farming.command;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.ability.GardenAbilitiesGui;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.ability.VerdantAuraManager;
import com.skyrealms.farming.garden.item.GardenItemFactory;
import com.skyrealms.farming.garden.journal.BotanicalJournalService;
import com.skyrealms.farming.garden.mastery.CropMasteryService;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.garden.offering.OfferingService;
import com.skyrealms.farming.garden.plot.*;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Command suite for the SkyRealms Garden System: /garden
 * Integrates mcMMO Farming skill stats, Garden abilities GUI, Crop Mastery GUI, and Botanical Journal GUI.
 */
public final class GardenCommand implements CommandExecutor, TabCompleter {

    private final GardenService gardenService;
    private final IslandService islandService;
    private final OfferingService offeringService;
    private final CropMasteryService masteryService;
    private final McMMOFarmingService mcMmoService;
    private final GardenAbilityService abilityService;
    private final VerdantAuraManager auraManager;
    private final BotanicalJournalService journalService;
    private final GardenPlotCustomizationService customizationService;
    private final PlotEditorService editorService;

    public GardenCommand(
            @NotNull GardenService gardenService,
            @NotNull IslandService islandService,
            @NotNull OfferingService offeringService,
            @NotNull CropMasteryService masteryService,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull GardenAbilityService abilityService,
            @NotNull VerdantAuraManager auraManager,
            @NotNull BotanicalJournalService journalService,
            @NotNull GardenPlotCustomizationService customizationService,
            @NotNull PlotEditorService editorService
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.offeringService = Objects.requireNonNull(offeringService, "offeringService cannot be null");
        this.masteryService = Objects.requireNonNull(masteryService, "masteryService cannot be null");
        this.mcMmoService = Objects.requireNonNull(mcMmoService, "mcMmoService cannot be null");
        this.abilityService = Objects.requireNonNull(abilityService, "abilityService cannot be null");
        this.auraManager = Objects.requireNonNull(auraManager, "auraManager cannot be null");
        this.journalService = Objects.requireNonNull(journalService, "journalService cannot be null");
        this.customizationService = Objects.requireNonNull(customizationService, "customizationService cannot be null");
        this.editorService = Objects.requireNonNull(editorService, "editorService cannot be null");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
                handleAdmin(sender, args);
                return true;
            }
            sender.sendMessage(Component.text("Console usage: /garden admin tp <player> | /garden help", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("home") || args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("enter")) {
            handleHome(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(player);
            case "leave" -> handleLeave(player);
            case "info" -> handleInfo(player);
            case "plots" -> handlePlots(player);
            case "plot" -> handlePlot(player, args);
            case "mastery", "skills" -> masteryService.openMasteryGui(player);
            case "abilities", "ability" -> GardenAbilitiesGui.open(player, mcMmoService, abilityService, auraManager);
            case "journal", "collections" -> journalService.openJournalGui(player);
            case "admin" -> handleAdmin(player, args);
            default -> player.sendMessage(Component.text("Unknown subcommand. Type /garden help for options.", NamedTextColor.RED));
        }
        return true;
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("---------------------------------------------", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("✦ SkyRealms Garden Commands ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/garden home ", NamedTextColor.YELLOW).append(Component.text("- Teleport to your Greenhouse Dome", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden leave ", NamedTextColor.YELLOW).append(Component.text("- Teleport back to your Island Home", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden plot ", NamedTextColor.YELLOW).append(Component.text("- Open Plot Studio GUI (edit/layout/save/custom/plant/reset)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden mastery ", NamedTextColor.YELLOW).append(Component.text("- Open mcMMO Farming & Crop Mastery GUI", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden abilities ", NamedTextColor.YELLOW).append(Component.text("- Open Garden Farming Abilities GUI", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden journal ", NamedTextColor.YELLOW).append(Component.text("- Open Botanical Journal & Collections GUI", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden info ", NamedTextColor.YELLOW).append(Component.text("- View your Garden coordinates & status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden plots ", NamedTextColor.YELLOW).append(Component.text("- View status of all 16 Crop Plots", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden help ", NamedTextColor.YELLOW).append(Component.text("- Display this command guide", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("---------------------------------------------", NamedTextColor.GREEN));
    }

    private void handleHome(@NotNull Player player) {
        Optional<Island> optIsland = islandService.getIslandByPlayer(player.getUniqueId());
        if (optIsland.isEmpty()) {
            player.sendMessage(Component.text("[✗] You must belong to an island to access a Garden. Create one with /is create.", NamedTextColor.RED));
            return;
        }

        UUID islandId = optIsland.get().id();

        if (!offeringService.isGardenUnlocked(islandId)) {
            player.sendMessage(Component.text("---------------------------------------------", NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text("✦ THE GARDEN IS LOCKED BY THE GODS ✦", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.text("To gain permanent access to the Sakura Conservatory, your island must make an offering:", NamedTextColor.YELLOW));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  • 3x Enchanted Hay Bales", NamedTextColor.WHITE));
            player.sendMessage(Component.text("  • 3x Enchanted Carrots", NamedTextColor.WHITE));
            player.sendMessage(Component.text("  • 3x Enchanted Sugar Cane", NamedTextColor.WHITE));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("Place these items inside a chest renamed: ", NamedTextColor.GRAY)
                    .append(Component.text("Offering to the Gods", NamedTextColor.GOLD, TextDecoration.BOLD)));
            player.sendMessage(Component.text("on your island to initiate the divine ritual.", NamedTextColor.GRAY));
            player.sendMessage(Component.text("---------------------------------------------", NamedTextColor.LIGHT_PURPLE));
            return;
        }

        player.sendMessage(Component.text("◆ Teleporting to your Greenhouse Conservatory...", TextColor.fromHexString("#77DD77")));
        gardenService.teleportToGarden(player, islandId);
    }

    private void handleLeave(@NotNull Player player) {
        Optional<Island> optIsland = islandService.getIslandByPlayer(player.getUniqueId());
        if (optIsland.isEmpty()) {
            player.sendMessage(Component.text("[✗] You do not belong to an island.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("◆ Returning to your Island Home...", TextColor.fromHexString("#FF77A8")));
        gardenService.teleportToIsland(player, optIsland.get().id());
    }

    private void handleInfo(@NotNull Player player) {
        Optional<Island> optIsland = islandService.getIslandByPlayer(player.getUniqueId());
        if (optIsland.isEmpty()) {
            player.sendMessage(Component.text("✖ You do not belong to an island.", NamedTextColor.RED));
            return;
        }

        Optional<Garden> optGarden = gardenService.getGarden(optIsland.get().id());
        if (optGarden.isEmpty()) {
            player.sendMessage(Component.text("Your island has not yet allocated a Garden. Use /garden home to initialize it.", NamedTextColor.YELLOW));
            return;
        }

        Garden garden = optGarden.get();
        List<GardenPlot> plots = gardenService.getPlots(garden.islandId());
        long unlockedCount = plots.stream().filter(GardenPlot::unlocked).count();

        player.sendMessage(Component.text("────────── [ SkyRealms Garden ] ──────────", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Region Index: ", NamedTextColor.GRAY).append(Component.text("#" + garden.regionIndex(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Center: ", NamedTextColor.GRAY).append(Component.text("X: " + garden.centerX() + ", Z: " + garden.centerZ(), NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Dimension: ", NamedTextColor.GRAY).append(Component.text(garden.size() + "x" + garden.size() + " blocks", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Unlocked Plots: ", NamedTextColor.GRAY).append(Component.text(unlockedCount + " / 16", NamedTextColor.GOLD)));
        player.sendMessage(Component.text("mcMMO Farming Lv.: ", NamedTextColor.GRAY).append(Component.text("Lv. " + mcMmoService.getFarmingLevel(player), NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text(garden.unlocked() ? "UNLOCKED" : "LOCKED", garden.unlocked() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        player.sendMessage(Component.text("─────────────────────────────────────────", NamedTextColor.GREEN));
    }

    private void handlePlots(@NotNull Player player) {
        Optional<Island> optIsland = islandService.getIslandByPlayer(player.getUniqueId());
        if (optIsland.isEmpty()) {
            player.sendMessage(Component.text("✖ You do not belong to an island.", NamedTextColor.RED));
            return;
        }

        List<GardenPlot> plots = gardenService.getPlots(optIsland.get().id());
        if (plots.isEmpty()) {
            player.sendMessage(Component.text("Your garden has not been initialized yet. Use /garden home.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("🌱 ─── Garden Plots (2 Rows x 8 Columns) ─── 🌱", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Row 1 (North - Basic & Specialized):", NamedTextColor.YELLOW));
        StringBuilder row1 = new StringBuilder("  ");
        for (int i = 1; i <= 8; i++) {
            int idx = i;
            GardenPlot p = plots.stream().filter(plot -> plot.plotIndex() == idx).findFirst().orElse(null);
            if (p != null && p.unlocked()) {
                row1.append("§a[#").append(idx).append(" ").append(p.cropType() != null ? p.cropType() : "Ready").append("] ");
            } else {
                row1.append("§7[#").append(idx).append(" 🔒] ");
            }
        }
        player.sendMessage(Component.text(row1.toString()));

        player.sendMessage(Component.text("Row 2 (South - Advanced & Experimental):", NamedTextColor.YELLOW));
        StringBuilder row2 = new StringBuilder("  ");
        for (int i = 9; i <= 16; i++) {
            int idx = i;
            GardenPlot p = plots.stream().filter(plot -> plot.plotIndex() == idx).findFirst().orElse(null);
            if (p != null && p.unlocked()) {
                row2.append("§a[#").append(idx).append(" ").append(p.cropType() != null ? p.cropType() : "Ready").append("] ");
            } else {
                row2.append("§7[#").append(idx).append(" 🔒] ");
            }
        }
        player.sendMessage(Component.text(row2.toString()));
        player.sendMessage(Component.text("─────────────────────────────────────────", NamedTextColor.GOLD));
    }

    private void handleAdmin(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skyrealms.garden.admin")) {
            sender.sendMessage(Component.text("✖ You lack permission skyrealms.garden.admin.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Admin usage: /garden admin tp <player> | reset <player> | unlockplot <player> <1-16> | giveitem <player> <hay|carrot|cane> [amount]", NamedTextColor.RED));
            return;
        }

        String adminSub = args[1].toLowerCase(Locale.ROOT);
        if (adminSub.equals("reset")) {
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /garden admin reset <player>", NamedTextColor.RED));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            UUID targetUuid = target != null ? target.getUniqueId() : null;
            String targetName = args[2];
            if (targetUuid == null) {
                try {
                    targetUuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
                } catch (Exception ignored) {}
            }
            if (targetUuid == null) {
                sender.sendMessage(Component.text("Could not find player: " + args[2], NamedTextColor.RED));
                return;
            }

            Optional<Island> optIsland = islandService.getIslandByPlayer(targetUuid);
            if (optIsland.isEmpty()) {
                sender.sendMessage(Component.text("Player " + targetName + " does not belong to an island.", NamedTextColor.RED));
                return;
            }

            UUID islandId = optIsland.get().id();
            offeringService.resetOfferingAndUnlock(islandId);

            if (target != null && target.isOnline() && gardenService.isGardenWorld(target.getWorld())) {
                gardenService.teleportToIsland(target, islandId);
            }

            sender.sendMessage(Component.text("[✓] Successfully reset Garden unlock and offering ritual for " + targetName + ".", NamedTextColor.GREEN));
            if (target != null && target.isOnline()) {
                target.sendMessage(Component.text("---------------------------------------------", NamedTextColor.RED));
                target.sendMessage(Component.text("✦ GARDEN ACCESS RESET ✦", NamedTextColor.RED, TextDecoration.BOLD));
                target.sendMessage(Component.text("An administrator has reset your Garden access.", NamedTextColor.WHITE));
                target.sendMessage(Component.text("You must present the Divine Offering to the Gods again to unlock the Conservatory.", NamedTextColor.YELLOW));
                target.sendMessage(Component.text("---------------------------------------------", NamedTextColor.RED));
            }
        } else if (adminSub.equals("tp")) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(Component.text("Only players can teleport.", NamedTextColor.RED));
                return;
            }
            if (args.length < 3) {
                admin.sendMessage(Component.text("Usage: /garden admin tp <player>", NamedTextColor.RED));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                admin.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return;
            }
            Optional<Island> opt = islandService.getIslandByPlayer(target.getUniqueId());
            if (opt.isEmpty()) {
                admin.sendMessage(Component.text("Player does not have an island.", NamedTextColor.RED));
                return;
            }
            gardenService.teleportToGarden(admin, opt.get().id());
        } else if (adminSub.equals("giveitem")) {
            if (args.length < 4) {
                sender.sendMessage(Component.text("Usage: /garden admin giveitem <player> <hay|carrot|cane> [amount]", NamedTextColor.RED));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return;
            }
            String itemType = args[3].toLowerCase(Locale.ROOT);
            int amount = 1;
            if (args.length >= 5) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[4]));
                } catch (NumberFormatException ignored) {}
            }

            ItemStack item = switch (itemType) {
                case "hay" -> GardenItemFactory.createEnchantedHayBale(amount);
                case "carrot" -> GardenItemFactory.createEnchantedCarrot(amount);
                case "cane" -> GardenItemFactory.createEnchantedSugarCane(amount);
                default -> null;
            };

            if (item == null) {
                sender.sendMessage(Component.text("Invalid item type. Options: hay, carrot, cane", NamedTextColor.RED));
                return;
            }

            target.getInventory().addItem(item);
            sender.sendMessage(Component.text("[✓] Gave " + amount + "x " + itemType + " to " + target.getName(), NamedTextColor.GREEN));
        } else if (adminSub.equals("unlockplot")) {
            if (args.length < 4) {
                sender.sendMessage(Component.text("Usage: /garden admin unlockplot <player> <1-16>", NamedTextColor.RED));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return;
            }
            Optional<Island> opt = islandService.getIslandByPlayer(target.getUniqueId());
            if (opt.isEmpty()) {
                sender.sendMessage(Component.text("Player does not have an island.", NamedTextColor.RED));
                return;
            }
            int plotIdx;
            try {
                plotIdx = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid plot number.", NamedTextColor.RED));
                return;
            }
            gardenService.unlockPlot(opt.get().id(), plotIdx).thenAccept(success -> {
                if (success) {
                    sender.sendMessage(Component.text("[✓] Unlocked plot #" + plotIdx + " for " + target.getName() + "'s garden.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("✖ Could not unlock plot (invalid or already unlocked).", NamedTextColor.RED));
                }
            });
        }
    }

    private void handlePlot(@NotNull Player player, @NotNull String[] args) {
        if (!gardenService.isGardenWorld(player.getWorld())) {
            player.sendMessage(Component.text("✖ You must be inside a Garden to manage plots.", NamedTextColor.RED));
            return;
        }
        Optional<Garden> optGarden = gardenService.getGardenAt(player.getLocation());
        if (optGarden.isEmpty() || !optGarden.get().unlocked()) {
            player.sendMessage(Component.text("✖ You are not standing inside an unlocked Garden.", NamedTextColor.RED));
            return;
        }
        Garden garden = optGarden.get();
        if (!editorService.hasEditPermission(player, garden.islandId())) {
            player.sendMessage(Component.text("⛔ You do not have co-op build permission in this Garden.", NamedTextColor.RED));
            return;
        }

        boolean openMenuOnly = args.length == 1;
        GardenPlot plot = resolveTargetPlot(player, garden, openMenuOnly);
        if (plot == null) return;

        if (args.length == 1) {
            GardenPlotGui.openPlotMenu(player, garden.islandId(), plot.plotIndex(), customizationService, editorService, mcMmoService);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "edit" -> {
                if (editorService.isEditing(player.getUniqueId())) {
                    editorService.exitEditMode(player, true);
                } else {
                    editorService.enterEditMode(player, garden, plot);
                }
            }
            case "layout" -> {
                if (args.length < 3) {
                    GardenPlotGui.openPlotMenu(player, garden.islandId(), plot.plotIndex(), customizationService, editorService, mcMmoService);
                    return;
                }
                boolean confirmed = args.length >= 4 && args[3].equalsIgnoreCase("confirm");
                if (args[2].equalsIgnoreCase("custom")) {
                    loadCustomWithPrompt(player, garden, plot, confirmed);
                    return;
                }
                PlotLayoutPreset preset = parseLayoutPreset(args[2]);
                if (preset == null) {
                    player.sendMessage(Component.text("✖ Unknown layout. Options: wheat, carrot, potato, beetroot, melon, pumpkin, sugar_cane, cactus, cocoa, nether_wart, clean, custom", NamedTextColor.RED));
                    return;
                }
                applyLayoutWithPrompt(player, garden, plot, preset, confirmed);
            }
            case "save" -> {
                GardenPlotCustomizationService.ActionResult res = customizationService.saveCustomLayout(garden.islandId(), plot.plotIndex());
                player.sendMessage(Component.text((res.success() ? "✦ " : "✖ ") + res.message(),
                        res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "custom" -> {
                boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
                loadCustomWithPrompt(player, garden, plot, confirmed);
            }
            case "plant" -> {
                CropType explicitCrop = args.length >= 3 ? CropType.fromCropId(args[2]) : null;
                GardenPlotCustomizationService.ActionResult res = customizationService.plantSeedsInPlot(
                        player, player.getWorld(), garden, plot, explicitCrop);
                player.sendMessage(Component.text((res.success() ? "✦ " : "✖ ") + res.message(),
                        res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "reset" -> {
                boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
                applyLayoutWithPrompt(player, garden, plot, PlotLayoutPreset.CLEAN_CANVAS, confirmed);
            }
            default -> {
                player.sendMessage(Component.text("Usage: /garden plot [edit|layout <crop|clean|custom> [confirm]|save|custom [confirm]|plant [crop]|reset [confirm]]", NamedTextColor.YELLOW));
            }
        }
    }

    private void applyLayoutWithPrompt(
            @NotNull Player player,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull PlotLayoutPreset preset,
            boolean confirmed
    ) {
        GardenPlotCustomizationService.ActionResult res = customizationService.applyPresetLayout(
                player.getWorld(), garden, plot, preset, confirmed, player
        );
        if (res.success()) {
            player.sendMessage(Component.text("✦ " + res.message(), NamedTextColor.GREEN));
        } else if (res.requiresConfirmation()) {
            player.sendMessage(Component.text("⚠ " + res.message(), NamedTextColor.YELLOW));
            PlotLayoutEngine.DestructiveScanResult scan = PlotLayoutEngine.scanDestructiveConflicts(player.getWorld(), garden, plot);
            GardenPlotGui.openConfirmLayout(player, garden.islandId(), plot.plotIndex(), preset, false,
                    scan.existingCrops(), scan.existingContainers());
        } else {
            player.sendMessage(Component.text("✖ " + res.message(), NamedTextColor.RED));
        }
    }

    private void loadCustomWithPrompt(
            @NotNull Player player,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            boolean confirmed
    ) {
        GardenPlotCustomizationService.ActionResult res = customizationService.loadCustomLayout(
                player.getWorld(), garden, plot, confirmed, player
        );
        if (res.success()) {
            player.sendMessage(Component.text("✦ " + res.message(), NamedTextColor.GREEN));
        } else if (res.requiresConfirmation()) {
            player.sendMessage(Component.text("⚠ " + res.message(), NamedTextColor.YELLOW));
            PlotLayoutEngine.DestructiveScanResult scan = PlotLayoutEngine.scanDestructiveConflicts(player.getWorld(), garden, plot);
            GardenPlotGui.openConfirmLayout(player, garden.islandId(), plot.plotIndex(), null, true,
                    scan.existingCrops(), scan.existingContainers());
        } else {
            player.sendMessage(Component.text("✖ " + res.message(), NamedTextColor.RED));
        }
    }

    private GardenPlot resolveTargetPlot(@NotNull Player player, @NotNull Garden garden, boolean fallbackToPlotOne) {
        Optional<GardenPlot> standing = gardenService.getPlotAt(player.getLocation());
        if (standing.isPresent()) {
            GardenPlot p = standing.get();
            if (!p.unlocked()) {
                player.sendMessage(Component.text("🔒 Plot #" + p.plotIndex() + " is locked! Unlock it first.", NamedTextColor.RED));
                return null;
            }
            return p;
        }
        if (fallbackToPlotOne) {
            return gardenService.getPlots(garden.islandId()).stream()
                    .filter(GardenPlot::unlocked)
                    .findFirst()
                    .orElse(null);
        }
        player.sendMessage(Component.text("✖ Please stand inside the unlocked plot you want to customize.", NamedTextColor.RED));
        return null;
    }

    private static PlotLayoutPreset parseLayoutPreset(@NotNull String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.equals("CLEAN") || normalized.equals("CLEANCANVAS")) return PlotLayoutPreset.CLEAN_CANVAS;
        if (normalized.equals("CANE") || normalized.equals("SUGARCANE")) return PlotLayoutPreset.SUGAR_CANE_OPTIMAL;
        if (normalized.equals("WART") || normalized.equals("NETHERWART")) return PlotLayoutPreset.NETHER_WART_OPTIMAL;

        CropType crop = CropType.fromCropId(normalized);
        if (crop != null) {
            return PlotLayoutPreset.forCrop(crop);
        }

        for (PlotLayoutPreset preset : PlotLayoutPreset.values()) {
            if (preset.name().equals(normalized) || preset.name().replace("_", "").equals(normalized.replace("_", ""))) {
                return preset;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("home", "leave", "info", "plots", "plot", "mastery", "abilities", "journal", "help"));
            if (sender.isOp() || sender.hasPermission("skyrealms.garden.admin")) {
                subs.add("admin");
            }
            return subs;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("plot")) {
            return List.of("edit", "layout", "save", "custom", "plant", "reset");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("plot") && args[1].equalsIgnoreCase("layout")) {
            List<String> presets = new ArrayList<>();
            for (CropType crop : CropType.values()) {
                presets.add(crop.name().toLowerCase(Locale.ROOT));
            }
            presets.add("clean");
            presets.add("custom");
            return presets;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("plot") && args[1].equalsIgnoreCase("plant")) {
            List<String> crops = new ArrayList<>();
            for (CropType crop : CropType.values()) {
                crops.add(crop.name().toLowerCase(Locale.ROOT));
            }
            return crops;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("plot")
                && (args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("custom"))) {
            return List.of("confirm");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("plot") && args[1].equalsIgnoreCase("layout")) {
            return List.of("confirm");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("tp", "reset", "unlockplot", "giveitem");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("giveitem")) {
            return List.of("hay", "carrot", "cane");
        }
        return List.of();
    }
}
