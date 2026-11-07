package com.skyrealms.player.command;

import com.skyrealms.player.model.PlayerProfile;
import com.skyrealms.player.service.PlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Command displaying player profile statistics and settings.
 */
public final class ProfileCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PlayerService playerService;

    public ProfileCommand(@NotNull PlayerService playerService) {
        this.playerService = playerService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[0] + "' is not currently online.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Console must specify a player name: /" + label + " <player>", NamedTextColor.RED));
            return true;
        }

        Optional<PlayerProfile> optionalProfile = playerService.getCached(target.getUniqueId());
        if (optionalProfile.isEmpty()) {
            sender.sendMessage(Component.text("Profile for '" + target.getName() + "' is not loaded in memory.", NamedTextColor.RED));
            return true;
        }

        PlayerProfile profile = optionalProfile.get();

        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("         SkyRealms Player Profile       ", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(" Player: ", NamedTextColor.GRAY)
                .append(Component.text(profile.username(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        sender.sendMessage(Component.text(" First Joined: ", NamedTextColor.GRAY)
                .append(Component.text(DATE_FORMAT.format(profile.firstJoined()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(" Playtime: ", NamedTextColor.GRAY)
                .append(Component.text(formatPlaytime(profile.stats().playtimeSeconds()), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text(" Total Logins: ", NamedTextColor.GRAY)
                .append(Component.text(profile.stats().joinsCount(), NamedTextColor.GREEN)));
        sender.sendMessage(Component.text(" Kills / Deaths: ", NamedTextColor.GRAY)
                .append(Component.text(profile.stats().kills(), NamedTextColor.RED))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.text(profile.stats().deaths(), NamedTextColor.DARK_RED)));
        sender.sendMessage(Component.text(" Scoreboard: ", NamedTextColor.GRAY)
                .append(Component.text(profile.settings().isScoreboardVisible() ? "ON" : "OFF",
                        profile.settings().isScoreboardVisible() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));

        return true;
    }

    private String formatPlaytime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
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
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
