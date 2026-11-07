package com.skyrealms.economy.command;

import com.skyrealms.economy.model.Currency;
import com.skyrealms.economy.service.EconomyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command: /pay <player> <amount>
 */
public final class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyService economyService;
    private final String currencyId;
    private final BigDecimal minPayAmount;

    public PayCommand(@NotNull EconomyService economyService, @NotNull String currencyId, @NotNull BigDecimal minPayAmount) {
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.currencyId = Objects.requireNonNull(currencyId, "currencyId cannot be null");
        this.minPayAmount = Objects.requireNonNull(minPayAmount, "minPayAmount cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can send payments.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /pay <player> <amount>", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Player '" + targetName + "' is not online.", NamedTextColor.RED));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You cannot pay yourself.", NamedTextColor.RED));
            return true;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid amount format. Must be a valid number.", NamedTextColor.RED));
            return true;
        }

        if (amount.compareTo(minPayAmount) < 0) {
            player.sendMessage(Component.text("Minimum payment amount is " + minPayAmount + ".", NamedTextColor.RED));
            return true;
        }

        Currency currency = economyService.getCurrency(currencyId).orElse(null);
        if (currency == null) {
            player.sendMessage(Component.text("Primary currency is currently unavailable.", NamedTextColor.RED));
            return true;
        }

        String idempotencyKey = "pay:" + player.getUniqueId() + ":" + target.getUniqueId() + ":" + System.currentTimeMillis();

        player.sendMessage(Component.text("Processing transfer of " + currency.format(amount) + " to " + target.getName() + "...", NamedTextColor.GRAY));

        economyService.transfer(player.getUniqueId(), target.getUniqueId(), currencyId, amount, "Player transfer to " + target.getName(), idempotencyKey)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        player.sendMessage(Component.text("✔ Sent ", NamedTextColor.GREEN)
                                .append(Component.text(currency.format(amount), NamedTextColor.YELLOW))
                                .append(Component.text(" to " + target.getName() + ". Balance: " + currency.format(result.balanceAfter()), NamedTextColor.GREEN)));

                        if (target.isOnline()) {
                            target.sendMessage(Component.text("✔ Received ", NamedTextColor.GREEN)
                                    .append(Component.text(currency.format(amount), NamedTextColor.YELLOW))
                                    .append(Component.text(" from " + player.getName() + ".", NamedTextColor.GREEN)));
                        }
                    } else {
                        player.sendMessage(Component.text("✖ Transfer failed: " + result.message(), NamedTextColor.RED));
                    }
                });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName()) && p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }
        if (args.length == 2) {
            return List.of("10", "100", "1000", "10000");
        }
        return List.of();
    }
}
