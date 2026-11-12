package com.skyrealms.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Administrative world loader & teleport command (/tpworld [world]) so custom worlds can be loaded and visited.
 */
public final class WorldCommand implements CommandExecutor, TabCompleter {

    private static final Pattern SAFE_WORLD_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    public static boolean isValidWorldName(@Nullable String name) {
        return name != null
                && !name.isBlank()
                && !name.contains("..")
                && SAFE_WORLD_NAME.matcher(name).matches();
    }

    /** Identifies custom void End worlds loaded via /tpworld or extra-worlds config. */
    public static boolean isEndWorldName(@NotNull String worldName) {
        String lower = worldName.toLowerCase(Locale.ROOT);
        return lower.endsWith("_end") || lower.endsWith("_the_end");
    }

    /** 100% empty void chunk generator preserving The End biome for sky/fog without terrain. */
    public static final class VoidEndChunkGenerator extends ChunkGenerator {
        private static final BiomeProvider END_BIOME_PROVIDER = new BiomeProvider() {
            @Override
            public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
                return Biome.THE_END;
            }

            @Override
            public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
                return List.of(Biome.THE_END);
            }
        };

        @Override
        public @NotNull BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
            return END_BIOME_PROVIDER;
        }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    @Nullable
    public static World loadWorldIfPresent(@NotNull String worldName) {
        if (!isValidWorldName(worldName)) {
            return null;
        }
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            return existing;
        }
        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (!folder.isDirectory() || !new File(folder, "level.dat").isFile()) {
            return null;
        }
        WorldCreator creator = new WorldCreator(worldName);
        if (isEndWorldName(worldName)) {
            creator.environment(World.Environment.THE_END);
            org.bukkit.plugin.Plugin endPlugin = Bukkit.getPluginManager().getPlugin("SkyRealmsEnd");
            ChunkGenerator customEndGen = (worldName.equalsIgnoreCase("fullya_end") && endPlugin != null)
                    ? endPlugin.getDefaultWorldGenerator(worldName, null)
                    : null;
            creator.generator(customEndGen != null ? customEndGen : new VoidEndChunkGenerator());
            creator.generateStructures(false);
        }
        return Bukkit.createWorld(creator);
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

        if (args.length == 0) {
            sender.sendMessage(Component.text("Loaded Worlds (click to teleport):", NamedTextColor.GOLD, TextDecoration.BOLD));
            for (World w : Bukkit.getWorlds()) {
                Component line = Component.text(" • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(w.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" [" + w.getEnvironment().name() + "]", NamedTextColor.GRAY))
                        .clickEvent(ClickEvent.runCommand("/tpworld " + w.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport to " + w.getName(), NamedTextColor.YELLOW)));
                sender.sendMessage(line);
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            World loaded = loadWorldIfPresent(args[0]);
            if (loaded != null) {
                sender.sendMessage(Component.text("Loaded world '" + loaded.getName() + "'.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("World '" + args[0] + "' not found.", NamedTextColor.RED));
            }
            return true;
        }

        String targetName = args[0];
        if (!isValidWorldName(targetName)) {
            sender.sendMessage(Component.text("Invalid world name: " + targetName, NamedTextColor.RED));
            return true;
        }

        World target = Bukkit.getWorld(targetName);
        if (target == null) {
            player.sendMessage(Component.text("⏳ Loading world '" + targetName + "'...", NamedTextColor.YELLOW));
            target = loadWorldIfPresent(targetName);
        }
        if (target == null) {
            player.sendMessage(Component.text("✖ World '" + targetName + "' not found (missing level.dat in server root).", NamedTextColor.RED));
            return true;
        }

        Location spawn = target.getSpawnLocation().clone();
        boolean interiorSafe = !spawn.clone().subtract(0, 1, 0).getBlock().isPassable()
                && spawn.getBlock().isPassable()
                && spawn.clone().add(0, 1, 0).getBlock().isPassable();
        int safeY = interiorSafe ? spawn.getBlockY() : Math.max(spawn.getBlockY(), target.getHighestBlockYAt(spawn) + 1);
        Location dest = new Location(target, spawn.getBlockX() + 0.5, safeY, spawn.getBlockZ() + 0.5, spawn.getYaw(), spawn.getPitch());
        player.teleportAsync(dest).thenAccept(ok -> {
            if (ok) {
                player.sendMessage(Component.text("✦ Teleported to world '" + dest.getWorld().getName() + "'.", NamedTextColor.GREEN));
            }
        });
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
        if (!sender.hasPermission("skyrealms.admin") || args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(w.getName());
            }
        }
        File[] dirs = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                String n = dir.getName();
                if (!matches.contains(n)
                        && isValidWorldName(n)
                        && new File(dir, "level.dat").isFile()
                        && n.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(n);
                }
            }
        }
        return matches;
    }
}

