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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command: /balance [player]
 */
public final class BalanceCommand implements CommandExecutor, TabCompleter {

    private final EconomyService economyService;

    public BalanceCommand(@NotNull EconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console must specify a player name: /balance <player>", NamedTextColor.RED));
                return true;
            }
            displayBalance(player, player.getUniqueId(), player.getName());
            return true;
        }

        if (!sender.hasPermission("skyrealms.economy.balance.others")) {
            sender.sendMessage(Component.text("You do not have permission to view other players' balances.", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(Component.text("Player '" + targetName + "' has never joined the server.", NamedTextColor.RED));
            return true;
        }

        displayBalance(sender, target.getUniqueId(), target.getName() != null ? target.getName() : targetName);
        return true;
    }

    private void displayBalance(CommandSender viewer, UUID targetId, String targetName) {
        viewer.sendMessage(Component.text("--------- [ " + targetName + "'s Wallets ] ---------", NamedTextColor.GOLD));
        for (Currency currency : economyService.getCurrencies()) {
            BigDecimal balance = economyService.getCachedBalance(targetId, currency.id());
            viewer.sendMessage(Component.text(currency.displayName() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(currency.format(balance), NamedTextColor.YELLOW)));
        }
        viewer.sendMessage(Component.text("--------------------------------------", NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("skyrealms.economy.balance.others")) {
            List<String> completions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }
        return List.of();
    }
}
