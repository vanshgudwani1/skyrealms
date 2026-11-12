package com.skyrealms.end.command;

import com.skyrealms.end.wilderness.EndMobArchetype;
import com.skyrealms.end.wilderness.EndWildernessService;
import com.skyrealms.end.wilderness.WildernessRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Command interface for SkyRealms End Wilderness regions, Bestiary, and hunting expeditions (`/wilds`).
 */
public final class EndWildernessCommand implements CommandExecutor, TabCompleter {

    private final EndWildernessService wildernessService;

    public EndWildernessCommand(@NotNull EndWildernessService wildernessService) {
        this.wildernessService = wildernessService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("regions")) {
            sender.sendMessage(Component.text("━━━ End Wilderness Hunting Regions ("
                    + wildernessService.wildernessWorldName() + ") ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
            for (WildernessRegion r : WildernessRegion.values()) {
                sender.sendMessage(Component.text("• " + r.id() + " ", TextColor.fromHexString(r.accentHex()), TextDecoration.BOLD)
                        .append(Component.text("[" + r.displayName() + " | Lv." + r.minLevel() + "-" + r.maxLevel() + "] ",
                                NamedTextColor.YELLOW))
                        .append(Component.text(r.description(), NamedTextColor.GRAY)));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "warp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can warp to the wilderness.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /wilds warp <shattered_fields|crystal_wilds|forgotten_ruins|abyssal_grounds>",
                            NamedTextColor.RED));
                    return true;
                }
                WildernessRegion region = WildernessRegion.byId(args[1]);
                if (region == null) {
                    sender.sendMessage(Component.text("Unknown hunting region: " + args[1], NamedTextColor.RED));
                    return true;
                }
                wildernessService.warpToRegion(player, region);
                return true;
            }
            case "return", "village" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can return to the End Village.");
                    return true;
                }
                wildernessService.warpBackToVillage(player);
                return true;
            }
            case "bestiary" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Run /wilds bestiary as a player.");
                    return true;
                }
                wildernessService.showBestiary(player);
                return true;
            }
            case "spawn" -> {
                if (!sender.hasPermission("skyrealms.end.admin") || !(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Admin player required: /wilds spawn <mobId> [elite]", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /wilds spawn <mobId> [elite]", NamedTextColor.RED));
                    return true;
                }
                EndMobArchetype mob = EndMobArchetype.byId(args[1]);
                if (mob == null) {
                    sender.sendMessage(Component.text("Unknown mob archetype: " + args[1], NamedTextColor.RED));
                    return true;
                }
                boolean elite = args.length >= 3 && args[2].equalsIgnoreCase("elite");
                wildernessService.spawnCustomMob(mob, player.getLocation().add(2, 0.2, 2), elite);
                sender.sendMessage(Component.text("Spawned " + mob.displayName() + (elite ? " (Elite)" : ""), NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /wilds [regions|warp <region>|bestiary|return|spawn <mobId>]",
                        NamedTextColor.YELLOW));
                return true;
            }
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
            List<String> out = new ArrayList<>(List.of("regions", "warp", "bestiary", "return"));
            if (sender.hasPermission("skyrealms.end.admin")) {
                out.add("spawn");
            }
            return filterPrefix(out, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("warp")) {
            List<String> ids = new ArrayList<>();
            for (WildernessRegion r : WildernessRegion.values()) {
                ids.add(r.id());
            }
            return filterPrefix(ids, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            List<String> ids = new ArrayList<>();
            for (EndMobArchetype m : EndMobArchetype.values()) {
                ids.add(m.id());
            }
            return filterPrefix(ids, args[1]);
        }
        return List.of();
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

