package com.skyrealms.core.command;

import com.skyrealms.platform.api.module.ModuleLifecycleManager;
import com.skyrealms.platform.api.module.ModuleRegistry;
import com.skyrealms.platform.api.module.SkyModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Administrative and diagnostic command for SkyRealms.
 */
public final class SkyRealmsCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final ModuleRegistry moduleRegistry;
    private final ModuleLifecycleManager lifecycleManager;

    public SkyRealmsCommand(
            @NotNull Plugin plugin,
            @NotNull ModuleRegistry moduleRegistry,
            @NotNull ModuleLifecycleManager lifecycleManager
    ) {
        this.plugin = plugin;
        this.moduleRegistry = moduleRegistry;
        this.lifecycleManager = lifecycleManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("skyrealms.admin")) {
            sender.sendMessage(Component.text("You lack permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendHeader(sender);
            sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                    .append(Component.text("ONLINE", NamedTextColor.GREEN, TextDecoration.BOLD)));
            sender.sendMessage(Component.text("Active Modules: ", NamedTextColor.GRAY)
                    .append(Component.text(lifecycleManager.enabledModules().size(), NamedTextColor.AQUA)));
            sender.sendMessage(Component.text("Failed Modules: ", NamedTextColor.GRAY)
                    .append(Component.text(lifecycleManager.failedModules().size(), NamedTextColor.RED)));
            sender.sendMessage(Component.text("Version: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.getPluginMeta().getVersion(), NamedTextColor.YELLOW)));
            return true;
        }

        if (args[0].equalsIgnoreCase("modules")) {
            sendHeader(sender);
            sender.sendMessage(Component.text("Registered SkyRealms Modules:", NamedTextColor.GOLD));
            for (SkyModule module : moduleRegistry.all()) {
                NamedTextColor stateColor = switch (module.state()) {
                    case ENABLED -> NamedTextColor.GREEN;
                    case FAILED -> NamedTextColor.RED;
                    case DISABLED -> NamedTextColor.YELLOW;
                    default -> NamedTextColor.GRAY;
                };

                sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(module.id().value(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(" [" + module.state().name() + "]", stateColor))
                        .append(Component.text(" v" + module.metadata().version(), NamedTextColor.DARK_AQUA)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text("[SkyRealms] Reloading module configurations...", NamedTextColor.YELLOW));
            lifecycleManager.reloadAll();
            sender.sendMessage(Component.text("[SkyRealms] Modules reloaded successfully.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /" + label + " [status|modules|reload]", NamedTextColor.RED));
        return true;
    }

    private void sendHeader(CommandSender sender) {
        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("         SkyRealms System Hub           ", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("skyrealms.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> sub = Arrays.asList("status", "modules", "reload");
            List<String> result = new ArrayList<>();
            for (String s : sub) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
