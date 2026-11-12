package com.skyrealms.end.command;

import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.XpSource;
import com.skyrealms.end.training.EndTrainingService;
import com.skyrealms.end.training.TrainingChallengeType;
import com.skyrealms.end.training.TrainingScoringEngine;
import com.skyrealms.end.training.TrainingScoringEngine.TrainingCompletionSummary;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Command interface for End Village outdoor training courtyards (`/training`, `/endtrain`).
 */
public final class EndTrainingCommand implements CommandExecutor, TabCompleter {

    private final EndTrainingService trainingService;
    private final TrainingScoringEngine scoringEngine;
    private final CombatProgressionService progressionService;

    public EndTrainingCommand(
            @NotNull EndTrainingService trainingService,
            @NotNull TrainingScoringEngine scoringEngine,
            @NotNull CombatProgressionService progressionService
    ) {
        this.trainingService = trainingService;
        this.scoringEngine = scoringEngine;
        this.progressionService = progressionService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            showChallengeList(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only online players inside a courtyard can start a live training session.");
                    return true;
                }
                if (args.length < 2) {
                    showChallengeList(sender);
                    return true;
                }
                TrainingChallengeType type = TrainingChallengeType.byKey(args[1]);
                if (type == null) {
                    sender.sendMessage(Component.text("Unknown training challenge: " + args[1], NamedTextColor.RED));
                    return true;
                }
                trainingService.startChallenge(player, type);
                return true;
            }
            case "stop" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can stop an active training session.");
                    return true;
                }
                if (!trainingService.hasActiveSession(player.getUniqueId())) {
                    sender.sendMessage(Component.text("You do not have an active training drill.", NamedTextColor.YELLOW));
                    return true;
                }
                trainingService.endSession(player.getUniqueId(), true, "Ended manually.");
                return true;
            }
            case "simulate" -> {
                if (!sender.hasPermission("skyrealms.end.admin")) {
                    sender.sendMessage(Component.text("You lack permission to simulate training rewards.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 6) {
                    sender.sendMessage(Component.text("Usage: /training simulate <player> <mode> <score> <hits> <misses>", NamedTextColor.RED));
                    return true;
                }
                handleSimulate(sender, args[1], args[2], args[3], args[4], args[5]);
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /training [list|start <mode>|stop|simulate]", NamedTextColor.YELLOW));
                return true;
            }
        }
    }

    private void showChallengeList(CommandSender sender) {
        sender.sendMessage(Component.text("━━━ End Village Courtyard Training Drills ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (CombatDiscipline disc : CombatDiscipline.values()) {
            TextColor accent = TextColor.fromHexString(disc.accentHex());
            sender.sendMessage(Component.text("• " + disc.displayName() + " Courtyard:", accent, TextDecoration.BOLD));
            for (TrainingChallengeType type : TrainingChallengeType.byDiscipline(disc)) {
                Component line = Component.text("  - " + type.key() + " ", NamedTextColor.YELLOW)
                        .append(Component.text("(" + type.displayName() + ", Lv." + type.minLevel() + "+) — " + type.description(), NamedTextColor.GRAY));
                if (sender instanceof Player) {
                    line = line.clickEvent(ClickEvent.runCommand("/training start " + type.key()));
                }
                sender.sendMessage(line);
            }
        }
    }

    private void handleSimulate(
            CommandSender sender,
            String playerArg,
            String modeArg,
            String scoreArg,
            String hitsArg,
            String missesArg
    ) {
        UUID uuid = resolveTargetUuid(playerArg);
        TrainingChallengeType mode = TrainingChallengeType.byKey(modeArg);
        int score;
        int hits;
        int misses;
        try {
            score = Integer.parseInt(scoreArg);
            hits = Integer.parseInt(hitsArg);
            misses = Integer.parseInt(missesArg);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Score, hits, and misses must be integers.", NamedTextColor.RED));
            return;
        }
        if (uuid == null || mode == null) {
            sender.sendMessage(Component.text("Invalid player or training mode.", NamedTextColor.RED));
            return;
        }
        TrainingCompletionSummary summary = scoringEngine.summarizeAndComputeXp(
                uuid, mode, score, hits, misses, hits, System.currentTimeMillis());
        try {
            boolean newRecord = false;
            if (summary.xpAwarded() > 0L) {
                newRecord = progressionService.recordTrainingBestScore(uuid, mode.discipline(), summary.finalScore());
                progressionService.awardXp(uuid, mode.discipline(), summary.xpAwarded(), XpSource.TRAINING_CHALLENGE, mode.key());
            }
            sender.sendMessage(Component.text(
                    String.format(Locale.US, "Simulated [%s]: Score=%d%s, Accuracy=%.0f%%, +%d %s XP (scale=%.2fx)",
                            mode.key(), summary.finalScore(), newRecord ? " NEW RECORD" : "",
                            summary.accuracyPercent(), summary.xpAwarded(),
                            mode.discipline().displayName(), summary.xpScaleApplied()),
                    NamedTextColor.GREEN
            ));
        } catch (SQLException ex) {
            sender.sendMessage(Component.text("Database error: " + ex.getMessage(), NamedTextColor.RED));
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
            List<String> out = new ArrayList<>(List.of("list", "start", "stop"));
            if (sender.hasPermission("skyrealms.end.admin")) {
                out.add("simulate");
            }
            return filterPrefix(out, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> keys = new ArrayList<>();
            for (TrainingChallengeType t : TrainingChallengeType.values()) {
                keys.add(t.key());
            }
            return filterPrefix(keys, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("simulate")) {
            List<String> keys = new ArrayList<>();
            for (TrainingChallengeType t : TrainingChallengeType.values()) {
                keys.add(t.key());
            }
            return filterPrefix(keys, args[2]);
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

