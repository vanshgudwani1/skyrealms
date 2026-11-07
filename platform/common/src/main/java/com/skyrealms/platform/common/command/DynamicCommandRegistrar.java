package com.skyrealms.platform.common.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Universal dynamic Bukkit command registrar directly interacting with Bukkit's CommandMap.
 * Guarantees that commands, aliases, and permissions work on Paper even when plugins are loaded via paper-plugin.yml.
 */
public final class DynamicCommandRegistrar {

    private DynamicCommandRegistrar() {}

    public static void register(
            @NotNull String prefix,
            @NotNull String name,
            @NotNull String description,
            @NotNull String usage,
            @NotNull List<String> aliases,
            @Nullable String permission,
            @NotNull PermissionDefault permissionDefault,
            @Nullable String permissionMessage,
            @NotNull CommandExecutor executor
    ) {
        TabCompleter completer = (executor instanceof TabCompleter tc) ? tc : null;
        register(prefix, name, description, usage, aliases, permission, permissionDefault, permissionMessage, executor, completer);
    }

    public static void register(
            @NotNull String prefix,
            @NotNull String name,
            @NotNull String description,
            @NotNull String usage,
            @NotNull List<String> aliases,
            @Nullable String permission,
            @NotNull PermissionDefault permissionDefault,
            @Nullable String permissionMessage,
            @NotNull CommandExecutor executor,
            @Nullable TabCompleter tabCompleter
    ) {
        Objects.requireNonNull(prefix, "prefix cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(executor, "executor cannot be null");

        if (permission != null && !permission.isBlank()) {
            if (Bukkit.getPluginManager().getPermission(permission) == null) {
                try {
                    Bukkit.getPluginManager().addPermission(new Permission(permission, description, permissionDefault));
                } catch (Exception ignored) {}
            }
        }

        Command command = new Command(name, description, usage, aliases) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                if (testPermission(sender)) {
                    return executor.onCommand(sender, this, commandLabel, args);
                }
                return true;
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                if (!testPermissionSilent(sender)) {
                    return Collections.emptyList();
                }
                if (tabCompleter != null) {
                    List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
                    return completions != null ? completions : Collections.emptyList();
                }
                return super.tabComplete(sender, alias, args);
            }
        };

        if (permission != null && !permission.isBlank()) {
            command.setPermission(permission);
        }

        CommandMap map = Bukkit.getCommandMap();
        map.register(prefix, command);
    }
}
