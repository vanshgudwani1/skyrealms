package com.skyrealms.end.command;

import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.end.gear.EndGearCatalog.EndGearSpec;
import com.skyrealms.end.gear.EndGearService;
import com.skyrealms.end.gear.EndGearService.CraftResult;
import com.skyrealms.end.gear.EndGearService.RequirementCheckResult;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.model.ItemDomain;
import com.skyrealms.items.service.ItemRegistryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Command interface for End Civilization equipment progression, forging, and inspection (`/endgear`).
 */
public final class EndGearCommand implements CommandExecutor, TabCompleter {

    private final EndGearCatalog gearCatalog;
    private final EndGearService gearService;
    private final ItemRegistryService itemService;
    private final Random rng = new Random();

    public EndGearCommand(
            @NotNull EndGearCatalog gearCatalog,
            @NotNull EndGearService gearService,
            @NotNull ItemRegistryService itemService
    ) {
        this.gearCatalog = gearCatalog;
        this.gearService = gearService;
        this.itemService = itemService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            String filter = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
            showList(sender, filter);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "craft" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can craft gear into inventory.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /endgear craft <itemId>", NamedTextColor.RED));
                    return true;
                }
                CraftResult res = gearService.craftGear(player, args[1]);
                sender.sendMessage(Component.text(res.message(), res.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                return true;
            }
            case "inspect" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Hold an item in-game to inspect.");
                    return true;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                var idOpt = itemService.getCustomItemId(held);
                if (idOpt.isEmpty()) {
                    sender.sendMessage(Component.text("Not a registered SkyRealms custom item.", NamedTextColor.YELLOW));
                    return true;
                }
                RequirementCheckResult req = gearService.checkRequirement(player.getUniqueId(), held);
                String lockText = req.discipline() == null
                        ? "No discipline level gate"
                        : "Requires " + req.discipline().displayName() + " Lv." + req.requiredLevel()
                          + (req.allowed() ? " (UNLOCKED)" : " (LOCKED)");
                sender.sendMessage(Component.text("✦ Item ID: " + idOpt.get() + " | " + lockText,
                        req.allowed() ? NamedTextColor.GREEN : NamedTextColor.RED));
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("skyrealms.end.admin")) {
                    sender.sendMessage(Component.text("You lack permission to mint End items.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /endgear give <player> <itemId> [1..5|roll]", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player must be online: " + args[1], NamedTextColor.RED));
                    return true;
                }
                int level = 1;
                boolean rolled = args.length >= 4 && args[3].equalsIgnoreCase("roll");
                ItemStack stack;
                if (rolled) {
                    stack = gearService.mintRolledGear(args[2], rng, true).orElse(null);
                } else {
                    if (args.length >= 4) {
                        try {
                            level = Math.max(1, Math.min(5, Integer.parseInt(args[3])));
                        } catch (NumberFormatException ignored) {
                            level = 1;
                        }
                    }
                    stack = itemService.createCustomItem(args[2], 1, level).orElse(null);
                }
                if (stack == null) {
                    sender.sendMessage(Component.text("Unknown item id in universal ItemCatalog: " + args[2], NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(stack);
                sender.sendMessage(Component.text("Minted " + args[2] + " to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /endgear [list|craft <id>|inspect|give <player> <id>]", NamedTextColor.YELLOW));
                return true;
            }
        }
    }

    private void showList(CommandSender sender, String filter) {
        sender.sendMessage(Component.text("━━━ SkyRealms End Equipment Catalog (Universal Item Engine) ━━━",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        for (EndGearSpec spec : gearCatalog.allSpecs()) {
            if (!matchesFilter(spec, filter)) {
                continue;
            }
            CustomItemDefinition def = itemService.catalog().find(spec.itemId());
            if (def == null) {
                continue;
            }
            sender.sendMessage(
                    Component.text("• " + spec.itemId() + " ", NamedTextColor.YELLOW)
                            .append(Component.text("[" + def.displayName() + "] ", def.rarity().color()))
                            .append(Component.text("Req: " + spec.requiredDiscipline().displayName() + " Lv." + spec.requiredLevel(),
                                    NamedTextColor.GRAY))
            );
        }
        if ("all".equals(filter) || "materials".equals(filter)) {
            for (CustomItemDefinition def : itemService.catalog().byDomain(ItemDomain.UTILITY)) {
                if (def.id().startsWith("end_")) {
                    sender.sendMessage(Component.text("• " + def.id() + " [" + def.displayName() + "] (Material)", NamedTextColor.AQUA));
                }
            }
        }
    }

    private static boolean matchesFilter(EndGearSpec spec, String filter) {
        return switch (filter) {
            case "archery" -> spec.forgeCategory().equals("archery_bows") || spec.requiredDiscipline().name().equals("ARCHERY");
            case "melee" -> spec.forgeCategory().equals("melee_weapons") || spec.requiredDiscipline().name().equals("MELEE");
            case "mage" -> spec.forgeCategory().equals("mage_staffs") || spec.requiredDiscipline().name().equals("MAGE");
            case "armor" -> spec.forgeCategory().equals("melee_armor");
            case "materials" -> false;
            default -> true;
        };
    }

    @Override
    public @NotNull List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list", "craft", "inspect"));
            if (sender.hasPermission("skyrealms.end.admin")) {
                subs.add("give");
            }
            return filterPrefix(subs, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return filterPrefix(List.of("all", "archery", "melee", "mage", "armor", "materials"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("craft")) {
            List<String> ids = new ArrayList<>();
            for (EndGearSpec s : gearCatalog.allSpecs()) {
                ids.add(s.itemId());
            }
            return filterPrefix(ids, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            for (EndGearSpec s : gearCatalog.allSpecs()) {
                ids.add(s.itemId());
            }
            ids.addAll(List.of(EndGearCatalog.MAT_SHATTERED_SHARD, EndGearCatalog.MAT_CRYSTAL_ESSENCE,
                    EndGearCatalog.MAT_FORGOTTEN_CORE, EndGearCatalog.MAT_ABYSSAL_FRAGMENT));
            return filterPrefix(ids, args[2]);
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

