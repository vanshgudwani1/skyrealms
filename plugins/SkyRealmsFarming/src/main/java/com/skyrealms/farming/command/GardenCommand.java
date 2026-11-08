package com.skyrealms.farming.command;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Command suite for the SkyRealms Garden System: /garden
 */
public final class GardenCommand implements CommandExecutor, TabCompleter {

    private final GardenService gardenService;
    private final IslandService islandService;

    public GardenCommand(
            @NotNull GardenService gardenService,
            @NotNull IslandService islandService
    ) {
        this.gardenService = Objects.requireNonNull(gardenService, "gardenService cannot be null");
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
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
            case "admin" -> handleAdmin(player, args);
            default -> player.sendMessage(Component.text("Unknown subcommand. Type /garden help for options.", NamedTextColor.RED));
        }
        return true;
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.STRIKETHROUGH));
        sender.sendMessage(Component.text("🌿 SkyRealms Garden Commands", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/garden home ", NamedTextColor.YELLOW).append(Component.text("— Teleport to your Greenhouse Dome", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden leave ", NamedTextColor.YELLOW).append(Component.text("— Teleport back to your Island Home", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden info ", NamedTextColor.YELLOW).append(Component.text("— View your Garden coordinates & status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden plots ", NamedTextColor.YELLOW).append(Component.text("— View status of all 16 Crop Plots", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/garden help ", NamedTextColor.YELLOW).append(Component.text("— Display this command guide", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN, TextDecoration.STRIKETHROUGH));
    }

    private void handleHome(@NotNull Player player) {
        Optional<Island> optIsland = islandService.getIslandByPlayer(player.getUniqueId());
        if (optIsland.isEmpty()) {
            player.sendMessage(Component.text("✖ You must belong to an island to access a Garden. Create one with /is create.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("🌿 Initiating teleport to your SkyRealms Greenhouse Garden...", TextColor.fromHexString("#77DD77")));
        gardenService.teleportToGarden(player, optIsland.get().id());
    }

    private void handleLeave(@NotNull Player player) {
        Optional<Island> optIsland = islandService.getIslandByPlayer(player.getUniqueId());
        if (optIsland.isEmpty()) {
            player.sendMessage(Component.text("✖ You do not belong to an island.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("🌸 Returning to your Island Home...", TextColor.fromHexString("#FF77A8")));
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
            sender.sendMessage(Component.text("Admin usage: /garden admin tp <player> | /garden admin unlockplot <player> <plot#>", NamedTextColor.RED));
            return;
        }

        String adminSub = args[1].toLowerCase(Locale.ROOT);
        if (adminSub.equals("tp")) {
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
                    sender.sendMessage(Component.text("✔ Unlocked plot #" + plotIdx + " for " + target.getName() + "'s garden.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("✖ Could not unlock plot (invalid or already unlocked).", NamedTextColor.RED));
                }
            });
        }
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("home", "leave", "info", "plots", "help"));
            if (sender.isOp() || sender.hasPermission("skyrealms.garden.admin")) {
                subs.add("admin");
            }
            return subs;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("tp", "unlockplot");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}
