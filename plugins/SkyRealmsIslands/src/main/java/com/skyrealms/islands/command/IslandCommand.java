package com.skyrealms.islands.command;

import com.skyrealms.islands.gui.IslandGui;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandMember;
import com.skyrealms.islands.model.IslandRole;
import com.skyrealms.islands.model.IslandSettings;
import com.skyrealms.islands.service.IslandManager;
import com.skyrealms.islands.service.IslandService;
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

import java.util.*;

/**
 * Core command handler for /island (/is).
 */
public final class IslandCommand implements CommandExecutor, TabCompleter {

    private final IslandService islandService;
    private final IslandGui islandGui;

    public IslandCommand(@NotNull IslandService islandService, @NotNull IslandGui islandGui) {
        this.islandService = Objects.requireNonNull(islandService, "islandService cannot be null");
        this.islandGui = Objects.requireNonNull(islandGui, "islandGui cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can manage islands.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) {
            islandGui.openMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                String name = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
                player.sendMessage(Component.text("🌸 Allocating celestial coordinates and generating your Cherry Island...", TextColor.fromHexString("#FF77A8")));
                islandService.createIsland(player.getUniqueId(), name)
                        .thenAccept(island -> {
                            player.sendMessage(Component.text("🌸 Welcome to your Cherry Blossom Sanctuary!", TextColor.fromHexString("#FF77A8"), TextDecoration.BOLD));
                            islandService.teleportHome(player);
                        })
                        .exceptionally(ex -> {
                            player.sendMessage(Component.text("✖ Could not create island: " + ex.getCause().getMessage(), NamedTextColor.RED));
                            return null;
                        });
            }
            case "home" -> islandService.teleportHome(player);
            case "farm", "garden" -> player.performCommand("garden home");
            case "border" -> {
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                if (islandService instanceof IslandManager im) {
                    im.effectsService().toggleBorderVisualizer(player, opt.get());
                }
            }
            case "upgrades" -> islandGui.openUpgradesMenu(player);
            case "warps" -> islandGui.openPublicIslandsMenu(player);
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /island invite <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(Component.text("Player '" + args[1] + "' is not online.", NamedTextColor.RED));
                    return true;
                }
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                islandService.invitePlayer(opt.get().id(), player.getUniqueId(), target.getUniqueId()).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("✔ Sent island invite to " + target.getName() + ".", NamedTextColor.GREEN));
                        target.sendMessage(Component.text("✉ " + player.getName() + " invited you to join their island! Type ", NamedTextColor.GOLD)
                                .append(Component.text("/island accept " + player.getName(), NamedTextColor.YELLOW))
                                .append(Component.text(" to join.", NamedTextColor.GOLD)));
                    } else {
                        player.sendMessage(Component.text("✖ You lack permission or the player is already a member.", NamedTextColor.RED));
                    }
                });
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /island accept <player>", NamedTextColor.RED));
                    return true;
                }
                Player inviter = Bukkit.getPlayer(args[1]);
                if (inviter == null) {
                    player.sendMessage(Component.text("Player '" + args[1] + "' is not online.", NamedTextColor.RED));
                    return true;
                }
                Optional<Island> targetIsland = islandService.getIslandByPlayer(inviter.getUniqueId());
                if (targetIsland.isEmpty()) {
                    player.sendMessage(Component.text("That player does not belong to an active island.", NamedTextColor.RED));
                    return true;
                }
                islandService.acceptInvite(player.getUniqueId(), targetIsland.get().id()).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("✔ Joined island successfully! Use /island home to visit.", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("✖ No pending invite found or you are already in an island.", NamedTextColor.RED));
                    }
                });
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /island kick <player>", NamedTextColor.RED));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                islandService.kickPlayer(opt.get().id(), player.getUniqueId(), target.getUniqueId()).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("✔ Kicked " + target.getName() + " from the island.", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("✖ Could not kick player (insufficient rank or player not in island).", NamedTextColor.RED));
                    }
                });
            }
            case "leave" -> {
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                islandService.leaveIsland(player.getUniqueId(), opt.get().id()).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("✔ You left the island.", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("✖ Island leaders cannot leave their island. Use /island delete or transfer ownership.", NamedTextColor.RED));
                    }
                });
            }
            case "visit" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /island visit <player>", NamedTextColor.RED));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                Optional<Island> targetIsland = islandService.getIslandByPlayer(target.getUniqueId());
                if (targetIsland.isEmpty()) {
                    player.sendMessage(Component.text("Player '" + args[1] + "' does not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                islandService.visitIsland(player, targetIsland.get().id());
            }
            case "info" -> {
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                Island is = opt.get();
                player.sendMessage(Component.text("--------- [ Island Information ] ---------", NamedTextColor.AQUA));
                player.sendMessage(Component.text("Name: ", NamedTextColor.GRAY).append(Component.text(is.name() != null ? is.name() : "Island", NamedTextColor.WHITE)));
                player.sendMessage(Component.text("Size: ", NamedTextColor.GRAY).append(Component.text(is.size() + "x" + is.size() + " blocks", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("Level: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(is.level()), NamedTextColor.GOLD)));
                player.sendMessage(Component.text("Members: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(is.members().size()), NamedTextColor.GREEN)));
                player.sendMessage(Component.text("------------------------------------------", NamedTextColor.AQUA));
            }
            case "members" -> {
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                Island is = opt.get();
                player.sendMessage(Component.text("--------- [ Island Members ] ---------", NamedTextColor.AQUA));
                for (IslandMember m : is.members().values()) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(m.playerUuid());
                    String name = op.getName() != null ? op.getName() : m.playerUuid().toString();
                    player.sendMessage(Component.text("• " + name + " - ", NamedTextColor.GRAY)
                            .append(Component.text(m.role().name(), NamedTextColor.YELLOW)));
                }
                player.sendMessage(Component.text("--------------------------------------", NamedTextColor.AQUA));
            }
            case "settings" -> {
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                Island is = opt.get();
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /island settings <public|pvp> <true|false>", NamedTextColor.RED));
                    return true;
                }
                if (is.getRole(player.getUniqueId()) != IslandRole.LEADER) {
                    player.sendMessage(Component.text("Only the island leader can modify settings.", NamedTextColor.RED));
                    return true;
                }

                String setting = args[1].toLowerCase(Locale.ROOT);
                boolean value = Boolean.parseBoolean(args[2]);

                IslandSettings current = is.settings();
                IslandSettings updated;
                if (setting.equals("public")) {
                    updated = new IslandSettings(value, current.visitorBlockBreak(), current.visitorBlockPlace(), current.visitorContainerAccess(), current.pvpEnabled(), current.settingsJson());
                } else if (setting.equals("pvp")) {
                    updated = new IslandSettings(current.publicVisits(), current.visitorBlockBreak(), current.visitorBlockPlace(), current.visitorContainerAccess(), value, current.settingsJson());
                } else {
                    player.sendMessage(Component.text("Unknown setting: " + setting + ". Available: public, pvp", NamedTextColor.RED));
                    return true;
                }

                islandService.updateSettings(is.id(), updated).thenRun(() -> {
                    player.sendMessage(Component.text("✔ Setting '" + setting + "' updated to " + value + ".", NamedTextColor.GREEN));
                });
            }
            case "delete" -> {
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                Island is = opt.get();
                if (is.getRole(player.getUniqueId()) != IslandRole.LEADER) {
                    player.sendMessage(Component.text("Only the island leader can delete the island.", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text("🌸 Deleting island and teleporting you to spawn...", TextColor.fromHexString("#FF77A8")));
                islandService.deleteIsland(is.id()).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("✔ Your island has been permanently deleted and cleared.", NamedTextColor.GREEN, TextDecoration.BOLD));
                    } else {
                        player.sendMessage(Component.text("✖ Could not delete island.", NamedTextColor.RED));
                    }
                });
            }
            case "reset" -> {
                Optional<Island> opt = islandService.getIslandByPlayer(player.getUniqueId());
                if (opt.isEmpty()) {
                    player.sendMessage(Component.text("You do not belong to an island.", NamedTextColor.RED));
                    return true;
                }
                Island is = opt.get();
                if (is.getRole(player.getUniqueId()) != IslandRole.LEADER) {
                    player.sendMessage(Component.text("Only the island leader can reset the island.", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text("🌸 Resetting island and generating fresh Cherry Blossom platform...", TextColor.fromHexString("#FF77A8")));
                islandService.resetIsland(is.id()).thenAccept(success -> {
                    if (!success) {
                        player.sendMessage(Component.text("✖ Could not reset island.", NamedTextColor.RED));
                    }
                });
            }
            default -> player.sendMessage(Component.text("Unknown subcommand. Type /island for the menu.", NamedTextColor.RED));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "home", "farm", "garden", "invite", "accept", "kick", "leave", "visit", "settings", "info", "members", "name", "reset", "delete", "gui", "menu", "border", "upgrades", "warps");
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("invite") || sub.equals("accept") || sub.equals("kick") || sub.equals("visit")) {
                List<String> list = new ArrayList<>();
                String prefix = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) {
                        list.add(p.getName());
                    }
                }
                return list;
            }
            if (sub.equals("settings")) {
                return List.of("public", "pvp");
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("settings")) {
            return List.of("true", "false");
        }
        return List.of();
    }
}
