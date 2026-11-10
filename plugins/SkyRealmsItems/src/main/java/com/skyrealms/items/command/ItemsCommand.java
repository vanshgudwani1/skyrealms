package com.skyrealms.items.command;

import com.skyrealms.items.gui.ItemsCatalogGui;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Command handler for /items (/sritems, /gear, /catalog, /hoe).
 */
public final class ItemsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "skyrealms.items.admin";

    private final ItemRegistryService itemService;

    public ItemsCommand(@NotNull ItemRegistryService itemService) {
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                ItemsCatalogGui.open(player, itemService);
            } else {
                sendList(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "catalog" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can open the Hoes GUI.", NamedTextColor.RED));
                    return true;
                }
                ItemsCatalogGui.open(player, itemService);
            }
            case "upgrade" -> handleUpgrade(sender, args);
            case "list" -> sendList(sender);
            case "stats" -> handleStats(sender, args);
            case "inspect" -> handleInspect(sender);
            case "give" -> handleGive(sender, args);
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use /items help.", NamedTextColor.RED));
        }
        return true;
    }

    private void handleUpgrade(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can upgrade held hoes.", NamedTextColor.RED));
            return;
        }
        boolean free = args.length >= 2 && args[1].equalsIgnoreCase("free")
                && (sender.hasPermission(ADMIN_PERMISSION) || sender.isOp());

        ItemStack held = player.getInventory().getItemInMainHand();
        HoeUpgradeService.UpgradeAttemptResult result = itemService.upgrades().upgradeHeldHoe(player, held, free);
        if (result.success()) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.3f);
            player.sendMessage(Component.text("✦ " + result.message(), NamedTextColor.GREEN));
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            player.sendMessage(Component.text("✖ " + result.message(), NamedTextColor.RED));
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player is not online: " + args[1], NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Usage: /items stats <player>", NamedTextColor.RED));
            return;
        }

        SkyItemStats stats = itemService.getPlayerEquippedStats(target);
        sender.sendMessage(Component.text("✦ " + target.getName() + "'s Equipped Hoe Stats", NamedTextColor.AQUA, TextDecoration.BOLD));
        sender.sendMessage(Component.text(String.format("  Farming Fortune: +%.0f%% | Farming XP: +%.1f%% | Walk Speed: +%.0f%%",
                stats.farmingFortune() * 100.0,
                stats.farmingXpBonus() * 100.0,
                stats.speedBonus() * 100.0), NamedTextColor.GREEN));
    }

    private void handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can inspect held items.", NamedTextColor.RED));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition def = itemService.factory().readDefinition(held).orElse(null);
        if (def == null) {
            String rawId = itemService.getCustomItemId(held).orElse(null);
            if (rawId != null) {
                player.sendMessage(Component.text("External custom item ID: " + rawId, NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("You are not holding a SkyRealms custom item.", NamedTextColor.GRAY));
            }
            return;
        }
        int level = itemService.factory().readItemLevel(held);
        SkyItemStats scaled = HoeUpgradeService.scaleStats(def, level);
        player.sendMessage(Component.text("✦ " + def.displayName() + " [Lv." + level + "/" + HoeUpgradeService.MAX_LEVEL_PER_TIER + "] (" + def.id() + ")",
                def.rarity().color(), TextDecoration.BOLD));
        player.sendMessage(Component.text(String.format("  Farming Fortune: +%.0f%% | Farming XP: +%.1f%% | Walk Speed: +%.0f%%",
                scaled.farmingFortune() * 100.0, scaled.farmingXpBonus() * 100.0, scaled.speedBonus() * 100.0), NamedTextColor.GREEN));
        if (def.hasAbility()) {
            player.sendMessage(Component.text("  Ability: " + def.abilityName() + " (" + def.abilityCooldownSeconds() + "s CD)", NamedTextColor.GOLD));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION) && !sender.isOp()) {
            sender.sendMessage(Component.text("You lack permission " + ADMIN_PERMISSION, NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /items give <player> <hoeId> [amount] [level]", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player must be online: " + args[1], NamedTextColor.RED));
            return;
        }
        String itemId = args[2].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Invalid amount: " + args[3], NamedTextColor.RED));
                return;
            }
        }
        int level = HoeUpgradeService.MIN_LEVEL;
        if (args.length >= 5) {
            try {
                level = HoeUpgradeService.clampLevel(Integer.parseInt(args[4]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Invalid level: " + args[4], NamedTextColor.RED));
                return;
            }
        }
        ItemStack stack = itemService.createCustomItem(itemId, amount, level).orElse(null);
        if (stack == null) {
            sender.sendMessage(Component.text("Unknown hoe ID: " + itemId, NamedTextColor.RED));
            return;
        }
        target.getInventory().addItem(stack).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        sender.sendMessage(Component.text("✦ Granted " + amount + "x " + itemId + " [Lv." + level + "] to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void sendList(CommandSender sender) {
        List<CustomItemDefinition> list = List.copyOf(itemService.catalog().all());
        sender.sendMessage(Component.text("✦ SkyRealms Farming Hoes (" + list.size() + ")", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (CustomItemDefinition def : list) {
            sender.sendMessage(Component.text("  • " + def.id(), def.rarity().color())
                    .append(Component.text(" — " + def.displayName() + " (Lv.1–Lv.5)", NamedTextColor.GRAY)));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("✦ SkyRealms Items Commands", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("  /items", NamedTextColor.YELLOW).append(Component.text(" — Open Farming Hoes & Upgrade GUI", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /items upgrade", NamedTextColor.YELLOW).append(Component.text(" — Upgrade or evolve your held hoe", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /items list", NamedTextColor.YELLOW).append(Component.text(" — List the 3 custom farming hoes", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /items stats [player]", NamedTextColor.YELLOW).append(Component.text(" — View equipped hoe stats", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /items inspect", NamedTextColor.YELLOW).append(Component.text(" — Inspect held hoe level and stats", NamedTextColor.GRAY)));
        if (sender.hasPermission(ADMIN_PERMISSION) || sender.isOp()) {
            sender.sendMessage(Component.text("  /items give <player> <hoeId> [amount] [level]", NamedTextColor.RED).append(Component.text(" — Grant a custom hoe", NamedTextColor.GRAY)));
        }
    }

    @Override
    @NotNull
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("gui", "upgrade", "list", "stats", "inspect", "help"));
            if (sender.hasPermission(ADMIN_PERMISSION) || sender.isOp()) {
                base.add("give");
            }
            return filterPrefix(base, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("give"))) {
            return null;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filterPrefix(itemService.catalog().all().stream().map(CustomItemDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return List.of("1");
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            return List.of("1", "2", "3", "4", "5");
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(opt -> opt.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
