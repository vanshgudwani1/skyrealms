package com.skyrealms.end.command;

import com.skyrealms.end.quest.EndQuestService;
import com.skyrealms.end.quest.EndQuestService.ClaimOutcome;
import com.skyrealms.end.quest.QuestDefinition;
import com.skyrealms.end.quest.QuestProgressRepository;
import com.skyrealms.end.quest.QuestProgressRepository.QuestProgressRow;
import com.skyrealms.end.quest.QuestProgressRepository.QuestStatus;
import com.skyrealms.end.quest.QuestRegistry;
import com.skyrealms.end.service.EndProfileRepository;
import com.skyrealms.end.service.EndProfileRepository.EndPlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Command interface for End Civilization quests, guild trials, contracts, and reputation (`/endquest`).
 */
public final class EndQuestCommand implements CommandExecutor, TabCompleter {

    private final QuestRegistry questRegistry;
    private final QuestProgressRepository questRepository;
    private final EndProfileRepository profileRepository;
    private final EndQuestService questService;

    public EndQuestCommand(
            @NotNull QuestRegistry questRegistry,
            @NotNull QuestProgressRepository questRepository,
            @NotNull EndProfileRepository profileRepository,
            @NotNull EndQuestService questService
    ) {
        this.questRegistry = questRegistry;
        this.questRepository = questRepository;
        this.profileRepository = profileRepository;
        this.questService = questService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("board")) {
            String filter = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
            showQuestLog(sender, filter);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "accept", "deliver", "claim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can run /endquest " + sub + ".");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /endquest " + sub + " <questId>", NamedTextColor.RED));
                    return true;
                }
                try {
                    ClaimOutcome outcome = switch (sub) {
                        case "accept" -> questService.acceptQuest(player.getUniqueId(), args[1]);
                        case "deliver" -> questService.deliverCollectionItems(player, args[1]);
                        default -> questService.claimReward(player.getUniqueId(), args[1]);
                    };
                    player.sendMessage(Component.text(outcome.message(),
                            outcome.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                } catch (SQLException ex) {
                    player.sendMessage(Component.text("Database error: " + ex.getMessage(), NamedTextColor.RED));
                }
                return true;
            }
            case "reputation", "rep" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Use /endquest inspect <player> from console.");
                    return true;
                }
                try {
                    EndPlayerProfile profile = profileRepository.loadOrCreate(player.getUniqueId());
                    player.sendMessage(Component.text(
                            "✦ End Reputation: " + profile.reputation()
                                    + " (" + EndQuestService.reputationTierName(profile.reputation()) + ")",
                            NamedTextColor.AQUA, TextDecoration.BOLD
                    ));
                } catch (SQLException ex) {
                    player.sendMessage(Component.text("Failed loading reputation.", NamedTextColor.RED));
                }
                return true;
            }
            case "complete", "adminclaim" -> {
                if (!sender.hasPermission("skyrealms.end.admin")) {
                    sender.sendMessage(Component.text("Missing permission skyrealms.end.admin", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /endquest " + sub + " <player> <questId>", NamedTextColor.RED));
                    return true;
                }
                UUID uuid = resolveTargetUuid(args[1]);
                if (uuid == null) {
                    sender.sendMessage(Component.text("Unknown player: " + args[1], NamedTextColor.RED));
                    return true;
                }
                try {
                    if ("complete".equals(sub)) {
                        questService.forceCompleteForTesting(uuid, args[2]);
                        sender.sendMessage(Component.text("Marked quest " + args[2] + " COMPLETED for " + args[1] + ".", NamedTextColor.GREEN));
                    } else {
                        ClaimOutcome res = questService.claimReward(uuid, args[2]);
                        sender.sendMessage(Component.text(res.message(), res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                    }
                } catch (Exception ex) {
                    sender.sendMessage(Component.text("Error: " + ex.getMessage(), NamedTextColor.RED));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /endquest [list|accept <id>|deliver <id>|claim <id>|reputation]",
                        NamedTextColor.YELLOW));
                return true;
            }
        }
    }

    private void showQuestLog(CommandSender sender, String filter) {
        sender.sendMessage(Component.text("━━━ SkyRealms End Quests (" + filter + ") ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        Map<String, QuestProgressRow> rows = Map.of();
        LocalDate today = LocalDate.now();
        if (sender instanceof Player player) {
            try {
                rows = questRepository.loadAll(player.getUniqueId());
            } catch (SQLException ignored) {
            }
        }
        for (QuestDefinition q : questRegistry.all()) {
            if (!"all".equals(filter) && !q.npcBoardTag().equals(filter) && !q.category().name().equalsIgnoreCase(filter)) {
                continue;
            }
            QuestProgressRow row = rows.get(QuestProgressRepository.key(q.id(), QuestRegistry.cycleKeyFor(q, today)));
            QuestStatus status = row != null ? row.status() : QuestStatus.AVAILABLE;
            int prog = row != null ? row.progressValue() : 0;
            Component line = Component.text("• [" + status.name() + " " + prog + "/" + q.targetValue() + "] ",
                            NamedTextColor.YELLOW)
                    .append(Component.text(q.id() + " ", TextColor.fromHexString(q.category().hexColor()), TextDecoration.BOLD))
                    .append(Component.text("— " + q.title(), NamedTextColor.GRAY));
            if (sender instanceof Player && status == QuestStatus.AVAILABLE) {
                line = line.clickEvent(ClickEvent.runCommand("/endquest accept " + q.id()));
            }
            sender.sendMessage(line);
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("list", "accept", "deliver", "claim", "reputation"));
            if (sender.hasPermission("skyrealms.end.admin")) {
                out.addAll(List.of("complete", "adminclaim"));
            }
            return filterPrefix(out, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deliver") || args[0].equalsIgnoreCase("claim"))) {
            List<String> ids = new ArrayList<>();
            for (QuestDefinition q : questRegistry.all()) {
                ids.add(q.id());
            }
            return filterPrefix(ids, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("complete") || args[0].equalsIgnoreCase("adminclaim"))) {
            List<String> ids = new ArrayList<>();
            for (QuestDefinition q : questRegistry.all()) {
                ids.add(q.id());
            }
            return filterPrefix(ids, args[2]);
        }
        return List.of();
    }

    private static UUID resolveTargetUuid(String arg) {
        Player online = Bukkit.getPlayerExact(arg);
        if (online != null) {
            return online.getUniqueId();
        }
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(arg);
            return cached != null ? cached.getUniqueId() : Bukkit.getOfflinePlayer(arg).getUniqueId();
        }
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(opt);
            }
        }
        return out;
    }
}

