package com.skyrealms.farming.command;

import com.skyrealms.farming.gui.FarmingGui;
import com.skyrealms.farming.model.FarmingCoreItemFactory;
import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import com.skyrealms.farming.model.FarmingPlayerProgress;
import com.skyrealms.farming.service.FarmingProgressionService;
import com.skyrealms.farming.service.FarmingService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Command suite for the SkyRealms Farming Engine: /farm
 */
public final class FarmCommand implements CommandExecutor, TabCompleter {

    private final FarmingService farmingService;
    private final IslandService islandService;

    public FarmCommand(@NotNull FarmingService farmingService, @NotNull IslandService islandService) {
        this.farmingService = farmingService;
        this.islandService = islandService;
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
            if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
                sendHelp(sender);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("stats")) {
                handleStats(sender, args);
                return true;
            }
            sender.sendMessage(Component.text("Console usage: /farm admin givecore <player> [tier] | /farm help | /farm stats <player>", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            FarmingGui.openMainMenu(player, farmingService);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(player);
            case "stats" -> handleStats(player, args);
            case "core" -> handleCore(player, args);
            case "generators" -> handleGenerators(player);
            case "generator" -> handleGeneratorSubcommand(player, args);
            case "admin" -> handleAdmin(player, args);
            default -> player.sendMessage(Component.text("Unknown subcommand. Type /farm help for options.", NamedTextColor.RED));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
        sender.sendMessage(Component.text("🌾 SkyRealms Farming Commands", NamedTextColor.GREEN, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/farm ", NamedTextColor.YELLOW).append(Component.text("— Open 54-slot Farming Menu", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/farm help ", NamedTextColor.YELLOW).append(Component.text("— Display this command guide", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/farm core [tier] ", NamedTextColor.YELLOW).append(Component.text("— Obtain a Farming Generator Core", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/farm stats [player] ", NamedTextColor.YELLOW).append(Component.text("— View Farming Level and XP stats", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/farm generators ", NamedTextColor.YELLOW).append(Component.text("— List active generators on your island", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/farm generator info ", NamedTextColor.YELLOW).append(Component.text("— Inspect generator at targeted block", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/farm generator upgrade ", NamedTextColor.YELLOW).append(Component.text("— Open upgrade view for targeted generator", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/farm generator remove ", NamedTextColor.YELLOW).append(Component.text("— Deactivate and refund targeted generator", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
    }

    private void handleCore(Player player, String[] args) {
        FarmingGeneratorTier tier = FarmingGeneratorTier.TIER_1_WHEAT;
        if (args.length > 1) {
            try {
                tier = FarmingGeneratorTier.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        FarmingPlayerProgress progress = farmingService.getPlayerProgress(player.getUniqueId());
        if (progress.farmingLevel() < tier.requiredFarmingLevel() && !player.hasPermission("skyrealms.farming.admin")) {
            player.sendMessage(Component.text("⛔ Requires Farming Level " + tier.requiredFarmingLevel() + " to use a " + tier.displayName() + "!", NamedTextColor.RED));
            return;
        }

        ItemStack core = FarmingCoreItemFactory.createCoreItem(tier, Collections.emptyMap());
        player.getInventory().addItem(core);
        player.sendMessage(Component.text("✦ Received ", NamedTextColor.GREEN)
                .append(Component.text(tier.displayName() + " Core", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("! Place on a flat 5x5 Dirt or Grass area to activate.", NamedTextColor.YELLOW)));
    }

    private void handleStats(CommandSender sender, String[] args) {
        Player target = (sender instanceof Player p) ? p : null;
        if (args.length > 1) {
            Player named = Bukkit.getPlayer(args[1]);
            if (named != null) target = named;
        }

        if (target == null) {
            sender.sendMessage(Component.text("Please specify a valid online player: /farm stats <player>", NamedTextColor.RED));
            return;
        }

        FarmingPlayerProgress progress = farmingService.getPlayerProgress(target.getUniqueId());
        long currentLvlXp = FarmingProgressionService.getRequiredXpForLevel(progress.farmingLevel());
        long nextLvlXp = FarmingProgressionService.getRequiredXpForLevel(progress.farmingLevel() + 1);

        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
        sender.sendMessage(Component.text("🌾 Farming Statistics: " + target.getName(), NamedTextColor.GREEN, TextDecoration.BOLD));
        sender.sendMessage(Component.text("• Farming Level: ", NamedTextColor.GRAY).append(Component.text("Level " + progress.farmingLevel(), NamedTextColor.GREEN, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("• Total Farming XP: ", NamedTextColor.GRAY).append(Component.text(String.format("%,d XP", progress.farmingXp()), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("• Next Level: ", NamedTextColor.GRAY).append(Component.text(progress.farmingXp() + " / " + nextLvlXp + " XP", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("• Total Crops Harvested: ", NamedTextColor.GRAY).append(Component.text(String.format("%,d", progress.totalHarvests()), NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
    }

    private void handleGenerators(Player player) {
        Optional<Island> optIsland = islandService.getIslandByPlayer(player.getUniqueId());
        if (optIsland.isEmpty()) {
            player.sendMessage(Component.text("⛔ You must belong to an island to view generators.", NamedTextColor.RED));
            return;
        }

        List<FarmingGenerator> gens = farmingService.getGeneratorsByIsland(optIsland.get().id());
        if (gens.isEmpty()) {
            player.sendMessage(Component.text("⚠ No active farming generators found on your island.", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Use /farm core and place it on 5x5 dirt to start!", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
        player.sendMessage(Component.text("⚡ Active Island Farming Generators (" + gens.size() + ")", NamedTextColor.GREEN, TextDecoration.BOLD));
        for (FarmingGenerator gen : gens) {
            player.sendMessage(Component.text("• " + gen.tier().displayName() + " ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .append(Component.text("at (" + gen.coreX() + ", " + gen.coreY() + ", " + gen.coreZ() + ")", NamedTextColor.AQUA))
                    .append(Component.text(" — Harvests: " + String.format("%,d", gen.totalHarvests()), NamedTextColor.GRAY)));
        }
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
    }

    private void handleGeneratorSubcommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /farm generator <info|upgrade|remove>", NamedTextColor.RED));
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage(Component.text("⚠ You must be looking at a Farming Core block within 5 blocks.", NamedTextColor.YELLOW));
            return;
        }

        Optional<FarmingGenerator> optGen = farmingService.getGeneratorAt(targetBlock.getLocation());
        if (optGen.isEmpty()) {
            player.sendMessage(Component.text("⚠ The targeted block is not an active Farming Core.", NamedTextColor.YELLOW));
            return;
        }

        FarmingGenerator gen = optGen.get();
        String action = args[1].toLowerCase(Locale.ROOT);

        switch (action) {
            case "info" -> {
                player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
                player.sendMessage(Component.text("⚡ Generator Details", NamedTextColor.GREEN, TextDecoration.BOLD));
                player.sendMessage(Component.text("• Tier: ", NamedTextColor.GRAY).append(Component.text(gen.tier().displayName(), NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("• Location: ", NamedTextColor.GRAY).append(Component.text(gen.coreX() + ", " + gen.coreY() + ", " + gen.coreZ(), NamedTextColor.AQUA)));
                player.sendMessage(Component.text("• Status: ", NamedTextColor.GRAY).append(Component.text(gen.status().name(), NamedTextColor.GREEN)));
                player.sendMessage(Component.text("• Lifetime Harvests: ", NamedTextColor.GRAY).append(Component.text(String.format("%,d", gen.totalHarvests()), NamedTextColor.GOLD)));
                player.sendMessage(Component.text("• Connected Crop Nodes: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(gen.cropNodes().size()), NamedTextColor.LIGHT_PURPLE)));
                player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
            }
            case "upgrade" -> FarmingGui.openGeneratorDetails(player, gen, farmingService);
            case "remove" -> {
                // Refund core item with upgrades
                Map<String, Integer> upgrades = new HashMap<>();
                for (var u : gen.upgrades().values()) {
                    upgrades.put(u.key(), u.level());
                }
                ItemStack coreItem = FarmingCoreItemFactory.createCoreItem(gen.tier(), upgrades);
                player.getInventory().addItem(coreItem);

                targetBlock.setType(org.bukkit.Material.AIR);
                farmingService.removeGenerator(gen.generatorId()).thenAccept(ok -> {
                    if (ok) {
                        player.sendMessage(Component.text("✦ Farming Generator removed! Core refunded to inventory.", NamedTextColor.GOLD));
                    }
                });
            }
            default -> player.sendMessage(Component.text("Unknown generator subcommand. Options: info, upgrade, remove", NamedTextColor.RED));
        }
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skyrealms.farming.admin")) {
            sender.sendMessage(Component.text("⛔ You do not have permission to use admin commands.", NamedTextColor.RED));
            return;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("givecore")) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return;
            }

            FarmingGeneratorTier tier = FarmingGeneratorTier.TIER_1_WHEAT;
            if (args.length >= 4) {
                try {
                    tier = FarmingGeneratorTier.valueOf(args[3].toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            ItemStack core = FarmingCoreItemFactory.createCoreItem(tier, Collections.emptyMap());
            target.getInventory().addItem(core);
            sender.sendMessage(Component.text("Given " + tier.displayName() + " Core to " + target.getName(), NamedTextColor.GREEN));
            return;
        }

        sender.sendMessage(Component.text("Usage: /farm admin givecore <player> [tier]", NamedTextColor.RED));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(List.of("help", "core", "stats", "generators", "generator"));
            if (sender.hasPermission("skyrealms.farming.admin")) {
                list.add("admin");
            }
            return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("core")) {
            return Arrays.stream(FarmingGeneratorTier.values()).map(Enum::name).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("generator")) {
            return List.of("info", "upgrade", "remove").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }

        return Collections.emptyList();
    }
}
