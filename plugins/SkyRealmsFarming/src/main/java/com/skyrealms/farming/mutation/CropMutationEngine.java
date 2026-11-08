package com.skyrealms.farming.mutation;

import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.GeneratorUpgrade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Deterministic, testable crop mutation engine.
 */
public final class CropMutationEngine {

    public static final NamespacedKey MUTATION_KEY = new NamespacedKey("skyrealms", "mutation_type");

    private final double baseMutationChance;
    private final double bonusChancePerUpgrade;
    private final Map<CropMutationType, Integer> mutationWeights = new EnumMap<>(CropMutationType.class);

    public CropMutationEngine(double baseMutationChance, double bonusChancePerUpgrade) {
        this.baseMutationChance = Math.max(0.0, baseMutationChance);
        this.bonusChancePerUpgrade = Math.max(0.0, bonusChancePerUpgrade);

        // Default weights
        mutationWeights.put(CropMutationType.GOLDEN, 40);
        mutationWeights.put(CropMutationType.GIANT, 30);
        mutationWeights.put(CropMutationType.ANCIENT, 15);
        mutationWeights.put(CropMutationType.FERTILE, 10);
        mutationWeights.put(CropMutationType.MAGICAL, 5);
    }

    public CropMutationEngine() {
        this(0.015, 0.005);
    }

    public void setWeight(@NotNull CropMutationType type, int weight) {
        mutationWeights.put(type, Math.max(1, weight));
    }

    /**
     * Evaluates whether a harvested crop mutates.
     */
    @NotNull
    public Optional<CropMutationType> evaluateMutation(@NotNull FarmingGenerator generator, @NotNull Random random) {
        int mutationUpgradeLevel = generator.getUpgradeLevel(GeneratorUpgrade.KEY_MUTATION);
        double chance = baseMutationChance + (mutationUpgradeLevel * bonusChancePerUpgrade);

        if (random.nextDouble() > chance) {
            return Optional.empty();
        }

        int totalWeight = mutationWeights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            return Optional.empty();
        }

        int roll = random.nextInt(totalWeight);
        int running = 0;
        for (Map.Entry<CropMutationType, Integer> entry : mutationWeights.entrySet()) {
            running += entry.getValue();
            if (roll < running) {
                return Optional.of(entry.getKey());
            }
        }

        return Optional.of(CropMutationType.GOLDEN);
    }

    /**
     * Creates a custom mutated crop ItemStack.
     */
    @NotNull
    public ItemStack createMutatedItem(@NotNull Material baseMaterial, @NotNull CropMutationType mutation, int amount) {
        ItemStack item = new ItemStack(baseMaterial, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Custom Title
        String rawName = formatMaterialName(baseMaterial);
        Component displayName = Component.text("✦ " + mutation.displayName() + " " + rawName, mutation.color())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
        meta.displayName(displayName);

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Rarity: ", NamedTextColor.GRAY)
                .append(Component.text("★ RARE MUTATION ★", mutation.color(), TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text(mutation.description(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (mutation.valueMultiplier() > 1.0) {
            lore.add(Component.text("• Sell Value: ", NamedTextColor.GRAY)
                    .append(Component.text("+" + (int) ((mutation.valueMultiplier() - 1.0) * 100) + "%", NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (mutation.xpMultiplier() > 1.0) {
            lore.add(Component.text("• Farming XP: ", NamedTextColor.GRAY)
                    .append(Component.text("+" + (int) ((mutation.xpMultiplier() - 1.0) * 100) + "%", NamedTextColor.LIGHT_PURPLE))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (mutation.dropMultiplier() > 1.0) {
            lore.add(Component.text("• Crop Yield: ", NamedTextColor.GRAY)
                    .append(Component.text("+" + (int) ((mutation.dropMultiplier() - 1.0) * 100) + "%", NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("SkyRealms Farming Generator", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        // Enchantment glint & flags
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        // Tag with PDC
        meta.getPersistentDataContainer().set(MUTATION_KEY, PersistentDataType.STRING, mutation.name());

        item.setItemMeta(meta);
        return item;
    }

    private static String formatMaterialName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
