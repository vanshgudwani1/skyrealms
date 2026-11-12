package com.skyrealms.end.command;

import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressRepository;
import com.skyrealms.end.progression.DisciplineProgressState.DisciplineEntry;
import com.skyrealms.end.progression.DisciplineProgressState.LeaderboardEntry;
import com.skyrealms.end.progression.DisciplineProgressState.PlayerDisciplineSnapshot;
import com.skyrealms.end.progression.DisciplineProgressState.XpAwardResult;
import com.skyrealms.end.progression.EndXpCurve;
import com.skyrealms.end.progression.EndXpCurve.DisciplineAttributes;
import com.skyrealms.end.progression.XpSource;
import net.kyori.adventure.text.Component;
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
 * Command interface for End Civilization discipline inspection, switching, rankings, and admin grants.
 * Registered as {@code /endcombat} with aliases {@code /discipline} and {@code /endstats}.
 */
public final class EndCombatCommand implements CommandExecutor, TabCompleter {

    private final CombatProgressionService progressionService;
    private final DisciplineProgressRepository repository;

    public EndCombatCommand(
            @NotNull CombatProgressionService progressionService,
            @NotNull DisciplineProgressRepository repository
    ) {
        this.progressionService = progressionService;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /discipline top [archery|melee|mage] | /discipline givexp <player> <discipline> <xp> | /discipline setlevel <player> <discipline> <level>");
                return true;
            }
            showPlayerStats(sender, player.getUniqueId(), player.getName());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "switch" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can switch active disciplines.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /discipline switch <archery|melee|mage>", NamedTextColor.RED));
                    return true;
                }
                CombatDiscipline target = parseDisciplineStrict(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Unknown discipline: " + args[1], NamedTextColor.RED));
                    return true;
                }
                boolean changed = progressionService.switchDiscipline(player, target, true);
                if (!changed) {
                    sender.sendMessage(Component.text("Your active discipline is already " + target.displayName() + ".", NamedTextColor.YELLOW));
                }
                return true;
            }
            case "top", "leaderboard" -> {
                CombatDiscipline filter = args.length >= 2 ? parseDisciplineStrict(args[1]) : null;
                showLeaderboard(sender, filter);
                return true;
            }
            case "givexp" -> {
                if (!sender.hasPermission("skyrealms.end.admin")) {
                    sender.sendMessage(Component.text("You lack permission to grant discipline XP.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /discipline givexp <player> <archery|melee|mage> <amount>", NamedTextColor.RED));
                    return true;
                }
                handleGiveXp(sender, args[1], args[2], args[3]);
                return true;
            }
            case "setlevel" -> {
                if (!sender.hasPermission("skyrealms.end.admin")) {
                    sender.sendMessage(Component.text("You lack permission to set discipline levels.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /discipline setlevel <player> <archery|melee|mage> <1..30>", NamedTextColor.RED));
                    return true;
                }
                handleSetLevel(sender, args[1], args[2], args[3]);
                return true;
            }
            case "inspect" -> {
                if (!sender.hasPermission("skyrealms.end.admin") || args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /discipline inspect <player>", NamedTextColor.RED));
                    return true;
                }
                UUID targetUuid = resolveTargetUuid(args[1]);
                if (targetUuid == null) {
                    sender.sendMessage(Component.text("Unknown player: " + args[1], NamedTextColor.RED));
                    return true;
                }
                showPlayerStats(sender, targetUuid, args[1]);
                return true;
            }
            default -> {
                sender.sendMessage(Component.text(
                        "Usage: /discipline [stats|switch <disc>|top [disc]|givexp|setlevel|inspect]",
                        NamedTextColor.YELLOW
                ));
                return true;
            }
        }
    }

    private void showPlayerStats(CommandSender viewer, UUID uuid, String name) {
        try {
            PlayerDisciplineSnapshot snap = progressionService.getOrLoadSnapshot(uuid);
            viewer.sendMessage(Component.text(
                    "━━━ End Combat Disciplines (" + name + ") ━━━",
                    NamedTextColor.GOLD,
                    TextDecoration.BOLD
            ));
            viewer.sendMessage(Component.text(
                    "Active Discipline: " + snap.activeDiscipline().displayName(),
                    TextColor.fromHexString(snap.activeDiscipline().accentHex())
            ));

            for (CombatDiscipline disc : CombatDiscipline.values()) {
                DisciplineEntry entry = snap.get(disc);
                TextColor accent = TextColor.fromHexString(disc.accentHex());
                String marker = disc == snap.activeDiscipline() ? " [ACTIVE]" : "";
                String seal = entry.masteryCompleted() ? " ★MASTER" : "";
                long nextStep = EndXpCurve.xpRequiredForNextLevel(entry.level());
                String stepStr = entry.level() >= EndXpCurve.MAX_LEVEL
                        ? "MAX (" + entry.totalXp() + "/" + EndXpCurve.TOTAL_XP_FOR_MAX_LEVEL + " XP)"
                        : EndXpCurve.xpIntoCurrentLevel(entry.totalXp()) + "/" + nextStep + " XP (Total: " + entry.totalXp() + ")";

                viewer.sendMessage(
                        Component.text("• " + disc.displayName() + " Lv." + entry.level() + marker + seal, accent, TextDecoration.BOLD)
                                .append(Component.text(" — " + stepStr, NamedTextColor.GRAY))
                );
                viewer.sendMessage(Component.text("  " + formatAttributeLine(disc, entry.level()), NamedTextColor.DARK_GRAY));
            }
        } catch (SQLException ex) {
            viewer.sendMessage(Component.text("Failed loading discipline stats: " + ex.getMessage(), NamedTextColor.RED));
        }
    }

    private void showLeaderboard(CommandSender viewer, CombatDiscipline filter) {
        List<CombatDiscipline> disciplines = filter != null
                ? List.of(filter)
                : List.of(CombatDiscipline.values());
        viewer.sendMessage(Component.text("━━━ End Discipline Rankings ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        try {
            for (CombatDiscipline disc : disciplines) {
                List<LeaderboardEntry> entries = repository.getTopByDiscipline(disc, 5);
                viewer.sendMessage(Component.text("• " + disc.displayName() + ":",
                        TextColor.fromHexString(disc.accentHex()), TextDecoration.BOLD));
                if (entries.isEmpty()) {
                    viewer.sendMessage(Component.text("  (No entries yet)", NamedTextColor.DARK_GRAY));
                    continue;
                }
                int rank = 1;
                for (LeaderboardEntry e : entries) {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(e.playerUuid());
                    String name = off.getName() != null ? off.getName() : e.playerUuid().toString().substring(0, 8);
                    viewer.sendMessage(Component.text(
                            "  #" + rank++ + " " + name + " — Lv." + e.level() + " (" + e.totalXp() + " XP)",
                            NamedTextColor.GRAY
                    ));
                }
            }
        } catch (SQLException ex) {
            viewer.sendMessage(Component.text("Failed querying rankings: " + ex.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleGiveXp(CommandSender sender, String targetArg, String discArg, String amountArg) {
        UUID uuid = resolveTargetUuid(targetArg);
        CombatDiscipline disc = parseDisciplineStrict(discArg);
        long amount;
        try {
            amount = Long.parseLong(amountArg);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Invalid XP amount: " + amountArg, NamedTextColor.RED));
            return;
        }
        if (uuid == null || disc == null) {
            sender.sendMessage(Component.text("Invalid player or discipline.", NamedTextColor.RED));
            return;
        }
        try {
            XpAwardResult res = progressionService.awardXp(uuid, disc, amount, XpSource.ADMIN_GRANT, "admin:" + sender.getName());
            sender.sendMessage(Component.text(
                    "Granted +" + res.grantedXp() + " " + disc.displayName() + " XP -> Lv." + res.newLevel()
                            + " (Total: " + res.newTotalXp() + "/" + EndXpCurve.TOTAL_XP_FOR_MAX_LEVEL + ")",
                    NamedTextColor.GREEN
            ));
        } catch (SQLException ex) {
            sender.sendMessage(Component.text("Database error: " + ex.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleSetLevel(CommandSender sender, String targetArg, String discArg, String levelArg) {
        UUID uuid = resolveTargetUuid(targetArg);
        CombatDiscipline disc = parseDisciplineStrict(discArg);
        int level;
        try {
            level = Integer.parseInt(levelArg);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Invalid level: " + levelArg, NamedTextColor.RED));
            return;
        }
        if (uuid == null || disc == null) {
            sender.sendMessage(Component.text("Invalid player or discipline.", NamedTextColor.RED));
            return;
        }
        try {
            progressionService.setDisciplineLevel(uuid, disc, level);
            long targetXp = EndXpCurve.totalXpForLevel(Math.max(1, Math.min(EndXpCurve.MAX_LEVEL, level)));
            sender.sendMessage(Component.text(
                    "Set " + targetArg + " " + disc.displayName() + " to Lv." + Math.max(1, Math.min(30, level))
                            + " (" + targetXp + " XP).",
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
            List<String> subs = new ArrayList<>(List.of("stats", "switch", "top"));
            if (sender.hasPermission("skyrealms.end.admin")) {
                subs.addAll(List.of("givexp", "setlevel", "inspect"));
            }
            return filterPrefix(subs, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("switch") || args[0].equalsIgnoreCase("top"))) {
            return filterPrefix(List.of("archery", "melee", "mage"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("setlevel"))) {
            return filterPrefix(List.of("archery", "melee", "mage"), args[2]);
        }
        return List.of();
    }

    private static CombatDiscipline parseDisciplineStrict(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return CombatDiscipline.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    private static String formatAttributeLine(CombatDiscipline disc, int level) {
        DisciplineAttributes a = EndXpCurve.attributesFor(disc, level);
        return switch (disc) {
            case ARCHERY -> String.format(Locale.US, "+%.1f%% Bow Dmg | +%.1f%% Arrow Speed | %.1f%% Crit | +%.1f%% Agility",
                    a.bonusRangedDamagePercent(), a.bonusArrowVelocityPercent(), a.critDrawChancePercent(), a.walkSpeedBonusScalar() * 100.0);
            case MELEE -> String.format(Locale.US, "+%.2f Melee Dmg | +%.1f Max HP | +%.2f Toughness",
                    a.bonusMeleeDamage(), a.bonusMaxHealth(), a.bonusArmorToughness());
            case MAGE -> String.format(Locale.US, "%.0f Max Mana | %.2f Mana/s | %.2fx Spell Power | %.0f%% Haste",
                    a.maxMana(), a.manaRegenPerSecond(), a.spellPowerMultiplier(), a.cooldownHastePercent());
        };
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

