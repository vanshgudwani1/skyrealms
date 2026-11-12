package com.skyrealms.end.command;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.npc.EndNpcDefinition;
import com.skyrealms.end.npc.EndNpcManager;
import com.skyrealms.end.npc.EndNpcRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Administrative inspection & management command (/endnpc) for End Village NPCs.
 */
public final class EndNpcCommand implements CommandExecutor, TabCompleter {

    private final EndVillageConfig config;
    private final EndNpcRegistry registry;
    private final EndNpcManager manager;

    public EndNpcCommand(
            @NotNull EndVillageConfig config,
            @NotNull EndNpcRegistry registry,
            @NotNull EndNpcManager manager
    ) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("skyrealms.end.admin")) {
            sender.sendMessage(Component.text("You lack permission to manage End NPCs.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("✦ Configured End NPCs (" + registry.size() + "):", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            for (EndNpcDefinition def : registry.all()) {
                boolean active = manager.getBodyEntity(def.id()) != null;
                sender.sendMessage(Component.text("  • " + def.id(), NamedTextColor.AQUA)
                        .append(Component.text(" (" + def.displayName() + ") [" + def.district().id() + "] "
                                + (active ? "ACTIVE" : "UNLOADED"), active ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("respawn")) {
            int count = manager.spawnAllLoaded();
            sender.sendMessage(Component.text("✦ Respawned " + count + "/" + registry.size() + " loaded End NPCs.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("tp") && args.length >= 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can teleport to NPCs.", NamedTextColor.RED));
                return true;
            }
            EndNpcDefinition def = registry.get(args[1]);
            if (def == null) {
                sender.sendMessage(Component.text("Unknown NPC id: " + args[1], NamedTextColor.RED));
                return true;
            }
            World world = Bukkit.getWorld(config.worldName());
            if (world == null) {
                sender.sendMessage(Component.text("World '" + config.worldName() + "' is not loaded.", NamedTextColor.RED));
                return true;
            }
            Location loc = new Location(world, def.anchor().x(), def.anchor().y(), def.anchor().z(), def.anchor().yaw(), 0f);
            player.teleportAsync(loc).thenAccept(ok -> {
                if (ok) {
                    player.sendMessage(Component.text("✦ Teleported to " + def.displayName() + ".", NamedTextColor.LIGHT_PURPLE));
                }
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("interact") && args.length >= 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can trigger NPC interaction.", NamedTextColor.RED));
                return true;
            }
            manager.handleInteraction(player, args[1]);
            return true;
        }

        sender.sendMessage(Component.text("Usage: /endnpc [list|respawn|tp <id>|interact <id>]", NamedTextColor.YELLOW));
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
        if (!sender.hasPermission("skyrealms.end.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(List.of("list", "respawn", "tp", "interact"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("interact"))) {
            List<String> ids = new ArrayList<>();
            for (EndNpcDefinition def : registry.all()) {
                ids.add(def.id());
            }
            return filter(ids, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> items, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String item : items) {
            if (item.startsWith(lower)) {
                out.add(item);
            }
        }
        return out;
    }
}

