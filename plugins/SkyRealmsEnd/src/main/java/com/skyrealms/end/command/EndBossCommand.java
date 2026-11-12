package com.skyrealms.end.command;

import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.items.service.ItemRegistryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Summons the Stormrake Wyvern (Vaelkor) boss encounter and provides prep/weapon test kits (`/endboss`).
 */
public final class EndBossCommand implements CommandExecutor, TabCompleter {

    private final ItemRegistryService itemService;

    public EndBossCommand(@NotNull ItemRegistryService itemService) {
        this.itemService = itemService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        String sub = args.length == 0 ? "vaelkor" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "vaelkor", "spawn", "wyvern" -> {
                Location spawnLoc;
                if (sender instanceof Player player) {
                    spawnLoc = player.getLocation().clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(10.0));
                    spawnLoc.setY(Math.max(player.getLocation().getY(), spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 1.0));
                } else {
                    var w = Bukkit.getWorld("fullya_end");
                    if (w == null) {
                        sender.sendMessage("World fullya_end is not loaded.");
                        return true;
                    }
                    spawnLoc = new Location(w, 0.5, 64.0, 12.5);
                }

                String mmCmd = String.format(Locale.US, "mm mobs spawn Vaelkor_StormrakeWyvern 1 %s,%.1f,%.1f,%.1f",
                        spawnLoc.getWorld().getName(), spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), mmCmd);
                sender.sendMessage(Component.text("☠ Summoned Vaelkor, the Stormrake Wyvern!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                return true;
            }
            case "enderloper" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Run in-game to summon near you.");
                    return true;
                }
                Location l = player.getLocation().add(4, 0, 4);
                String mmCmd = String.format(Locale.US, "mm mobs spawn Vaelkor_Enderloper 1 %s,%.1f,%.1f,%.1f",
                        l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), mmCmd);
                sender.sendMessage(Component.text("✦ Summoned Vaelkor's Enderloper!", NamedTextColor.LIGHT_PURPLE));
                return true;
            }
            case "prep", "weapons" -> {
                if (!sender.hasPermission("skyrealms.end.admin") || !(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Admin player required for /endboss weapons.", NamedTextColor.RED));
                    return true;
                }
                List<String> ids = List.of(
                        EndGearCatalog.RELIC_TEMPEST_WARD,
                        "end_stormrake_fangblade",
                        "end_galewing_longbow",
                        "end_tempest_conduit_staff",
                        "end_astral_executioner",
                        "end_astral_longbow",
                        "end_astral_archmage_staff"
                );
                for (String id : ids) {
                    itemService.createCustomItem(id, 1, 5).ifPresent(player.getInventory()::addItem);
                }
                player.getInventory().addItem(new ItemStack(Material.ARROW, 64));
                player.sendMessage(Component.text("✦ Granted all Stormrake & Astral Tier-5 End Weapons + Arrows!", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /endboss [vaelkor|enderloper|weapons]", NamedTextColor.YELLOW));
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
            return List.of("vaelkor", "enderloper", "weapons", "prep");
        }
        return List.of();
    }
}
