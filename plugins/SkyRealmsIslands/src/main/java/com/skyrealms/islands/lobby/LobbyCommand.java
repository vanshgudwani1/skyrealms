package com.skyrealms.islands.lobby;

import com.skyrealms.islands.SkyRealmsIslandsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Command handling /spawn, /lobby, and /lobby paste administrative action.
 */
public final class LobbyCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final LobbyService lobbyService;

    public LobbyCommand(Plugin plugin, LobbyService lobbyService) {
        this.plugin = plugin;
        this.lobbyService = lobbyService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("holograms")) {
            if (!sender.hasPermission("skyrealms.admin")) {
                sender.sendMessage(Component.text("You do not have permission to execute this command.", NamedTextColor.RED));
                return true;
            }
            World world = Bukkit.getWorld("world");
            if (world != null) {
                lobbyService.getHologramService().spawnAllHolograms(world);
                sender.sendMessage(Component.text("✔ Celestial TextDisplay holograms refreshed!", NamedTextColor.GREEN));
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("paste")) {
            if (!sender.hasPermission("skyrealms.admin")) {
                sender.sendMessage(Component.text("You do not have permission to execute this command.", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("🌸 Re-generating Celestial Dragon Nest Lobby...", NamedTextColor.LIGHT_PURPLE));
            lobbyService.initLobby(true).thenAccept(success -> {
                if (success) {
                    sender.sendMessage(Component.text("✔ Celestial Dragon Nest Lobby regenerated successfully!", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("✖ Failed to regenerate lobby. Check server logs.", NamedTextColor.RED));
                }
            });
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport to spawn.", NamedTextColor.RED));
            return true;
        }

        Location spawn = lobbyService.getLobbySpawnLocation();
        player.teleportAsync(spawn).thenAccept(success -> {
            if (success) {
                player.playSound(spawn, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 1.0f);
                player.playSound(spawn, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                player.spawnParticle(Particle.CHERRY_LEAVES, spawn, 30, 0.8, 0.8, 0.8, 0.05);
                player.sendMessage(Component.text("🌸 Teleported to the Celestial Dragon Nest Lobby.", NamedTextColor.LIGHT_PURPLE));
            }
        });

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("skyrealms.admin")) {
            return List.of("holograms", "paste");
        }
        return List.of();
    }
}
