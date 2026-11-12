package com.skyrealms.end.command;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.district.EndDistrict;
import com.skyrealms.end.district.EndVillageBounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Administrative & exploration command (/endvillage) for inspecting districts, safe-zones,
 * and warping to surveyed End Civilization districts.
 */
public final class EndVillageCommand implements CommandExecutor, TabCompleter {

    private final EndVillageConfig config;
    private final EndVillageBounds bounds;

    public EndVillageCommand(@NotNull EndVillageConfig config, @NotNull EndVillageBounds bounds) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.bounds = Objects.requireNonNull(bounds, "bounds cannot be null");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            return handleInfo(sender);
        }

        if (args[0].equalsIgnoreCase("districts")) {
            sender.sendMessage(Component.text("✦ SkyRealms End Civilization Districts:", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            for (EndDistrict d : EndDistrict.values()) {
                EndVillageConfig.DistrictSpec spec = config.districts().get(d);
                TextColor col = TextColor.fromHexString(spec != null ? spec.hexColor() : d.hexColor());
                long npcCount = config.npcs().values().stream().filter(n -> n.district() == d).count();
                sender.sendMessage(
                        Component.text("  • ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(d.displayName(), col != null ? col : NamedTextColor.AQUA, TextDecoration.BOLD))
                                .append(Component.text(" [" + d.id() + "] (" + npcCount + " NPCs)", NamedTextColor.GRAY))
                );
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("tp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can teleport to districts.", NamedTextColor.RED));
                return true;
            }
            if (!player.hasPermission("skyrealms.end.admin")) {
                player.sendMessage(Component.text("You lack permission to teleport across End districts.", NamedTextColor.RED));
                return true;
            }
            EndDistrict target = args.length >= 2 ? EndDistrict.fromId(args[1]) : EndDistrict.CENTRAL_PLAZA;
            if (target == null) {
                player.sendMessage(Component.text("Unknown district: " + args[1], NamedTextColor.RED));
                return true;
            }
            World world = Bukkit.getWorld(config.worldName());
            if (world == null) {
                player.sendMessage(Component.text("World '" + config.worldName() + "' is not currently loaded.", NamedTextColor.RED));
                return true;
            }
            EndVillageConfig.DistrictSpec spec = config.districts().get(target);
            EndVillageConfig.AnchorPoint anchor = spec != null ? spec.spawn() : config.spawnPoint();
            Location dest = new Location(world, anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch());
            player.teleportAsync(dest).thenAccept(ok -> {
                if (ok) {
                    player.sendMessage(Component.text("✦ Warped to " + target.displayName() + ".", NamedTextColor.LIGHT_PURPLE));
                }
            });
            return true;
        }

        sender.sendMessage(Component.text("Usage: /endvillage [info|districts|tp <district>]", NamedTextColor.YELLOW));
        return true;
    }

    private boolean handleInfo(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("✦ SkyRealms End Civilization (World: " + config.worldName() + ")", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        sender.sendMessage(Component.text("  Districts: " + config.districts().size()
                + " | Configured NPCs: " + config.npcs().size()
                + " | Training Arenas: " + config.trainingArenas().size(), NamedTextColor.GRAY));

        if (sender instanceof Player player) {
            Location loc = player.getLocation();
            String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "";
            boolean safe = bounds.isInSafeZone(worldName, loc.getX(), loc.getY(), loc.getZ());
            EndDistrict district = bounds.isEndWorld(worldName) ? bounds.resolveDistrict(loc.getX(), loc.getY(), loc.getZ()) : null;
            sender.sendMessage(Component.text("  Your Location: ", NamedTextColor.GRAY)
                    .append(Component.text(district != null ? district.displayName() : "Outside Village", NamedTextColor.AQUA))
                    .append(Component.text(" | Safe-Zone: " + (safe ? "PROTECTED" : "NONE"), safe ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
        }
        return true;
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
            List<String> subs = new ArrayList<>(List.of("info", "districts"));
            if (sender.hasPermission("skyrealms.end.admin")) {
                subs.add("tp");
            }
            return filterPrefix(subs, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp") && sender.hasPermission("skyrealms.end.admin")) {
            List<String> ids = new ArrayList<>();
            for (EndDistrict d : EndDistrict.values()) {
                ids.add(d.id());
            }
            return filterPrefix(ids, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.startsWith(lower)) {
                out.add(opt);
            }
        }
        return out;
    }
}

