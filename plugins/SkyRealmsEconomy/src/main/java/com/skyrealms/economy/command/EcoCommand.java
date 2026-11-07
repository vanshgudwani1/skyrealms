package com.skyrealms.economy.command;

import com.skyrealms.economy.model.Currency;
import com.skyrealms.economy.service.EconomyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;

/**
 * Administrative command: /eco <give|take|set> <player> <currency> <amount> [reason]
 */
public final class EcoCommand implements CommandExecutor, TabCompleter {

    private final EconomyService economyService;

    public EcoCommand(@NotNull EconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("skyrealms.admin.eco")) {
            sender.sendMessage(Component.text("You do not have permission to use /eco.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /eco <give|take|set> <player> <currency> <amount> [reason]", NamedTextColor.RED));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String targetName = args[1];
        String currencyId = args[2].toLowerCase(Locale.ROOT);

        Currency currency = economyService.getCurrency(currencyId).orElse(null);
        if (currency == null) {
            sender.sendMessage(Component.text("Currency '" + currencyId + "' does not exist.", NamedTextColor.RED));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(Component.text("Player '" + targetName + "' has never joined the server.", NamedTextColor.RED));
            return true;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid amount format. Must be a valid number.", NamedTextColor.RED));
            return true;
        }

        String reason = args.length >= 5 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "Admin command";
        UUID actorId = sender instanceof Player p ? p.getUniqueId() : null;
        String actorName = sender.getName();

        switch (action) {
            case "give" -> {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    sender.sendMessage(Component.text("Amount must be greater than zero.", NamedTextColor.RED));
                    return true;
                }
                String idempotencyKey = "admin:give:" + UUID.randomUUID();
                economyService.deposit(target.getUniqueId(), currencyId, amount, "Admin Give: " + reason, idempotencyKey)
                        .thenAccept(res -> {
                            if (res.isSuccess()) {
                                sender.sendMessage(Component.text("✔ Added " + currency.format(amount) + " to " + targetName + ". New balance: " + currency.format(res.balanceAfter()), NamedTextColor.GREEN));
                            } else {
                                sender.sendMessage(Component.text("✖ Failed: " + res.message(), NamedTextColor.RED));
                            }
                        });
            }
            case "take" -> {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    sender.sendMessage(Component.text("Amount must be greater than zero.", NamedTextColor.RED));
                    return true;
                }
                String idempotencyKey = "admin:take:" + UUID.randomUUID();
                economyService.withdraw(target.getUniqueId(), currencyId, amount, "Admin Take: " + reason, idempotencyKey)
                        .thenAccept(res -> {
                            if (res.isSuccess()) {
                                sender.sendMessage(Component.text("✔ Deducted " + currency.format(amount) + " from " + targetName + ". New balance: " + currency.format(res.balanceAfter()), NamedTextColor.GREEN));
                            } else {
                                sender.sendMessage(Component.text("✖ Failed: " + res.message(), NamedTextColor.RED));
                            }
                        });
            }
            case "set" -> {
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    sender.sendMessage(Component.text("Balance cannot be set to a negative number.", NamedTextColor.RED));
                    return true;
                }
                economyService.adminSet(actorId, actorName, target.getUniqueId(), currencyId, amount, reason)
                        .thenAccept(res -> {
                            if (res.isSuccess()) {
                                sender.sendMessage(Component.text("✔ Set " + targetName + "'s " + currency.displayName() + " balance to " + currency.format(res.balanceAfter()), NamedTextColor.GREEN));
                            } else {
                                sender.sendMessage(Component.text("✖ Failed: " + res.message(), NamedTextColor.RED));
                            }
                        });
            }
            default -> sender.sendMessage(Component.text("Unknown action '" + action + "'. Valid actions: give, take, set.", NamedTextColor.RED));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("skyrealms.admin.eco")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("give", "take", "set");
        }
        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            String prefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        if (args.length == 3) {
            List<String> list = new ArrayList<>();
            for (Currency c : economyService.getCurrencies()) {
                list.add(c.id());
            }
            return list;
        }
        if (args.length == 4) {
            return List.of("100", "500", "1000", "5000");
        }
        return List.of();
    }
}
