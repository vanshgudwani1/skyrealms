package com.skyrealms.end.gear;

import com.skyrealms.end.gear.EndGearCatalog.EndGearSpec;
import com.skyrealms.end.npc.NpcInteractionHandler;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressState.PlayerDisciplineSnapshot;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.service.ItemRegistryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Authoritative bridge integrating End Civilization progression requirements, RNG loot rolling,
 * and Blacksmith/Fletcher/Rune Enchanter crafting with the single universal {@link ItemRegistryService}.
 */
public final class EndGearService {

    public record RequirementCheckResult(
            boolean allowed,
            @Nullable CombatDiscipline discipline,
            int requiredLevel,
            int playerLevel,
            @Nullable String displayName
    ) {
        public static final RequirementCheckResult ALLOWED = new RequirementCheckResult(true, null, 0, 0, null);
    }

    public record CraftResult(
            boolean success,
            @NotNull String message,
            @Nullable ItemStack craftedItem
    ) {}

    private final EndGearCatalog gearCatalog;
    private final ItemRegistryService itemService;
    private final CombatProgressionService progressionService;

    public EndGearService(
            @NotNull EndGearCatalog gearCatalog,
            @NotNull ItemRegistryService itemService,
            @NotNull CombatProgressionService progressionService
    ) {
        this.gearCatalog = Objects.requireNonNull(gearCatalog, "gearCatalog cannot be null");
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
    }

    /**
     * Checks if a player meets the End discipline level requirement of a custom {@link ItemStack}.
     */
    @NotNull
    public RequirementCheckResult checkRequirement(@NotNull UUID playerUuid, @Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return RequirementCheckResult.ALLOWED;
        }
        Optional<String> idOpt = itemService.getCustomItemId(item);
        if (idOpt.isEmpty()) {
            return RequirementCheckResult.ALLOWED;
        }
        EndGearSpec spec = gearCatalog.spec(idOpt.get());
        if (spec == null) {
            return RequirementCheckResult.ALLOWED;
        }
        try {
            PlayerDisciplineSnapshot snap = progressionService.getOrLoadSnapshot(playerUuid);
            int actual = snap.get(spec.requiredDiscipline()).level();
            CustomItemDefinition def = itemService.catalog().find(spec.itemId());
            String label = def != null ? def.displayName() : spec.itemId();
            return new RequirementCheckResult(actual >= spec.requiredLevel(), spec.requiredDiscipline(),
                    spec.requiredLevel(), actual, label);
        } catch (SQLException ex) {
            return RequirementCheckResult.ALLOWED;
        }
    }

    /**
     * Mints an authoritative custom item through {@link ItemRegistryService} with RNG quality scaling.
     */
    @NotNull
    public Optional<ItemStack> mintRolledGear(@NotNull String itemId, @NotNull Random rng, boolean eliteOrBoss) {
        int quality = EndGearCatalog.rollQualityLevel(rng, eliteOrBoss);
        return itemService.createCustomItem(itemId, 1, quality);
    }

    /**
     * Authoritatively crafts an End weapon or armor piece: checks inventory space, discipline level,
     * and custom material counts before atomically consuming materials and granting the crafted gear.
     */
    @NotNull
    public CraftResult craftGear(@NotNull Player player, @NotNull String itemId) {
        EndGearSpec spec = gearCatalog.spec(itemId);
        CustomItemDefinition def = itemService.catalog().find(itemId);
        if (spec == null || def == null) {
            return new CraftResult(false, "Unknown craftable End gear: " + itemId, null);
        }

        RequirementCheckResult req = checkRequirementForSpec(player.getUniqueId(), spec, def.displayName());
        if (!req.allowed()) {
            return new CraftResult(false,
                    "Requires " + spec.requiredDiscipline().displayName() + " Level " + spec.requiredLevel()
                            + " (you are Lv." + req.playerLevel() + ").",
                    null);
        }

        PlayerInventory inv = player.getInventory();
        if (inv.firstEmpty() == -1) {
            return new CraftResult(false, "Free at least 1 inventory slot before crafting.", null);
        }

        // Verify all required End custom reagents exist in inventory
        for (Map.Entry<String, Integer> entry : spec.materialCosts().entrySet()) {
            int found = countCustomItem(inv, entry.getKey());
            if (found < entry.getValue()) {
                CustomItemDefinition matDef = itemService.catalog().find(entry.getKey());
                String matName = matDef != null ? matDef.displayName() : entry.getKey();
                return new CraftResult(false,
                        "Missing " + (entry.getValue() - found) + "x " + matName + ".",
                        null);
            }
        }

        // Atomically consume ingredients before granting result
        for (Map.Entry<String, Integer> entry : spec.materialCosts().entrySet()) {
            consumeCustomItem(inv, entry.getKey(), entry.getValue());
        }

        ItemStack created = itemService.createCustomItem(itemId, 1, 1).orElse(null);
        if (created == null) {
            return new CraftResult(false, "Failed creating item from ItemCatalog.", null);
        }
        inv.addItem(created);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
        return new CraftResult(true, "Crafted " + def.displayName() + "!", created);
    }

    @NotNull
    public NpcInteractionHandler craftingNpcHandler() {
        return (player, npc) -> {
            List<EndGearSpec> list = gearCatalog.byForgeCategory(npc.actionTarget());
            player.sendMessage(Component.text("━━━ " + npc.displayName() + " Forge Recipes ━━━",
                    NamedTextColor.GOLD, TextDecoration.BOLD));
            if (list.isEmpty()) {
                player.sendMessage(Component.text("No recipes assigned to category: " + npc.actionTarget(), NamedTextColor.GRAY));
                return;
            }
            for (EndGearSpec spec : list) {
                CustomItemDefinition def = itemService.catalog().find(spec.itemId());
                if (def == null) {
                    continue;
                }
                RequirementCheckResult req = checkRequirementForSpec(player.getUniqueId(), spec, def.displayName());
                String matsText = formatCosts(spec.materialCosts());
                Component row = Component.text(req.allowed() ? " [CRAFT] " : " [Lv." + spec.requiredLevel() + "] ",
                                req.allowed() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                        .append(Component.text(def.displayName(), def.rarity().color(), TextDecoration.BOLD))
                        .append(Component.text(" — " + matsText, NamedTextColor.GRAY));
                if (req.allowed()) {
                    row = row.clickEvent(ClickEvent.runCommand("/endgear craft " + spec.itemId()))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to craft " + def.displayName(), NamedTextColor.YELLOW)));
                }
                player.sendMessage(row);
            }
        };
    }

    private RequirementCheckResult checkRequirementForSpec(UUID uuid, EndGearSpec spec, String displayName) {
        try {
            int actual = progressionService.getOrLoadSnapshot(uuid).get(spec.requiredDiscipline()).level();
            return new RequirementCheckResult(actual >= spec.requiredLevel(), spec.requiredDiscipline(),
                    spec.requiredLevel(), actual, displayName);
        } catch (SQLException ex) {
            return RequirementCheckResult.ALLOWED;
        }
    }

    public int countCustomItem(@NotNull PlayerInventory inv, @NotNull String customId) {
        int count = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            Optional<String> id = itemService.getCustomItemId(stack);
            if (id.isPresent() && id.get().equalsIgnoreCase(customId)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    public boolean consumeCustomItem(@NotNull PlayerInventory inv, @NotNull String customId, int amountToConsume) {
        int remaining = amountToConsume;
        for (int slot = 0; slot < inv.getSize() && remaining > 0; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            Optional<String> id = itemService.getCustomItemId(stack);
            if (id.isPresent() && id.get().equalsIgnoreCase(customId)) {
                int take = Math.min(remaining, stack.getAmount());
                remaining -= take;
                int left = stack.getAmount() - take;
                inv.setItem(slot, left > 0 ? stack.asQuantity(left) : null);
            }
        }
        return remaining == 0;
    }

    private String formatCosts(Map<String, Integer> costs) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            CustomItemDefinition def = itemService.catalog().find(entry.getKey());
            String name = def != null ? def.displayName() : entry.getKey();
            sb.append(entry.getValue()).append("x ").append(name);
        }
        return sb.toString();
    }
}

