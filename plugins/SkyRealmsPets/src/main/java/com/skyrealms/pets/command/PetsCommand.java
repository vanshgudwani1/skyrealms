package com.skyrealms.pets.command;

import com.skyrealms.pets.gui.PetsGui;
import com.skyrealms.pets.model.PetDefinition;
import com.skyrealms.pets.service.PetService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Command handler for /pets (/pet, /cubees, /cubee).
 */
public final class PetsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "skyrealms.pets.admin";

    private final PetService petService;

    public PetsCommand(@NotNull PetService petService) {
        this.petService = Objects.requireNonNull(petService, "petService cannot be null");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                PetsGui.open(player, petService);
            } else {
                sendList(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can open the Pets GUI.", NamedTextColor.RED));
                    return true;
                }
                PetsGui.open(player, petService);
            }
            case "dismiss", "recall" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can recall pets.", NamedTextColor.RED));
                    return true;
                }
                petService.dismissActivePet(player, true);
            }
            case "rename" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can rename their pet.", NamedTextColor.RED));
                    return true;
                }
                String name = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
                if (petService.renameActivePet(player, name)) {
                    player.sendMessage(Component.text(
                            name != null ? "✦ Renamed your active pet to: " + name : "✦ Reset your active pet's name.",
                            NamedTextColor.GREEN
                    ));
                } else {
                    player.sendMessage(Component.text("✖ Summon a pet first before renaming it.", NamedTextColor.RED));
                }
            }
            case "give", "unlock" -> handleAdminGive(sender, args);
            case "list" -> sendList(sender);
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use /pets help.", NamedTextColor.RED));
        }
        return true;
    }

    private void handleAdminGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION) && !sender.isOp()) {
            sender.sendMessage(Component.text("You lack permission " + ADMIN_PERMISSION, NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /pets give <player> <petId|all>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player must be online: " + args[1], NamedTextColor.RED));
            return;
        }
        String targetPet = args[2].toLowerCase(Locale.ROOT);
        if (targetPet.equals("all")) {
            for (PetDefinition def : petService.catalog().all()) {
                petService.grantPetFree(target, def);
            }
            sender.sendMessage(Component.text("✦ Unlocked all 8 Cubees for " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text("✦ All 8 Cubees pets have been unlocked for you! Open /pets.", NamedTextColor.GOLD));
            return;
        }
        PetDefinition def = petService.catalog().find(targetPet);
        if (def == null) {
            sender.sendMessage(Component.text("Unknown pet ID: " + targetPet, NamedTextColor.RED));
            return;
        }
        petService.grantPetFree(target, def);
        sender.sendMessage(Component.text("✦ Granted " + def.displayName() + " to " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("✦ You received " + def.displayName() + "! Open /pets to summon it.", NamedTextColor.GOLD));
    }

    private void sendList(CommandSender sender) {
        sender.sendMessage(Component.text("✦ SkyRealms Cubees Pets (" + petService.catalog().all().size() + ")",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        for (PetDefinition def : petService.catalog().all()) {
            sender.sendMessage(Component.text("  • " + def.id(), def.color())
                    .append(Component.text(" — " + def.displayName() + " (" + def.rarityLabel() + ")", NamedTextColor.GRAY)));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("✦ SkyRealms Pets Commands", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("  /pets", NamedTextColor.YELLOW).append(Component.text(" — Open Cubees Pets GUI", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /pets dismiss", NamedTextColor.YELLOW).append(Component.text(" — Recall your active pet", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /pets rename <name>", NamedTextColor.YELLOW).append(Component.text(" — Rename your active pet", NamedTextColor.GRAY)));
        if (sender.hasPermission(ADMIN_PERMISSION) || sender.isOp()) {
            sender.sendMessage(Component.text("  /pets give <player> <petId|all>", NamedTextColor.RED).append(Component.text(" — Unlock pet(s) for a player", NamedTextColor.GRAY)));
        }
    }

    @Override
    @NotNull
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("gui", "dismiss", "rename", "list", "help"));
            if (sender.hasPermission(ADMIN_PERMISSION) || sender.isOp()) {
                base.add("give");
            }
            return filterPrefix(base, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock"))) {
            return null;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock"))) {
            List<String> ids = new ArrayList<>(petService.catalog().all().stream().map(PetDefinition::id).toList());
            ids.add("all");
            return filterPrefix(ids, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(opt -> opt.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

